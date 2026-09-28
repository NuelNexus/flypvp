package dev.flycolony.bridge;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeldInputsTest {
 @Test void targetedMiningHoldsAcrossRequestsWithoutRepeatedAttackClicks(){
  var inputs=new HeldInputs();assertTrue(inputs.update(request(1,"mine_block"),true).attackPressed());
  assertFalse(inputs.update(request(2,"mine_block"),true).attackPressed());
  assertEquals(Set.of("attack"),inputs.update(request(3,"mine_block"),false).keys());
  assertTrue(inputs.update(request(4,"wait"),true).keys().isEmpty());
 }
 private StepRequest request(long sequence,String action,String...keys){return new StepRequest(sequence,action,1,.13f,-.2f,0,0,.5,.5,Set.of(keys));}
 @Test void holdingAcrossRequestsDoesNotRepeatButtonPress(){
  var inputs=new HeldInputs();assertTrue(inputs.update(request(1,"control","forward","attack","use")).attackPressed());
  var next=inputs.update(request(2,"control","forward","attack","use"));assertEquals(Set.of("forward","attack","use"),next.keys());assertFalse(next.attackPressed());assertFalse(next.usePressed());
  inputs.update(request(3,"wait"));assertTrue(inputs.update(request(4,"control","attack")).attackPressed());
 }
 @Test void releasingForStopOrMenuClearsHeldState(){
  var inputs=new HeldInputs();inputs.update(request(1,"attack","forward"));assertTrue(inputs.update(request(2,"inventory")).keys().isEmpty());
  inputs.update(request(3,"use"));inputs.release();assertTrue(inputs.update(request(4,"use")).usePressed());
 }
 @Test void singleAttackNeverHoldsTheKeyOrRepeatsAcrossObservationTicks(){
  var inputs=new HeldInputs();var tap=request(1,"attack_once");
  var first=inputs.update(tap,true);assertTrue(first.attackPressed());assertTrue(first.keys().isEmpty());
  // A delayed HTTP response must not turn one crop click into held mining.
  assertFalse(inputs.update(tap,false).attackPressed());
  var idle=tap.observationHold();assertEquals("wait",idle.action());
  for(int i=0;i<100;i++){
   var frame=inputs.update(idle,false);assertFalse(frame.attackPressed());assertTrue(frame.keys().isEmpty());
  }
  assertTrue(inputs.update(request(2,"attack_once"),true).attackPressed());
 }
 @Test void singleAttackReleasesEarlierHeldMining(){
  var inputs=new HeldInputs();inputs.update(request(1,"attack"),true);
  var tap=inputs.update(request(2,"attack_once"),true);
  assertTrue(tap.attackPressed());assertFalse(tap.keys().contains("attack"));
  assertTrue(inputs.update(request(3,"attack"),true).attackPressed());
 }
}
