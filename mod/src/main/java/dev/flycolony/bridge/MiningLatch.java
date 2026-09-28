package dev.flycolony.bridge;
/** A held attack can finish its chosen block but must never spill into another. */
public final class MiningLatch<T> {
 private T target;
 public void bind(T value){target=value;}
 public void clear(){target=null;}
 public boolean allows(T current){if(target==null||!target.equals(current)){clear();return false;}return true;}
}
