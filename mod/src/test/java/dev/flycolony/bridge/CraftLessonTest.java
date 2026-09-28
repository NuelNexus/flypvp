package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CraftLessonTest {
 @Test void nativeCraftAndPlacementAreRequired(){
  assertTrue(CraftLesson.success(8,4,1,1,1,4,true,true));
  assertFalse(CraftLesson.success(0,4,1,1,1,4,true,true));
  assertFalse(CraftLesson.success(8,0,1,1,1,4,true,true));
  assertFalse(CraftLesson.success(8,4,0,1,1,4,true,true));
  assertFalse(CraftLesson.success(8,4,1,0,1,4,true,true));
  assertFalse(CraftLesson.success(8,4,1,1,0,4,true,true));
 }
 @Test void HeldSticksAliveAndCleanGridRequired(){
  assertFalse(CraftLesson.success(8,4,1,1,1,0,true,true));
  assertFalse(CraftLesson.success(8,4,1,1,1,4,false,true));
  assertFalse(CraftLesson.success(8,4,1,1,1,4,true,false));
 }
}
