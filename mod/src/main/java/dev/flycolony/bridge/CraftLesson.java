package dev.flycolony.bridge;
/** Pure contract: supplied logs never count as crafted items. */
public final class CraftLesson {
 public static void validate(String world,long seed){ResourceArena.validate(world,seed);}
 public static boolean success(int planks,int sticks,int tables,int used,int placed,int heldSticks,boolean alive,boolean clean){
  return alive&&clean&&planks>=8&&sticks>=4&&tables>=1&&used>=1&&placed>=1&&heldSticks>=4;
 }
 private CraftLesson(){}
}
