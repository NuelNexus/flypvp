package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmMaintenanceTest {
 @Test void fullFieldNeedsRegrowthCoverageAndStoredYield(){
  assertTrue(FarmMaintenance.success(2,2,48,96,96,48,48,96,4,true,0,0));
  assertFalse(FarmMaintenance.success(2,1,48,96,96,48,48,96,4,true,0,0));
  assertFalse(FarmMaintenance.success(2,2,47,96,96,48,48,96,4,true,0,0));
  assertFalse(FarmMaintenance.success(2,2,48,96,96,48,47,96,4,true,0,0));
  assertFalse(FarmMaintenance.success(2,2,48,96,96,48,48,95,4,true,0,0));
  assertFalse(FarmMaintenance.success(2,2,48,96,96,48,48,96,3,true,0,0));
  assertFalse(FarmMaintenance.success(2,2,48,96,96,48,48,96,4,true,1,0));
  assertFalse(FarmMaintenance.success(2,2,48,96,96,48,48,96,4,true,0,1));
 }
 @Test void teacherWindowsCannotMasqueradeAsFullRounds(){
  assertTrue(FarmMaintenance.success(0,0,0,8,8,8,48,8,4,true,0,0));
  assertFalse(FarmMaintenance.success(0,0,0,8,8,1,48,8,4,true,0,0));
  assertFalse(FarmMaintenance.success(2,0,0,8,8,8,48,8,4,true,0,0));
  assertThrows(IllegalArgumentException.class,()->FarmMaintenance.validateRounds(1));
  assertThrows(IllegalArgumentException.class,()->FarmLesson.validate("Fly Colony School","maintain",1,3));
  assertDoesNotThrow(()->FarmLesson.validate("Fly Colony School","maintain",1,60));
 }
 @Test void sameTileLoopsDoNotEarnWholeFieldRoundsAndFreshPlantsMustGrow(){
  var l=new FarmLedger();var mature=new FarmLedger.Cell(true,true,7,7);var empty=new FarmLedger.Cell(true,false,0,7);var young=new FarmLedger.Cell(true,true,0,7);
  l.initialize("a",mature);l.initialize("b",mature);
  l.update("a",empty,true);l.update("a",young,false);assertEquals(0,l.completedRounds());assertEquals(0,l.regrownHarvests);
  l.update("a",new FarmLedger.Cell(true,true,3,7),false);l.update("a",mature,false);l.update("a",empty,true);l.update("a",young,false);
  assertEquals(2,l.harvests("a"));assertEquals(2,l.replants("a"));assertEquals(1,l.regrownHarvests);assertEquals(0,l.completedRounds());
  l.update("b",empty,true);l.update("b",young,false);assertEquals(1,l.completedRounds());assertEquals(2,l.servicedPlots());
  l.update("b",empty,true);assertEquals(1,l.immatureBreaks);assertEquals(1,l.regrownHarvests);
 }
 @Test void resetsCannotCarryPriorCycleCredit(){
  var l=new FarmLedger();assertEquals(0,l.completedRounds());assertEquals(0,l.servicedPlots());assertEquals(0,l.regrownHarvests);
  assertFalse(FarmLesson.success("maintain",48,0,3,0,3,3,0,true,48));
 }
}
