package dev.flycolony.bridge;
import java.util.*;
/** Pure fixture/evaluation contract. Goals are separate from actor observations. */
public final class WoodLesson {
 public static final int TARGET=4;
 public record Tree(int x,int z){}
 public static List<Tree> trees(long seed){
  var r=new Random(seed);int a=2+r.nextInt(2),b=2+r.nextInt(2);
  return List.of(new Tree(-a,-b),new Tree(a,b));
 }
 public static void validate(String world,long seed){ResourceArena.validate(world,seed);}
 public static boolean success(int mined,int logs,boolean alive){return alive&&mined>=TARGET&&logs>=TARGET;}
 private WoodLesson(){}
}
