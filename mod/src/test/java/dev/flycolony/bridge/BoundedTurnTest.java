package dev.flycolony.bridge;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedTurnTest {
 private StepRequest request(){return new StepRequest(1,"control",4,3,-1,0,0,.5,.5,Set.of("forward","jump","attack"));}
 @Test void renderingAndLongObservationDelayCannotMultiplyTheRequestedTurn(){
  var turn=new BoundedTurn();turn.start(request(),0);double yaw=0,pitch=0;
  for(long t:new long[]{0,16_000_000,35_000_000,70_000_000,170_000_000,200_000_000,900_000_000,5_000_000_000L}){
   var d=turn.advance(t);yaw+=d.yaw();pitch+=d.pitch();
  }
  assertEquals(12,yaw,1e-9);assertEquals(-4,pitch,1e-9);assertEquals(0,turn.finish().yaw(),1e-9);
 }
 @Test void finishingWithoutRenderingAppliesOnlyTheUnconsumedRemainder(){
  var turn=new BoundedTurn();turn.start(request(),0);assertEquals(3,turn.advance(50_000_000).yaw(),1e-9);
  assertEquals(9,turn.finish().yaw(),1e-9);assertEquals(0,turn.advance(3_000_000_000L).yaw(),1e-9);
 }
 @Test void cancellationAndNextRequestDoNotLeakAnOldTurn(){
  var turn=new BoundedTurn();turn.start(request(),0);turn.advance(50_000_000);turn.cancel();assertEquals(0,turn.finish().yaw());
  turn.start(new StepRequest(2,"control",64,-.0625f,0,0,0,.5,.5,Set.of()),1_000_000);
  assertEquals(-4,turn.finish().yaw());
 }
 @Test void observationGapPreservesMiningWithoutWalkingJumpingOrCameraInput(){
  var idle=request().observationHold();assertEquals(Set.of("attack"),idle.keys());assertEquals(0,idle.yaw());assertEquals(0,idle.pitch());
  var inputs=new HeldInputs();assertTrue(inputs.update(request()).attackPressed());assertFalse(inputs.update(idle).attackPressed());
 }
}
