package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SiteLessonTest {
 @Test void intactMixedGroundHasOneCompleteSiteAndFourRotations(){
  var layouts=new HashSet<List<SiteLesson.Tile>>();
  for(long seed=0;seed<4;seed++){
   var tiles=SiteLesson.layout(seed);layouts.add(tiles);assertEquals(43,tiles.size());assertEquals(43,new HashSet<>(tiles).size());
   assertEquals(34,tiles.stream().filter(SiteLesson.Tile::dirt).count());
   int max=tiles.stream().filter(SiteLesson.Tile::dirt).mapToInt(t->(int)tiles.stream().filter(v->v.dirt()&&!v.equals(t)&&Math.abs(v.x()-t.x())<=4&&Math.abs(v.z()-t.z())<=4).count()).max().orElseThrow();assertEquals(24,max);
   assertTrue(tiles.stream().allMatch(t->Math.abs(t.x())<=9&&Math.abs(t.z())<=9));
  }assertEquals(4,layouts.size());assertNotEquals(SiteLesson.layout(3308002),SiteLesson.layout(3308019));
 }
 @Test void requireActualDigSourcePlantingNativeMoistureAndBucketUse(){
  int[] good={1,1,1,24,24,24,1,24,1};
  assertTrue(SiteLesson.success(1,1,1,24,24,24,1,24,1,true,0,true,true));
  for(int i=0;i<good.length;i++){var x=good.clone();x[i]=0;assertFalse(SiteLesson.success(x[0],x[1],x[2],x[3],x[4],x[5],x[6],x[7],x[8],true,0,true,true));}
  assertFalse(SiteLesson.success(2,2,1,24,24,24,1,24,1,true,0,true,true));
 }
 @Test void rejectLostCropsDeathOrUnfinishedInventory(){
  assertFalse(SiteLesson.success(1,1,1,24,24,24,1,24,1,false,0,true,true));
  assertFalse(SiteLesson.success(1,1,1,24,24,24,1,24,1,true,1,true,true));
  assertFalse(SiteLesson.success(1,1,1,24,24,24,1,24,1,true,0,false,true));
  assertFalse(SiteLesson.success(1,1,1,24,24,24,1,24,1,true,0,true,false));
 }
 @Test void tickLedgerCannotHideBreakAndReplantOrCreditRepeatedTilling(){
  var l=new SiteLedger();l.update("a",false,false);l.update("a",true,false);l.update("a",true,true);l.update("a",true,false);l.update("a",true,true);
  assertEquals(1,l.tilled());assertEquals(1,l.planted());assertEquals(1,l.damage);
  l.update("b",true,false);l.update("b",false,false);assertEquals(1,l.damage);
 }
}
