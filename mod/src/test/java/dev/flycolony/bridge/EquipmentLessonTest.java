package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EquipmentLessonTest {
 @Test void craftedItemsAndActualPlacementRequired(){
  assertTrue(EquipmentLesson.success(1,1,1,1,1,true,true));
  assertFalse(EquipmentLesson.success(0,1,1,1,1,true,true));
  assertFalse(EquipmentLesson.success(1,0,1,1,1,true,true));
  assertFalse(EquipmentLesson.success(1,1,0,1,1,true,true));
  assertFalse(EquipmentLesson.success(1,1,1,0,1,true,true));
 }
 @Test void mustKeepHoeAndFinishAliveWithCleanInventory(){
  assertFalse(EquipmentLesson.success(1,1,1,1,0,true,true));
  assertFalse(EquipmentLesson.success(1,1,1,1,1,false,true));
  assertFalse(EquipmentLesson.success(1,1,1,1,1,true,false));
 }
}
