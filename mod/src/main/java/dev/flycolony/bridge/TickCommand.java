package dev.flycolony.bridge;

/** Pair a tick's completion with the command actually applied at its beginning. */
final class TickCommand<T> {
 private T applied;
 synchronized void begin(T command){applied=command;}
 synchronized T finish(T current){
  T result=applied;applied=null;
  return result==current?result:null;
 }
 synchronized void clear(){applied=null;}
}
