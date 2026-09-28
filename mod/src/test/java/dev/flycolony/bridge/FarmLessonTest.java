package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarmLessonTest {
 @Test void independentCycleRequiresThreeCollectedAndReplanted(){assertFalse(FarmLesson.success("cycle",4,4,3,0,2,3,0,true,4));assertFalse(FarmLesson.success("cycle",4,4,3,0,3,2,0,true,4));assertTrue(FarmLesson.success("cycle",4,4,3,0,3,3,0,true,4));assertFalse(FarmLesson.success("cycle",4,4,3,1,3,3,0,true,4));}
 @Test void onlyNamedWorldAndDisclosedGrowth(){assertThrows(IllegalArgumentException.class,()->FarmLesson.validate("My world","plant",1,3));assertThrows(IllegalArgumentException.class,()->FarmLesson.validate("Fly Colony School","plant",1,999));assertThrows(IllegalArgumentException.class,()->FarmLesson.validate("Fly Colony School","bad",1,3));}
 @Test void hydrationIncludesDiagonalsButNotLowerWater(){assertTrue(FarmLesson.irrigated(0,0,0,4,0,4));assertFalse(FarmLesson.irrigated(0,0,0,5,0,0));assertFalse(FarmLesson.irrigated(0,0,0,0,-1,0));}
 @Test void transitionsDoNotCreditMereInventoryMovement(){var l=new FarmLedger();var mature=new FarmLedger.Cell(true,true,7,7);var empty=new FarmLedger.Cell(true,false,0,7);l.initialize("a",mature);l.update("a",empty,true);assertEquals(1,l.matureHarvests);l.update("a",empty,true);assertEquals(1,l.matureHarvests);l.update("a",new FarmLedger.Cell(true,true,0,7),false);assertEquals(1,l.replants);l.update("a",empty,true);assertEquals(1,l.immatureBreaks);assertEquals(1,l.matureHarvests);}
 @Test void TramplingIsNotAHarvest(){var l=new FarmLedger();l.initialize("a",new FarmLedger.Cell(true,true,7,7));l.update("a",new FarmLedger.Cell(false,false,0,7),false);assertEquals(0,l.matureHarvests);assertEquals(1,l.tramples);}
}
