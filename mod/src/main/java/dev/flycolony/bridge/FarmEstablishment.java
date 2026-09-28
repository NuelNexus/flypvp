package dev.flycolony.bridge;

/** One continuous build-and-tend lesson; short preflight checks construction only. */
public final class FarmEstablishment {
 public static final int CELLS=48;
 public static void validateRounds(int rounds){if(rounds!=0&&rounds!=1)throw new IllegalArgumentException("Test 9 uses construction control (0) or one full cycle (1)");}
 public static int targetStored(int rounds){validateRounds(rounds);return rounds*CELLS;}
 public static boolean success(int rounds,int tilled,int plantedOnce,int restocks,int completed,int mature,int replanted,int planted,int stored,int seeds,boolean water,int immature,int tramples){
  validateRounds(rounds);
  boolean built=tilled==CELLS&&plantedOnce==CELLS&&restocks>=1&&planted==CELLS&&seeds>=4&&water&&immature==0&&tramples==0;
  return built&&(rounds==0||completed>=1&&mature>=CELLS&&replanted>=CELLS&&stored>=CELLS);
 }
 private FarmEstablishment(){}
}
