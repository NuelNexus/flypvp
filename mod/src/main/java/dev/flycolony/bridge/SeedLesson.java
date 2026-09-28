package dev.flycolony.bridge;
import java.util.*;
/** Seed loot stays vanilla and stochastic; only fixture positions are seeded. */
public final class SeedLesson {
 public static final int TARGET=8,GRASS=192,SITES=198;
 public record Cell(int x,int z){}
 public static List<Cell> sites(long seed){
  var cells=new ArrayList<Cell>();
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(Math.abs(x)>1||Math.abs(z)>1)cells.add(new Cell(x,z));
  Collections.shuffle(cells,new Random(seed));return List.copyOf(cells.subList(0,SITES));
 }
 public static void validate(String world,long seed){ResourceArena.validate(world,seed);}
 public static boolean success(int grass,int picked,int held,boolean alive,int target){return alive&&grass>0&&picked>=target&&held>=target;}
 private SeedLesson(){}
}
