package dev.flycolony.bridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** A bounded inventory-crafting lesson. Only reset supplies logs; no recipe execution API. */
public final class CraftArena {
 public static final int CX=368,FLOOR=249,R=6;
 private static MinecraftServer owner;private static UUID player;private static long seed,startTick;
 private static int initialPlanks,initialSticks,initialTables,initialUsed,initialDeaths;
 private static final List<BlockPos> sites=new ArrayList<>();
 private static int crafted(ServerPlayerEntity p,Item item){return p.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item));}
 private static int used(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.CRAFTING_TABLE));}
 private static int inventory(ServerPlayerEntity p,Item item){int n=0;for(int i=0;i<p.getInventory().size();i++){var v=p.getInventory().getStack(i);if(v.isOf(item))n+=v.getCount();}return n;}
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed){
  CraftLesson.validate(s.getSaveProperties().getLevelName(),trialSeed);
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  FarmArena.restore(s);owner=s;player=p.getUuid();seed=trialSeed;sites.clear();var w=s.getOverworld();
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
  w.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,s);w.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(3,s);w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  for(var e:w.getOtherEntities(p,new Box(CX-R-1,245,-R-1,CX+R+2,258,R+2),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  for(int x=CX-R;x<=CX+R;x++)for(int z=-R;z<=R;z++)for(int y=245;y<=256;y++){
   boolean edge=Math.abs(x-CX)==R||Math.abs(z)==R;
   var b=y==245?Blocks.BEDROCK:y<FLOOR?Blocks.DIRT:y==FLOOR?Blocks.GRASS_BLOCK:edge&&y<=252?Blocks.GLASS:Blocks.AIR;
   w.setBlockState(new BlockPos(x,y,z),b.getDefaultState(),3);
  }
  // Four equally valid installation sites; none is selected by the sensor.
  for(int[] offset:new int[][]{{-3,-3},{-3,3},{3,-3},{3,3}})sites.add(new BlockPos(CX+offset[0],FLOOR+1,offset[1]));
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;
  var rng=new Random(seed);int slot=rng.nextInt(36),logs=2+rng.nextInt(3);p.getInventory().setStack(slot,new ItemStack(Items.OAK_LOG,logs));
  p.teleport(w,CX+.5,FLOOR+1,.5,Set.of(),rng.nextFloat()*360,15,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  initialPlanks=crafted(p,Items.OAK_PLANKS);initialSticks=crafted(p,Items.STICK);initialTables=crafted(p,Items.CRAFTING_TABLE);initialUsed=used(p);initialDeaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));startTick=w.getTime();
  var out=new JsonObject();out.addProperty("schema","fly-craft-fixture-1");out.addProperty("seed",seed);out.addProperty("suppliedLogs",logs);out.addProperty("inventorySlot",slot);out.addProperty("trainingOnly",true);
  out.addProperty("assistance","Supplied raw oak logs and safe floor. Three disclosed 2x2 recipe patterns. Native inventory clicks consume ingredients; no commands craft, grant outputs or place the table during an attempt.");return out;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-CX)<=R+1&&Math.abs(p.getZ())<=R+1;}
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var out=new JsonObject();out.addProperty("schema","fly-craft-senses-1");out.add("center",at(new BlockPos(CX,FLOOR,0)));out.addProperty("radius",R);
  var blocks=new JsonArray();for(var pos:sites){var row=new JsonObject();row.addProperty("id",pos.getX()+":"+pos.getY()+":"+pos.getZ());row.add("position",at(pos));row.addProperty("name",Registries.BLOCK.getId(w.getBlockState(pos).getBlock()).toString());row.addProperty("support",w.getBlockState(pos.down()).isFullCube(w,pos.down()));blocks.add(row);}out.add("sites",blocks);return out;
 }
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the crafting arena and remain inside it");
  int planks=crafted(p,Items.OAK_PLANKS)-initialPlanks,sticks=crafted(p,Items.STICK)-initialSticks,tables=crafted(p,Items.CRAFTING_TABLE)-initialTables,uses=used(p)-initialUsed;
  int placed=(int)sites.stream().filter(pos->p.getServerWorld().getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)).count();
  boolean clean=p.currentScreenHandler==p.playerScreenHandler&&p.playerScreenHandler.getCursorStack().isEmpty();
  for(int i=1;i<=4;i++)clean=clean&&p.playerScreenHandler.getSlot(i).getStack().isEmpty();
  var out=new JsonObject();out.addProperty("schema","fly-craft-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("lesson","craft");out.addProperty("seed",seed);out.addProperty("elapsedTicks",s.getOverworld().getTime()-startTick);
  out.addProperty("craftedPlanks",planks);out.addProperty("craftedSticks",sticks);out.addProperty("craftedTables",tables);out.addProperty("tableUses",uses);out.addProperty("placedTables",placed);out.addProperty("inventorySticks",inventory(p,Items.STICK));out.addProperty("cleanInventory",clean);out.addProperty("deaths",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS))-initialDeaths);out.addProperty("health",p.getHealth());
  out.addProperty("lessonSuccess",CraftLesson.success(planks,sticks,tables,uses,placed,inventory(p,Items.STICK),p.isAlive(),clean));return out;
 }
 private CraftArena(){}
}
