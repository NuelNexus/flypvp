package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import com.google.gson.JsonParser;
class StepRequestTest {
 @Test void miningSurvivesObservationButNeverAnOpenMenu(){
  var r=parse("{\"sequence\":1,\"action\":\"mine_block\",\"ticks\":5}");
  assertEquals("mine_block",r.observationHold(false).action());
  assertEquals("wait",r.observationHold(true).action());
  for(var invalid:java.util.List.of("\"ticks\":11","\"yaw\":1","\"keys\":[\"forward\"]"))
   assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"mine_block\","+invalid+"}"));
 }
 private StepRequest parse(String s){return StepRequest.parse(JsonParser.parseString(s).getAsJsonObject());}
 @Test void continuousControlAcceptsSimultaneousKeysAndFractionalMouseDeltas(){var r=parse("{\"sequence\":1,\"action\":\"control\",\"ticks\":1,\"yaw\":0.137,\"pitch\":-0.421,\"keys\":[\"forward\",\"attack\",\"jump\"]}");assertEquals(.137f,r.yaw());assertEquals(-.421f,r.pitch());assertEquals(java.util.Set.of("forward","attack","jump"),r.keys());}
 @Test void nativeActionsHaveBoundedDuration(){var r=parse("{\"sequence\":1,\"action\":\"attack\",\"ticks\":5}");assertEquals(5,r.ticks());assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"attack\",\"ticks\":101}"));assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"attack\",\"ticks\":1.5}"));}
 @Test void commandsAndUnboundedMouseInputsAreRejected(){assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"command\"}"));assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"wait\",\"command\":\"give diamond\"}"));assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"gui_left\",\"x\":2}"));}
 @Test void inventoryClicksAndAimingUseNativeActions(){assertEquals("craft_output",parse("{\"sequence\":1,\"action\":\"craft_output\"}").action());assertEquals(22.5f,parse("{\"sequence\":1,\"action\":\"look\",\"yaw\":22.5}").yaw());}
 @Test void singleAttackCannotSmuggleHeldKeysOrMultipleTicks(){
  assertEquals("attack_once",parse("{\"sequence\":1,\"action\":\"attack_once\"}").action());
  assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"attack_once\",\"ticks\":2}"));
  assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"attack_once\",\"keys\":[\"attack\"]}"));
  assertThrows(IllegalArgumentException.class,()->parse("{\"sequence\":1,\"action\":\"attack_once\",\"yaw\":5}"));
 }
}
