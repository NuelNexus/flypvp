package dev.flycolony.bridge;

/** A requested total turn cannot grow while HTTP observations/inference wait. */
public final class BoundedTurn {
 public record Delta(double yaw,double pitch) {}
 private double yaw,pitch,fraction;private long started,duration;
 public synchronized void start(StepRequest request,long now){
  yaw=request.action().equals("control")?request.yaw()*request.ticks():0;
  pitch=request.action().equals("control")?request.pitch()*request.ticks():0;
  started=now;duration=request.ticks()*50_000_000L;fraction=0;
 }
 public synchronized Delta advance(long now){return take(Math.max(fraction,Math.min(1,(double)(now-started)/Math.max(1,duration))));}
 public synchronized Delta finish(){return take(1);}
 public synchronized void cancel(){yaw=pitch=0;fraction=1;}
 private Delta take(double next){double change=next-fraction;fraction=next;return new Delta(yaw*change,pitch*change);}
}
