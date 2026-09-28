package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmEstablishmentTest {
 @Test void constructionOnlyCannotPassFullCycle(){
  assertTrue(FarmEstablishment.success(0,48,48,1,0,0,0,48,0,16,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,0,0,0,48,0,16,true,0,0));
  assertTrue(FarmEstablishment.success(1,48,48,1,1,48,48,48,48,4,true,0,0));
 }
 @Test void coverageSuppliesStoredYieldAndNoDamageAreRequired(){
  assertFalse(FarmEstablishment.success(1,47,48,1,1,48,48,48,48,4,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,47,1,1,48,48,48,48,4,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,0,1,48,48,48,48,4,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,0,100,100,48,100,4,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,1,48,48,48,47,4,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,1,48,48,47,48,4,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,1,48,48,48,48,3,true,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,1,48,48,48,48,4,false,0,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,1,48,48,48,48,4,true,1,0));
  assertFalse(FarmEstablishment.success(1,48,48,1,1,48,48,48,48,4,true,0,1));
 }
 @Test void resetContractsDoNotChangeMaintenance(){
  assertDoesNotThrow(()->FarmLesson.validate("Fly Colony School","establish",2800000,60));
  assertThrows(IllegalArgumentException.class,()->FarmLesson.validate("My world","establish",2800000,60));
  assertThrows(IllegalArgumentException.class,()->FarmLesson.validate("Fly Colony School","establish",2800000,3));
  assertDoesNotThrow(()->FarmLesson.validateRounds("establish",0));
  assertDoesNotThrow(()->FarmLesson.validateRounds("establish",1));
  assertThrows(IllegalArgumentException.class,()->FarmLesson.validateRounds("establish",2));
  assertThrows(IllegalArgumentException.class,()->FarmLesson.validateRounds("maintain",1));
  assertEquals(48,FarmEstablishment.targetStored(1));
 }
}
