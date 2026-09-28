package dev.flycolony.bridge;

/** JSON-safe fraction of one block broken per tick, including instant crops. */
public final class BlockDamage {
 public static float fraction(float nativeDelta){
  if(Float.isNaN(nativeDelta)||nativeDelta<0)return 0;
  return Math.min(1,nativeDelta);
 }
 private BlockDamage(){}
}
