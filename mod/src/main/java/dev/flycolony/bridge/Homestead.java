package dev.flycolony.bridge;

import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.block.enums.DoubleBlockHalf;

/** School-only reset scenery. The actual farm still uses native survival rules. */
public final class Homestead {
 public static int floor(ServerWorld w){
  int[] heights=new int[9];int i=0;
  for(int x:new int[]{-12,0,12})for(int z:new int[]{-12,0,12})heights[i++]=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,HomesteadPlan.CX+x,HomesteadPlan.CZ+z)-1;
  java.util.Arrays.sort(heights);
  return Math.max(w.getBottomY()+6,Math.min(w.getTopYInclusive()-24,heights[4]));
 }
 public static void build(ServerWorld w,int floor){
  // Blend a level garden into the existing landscape; no floating glass box.
  int r=HomesteadPlan.RADIUS;
  for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
   int radius=Math.max(Math.abs(x),Math.abs(z));if(radius>r)continue;
   int wx=HomesteadPlan.CX+x,wz=HomesteadPlan.CZ+z;
   int original=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,wx,wz)-1;
   double blend=Math.max(0,Math.min(1,(radius-21)/19.));
   int top=(int)Math.round(floor*(1-blend)+original*blend);
   int bottom=Math.min(top-4,original-1),clearTop=Math.max(top+16,original+12);
   for(int y=bottom;y<=Math.min(clearTop,w.getTopYInclusive());y++){
    Block block=y==top?Blocks.GRASS_BLOCK:y<top?Blocks.DIRT:Blocks.AIR;
    w.setBlockState(new BlockPos(wx,y,wz),block.getDefaultState(),2);
   }
  }
  // Shallow ornamental pond is safely outside crop hydration distance/routes.
  for(int x=-16;x<=-11;x++)for(int z=13;z<=17;z++)if((x+13.5)*(x+13.5)/9+(z-15)*(z-15)/6<1){
   w.setBlockState(new BlockPos(HomesteadPlan.CX+x,floor-1,HomesteadPlan.CZ+z),Blocks.CLAY.getDefaultState(),3);
   w.setBlockState(new BlockPos(HomesteadPlan.CX+x,floor,HomesteadPlan.CZ+z),Blocks.WATER.getDefaultState(),3);
  }
  for(var c:HomesteadPlan.cells()){
   var state=state(c);w.setBlockState(new BlockPos(HomesteadPlan.CX+c.x(),floor+c.y(),HomesteadPlan.CZ+c.z()),state,3);
  }
 }
 private static BlockState state(HomesteadPlan.Cell c){
  if(c.block().startsWith("door_"))return Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING,Direction.WEST).with(DoorBlock.HALF,c.block().equals("door_upper")?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
  var block=Registries.BLOCK.get(Identifier.of("minecraft",c.block()));var state=block.getDefaultState();
  if(block instanceof LeavesBlock)state=state.with(LeavesBlock.PERSISTENT,true);
  if(block instanceof StairsBlock)state=state.with(StairsBlock.FACING,Direction.byName(c.facing()));
  return state;
 }
 private Homestead(){}
}
