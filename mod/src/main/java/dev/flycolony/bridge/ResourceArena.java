package dev.flycolony.bridge;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Disposable raw-resource curriculum, spatially separate from the old guided exercise. */
public final class ResourceArena {
 public static final int CX=96,R=24,FLOOR=249;
 private static final int[][] TREES={{-10,-11},{-17,-16},{-3,-17},{7,-17},{17,-16},{-18,-4},{-17,8},{-12,17},{-3,18},{8,18},{18,13},{18,3}};
 public static void validate(String name,long seed){
  if(!name.startsWith(SchoolArena.WORLD_PREFIX))throw new IllegalArgumentException("Resource arena requires a separate Fly Colony School world");
  if(seed<0||seed>2147483647L)throw new IllegalArgumentException("Invalid arena seed");
 }
 public static JsonObject reset(MinecraftServer server,ServerPlayerEntity p,long seed){
  return reset(server,p,seed,"front");
 }
 public record Approach(double x,double z,float yaw){}
 public static Approach approach(String name){
  return switch(name){case "front"->new Approach(CX-9.5,-13.5,0);case "left"->new Approach(CX-12.5,-10.5,-90);case "right"->new Approach(CX-6.5,-10.5,90);default->throw new IllegalArgumentException("Unknown teaching approach");};
 }
 public static JsonObject reset(MinecraftServer server,ServerPlayerEntity p,long seed,String approachName){
  validate(server.getSaveProperties().getLevelName(),seed);
  var pose=approach(approachName);
  if(p.getServerWorld()!=server.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  var w=server.getOverworld();var random=new Random(seed);
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
  w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);
  w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
  w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  for(var e:w.getOtherEntities(p,new Box(CX-R-1,243,-R-1,CX+R+2,272,R+2),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  for(int x=CX-R;x<=CX+R;x++)for(int z=-R;z<=R;z++)for(int y=244;y<=270;y++){
   boolean edge=Math.abs(x-CX)==R||Math.abs(z)==R;
   var block=y==244?Blocks.BEDROCK:y<249?Blocks.DIRT:y==249?Blocks.GRASS_BLOCK:edge&&y<=253?Blocks.GLASS:Blocks.AIR;
   var at=new BlockPos(x,y,z);if(!w.getBlockState(at).isOf(block))w.setBlockState(at,block.getDefaultState(),3);
  }
  // Whole trees with ordinary, decaying leaves and six logs each; no supplied lumber.
  for(var t:TREES){int tx=CX+t[0],tz=t[1];
   for(int dy=3;dy<=6;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<=(dy==6?2:3))
    w.setBlockState(new BlockPos(tx+dx,250+dy,tz+dz),Blocks.OAK_LEAVES.getDefaultState(),3);
   for(int dy=0;dy<6;dy++)w.setBlockState(new BlockPos(tx,250+dy,tz),Blocks.OAK_LOG.getDefaultState(),3);
  }
  int stone=0,coal=0;
  for(int x=8;x<=15;x++)for(int z=-9;z<=-4;z++)for(int y=250;y<=252;y++){
   boolean ore=y==252&&(x+z)%3==0;w.setBlockState(new BlockPos(CX+x,y,z),(ore?Blocks.COAL_ORE:Blocks.STONE).getDefaultState(),3);if(ore)coal++;else stone++;
  }
  for(int i=0;i<12;i++){
   var sheep=EntityType.SHEEP.spawn(w,new BlockPos(CX-5+(i%4)*3,250,6+(i/4)*3),SpawnReason.COMMAND);
   if(sheep==null)throw new IllegalStateException("Sheep spawn failed");sheep.setColor(DyeColor.WHITE);sheep.setPersistent();
  }
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
  p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);
  p.getInventory().selectedSlot=0;p.setExperienceLevel(0);p.setExperiencePoints(0);p.setVelocity(0,0,0);p.fallDistance=0;
  // Start near a real tree. Pose variation is an explicit early curriculum assistance.
  p.teleport(w,pose.x()+(random.nextDouble()-.5)*.7,250,pose.z(),Set.of(),pose.yaw()+(random.nextFloat()-.5f)*28,4,true);
  p.currentScreenHandler.sendContentUpdates();
  var j=new JsonObject();j.addProperty("schema","fly-resource-arena-1");j.addProperty("seed",seed);j.addProperty("trainingOnly",true);
  j.addProperty("approach",approachName);
  j.addProperty("worldName",server.getSaveProperties().getLevelName());j.addProperty("trees",TREES.length);j.addProperty("logs",TREES.length*6);j.addProperty("stone",stone);j.addProperty("coalOre",coal);j.addProperty("whiteSheep",12);j.addProperty("width",R*2+1);
  j.addProperty("goal","Gather raw materials; craft tools and a table; build an enclosed small room with a wooden door and a reachable complete bed.");
  j.addProperty("assistance","Separate elevated 49x49 school arena; glass perimeter; fixed daylight and clear weather; no natural mob spawning; empty inventory, restored health/hunger; near-tree varied starting pose. No tools, crafted materials, prebuilt shelter, pathfinder or recipe executor. Usable bed geometry is evaluated; sleeping requires a later night test.");
  return j;
 }
 public static JsonObject evaluate(MinecraftServer server,ServerPlayerEntity p){
  validate(server.getSaveProperties().getLevelName(),0);var w=server.getOverworld();
  int tables=0,doors=0,beds=0;RoomCheck.Result best=null;
  var volume=new RoomCheck.Volume(){
   public boolean walkable(int x,int y,int z){var at=new BlockPos(x,y,z);var b=w.getBlockState(at);return b.getCollisionShape(w,at).isEmpty()||b.getBlock() instanceof BedBlock;}
   public boolean solid(int x,int y,int z){var at=new BlockPos(x,y,z);return w.getBlockState(at).isFullCube(w,at);}
   public boolean door(int x,int y,int z){var b=w.getBlockState(new BlockPos(x,y,z));var top=w.getBlockState(new BlockPos(x,y+1,z));return b.getBlock() instanceof DoorBlock&&DoorBlock.canOpenByHand(b)&&b.get(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&top.isOf(b.getBlock())&&top.get(DoorBlock.HALF)==DoubleBlockHalf.UPPER;}
  };
  for(int x=CX-R+1;x<CX+R;x++)for(int z=-R+1;z<R;z++)for(int y=245;y<=260;y++){
   var at=new BlockPos(x,y,z);var b=w.getBlockState(at);
   if(b.isOf(Blocks.CRAFTING_TABLE))tables++;
   if(volume.door(x,y,z))doors++;
   if(b.getBlock() instanceof BedBlock&&b.get(BedBlock.PART)==BedPart.FOOT){
    var head=at.offset(b.get(BedBlock.FACING));var hb=w.getBlockState(head);
    if(!hb.isOf(b.getBlock())||hb.get(BedBlock.PART)!=BedPart.HEAD||hb.get(BedBlock.FACING)!=b.get(BedBlock.FACING))continue;
    beds++;
    if(!w.getBlockState(at.up()).isAir()||!w.getBlockState(head.up()).isAir())continue;
    var result=RoomCheck.check(volume,x,y,z);if(best==null||result.enclosed())best=result;
   }
  }
  var j=new JsonObject();j.addProperty("schema","fly-arena-evaluation-1");j.addProperty("privilegedEvaluatorOnly",true);j.addProperty("placedTables",tables);j.addProperty("completeDoors",doors);j.addProperty("completeBeds",beds);
  j.addProperty("shelterComplete",best!=null&&best.enclosed());j.addProperty("bedCanBeSleptInAtNight",best!=null&&best.enclosed());j.addProperty("sleepVerified",p.isSleeping());
  j.addProperty("roomCheck",best==null?"No complete bed with headroom yet":best.reason());j.addProperty("interiorFloorCells",best==null?0:best.floorCells());return j;
 }
}
