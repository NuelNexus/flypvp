package dev.flycolony.bridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** One continuous attempt. World/inventory writes occur only in reset. */
public final class SeedFarmArena {
 public static final int CX=HomesteadPlan.CX,CZ=HomesteadPlan.CZ,R=19;
 private static int FLOOR;
 private static MinecraftServer owner;private static UUID player;private static long seed,startTick;
 private static FarmLedger ledger;private static int lastWheat,uncreditedLoss,seedsBeforePlant,oldRandomTicks;private static boolean restored=true;
 private static final Map<String,Integer> initial=new HashMap<>();
 private static final Map<String,Boolean> previousCrops=new HashMap<>();
 private static final List<BlockPos> tiles=new ArrayList<>(),wood=new ArrayList<>(),grass=new ArrayList<>(),sites=new ArrayList<>();
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 private static String id(BlockPos p){return p.getX()+":"+p.getY()+":"+p.getZ();}
 private static int used(ServerPlayerEntity p,Item item){return p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));}
 private static int mined(ServerPlayerEntity p,Block b){return p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(b));}
 private static int crafted(ServerPlayerEntity p,Item i){return p.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(i));}
 private static int count(ServerPlayerEntity p,Item item){int n=0;for(int i=0;i<p.getInventory().size();i++){var v=p.getInventory().getStack(i);if(v.isOf(item))n+=v.getCount();}return n;}
 private static Map<String,Integer> stats(ServerPlayerEntity p){
  var m=new HashMap<String,Integer>();m.put("minedLogs",mined(p,Blocks.OAK_LOG));m.put("grassBroken",mined(p,Blocks.SHORT_GRASS));m.put("dug",mined(p,Blocks.DIRT)+mined(p,Blocks.GRASS_BLOCK));m.put("seedUses",used(p,Items.WHEAT_SEEDS));m.put("bucketUses",used(p,Items.WATER_BUCKET));
  m.put("seedsPicked",p.getStatHandler().getStat(Stats.PICKED_UP.getOrCreateStat(Items.WHEAT_SEEDS)));m.put("deaths",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)));
  m.put("craftedPlanks",crafted(p,Items.OAK_PLANKS));m.put("craftedSticks",crafted(p,Items.STICK));m.put("craftedTables",crafted(p,Items.CRAFTING_TABLE));m.put("craftedHoes",crafted(p,Items.WOODEN_HOE));m.put("craftedChests",crafted(p,Items.CHEST));
  int other=0;for(var b:Registries.BLOCK)if(b!=Blocks.SHORT_GRASS&&b!=Blocks.DIRT&&b!=Blocks.GRASS_BLOCK&&b!=Blocks.WHEAT)other+=mined(p,b);m.put("otherBreaks",other);return m;
 }
 private static int delta(ServerPlayerEntity p,String key){return stats(p).get(key)-initial.get(key);}
 private static Box bounds(){return new Box(CX-R-1,FLOOR-4,CZ-R-1,CX+R+2,FLOOR+17,CZ+R+2);}
 public static void restore(MinecraftServer s){if(owner==s&&!restored){s.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(oldRandomTicks,s);restored=true;}}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed){
  SiteLesson.validate(s.getSaveProperties().getLevelName(),trialSeed);
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  FarmArena.restore(s);PipelineArena.restore(s);restore(s);owner=s;player=p.getUuid();seed=trialSeed;tiles.clear();wood.clear();grass.clear();sites.clear();previousCrops.clear();ledger=new FarmLedger();uncreditedLoss=0;seedsBeforePlant=-1;var w=s.getOverworld();FLOOR=w.getBottomY()+6;
  oldRandomTicks=w.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);restored=false;w.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(60,s);
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);w.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,s);w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  for(var e:w.getOtherEntities(p,bounds(),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  Homestead.build(w,FLOOR);
  // Remove decorative pond: site selection must begin without existing water.
  for(int x=-16;x<=-11;x++)for(int z=13;z<=17;z++)w.setBlockState(new BlockPos(CX+x,FLOOR,CZ+z),Blocks.GRASS_BLOCK.getDefaultState(),3);
  for(var t:SeedFarmLesson.tiles(seed)){
   var pos=new BlockPos(CX+t.x(),FLOOR,CZ+t.z());tiles.add(pos);
   // Real soil and a clear top, with full support and warm subsurface light.
   w.setBlockState(pos.down(),Blocks.SEA_LANTERN.getDefaultState(),3);
   w.setBlockState(pos,(t.dirt()?Blocks.DIRT:Blocks.STONE).getDefaultState(),3);
   w.setBlockState(pos.up(),Blocks.AIR.getDefaultState(),3);w.setBlockState(pos.up(2),Blocks.AIR.getDefaultState(),3);
   ledger.initialize(id(pos),new FarmLedger.Cell(false,false,0,7));previousCrops.put(id(pos),false);
  }
  for(var c:SeedFarmLesson.grass(seed)){
   var pos=new BlockPos(CX+c.x(),FLOOR+1,CZ+c.z());grass.add(pos);
   if(!w.getBlockState(pos).isAir())throw new IllegalStateException("Scenery obstructs seed field at "+pos);
   w.setBlockState(pos.down(),Blocks.GRASS_BLOCK.getDefaultState(),3);w.setBlockState(pos,Blocks.SHORT_GRASS.getDefaultState(),3);
  }
  var chestPos=new BlockPos(CX+SeedFarmLesson.CHEST_X,FLOOR+1,CZ+SeedFarmLesson.CHEST_Z);sites.add(chestPos);
  w.setBlockState(chestPos,Blocks.CHEST.getDefaultState(),3);
  if(w.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)chest.clear();else throw new IllegalStateException("Missing empty storage chest");
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);
  var rng=new Random(seed^3415);p.getInventory().setStack(0,new ItemStack(Items.IRON_HOE));p.getInventory().setStack(2,new ItemStack(Items.WATER_BUCKET));p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;p.teleport(w,CX-4.5,FLOOR+1,CZ+.5,Set.of(),rng.nextFloat()*360,15,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));for(var e:w.getEntitiesByClass(ItemEntity.class,bounds(),e->true))e.discard();
  initial.clear();initial.putAll(stats(p));lastWheat=mined(p,Blocks.WHEAT);startTick=w.getTime();var out=new JsonObject();out.addProperty("schema","fly-seedfarm-fixture-1");out.addProperty("seed",seed);out.addProperty("grassPlants",grass.size());out.addProperty("initialSeeds",0);out.addProperty("emptyChest",true);out.addProperty("targetPlots",24);out.addProperty("targetServiced",24);out.addProperty("randomTickSpeed",60);out.addProperty("trainingOnly",true);out.addProperty("assistance","Supplied cottage courtyard, iron hoe, filled bucket and empty chest. Zero seeds, 384 native random-drop grass plants and intact mixed-ground sites. Learned seeds/site/farm heads receive externally ordered stage goals. Accelerated native crop growth (60); shared native motor assistance. No world edits or grants during the attempt.");return out;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&ledger!=null&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-CX)<=R+2&&Math.abs(p.getZ()-CZ)<=R+2;}
 private static FarmLedger.Cell cell(ServerPlayerEntity p,BlockPos at){var w=p.getServerWorld();var soil=w.getBlockState(at);var c=w.getBlockState(at.up());return new FarmLedger.Cell(soil.isOf(Blocks.FARMLAND),c.getBlock() instanceof CropBlock,c.getBlock() instanceof CropBlock b?b.getAge(c):0,c.getBlock() instanceof CropBlock b?b.getMaxAge():7);}
 public static void track(MinecraftServer s){
  if(owner!=s||ledger==null)return;var p=s.getPlayerManager().getPlayer(player);if(p==null||!available(p))return;
  int now=mined(p,Blocks.WHEAT),budget=Math.max(0,now-lastWheat);lastWheat=now;
  for(var at:tiles){var c=cell(p,at);boolean old=previousCrops.get(id(at));boolean broken=old&&!c.crop()&&budget>0;
   if(old&&!c.crop()&&!broken)uncreditedLoss++;if(broken)budget--;
   if(!old&&c.crop()&&seedsBeforePlant<0)seedsBeforePlant=delta(p,"seedsPicked");
   ledger.update(id(at),c,broken);previousCrops.put(id(at),c.crop());
  }
 }
 private static boolean waterNear(ServerPlayerEntity p,BlockPos pos){var w=p.getServerWorld();for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=1;y++)if(w.getFluidState(pos.add(x,y,z)).isIn(FluidTags.WATER))return true;return false;}
 private static JsonObject block(ServerPlayerEntity p,BlockPos pos){var w=p.getServerWorld();var state=w.getBlockState(pos);var b=new JsonObject();b.addProperty("id",id(pos));b.add("position",at(pos));b.addProperty("name",Registries.BLOCK.getId(state.getBlock()).toString());b.addProperty("hardness",state.getHardness(w,pos));b.addProperty("support",w.getBlockState(pos.down()).isFullCube(w,pos.down()));var shape=state.getOutlineShape(w,pos);if(!shape.isEmpty()){var c=shape.getBoundingBox().getCenter().add(pos.getX(),pos.getY(),pos.getZ());var aim=new JsonObject();aim.addProperty("x",c.x);aim.addProperty("y",c.y);aim.addProperty("z",c.z);b.add("aim",aim);}return b;}
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var out=new JsonObject();out.addProperty("schema","fly-seedfarm-senses-1");out.add("center",at(new BlockPos(CX,FLOOR,CZ)));out.addProperty("radius",R);out.addProperty("randomTickSpeed",w.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED));out.addProperty("surveyGrass",grass.size());
  var rows=new JsonArray();for(var pos:tiles){var soil=w.getBlockState(pos);var crop=w.getBlockState(pos.up());var fluid=w.getFluidState(pos);var row=new JsonObject();row.addProperty("id",id(pos));row.add("position",at(pos));row.addProperty("soil",Registries.BLOCK.getId(soil.getBlock()).toString());row.addProperty("empty",crop.isAir());row.addProperty("crop",crop.getBlock() instanceof CropBlock?Registries.BLOCK.getId(crop.getBlock()).toString():"");row.addProperty("moisture",soil.isOf(Blocks.FARMLAND)?soil.get(FarmlandBlock.MOISTURE):0);row.addProperty("hydrated",soil.isOf(Blocks.FARMLAND)&&soil.get(FarmlandBlock.MOISTURE)>0);row.addProperty("waterInRange",waterNear(p,pos));row.addProperty("age",crop.getBlock() instanceof CropBlock c?c.getAge(crop):0);row.addProperty("maxAge",7);row.addProperty("mature",crop.getBlock() instanceof CropBlock c&&c.isMature(crop));row.addProperty("light",w.getBaseLightLevel(pos.up(),0));row.addProperty("hole",soil.isAir());row.addProperty("water",fluid.isIn(FluidTags.WATER));row.addProperty("source",fluid.isIn(FluidTags.WATER)&&fluid.isStill());row.addProperty("supported",w.getBlockState(pos.down()).isFullCube(w,pos.down()));boolean contained=true;for(var d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}){var q=pos.offset(d);var b=w.getBlockState(q);contained&=b.isFullCube(w,q)||b.isOf(Blocks.FARMLAND);}row.addProperty("contained",contained);row.addProperty("harvests",ledger.harvests(id(pos)));row.addProperty("replants",ledger.replants(id(pos)));rows.add(row);}out.add("tiles",rows);
  var plants=new JsonArray();for(var pos:grass)plants.add(block(p,pos));out.add("grass",plants);
  var chests=new JsonArray();for(var pos:sites)if(w.getBlockEntity(pos) instanceof ChestBlockEntity box){var chest=at(pos);chest.addProperty("size",box.size());var items=new JsonArray();for(int i=0;i<box.size();i++)if(!box.getStack(i).isEmpty())items.add(WorldObservation.item(box.getStack(i),i));chest.add("items",items);chests.add(chest);}out.add("chests",chests);return out;
 }
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the seed-to-farm courtyard and remain inside it");track(s);var w=p.getServerWorld();var values=stats(p);values.replaceAll((k,v)->v-initial.get(k));int planted=0,wet=0,moist=0,holes=0,water=0,stored=0,tables=0,chests=0;
  for(var at:tiles){var b=w.getBlockState(at);var f=w.getFluidState(at);if(b.isAir()||f.isIn(FluidTags.WATER))holes++;if(f.isIn(FluidTags.WATER)&&f.isStill())water++;if(cell(p,at).crop()&&b.isOf(Blocks.FARMLAND)){planted++;if(waterNear(p,at))wet++;if(b.get(FarmlandBlock.MOISTURE)>0)moist++;}}
  for(var at:sites){if(w.getBlockState(at).isOf(Blocks.CRAFTING_TABLE))tables++;if(w.getBlockEntity(at) instanceof ChestBlockEntity box){chests++;for(int i=0;i<box.size();i++)if(box.getStack(i).isOf(Items.WHEAT))stored+=box.getStack(i).getCount();}}
  boolean empty=count(p,Items.BUCKET)==1&&count(p,Items.WATER_BUCKET)==0,hoe=count(p,Items.IRON_HOE)>0,clean=p.currentScreenHandler==p.playerScreenHandler&&p.playerScreenHandler.getCursorStack().isEmpty();for(int i=1;i<=4;i++)clean&=p.playerScreenHandler.getSlot(i).getStack().isEmpty();int damage=ledger.immatureBreaks+uncreditedLoss;
  var out=new JsonObject();out.addProperty("schema","fly-seedfarm-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("lesson","seedfarm");out.addProperty("seed",seed);out.addProperty("elapsedTicks",w.getTime()-startTick);for(var e:values.entrySet())out.addProperty(e.getKey(),e.getValue());out.addProperty("grassSeedsBeforePlant",Math.max(0,seedsBeforePlant));out.addProperty("inventorySeeds",count(p,Items.WHEAT_SEEDS));out.addProperty("targetPlots",24);out.addProperty("tramples",ledger.tramples);out.addProperty("immatureBreaks",ledger.immatureBreaks);out.addProperty("storageChests",chests);out.addProperty("holes",holes);out.addProperty("sources",water);out.addProperty("tilled",ledger.tilled());out.addProperty("planted",planted);out.addProperty("irrigated",wet);out.addProperty("hydrated",moist);out.addProperty("matureHarvests",ledger.matureHarvests);out.addProperty("replanted",ledger.replants);out.addProperty("servicedPlots",ledger.servicedPlots());out.addProperty("stored",stored);out.addProperty("cropDamage",damage);out.addProperty("emptyBucket",empty);out.addProperty("heldHoe",hoe);out.addProperty("cleanInventory",clean);out.addProperty("health",p.getHealth());
  out.addProperty("lessonSuccess",SeedFarmLesson.success(values.get("grassBroken"),Math.max(0,seedsBeforePlant),values.get("dug"),holes,water,planted,wet,moist,ledger.servicedPlots(),stored,values.get("seedUses"),values.get("bucketUses"),empty,hoe,damage,values.get("otherBreaks"),p.isAlive(),clean));return out;
 }
 private SeedFarmArena(){}
}
