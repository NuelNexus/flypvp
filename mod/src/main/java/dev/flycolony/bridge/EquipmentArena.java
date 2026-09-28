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

/** A bounded equipment lesson. Reset supplies a workbench and ingredients; native clicks craft all outputs. */
public final class EquipmentArena {
 public static final int CX=416,FLOOR=249,R=6;
 private static MinecraftServer owner;private static UUID player;private static long seed,startTick;
 private static int initialHoes,initialChests,initialUsed,initialDeaths;
 private static BlockPos bench;
 private static final List<BlockPos> sites=new ArrayList<>();
 private static int crafted(ServerPlayerEntity p,Item item){return p.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item));}
 private static int used(ServerPlayerEntity p){return p.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.CHEST));}
 private static int inventory(ServerPlayerEntity p,Item item){int n=0;for(int i=0;i<p.getInventory().size();i++){var v=p.getInventory().getStack(i);if(v.isOf(item))n+=v.getCount();}return n;}
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed){
  EquipmentLesson.validate(s.getSaveProperties().getLevelName(),trialSeed);
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
  // Four equally valid chest sites. The supplied workbench is separate.
  for(int[] offset:new int[][]{{-3,-3},{-3,3},{3,-3},{3,3}})sites.add(new BlockPos(CX+offset[0],FLOOR+1,offset[1]));
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(20);p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;
  var rng=new Random(seed);int slot=rng.nextInt(36),stickSlot=(slot+1+rng.nextInt(35))%36,planks=10+rng.nextInt(5),sticks=2+rng.nextInt(3);
  p.getInventory().setStack(slot,new ItemStack(Items.OAK_PLANKS,planks));p.getInventory().setStack(stickSlot,new ItemStack(Items.STICK,sticks));
  int[][] offsets={{-2,0},{2,0},{0,-2},{0,2}};int[] offset=offsets[rng.nextInt(4)];bench=new BlockPos(CX+offset[0],FLOOR+1,offset[1]);w.setBlockState(bench,Blocks.CRAFTING_TABLE.getDefaultState(),3);
  p.teleport(w,CX+.5,FLOOR+1,.5,Set.of(),rng.nextFloat()*360,15,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  initialHoes=crafted(p,Items.WOODEN_HOE);initialChests=crafted(p,Items.CHEST);initialUsed=used(p);initialDeaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));startTick=w.getTime();
  var out=new JsonObject();out.addProperty("schema","fly-equipment-fixture-1");out.addProperty("seed",seed);out.addProperty("suppliedPlanks",planks);out.addProperty("suppliedSticks",sticks);out.addProperty("plankSlot",slot);out.addProperty("stickSlot",stickSlot);out.addProperty("trainingOnly",true);
  out.addProperty("assistance","Supplied workbench, ten to fourteen oak planks, two to four sticks and safe floor. Two disclosed 3x3 recipes. Native inventory clicks consume ingredients; no output grants, server crafting or commanded chest placement during attempts.");return out;
 }
 private static boolean available(ServerPlayerEntity p){return owner==p.getServer()&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld()&&Math.abs(p.getX()-CX)<=R+1&&Math.abs(p.getZ())<=R+1;}
 public static JsonObject observe(ServerPlayerEntity p){
  if(!available(p))return null;var w=p.getServerWorld();var out=new JsonObject();out.addProperty("schema","fly-equipment-senses-1");out.add("center",at(new BlockPos(CX,FLOOR,0)));out.addProperty("radius",R);
  var blocks=new JsonArray();for(var pos:sites){var row=new JsonObject();row.addProperty("id",pos.getX()+":"+pos.getY()+":"+pos.getZ());row.add("position",at(pos));row.addProperty("name",Registries.BLOCK.getId(w.getBlockState(pos).getBlock()).toString());row.addProperty("support",w.getBlockState(pos.down()).isFullCube(w,pos.down()));blocks.add(row);}out.add("sites",blocks);var table=new JsonObject();table.add("position",at(bench));table.addProperty("name",Registries.BLOCK.getId(w.getBlockState(bench).getBlock()).toString());out.add("bench",table);return out;
 }
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the equipment arena and remain inside it");
  int hoes=crafted(p,Items.WOODEN_HOE)-initialHoes,chests=crafted(p,Items.CHEST)-initialChests,uses=used(p)-initialUsed;
  int placed=(int)sites.stream().filter(pos->p.getServerWorld().getBlockState(pos).isOf(Blocks.CHEST)).count();
  boolean clean=p.currentScreenHandler==p.playerScreenHandler&&p.playerScreenHandler.getCursorStack().isEmpty();
  for(int i=1;i<=4;i++)clean=clean&&p.playerScreenHandler.getSlot(i).getStack().isEmpty();
  var out=new JsonObject();out.addProperty("schema","fly-equipment-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("lesson","equipment");out.addProperty("seed",seed);out.addProperty("elapsedTicks",s.getOverworld().getTime()-startTick);
  out.addProperty("craftedHoes",hoes);out.addProperty("craftedChests",chests);out.addProperty("chestUses",uses);out.addProperty("placedChests",placed);out.addProperty("inventoryHoes",inventory(p,Items.WOODEN_HOE));out.addProperty("cleanInventory",clean);out.addProperty("deaths",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS))-initialDeaths);out.addProperty("health",p.getHealth());
  out.addProperty("lessonSuccess",EquipmentLesson.success(hoes,chests,uses,placed,inventory(p,Items.WOODEN_HOE),p.isAlive(),clean));return out;
 }
 private EquipmentArena(){}
}
