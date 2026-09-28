package dev.flycolony.bridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Intact mixed ground. Only setup edits terrain; native inputs construct the farm. */
public final class SiteArena {
 public static final int CX=464,FLOOR=249,R=12;
 private static MinecraftServer owner;private static UUID player;private static long seed,startTick;
 private static int initialDirt,initialSeeds,initialBuckets,initialDeaths;private static SiteLedger ledger;
 private static final List<BlockPos> tiles=new ArrayList<>();
 private static int used(ServerPlayerEntity p,Item item){return p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));}
 private static int mined(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.DIRT))+p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.GRASS_BLOCK));}
 private static int count(ServerPlayerEntity p,Item item){int n=0;for(int i=0;i<p.getInventory().size();i++){var v=p.getInventory().getStack(i);if(v.isOf(item))n+=v.getCount();}return n;}
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 private static String id(BlockPos p){return p.getX()+":"+p.getY()+":"+p.getZ();}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed){
  SiteLesson.validate(s.getSaveProperties().getLevelName(),trialSeed);
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  FarmArena.restore(s);owner=s;player=p.getUuid();seed=trialSeed;tiles.clear();ledger=new SiteLedger();var w=s.getOverworld();
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
  w.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,s);w.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(3,s);w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  var bounds=new Box(CX-R-1,245,-R-1,CX+R+2,258,R+2);
  for(var e:w.getOtherEntities(p,bounds,e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  for(int x=CX-R;x<=CX+R;x++)for(int z=-R;z<=R;z++)for(int y=245;y<=256;y++){
   boolean edge=Math.abs(x-CX)==R||Math.abs(z)==R;
   var b=y==245?Blocks.BEDROCK:y<=FLOOR?Blocks.STONE_BRICKS:edge&&y<=252?Blocks.GLASS:Blocks.AIR;
   w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);
  }
  for(var tile:SiteLesson.layout(seed)){var pos=new BlockPos(CX+tile.x(),FLOOR,tile.z());tiles.add(pos);w.setBlockState(pos,(tile.dirt()?Blocks.DIRT:Blocks.STONE).getDefaultState(),3);ledger.update(id(pos),false,false);}
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);
  var slots=new ArrayList<Integer>();for(int i=0;i<36;i++)slots.add(i);var rng=new Random(seed^4314);Collections.shuffle(slots,rng);
  p.getInventory().setStack(slots.get(0),new ItemStack(Items.WOODEN_HOE));p.getInventory().setStack(slots.get(1),new ItemStack(Items.WHEAT_SEEDS,64));p.getInventory().setStack(slots.get(2),new ItemStack(Items.WATER_BUCKET));
  p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;p.teleport(w,CX+.5,FLOOR+1,.5,Set.of(),rng.nextFloat()*360,15,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  // Replacing last attempt's crops may spawn drops during reconstruction.
  for(var e:w.getEntitiesByClass(ItemEntity.class,bounds,e->true))e.discard();
  initialDirt=mined(p);initialSeeds=used(p,Items.WHEAT_SEEDS);initialBuckets=used(p,Items.WATER_BUCKET);initialDeaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));startTick=w.getTime();
  var out=new JsonObject();out.addProperty("schema","fly-site-fixture-1");out.addProperty("seed",seed);out.addProperty("cells",tiles.size());out.addProperty("targetPlots",SiteLesson.TARGET);out.addProperty("randomTickSpeed",3);out.addProperty("trainingOnly",true);
  out.addProperty("assistance","Intact mixed ground, supplied wooden hoe, 64 seeds and one filled bucket. Learner chooses dig/water/till/plant targets. Native movement, safe target-bound mining and clicks; no holes, water or crops supplied, no edits during attempts.");return out;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&ledger!=null&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-CX)<=R+2&&Math.abs(p.getZ())<=R+2;}
 public static void track(MinecraftServer s){
  if(owner!=s||ledger==null)return;var p=s.getPlayerManager().getPlayer(player);if(p==null||!available(p))return;
  for(var pos:tiles)ledger.update(id(pos),p.getServerWorld().getBlockState(pos).isOf(Blocks.FARMLAND),p.getServerWorld().getBlockState(pos.up()).getBlock() instanceof CropBlock);
 }
 private static boolean waterNear(ServerPlayerEntity p,BlockPos pos){
  var w=p.getServerWorld();for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=0;dy<=1;dy++)if(w.getFluidState(pos.add(dx,dy,dz)).isIn(FluidTags.WATER))return true;return false;
 }
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var out=new JsonObject();out.addProperty("schema","fly-site-senses-1");out.add("center",at(new BlockPos(CX,FLOOR,0)));out.addProperty("radius",R);out.addProperty("randomTickSpeed",w.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED));
  var rows=new JsonArray();for(var pos:tiles){var soil=w.getBlockState(pos);var crop=w.getBlockState(pos.up());var fluid=w.getFluidState(pos);var row=new JsonObject();row.addProperty("id",id(pos));row.add("position",at(pos));
   row.addProperty("soil",Registries.BLOCK.getId(soil.getBlock()).toString());row.addProperty("empty",crop.isAir());row.addProperty("crop",crop.getBlock() instanceof CropBlock?Registries.BLOCK.getId(crop.getBlock()).toString():"");
   row.addProperty("moisture",soil.isOf(Blocks.FARMLAND)?soil.get(FarmlandBlock.MOISTURE):0);row.addProperty("hydrated",soil.isOf(Blocks.FARMLAND)&&soil.get(FarmlandBlock.MOISTURE)>0);row.addProperty("waterInRange",waterNear(p,pos));
   row.addProperty("age",crop.getBlock() instanceof CropBlock c?c.getAge(crop):0);row.addProperty("maxAge",crop.getBlock() instanceof CropBlock c?c.getMaxAge():7);row.addProperty("mature",crop.getBlock() instanceof CropBlock c&&c.isMature(crop));row.addProperty("light",w.getBaseLightLevel(pos.up(),0));
   row.addProperty("hole",soil.isAir());row.addProperty("water",fluid.isIn(FluidTags.WATER));row.addProperty("source",fluid.isIn(FluidTags.WATER)&&fluid.isStill());row.addProperty("supported",w.getBlockState(pos.down()).isFullCube(w,pos.down()));
   boolean contained=true;for(var d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}){var q=pos.offset(d);var b=w.getBlockState(q);contained&=b.isFullCube(w,q)||b.isOf(Blocks.FARMLAND);}row.addProperty("contained",contained);rows.add(row);
  }out.add("tiles",rows);return out;
 }
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the site arena and remain inside it");track(s);var w=p.getServerWorld();int planted=0,irrigated=0,moist=0,holes=0,sources=0;
  for(var pos:tiles){var soil=w.getBlockState(pos);var fluid=w.getFluidState(pos);boolean crop=w.getBlockState(pos.up()).getBlock() instanceof CropBlock;
   if(soil.isAir()||fluid.isIn(FluidTags.WATER))holes++;if(fluid.isIn(FluidTags.WATER)&&fluid.isStill())sources++;
   if(crop&&soil.isOf(Blocks.FARMLAND)){planted++;if(waterNear(p,pos))irrigated++;if(soil.get(FarmlandBlock.MOISTURE)>0)moist++;}
  }
  int dug=mined(p)-initialDirt,seedsUsed=used(p,Items.WHEAT_SEEDS)-initialSeeds,bucketsUsed=used(p,Items.WATER_BUCKET)-initialBuckets,deaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS))-initialDeaths;
  boolean emptyBucket=count(p,Items.BUCKET)==1&&count(p,Items.WATER_BUCKET)==0,clean=p.currentScreenHandler==p.playerScreenHandler&&p.playerScreenHandler.getCursorStack().isEmpty();for(int i=1;i<=4;i++)clean&=p.playerScreenHandler.getSlot(i).getStack().isEmpty();
  var out=new JsonObject();out.addProperty("schema","fly-site-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("lesson","site");out.addProperty("seed",seed);out.addProperty("elapsedTicks",w.getTime()-startTick);out.addProperty("targetPlots",SiteLesson.TARGET);
  out.addProperty("dug",dug);out.addProperty("holes",holes);out.addProperty("sources",sources);out.addProperty("tilled",ledger.tilled());out.addProperty("uniquePlanted",ledger.planted());out.addProperty("planted",planted);out.addProperty("irrigated",irrigated);out.addProperty("hydrated",moist);out.addProperty("seedUses",seedsUsed);out.addProperty("bucketUses",bucketsUsed);out.addProperty("emptyBucket",emptyBucket);out.addProperty("cropDamage",ledger.damage);out.addProperty("deaths",deaths);out.addProperty("health",p.getHealth());out.addProperty("cleanInventory",clean);
  out.addProperty("lessonSuccess",SiteLesson.success(dug,holes,sources,ledger.tilled(),planted,irrigated,moist,seedsUsed,bucketsUsed,emptyBucket,ledger.damage,p.isAlive(),clean));return out;
 }
 private SiteArena(){}
}
