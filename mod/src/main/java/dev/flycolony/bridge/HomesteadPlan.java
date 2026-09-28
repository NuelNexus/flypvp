package dev.flycolony.bridge;

import java.util.*;

/** Decorative geometry only. No jobs, rewards or learner choices live here. */
public final class HomesteadPlan {
 public static final int CX=1024,CZ=1024,RADIUS=40,YARD=19;
 public record Cell(int x,int y,int z,String block,String facing){}
 public static boolean workArea(int x,int z){return Math.abs(x)<=6&&Math.abs(z)<=6;}
 public static boolean plot(int x,int z){return Math.abs(x)<=3&&Math.abs(z)<=3&&(x!=0||z!=0);}
 public static List<Cell> cells(){
  var cells=new LinkedHashMap<String,Cell>();
  class Builder {
   void put(int x,int y,int z,String b){put(x,y,z,b,"");}
   void put(int x,int y,int z,String b,String f){cells.put(x+":"+y+":"+z,new Cell(x,y,z,b,f));}
   void box(int x1,int x2,int y1,int y2,int z1,int z2,String b){for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)put(x,y,z,b);}
   void tree(int x,int z,int height){
    for(int y=height-2;y<=height+1;y++){int r=y==height+1?1:2;for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)if(Math.abs(dx)+Math.abs(dz)<r*2+1)put(x+dx,y,z+dz,"oak_leaves");}
    box(x,x,1,height,z,z,"oak_log");
   }
  }
  var b=new Builder();
  // Cottage sits east of the unobstructed field and chest approaches.
  b.box(8,16,0,0,-6,2,"cobblestone");b.box(9,15,0,0,-5,1,"oak_planks");
  for(int y=1;y<=4;y++)for(int x=8;x<=16;x++)for(int z=-6;z<=2;z++)if(x==8||x==16||z==-6||z==2)b.put(x,y,z,y==1?"cobblestone":"white_terracotta");
  for(int x:new int[]{8,16})for(int z:new int[]{-6,2})b.box(x,x,1,5,z,z,"stripped_oak_log");
  b.box(8,16,5,5,-6,2,"oak_planks");
  b.put(8,1,-2,"door_lower","west");b.put(8,2,-2,"door_upper","west");
  for(int x:new int[]{8,16})for(int z:new int[]{-4,0})for(int y=2;y<=3;y++)b.put(x,y,z,"glass");
  for(int x:new int[]{10,11,13,14})for(int z:new int[]{-6,2})for(int y=2;y<=3;y++)b.put(x,y,z,"glass");
  // A pitched spruce roof with overhang, ridge and filled gable ends.
  for(int z=-7;z<=3;z++){
   int top=10-Math.abs(z+2);
   for(int x=7;x<=17;x++)b.put(x,top,z,z==-2?"spruce_slab":"spruce_stairs",z<-2?"south":"north");
   for(int x:new int[]{8,16})for(int y=6;y<top;y++)b.put(x,y,z,"oak_planks");
  }
  b.box(14,14,5,11,-4,-4,"bricks");b.put(14,12,-4,"brick_slab");
  for(int z:new int[]{-3,-1}){b.box(7,7,1,2,z,z,"oak_fence");b.put(7,3,z,"lantern");}
  b.put(12,1,-5,"crafting_table");b.put(13,1,-5,"furnace");
  b.box(15,15,1,2,-4,-3,"bookshelf");b.put(11,1,-4,"red_carpet");b.put(11,1,-3,"red_carpet");
  b.put(12,1,0,"lantern");
  // Ground-level paths have full-block collision: no learner step/trample trap.
  for(int x=4;x<=7;x++)for(int z=-2;z<=1;z++)if(!plot(x,z))b.put(x,0,z,"coarse_dirt");
  for(int z=-18;z<=-5;z++)for(int x=-1;x<=1;x++)b.put(x,0,z,"coarse_dirt");
  for(int i=-YARD;i<=YARD;i++){
   b.put(-YARD,1,i,"oak_fence");b.put(YARD,1,i,"oak_fence");
   b.put(i,1,YARD,"oak_fence");b.put(i,1,-YARD,Math.abs(i)<=1?"oak_fence_gate":"oak_fence");
  }
  for(int x:new int[]{-18,18})for(int z:new int[]{-18,18}){b.put(x,1,z,"cobblestone_wall");b.put(x,2,z,"lantern");}
  b.tree(-13,-11,5);b.tree(-14,11,6);b.tree(12,13,5);b.tree(-11,16,5);
  // Garden accents stay beyond every work route and spawn.
  for(int x=-16;x<=-9;x++)for(int z=-4;z<=5;z++)if((x*7+z*11)%5==0)b.put(x,1,z,(x+z)%2==0?"dandelion":"poppy");
  for(int z=7;z<=10;z++)b.put(15,1,z,"hay_block");
  return List.copyOf(cells.values());
 }
 private HomesteadPlan(){}
}
