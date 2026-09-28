package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
class SeedLessonTest {
 @Test void layoutsAreDistinctBoundedAndLeaveSpawnClear(){
  for(long seed=3000000;seed<3000050;seed++){
   var sites=SeedLesson.sites(seed);assertEquals(198,sites.size());assertEquals(198,new HashSet<>(sites).size());assertEquals(sites,SeedLesson.sites(seed));
   for(var c:sites){assertTrue(Math.abs(c.x())<=8&&Math.abs(c.z())<=8);assertTrue(Math.abs(c.x())>1||Math.abs(c.z())>1);}
  }assertNotEquals(SeedLesson.sites(1),SeedLesson.sites(2));
 }
 @Test void eightRealPickupsAndInventoryRequired(){
  assertTrue(SeedLesson.success(30,8,8,true,8));assertFalse(SeedLesson.success(30,7,8,true,8));assertFalse(SeedLesson.success(30,8,7,true,8));assertFalse(SeedLesson.success(0,8,8,true,8));assertFalse(SeedLesson.success(30,8,8,false,8));
 }
 @Test void noDropIsNotMisreportedAsGoalCompletion(){assertFalse(SeedLesson.success(12,0,0,true,8));assertTrue(SeedLesson.success(12,1,1,true,1));}
}
