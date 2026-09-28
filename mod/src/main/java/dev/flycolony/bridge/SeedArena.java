package dev.flycolony.bridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Isolated grass gathering fixture. No loot overrides or item grants. */
public final class SeedArena {
 public static final int CX=320,FLOOR=249,R=9;
 private static MinecraftServer owner;private static UUID player;private static long seed,startTick;
 private static int initialGrass,initialPicked,initialOther,initialDeaths,target;
 private static final List<BlockPos> sites=new ArrayList<>();
 private static int grass(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.SHORT_GRASS));}
 private static int picked(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.PICKED_UP.getOrCreateStat(Items.WHEAT_SEEDS));}
 private static int other(ServerPlayerEntity p){int n=0;for(var b:Registries.BLOCK)if(b!=Blocks.SHORT_GRASS)n+=p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(b));return n;}
 private static int inventory(ServerPlayerEntity p){int n=0;for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(stack.isOf(Items.WHEAT_SEEDS))n+=stack.getCount();}return n;}
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 private static Box bounds(){return new Box(CX-R-1,245,-R-1,CX+R+2,258,R+2);}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed,boolean controlsOnly){
  SeedLesson.validate(s.getSaveProperties().getLevelName(),trialSeed);
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  FarmArena.restore(s);owner=s;player=p.getUuid();seed=trialSeed;target=controlsOnly?1:SeedLesson.TARGET;sites.clear();var w=s.getOverworld();
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
  w.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,s);w.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(3,s);w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  for(var e:w.getOtherEntities(p,bounds(),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  for(int x=CX-R;x<=CX+R;x++)for(int z=-R;z<=R;z++)for(int y=245;y<=256;y++){
   boolean edge=Math.abs(x-CX)==R||Math.abs(z)==R;
   var b=y==245?Blocks.BEDROCK:y<FLOOR?Blocks.DIRT:y==FLOOR?Blocks.GRASS_BLOCK:edge&&y<=252?Blocks.GLASS:Blocks.AIR;
   w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);
  }
  int index=0;for(var cell:SeedLesson.sites(seed)){
   var pos=new BlockPos(CX+cell.x(),FLOOR+1,cell.z());sites.add(pos);
   w.setBlockState(pos,(index<SeedLesson.GRASS?Blocks.SHORT_GRASS:index<SeedLesson.GRASS+4?Blocks.POPPY:Blocks.STONE).getDefaultState(),3);index++;
  }
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;
  p.teleport(w,CX+.5,FLOOR+1,.5,Set.of(),new Random(seed^0x53454544L).nextFloat()*360,15,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  for(var e:w.getEntitiesByClass(ItemEntity.class,bounds(),e->true))e.discard();
  initialGrass=grass(p);initialPicked=picked(p);initialOther=other(p);initialDeaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));startTick=w.getTime();
  var out=new JsonObject();out.addProperty("schema","fly-seed-fixture-1");out.addProperty("seed",seed);out.addProperty("grassPlants",SeedLesson.GRASS);out.addProperty("surveySites",SeedLesson.SITES);out.addProperty("targetSeeds",target);out.addProperty("controlsOnly",controlsOnly);out.addProperty("trainingOnly",true);
  out.addProperty("assistance","Supplied short grass, flowers and stone in a safe enclosure; empty survival inventory. Vanilla loot only, no guaranteed drops or items granted. Shared native walking, aiming, single clicks and pickup.");return out;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-CX)<=R+2&&Math.abs(p.getZ())<=R+2;}
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var out=new JsonObject();out.addProperty("schema","fly-seed-senses-1");out.addProperty("capturedAt",System.currentTimeMillis());out.add("center",at(new BlockPos(CX,FLOOR,0)));out.addProperty("radius",R);out.addProperty("surveySites",SeedLesson.SITES);
  var blocks=new JsonArray();for(var pos:sites){
   // Match the local collision survey: unknown distant sites are not offered as jobs.
   if(Math.abs(pos.getX()-p.getBlockX())>9||Math.abs(pos.getZ()-p.getBlockZ())>9)continue;
   var state=w.getBlockState(pos);var b=new JsonObject();b.addProperty("id",pos.getX()+":"+pos.getY()+":"+pos.getZ());b.add("position",at(pos));b.addProperty("name",Registries.BLOCK.getId(state.getBlock()).toString());b.addProperty("hardness",state.getHardness(w,pos));
   var shape=state.getOutlineShape(w,pos);if(!shape.isEmpty()){var center=shape.getBoundingBox().getCenter().add(pos.getX(),pos.getY(),pos.getZ());var aim=new JsonObject();aim.addProperty("x",center.x);aim.addProperty("y",center.y);aim.addProperty("z",center.z);b.add("aim",aim);}
   blocks.add(b);
  }out.add("blocks",blocks);return out;
 }
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the seed arena and remain inside it");
  int broken=grass(p)-initialGrass,collected=picked(p)-initialPicked,held=inventory(p);var out=new JsonObject();out.addProperty("schema","fly-seed-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("lesson","seeds");out.addProperty("seed",seed);out.addProperty("elapsedTicks",s.getOverworld().getTime()-startTick);
  out.addProperty("grassBroken",broken);out.addProperty("grassRemaining",sites.stream().filter(pos->s.getOverworld().getBlockState(pos).isOf(Blocks.SHORT_GRASS)).count());out.addProperty("seedsPicked",collected);out.addProperty("inventorySeeds",held);out.addProperty("nonGrassBreaks",other(p)-initialOther);out.addProperty("deaths",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS))-initialDeaths);out.addProperty("health",p.getHealth());out.addProperty("targetSeeds",target);
  out.addProperty("lessonSuccess",SeedLesson.success(broken,collected,held,p.isAlive(),target));return out;
 }
 private SeedArena(){}
}
