package dev.flycolony.bridge;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Bounded, disposable farming plots. Observations contain facts; scores are separate. */
public final class FarmArena {
 public static final int CX=192,FLOOR=249,R=10;
 private static int cx=CX,cz=0,floor=FLOOR,radius=R;private static boolean homestead;
 private static MinecraftServer owner;private static UUID playerId;private static String lesson;private static long seed,startTick;
 private static int oldRandomTicks,lastMined,initialSeeds,restocks,maintenanceRounds;private static boolean restored=true;
 private static FarmLedger ledger;private static final List<BlockPos> plots=new ArrayList<>();
 private static BlockPos waterPos=new BlockPos(cx,floor,0),chestPos=new BlockPos(cx+5,floor+1,0);
 private static int count(ServerPlayerEntity p,Item item){int n=0;for(int i=0;i<p.getInventory().size();i++){var s=p.getInventory().getStack(i);if(s.isOf(item))n+=s.getCount();}return n;}
 private static int mined(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.WHEAT));}
 private static String id(BlockPos p){return (p.getX()-cx)+":"+(p.getZ()-cz);}
 private static FarmLedger.Cell cell(MinecraftServer s,BlockPos at){var w=s.getOverworld();var soil=w.getBlockState(at);var crop=w.getBlockState(at.up());return new FarmLedger.Cell(soil.isOf(Blocks.FARMLAND),crop.getBlock() instanceof CropBlock,crop.getBlock() instanceof CropBlock c?c.getAge(crop):0,crop.getBlock() instanceof CropBlock c?c.getMaxAge():7);}
 public static void restore(MinecraftServer server){if(owner==server&&!restored){server.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(oldRandomTicks,server);restored=true;}}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,String name,long trialSeed,int randomTicks,int rounds){return reset(s,p,name,trialSeed,randomTicks,rounds,"school");}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,String name,long trialSeed,int randomTicks,int rounds,String environment){
  if(!environment.equals("school")&&!environment.equals("homestead"))throw new IllegalArgumentException("Unknown farm environment");
  if(environment.equals("homestead")&&!name.equals("establish"))throw new IllegalArgumentException("Homestead uses the supplied build-and-tend lesson");
  FarmLesson.validate(s.getSaveProperties().getLevelName(),name,trialSeed,randomTicks);FarmLesson.validateRounds(name,rounds);maintenanceRounds=rounds;
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  restore(s);homestead=environment.equals("homestead");cx=homestead?HomesteadPlan.CX:CX;cz=homestead?HomesteadPlan.CZ:0;floor=homestead?Homestead.floor(s.getOverworld()):FLOOR;radius=homestead?HomesteadPlan.YARD:R;waterPos=new BlockPos(cx,floor,cz);chestPos=new BlockPos(cx+5,floor+1,cz);owner=s;playerId=p.getUuid();lesson=name;seed=trialSeed;ledger=new FarmLedger();plots.clear();restocks=0;
  var w=s.getOverworld();oldRandomTicks=w.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);restored=false;
  w.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(randomTicks,s);
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
  w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  for(var e:w.getOtherEntities(p,new Box(cx-radius-1,floor-4,cz-radius-1,cx+radius+2,floor+16,cz+radius+2),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  if(homestead)Homestead.build(w,floor);
  else for(int x=cx-radius;x<=cx+radius;x++)for(int z=-radius;z<=radius;z++)for(int y=245;y<=257;y++){
   boolean edge=Math.abs(x-cx)==radius||Math.abs(z)==radius;
   var b=y==245?Blocks.BEDROCK:y<floor?Blocks.DIRT:y==floor?((name.equals("bed")||name.equals("maintain")||name.equals("establish"))?Blocks.GRASS_BLOCK:Blocks.STONE_BRICKS):edge&&y<=252?Blocks.GLASS:Blocks.AIR;
   w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);
  }
  var random=new Random(seed);boolean establish=name.equals("establish"),large=name.equals("maintain")||establish;
  if(name.equals("bed")){
   for(var tile:FarmBed.layout(seed))plots.add(new BlockPos(cx+tile.x(),floor,cz+tile.z()));
  }else{
   for(int z=-3;z<=3;z++)for(int x=-3;x<=3;x++)if((large||Math.abs(x)>=1&&Math.abs(x)<=2&&Math.abs(z)>=1&&Math.abs(z)<=2)&&!(x==0&&z==0))plots.add(new BlockPos(cx+x,floor,cz+z));
   Collections.shuffle(plots,random);if(!large)plots.subList(4,plots.size()).clear();
  }
  w.setBlockState(waterPos,((name.equals("irrigate")||establish)?Blocks.AIR:Blocks.WATER).getDefaultState(),3);
  // Under-basin garden lighting prevents freezing in cold natural biomes; no water is supplied.
  if(homestead)w.setBlockState(waterPos.down(),Blocks.SEA_LANTERN.getDefaultState(),3);
  for(int i=0;i<plots.size();i++){
   var at=plots.get(i);boolean dirt=establish||name.equals("till")||name.equals("supplies")||name.equals("bed");
   w.setBlockState(at,dirt?Blocks.DIRT.getDefaultState():Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,name.equals("irrigate")?0:7),3);
   if(name.equals("harvest")||name.equals("cycle")||name.equals("maintain")){int age=large?(seed%3==0?1+random.nextInt(3):i<2?7:1+random.nextInt(6)):i<(name.equals("harvest")?1:3)?7:1+random.nextInt(5);w.setBlockState(at.up(),Blocks.WHEAT.getDefaultState().with(CropBlock.AGE,age),3);}
   ledger.initialize(id(at),cell(s,at));
  }
  w.setBlockState(chestPos,Blocks.CHEST.getDefaultState(),3);if(w.getBlockEntity(chestPos) instanceof ChestBlockEntity chest){chest.clear();chest.setStack(0,new ItemStack(Items.WHEAT_SEEDS,64));chest.setStack(1,new ItemStack(Items.IRON_HOE));if(establish)chest.setStack(2,new ItemStack(Items.WATER_BUCKET));chest.markDirty();}
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);
  if(!name.equals("supplies")&&!establish){p.getInventory().setStack(0,new ItemStack(Items.IRON_HOE));p.getInventory().setStack(1,new ItemStack(Items.WHEAT_SEEDS,64));}
  if(name.equals("irrigate"))p.getInventory().setStack(2,new ItemStack(Items.WATER_BUCKET));
  p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;
  int side=(int)(seed%4);double angle=side*Math.PI/2;double spawnRadius=(name.equals("bed")||establish)?6:5;double x=cx+.5+Math.sin(angle)*spawnRadius,z=cz+.5-Math.cos(angle)*spawnRadius;
  p.teleport(w,x+(random.nextDouble()-.5)*.5,floor+1,z,Set.of(),side*90f+(random.nextFloat()-.5f)*40,15,true);p.currentScreenHandler.sendContentUpdates();
  p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  // Replacing old crops and containers can spawn fresh drops during construction.
  // Clear after the rebuild and inventory/screen reset, before any scored action.
  for(var e:w.getEntitiesByClass(ItemEntity.class,new Box(cx-radius-1,floor-4,cz-radius-1,cx+radius+2,floor+16,cz+radius+2),e->true))e.discard();
  lastMined=mined(p);initialSeeds=count(p,Items.WHEAT_SEEDS);startTick=w.getTime();
  var result=new JsonObject();result.addProperty("schema","fly-farm-fixture-1");result.addProperty("environment",environment);result.addProperty("sceneryVersion",homestead?1:0);result.addProperty("lesson",lesson);result.addProperty("seed",seed);result.addProperty("cells",plots.size());result.addProperty("randomTickSpeed",randomTicks);result.addProperty("trainingOnly",true);
  result.addProperty("layout",establish?"establishment_bed":name.equals("bed")?"connected_bed":large?"maintenance_bed":"isolated_tiles");if(large){result.addProperty(establish?"establishmentRounds":"maintenanceRounds",maintenanceRounds);result.addProperty("targetStored",establish?FarmEstablishment.targetStored(maintenanceRounds):FarmMaintenance.targetHarvests(maintenanceRounds));}
  result.addProperty("assistance",(homestead?"Supplied cottage farmstead at x=1024,z=1024; prepared basin;":"Dedicated farm at x=192;")+" fixed daylight; supplied tools/seeds or stocked chest; seeded crop ages; native survival interactions. Growth setting explicit. Learner chooses farm jobs; shared scripted motor controller walks, aims and clicks. No video.");return result;
 }
 public static void track(MinecraftServer s){
  if(owner!=s||ledger==null)return;var p=s.getPlayerManager().getPlayer(playerId);if(p==null||p.getServerWorld()!=s.getOverworld())return;
  int nowMined=mined(p),budget=Math.max(0,nowMined-lastMined);lastMined=nowMined;
  // Track once per server tick, before HTTP sampling can skip break/replant transitions.
  for(var at:plots){var now=cell(s,at);boolean credited=budget>0&&!now.crop();int before=ledger.matureHarvests+ledger.immatureBreaks;ledger.update(id(at),now,credited);if(ledger.matureHarvests+ledger.immatureBreaks>before)budget--;}
  if(initialSeeds==0&&count(p,Items.WHEAT_SEEDS)>0)restocks=1;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&ledger!=null&&p.getUuid().equals(playerId)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-cx)<=radius+3&&Math.abs(p.getZ()-cz)<=radius+3;}
 private static JsonObject position(BlockPos at){var j=new JsonObject();j.addProperty("x",at.getX());j.addProperty("y",at.getY());j.addProperty("z",at.getZ());return j;}
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var result=new JsonObject();result.addProperty("schema","fly-farm-senses-1");result.addProperty("environment",homestead?"homestead":"school");result.addProperty("sceneryVersion",homestead?1:0);result.addProperty("capturedAt",System.currentTimeMillis());result.addProperty("worldTick",w.getTime());result.addProperty("randomTickSpeed",w.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED));result.add("origin",position(new BlockPos(cx-3,floor,cz-3)));result.addProperty("width",7);
  var tiles=new JsonArray();
  for(var at:plots){var soil=w.getBlockState(at);var crop=w.getBlockState(at.up());var row=new JsonObject();row.addProperty("id",id(at));row.add("position",position(at));if(lesson.equals("maintain")||lesson.equals("establish")){row.addProperty("harvests",ledger.harvests(id(at)));row.addProperty("replants",ledger.replants(id(at)));}row.addProperty("soil",Registries.BLOCK.getId(soil.getBlock()).toString());row.addProperty("moisture",soil.isOf(Blocks.FARMLAND)?soil.get(FarmlandBlock.MOISTURE):0);row.addProperty("hydrated",soil.isOf(Blocks.FARMLAND)&&soil.get(FarmlandBlock.MOISTURE)>0);row.addProperty("crop",crop.getBlock() instanceof CropBlock?Registries.BLOCK.getId(crop.getBlock()).toString():"");row.addProperty("empty",crop.isAir());row.addProperty("age",crop.getBlock() instanceof CropBlock c?c.getAge(crop):0);row.addProperty("maxAge",crop.getBlock() instanceof CropBlock c?c.getMaxAge():7);row.addProperty("mature",crop.getBlock() instanceof CropBlock c&&c.isMature(crop));row.addProperty("light",w.getBaseLightLevel(at.up(),0));boolean water=false;for(int dx=-4;dx<=4&&!water;dx++)for(int dz=-4;dz<=4&&!water;dz++)for(int dy=0;dy<=1;dy++)if(w.getFluidState(at.add(dx,dy,dz)).isIn(net.minecraft.registry.tag.FluidTags.WATER))water=true;row.addProperty("waterInRange",water);tiles.add(row);}
  result.addProperty("layout",lesson.equals("establish")?"establishment_bed":lesson.equals("bed")?"connected_bed":lesson.equals("maintain")?"maintenance_bed":"isolated_tiles");
  result.add("tiles",tiles);var basin=position(waterPos);basin.addProperty("filled",w.getFluidState(waterPos).isIn(net.minecraft.registry.tag.FluidTags.WATER));result.add("basin",basin);
  var chest=position(chestPos);var contents=new JsonArray();if(w.getBlockEntity(chestPos) instanceof ChestBlockEntity box)for(int i=0;i<box.size();i++)if(!box.getStack(i).isEmpty())contents.add(WorldObservation.item(box.getStack(i),i));chest.addProperty("size",27);chest.add("items",contents);result.add("chest",chest);if(lesson.equals("maintain"))result.add("maintenance",maintenance());if(lesson.equals("establish"))result.add("establishment",establishment());return result;
 }
 private static JsonObject maintenance(){var m=new JsonObject();m.addProperty("schema","fly-farm-maintenance-1");m.addProperty("requiredRounds",maintenanceRounds);m.addProperty("targetStored",FarmMaintenance.targetHarvests(maintenanceRounds));m.addProperty("completedRounds",ledger.completedRounds());m.addProperty("regrownHarvests",ledger.regrownHarvests);m.addProperty("servicedPlots",ledger.servicedPlots());return m;}
 private static JsonObject establishment(){var m=new JsonObject();m.addProperty("schema","fly-farm-establishment-1");m.addProperty("requiredRounds",maintenanceRounds);m.addProperty("targetStored",FarmEstablishment.targetStored(maintenanceRounds));m.addProperty("completedRounds",ledger.completedRounds());m.addProperty("servicedPlots",ledger.servicedPlots());m.addProperty("tilled",ledger.tilled());m.addProperty("uniquePlanted",ledger.planted());m.addProperty("restocks",restocks);return m;}
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare a farm first and remain inside it");track(s);var w=s.getOverworld();int planted=0,hydrated=0;for(var at:plots){if(cell(s,at).crop())planted++;var b=w.getBlockState(at);if(b.isOf(Blocks.FARMLAND)&&b.get(FarmlandBlock.MOISTURE)>0)hydrated++;}
  int produce=count(p,Items.WHEAT),stored=0;if(w.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)for(int i=0;i<chest.size();i++){var stack=chest.getStack(i);if(stack.isOf(Items.WHEAT))stored+=stack.getCount();}
  boolean water=w.getFluidState(waterPos).isIn(net.minecraft.registry.tag.FluidTags.WATER);boolean success=lesson.equals("establish")?FarmEstablishment.success(maintenanceRounds,ledger.tilled(),ledger.planted(),restocks,ledger.completedRounds(),ledger.matureHarvests,ledger.replants,planted,stored,count(p,Items.WHEAT_SEEDS),water,ledger.immatureBreaks,ledger.tramples):lesson.equals("maintain")?FarmMaintenance.success(maintenanceRounds,ledger.completedRounds(),ledger.regrownHarvests,ledger.matureHarvests,ledger.replants,ledger.servicedPlots(),planted,stored,count(p,Items.WHEAT_SEEDS),water,ledger.immatureBreaks,ledger.tramples):FarmLesson.success(lesson,planted,ledger.tilled(),ledger.matureHarvests,ledger.immatureBreaks,ledger.replants,produce+stored,restocks,water,plots.size())&&ledger.tramples==0;
  var j=new JsonObject();j.addProperty("schema","fly-farm-evaluation-1");j.addProperty("privilegedEvaluatorOnly",true);j.addProperty("lesson",lesson);j.addProperty("seed",seed);j.addProperty("elapsedTicks",w.getTime()-startTick);j.addProperty("cells",plots.size());j.addProperty("planted",planted);j.addProperty("hydrated",hydrated);j.addProperty("tilled",ledger.tilled());j.addProperty("uniquePlanted",ledger.planted());j.addProperty("matureHarvests",ledger.matureHarvests);j.addProperty("immatureBreaks",ledger.immatureBreaks);j.addProperty("replanted",ledger.replants);j.addProperty("tramples",ledger.tramples);j.addProperty("produce",produce+stored);j.addProperty("stored",stored);j.addProperty("seeds",count(p,Items.WHEAT_SEEDS));j.addProperty("restocks",restocks);j.addProperty("water",water);j.addProperty("lessonSuccess",success);if(lesson.equals("maintain")){j.addProperty("requiredRounds",maintenanceRounds);j.addProperty("completedRounds",ledger.completedRounds());j.addProperty("regrownHarvests",ledger.regrownHarvests);j.addProperty("servicedPlots",ledger.servicedPlots());j.addProperty("targetStored",FarmMaintenance.targetHarvests(maintenanceRounds));}if(lesson.equals("establish")){j.addProperty("requiredRounds",maintenanceRounds);j.addProperty("completedRounds",ledger.completedRounds());j.addProperty("servicedPlots",ledger.servicedPlots());j.addProperty("targetStored",FarmEstablishment.targetStored(maintenanceRounds));}return j;
 }
 private FarmArena(){}
}
