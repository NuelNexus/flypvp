package dev.flycolony.bridge;
/** Supplied ingredients/workbench never count as crafted equipment. */
public final class EquipmentLesson {
 public static void validate(String world,long seed){ResourceArena.validate(world,seed);}
 public static boolean success(int hoes,int chests,int used,int placed,int heldHoes,boolean alive,boolean clean){
  return alive&&clean&&hoes>=1&&chests>=1&&used>=1&&placed>=1&&heldHoes>=1;
 }
 private EquipmentLesson(){}
}
