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

/** Disposable native tree-gathering lesson, separate from the saved farm. */
public final class WoodArena {
 public static final int CX=256,FLOOR=249,R=9;
 private static MinecraftServer owner;private static UUID player;private static long seed,startTick;
 private static int initialMined,initialOther,initialDeaths;
 private static final List<BlockPos> logs=new ArrayList<>();
 private static int mined(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.OAK_LOG));}
 private static int otherMined(ServerPlayerEntity p){int count=0;for(var block:Registries.BLOCK)if(block!=Blocks.OAK_LOG)count+=p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(block));return count;}
 private static int inventory(ServerPlayerEntity p){int n=0;for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(stack.isOf(Items.OAK_LOG))n+=stack.getCount();}return n;}
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 private static Box bounds(){return new Box(CX-R-1,245,-R-1,CX+R+2,259,R+2);}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed){
  WoodLesson.validate(s.getSaveProperties().getLevelName(),trialSeed);
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  FarmArena.restore(s);owner=s;player=p.getUuid();seed=trialSeed;logs.clear();var w=s.getOverworld();
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
  w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);w.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(3,s);
  for(var e:w.getOtherEntities(p,bounds(),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  for(int x=CX-R;x<=CX+R;x++)for(int z=-R;z<=R;z++)for(int y=245;y<=257;y++){
   boolean edge=Math.abs(x-CX)==R||Math.abs(z)==R;
   var b=y==245?Blocks.BEDROCK:y<FLOOR?Blocks.DIRT:y==FLOOR?Blocks.GRASS_BLOCK:edge&&y<=252?Blocks.GLASS:Blocks.AIR;
   w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);
  }
  for(var tree:WoodLesson.trees(seed)){
   for(int dy=1;dy<=4;dy++){var pos=new BlockPos(CX+tree.x(),FLOOR+dy,tree.z());logs.add(pos);w.setBlockState(pos,Blocks.OAK_LOG.getDefaultState(),3);}
   for(int dy=5;dy<=6;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
    if(dx==0&&dz==0&&dy==4||dy==6&&Math.abs(dx)+Math.abs(dz)>1||dy<6&&Math.abs(dx)==2&&Math.abs(dz)==2)continue;
    w.setBlockState(new BlockPos(CX+tree.x()+dx,FLOOR+dy,tree.z()+dz),Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT,true),3);
   }
  }
  // A visible low stone obstacle offers non-wood material and collision data.
  w.setBlockState(new BlockPos(CX,FLOOR+1,2),Blocks.STONE.getDefaultState(),3);
  w.setBlockState(new BlockPos(CX+1,FLOOR+1,2),Blocks.STONE.getDefaultState(),3);
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;
  int side=(int)(seed%4);double angle=side*Math.PI/2;var random=new Random(seed^0x574f4f44L);
  p.teleport(w,CX+.5+Math.sin(angle)*6,FLOOR+1,.5-Math.cos(angle)*6,Set.of(),side*90f+(random.nextFloat()-.5f)*30,5,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  for(var e:w.getEntitiesByClass(ItemEntity.class,bounds(),e->true))e.discard();
  initialMined=mined(p);initialOther=otherMined(p);initialDeaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));startTick=w.getTime();
  var out=new JsonObject();out.addProperty("schema","fly-wood-fixture-1");out.addProperty("seed",seed);out.addProperty("trees",2);out.addProperty("logs",8);out.addProperty("targetLogs",WoodLesson.TARGET);out.addProperty("trainingOnly",true);out.addProperty("assistance","Two supplied oak trees, a stone obstacle and safe grass enclosure; empty survival inventory. No items granted during the attempt. Shared motor walks, aims and holds attack on the selected block.");return out;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-CX)<=R+2&&Math.abs(p.getZ())<=R+2;}
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var out=new JsonObject();out.addProperty("schema","fly-wood-senses-1");out.addProperty("capturedAt",System.currentTimeMillis());out.add("center",at(new BlockPos(CX,FLOOR,0)));out.addProperty("radius",R);
  var blocks=new JsonArray();var surveyed=new ArrayList<BlockPos>(logs);surveyed.add(new BlockPos(CX,FLOOR+1,2));surveyed.add(new BlockPos(CX+1,FLOOR+1,2));for(var pos:surveyed){var state=w.getBlockState(pos);var b=new JsonObject();b.addProperty("id",pos.getX()+":"+pos.getY()+":"+pos.getZ());b.add("position",at(pos));b.addProperty("name",Registries.BLOCK.getId(state.getBlock()).toString());b.addProperty("hardness",state.getHardness(w,pos));blocks.add(b);}out.add("blocks",blocks);return out;
 }
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the wood arena and remain inside it");
  int broken=mined(p)-initialMined,held=inventory(p);var out=new JsonObject();out.addProperty("schema","fly-wood-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("lesson","wood");out.addProperty("seed",seed);out.addProperty("elapsedTicks",s.getOverworld().getTime()-startTick);out.addProperty("minedLogs",broken);out.addProperty("inventoryLogs",held);out.addProperty("nonLogBreaks",otherMined(p)-initialOther);out.addProperty("deaths",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS))-initialDeaths);out.addProperty("health",p.getHealth());out.addProperty("targetLogs",WoodLesson.TARGET);out.addProperty("lessonSuccess",WoodLesson.success(broken,held,p.isAlive()));return out;
 }
 private WoodArena(){}
}
