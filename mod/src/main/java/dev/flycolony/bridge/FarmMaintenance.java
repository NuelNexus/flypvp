package dev.flycolony.bridge;

/** Long-running tending uses native regrowth, per-plot coverage and stored yield. */
public final class FarmMaintenance {
 public static final int CELLS=48,ROUNDS=2,WINDOW_HARVESTS=8;
 public static void validateRounds(int rounds){if(rounds!=0&&rounds!=ROUNDS)throw new IllegalArgumentException("Use a short teaching window (0) or two complete maintenance rounds (2)");}
 public static int targetHarvests(int rounds){validateRounds(rounds);return rounds==0?WINDOW_HARVESTS:rounds*CELLS;}
 public static boolean success(int rounds,int completed,int regrown,int mature,int replanted,int serviced,int planted,int stored,int seeds,boolean water,int immature,int tramples){
  validateRounds(rounds);
  boolean coverage=rounds==0?serviced>=WINDOW_HARVESTS:completed>=ROUNDS&&regrown>=CELLS;
  return coverage&&mature>=targetHarvests(rounds)&&replanted>=targetHarvests(rounds)&&planted==CELLS&&stored>=targetHarvests(rounds)&&seeds>=4&&water&&immature==0&&tramples==0;
 }
 private FarmMaintenance(){}
}
