package dev.flycolony.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickCommandTest {
 @Test void commandArrivingDuringIdleTickMustNotLoseItsFirstInput(){
  var tick=new TickCommand<Object>();var command=new Object();
  tick.begin(null);
  assertNull(tick.finish(command));
  tick.begin(command);
  assertSame(command,tick.finish(command));
  assertNull(tick.finish(command));
 }
 @Test void stopOrReplacementCannotCompleteAnUnappliedCommand(){
  var tick=new TickCommand<Object>();var old=new Object();var next=new Object();
  tick.begin(old);assertNull(tick.finish(next));
  tick.begin(old);tick.clear();assertNull(tick.finish(old));
  tick.begin(next);assertSame(next,tick.finish(next));
 }
 @Test void everyCountedTickAppliedTheSameRequest(){
  var tick=new TickCommand<Object>();var request=new Object();int applied=0,completed=0;
  for(int i=0;i<8;i++){
   boolean arrivedBeforeTick=i>=3;
   tick.begin(arrivedBeforeTick?request:null);
   if(arrivedBeforeTick)applied++;
   if(tick.finish(request)!=null)completed++;
  }
  assertEquals(5,applied);assertEquals(applied,completed);
 }
}
