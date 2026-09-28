package dev.flycolony.bridge;

import java.util.Set;

/** Pure contracts shared by the arena and its unit tests. */
public final class FarmLesson {
 public static final Set<String> NAMES=Set.of("plant","till","harvest","cycle","supplies","irrigate","maintain","bed","establish");
 public static void validate(String world,String lesson,long seed,int randomTicks){
  ResourceArena.validate(world,seed);
  if(!NAMES.contains(lesson))throw new IllegalArgumentException("Unknown farming lesson");
  if(randomTicks!=3&&randomTicks!=60)throw new IllegalArgumentException("Use normal growth (3) or disclosed development growth (60)");
  if((lesson.equals("maintain")||lesson.equals("establish"))&&randomTicks!=60)throw new IllegalArgumentException("Field development uses disclosed accelerated native growth (60); normal-speed endurance is a separate future check");
 }
 public static void validateRounds(String lesson,int rounds){if(lesson.equals("maintain"))FarmMaintenance.validateRounds(rounds);else if(lesson.equals("establish"))FarmEstablishment.validateRounds(rounds);else if(rounds!=0)throw new IllegalArgumentException("Only maintenance uses round targets");}
 public static boolean irrigated(int x,int y,int z,int wx,int wy,int wz){return Math.abs(x-wx)<=4&&Math.abs(z-wz)<=4&&wy>=y&&wy<=y+1;}
 public static boolean success(String lesson,int planted,int tilled,int mature,int immature,int replanted,int produce,int restocks,boolean water,int cells){
  if(immature>0)return false;
  return switch(lesson){
   case "plant" -> planted>=cells;
   case "till","bed" -> tilled>=cells&&planted>=cells;
   case "harvest" -> mature>=1&&produce>=1;
   case "cycle" -> mature>=3&&replanted>=3&&produce>=3;
   case "supplies" -> restocks>=1&&tilled>=cells&&planted>=cells;
   case "irrigate" -> water&&planted>=cells;
   case "maintain","establish" -> false; // Requires the per-tile regrowth/storage contract above.
   default -> false;
  };
 }
 private FarmLesson(){}
}
