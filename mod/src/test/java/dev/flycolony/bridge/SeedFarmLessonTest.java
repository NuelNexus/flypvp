package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedFarmLessonTest {
 @Test void everyRotationPreservesChoicesAndClearNativeGrass(){
  for(long seed=3608000;seed<3608032;seed++){
   var plots=SeedFarmLesson.tiles(seed);var grass=SeedFarmLesson.grass(seed);
   assertEquals(43,plots.size());assertEquals(34,plots.stream().filter(SiteLesson.Tile::dirt).count());
   assertEquals(384,grass.size());assertEquals(384,grass.stream().distinct().count());
   for(var g:grass){
    assertTrue(plots.stream().noneMatch(t->Math.abs(g.x()-t.x())<=1&&Math.abs(g.z()-t.z())<=1));
    assertTrue(HomesteadPlan.cells().stream().noneMatch(c->c.y()>0&&Math.abs(c.x()-g.x())<=1&&Math.abs(c.z()-g.z())<=1));
   }
   for(var t:plots)assertTrue(HomesteadPlan.cells().stream().noneMatch(c->!c.block().equals("poppy")&&!c.block().equals("dandelion")&&c.y()>0&&c.y()<=2&&c.x()==t.x()&&c.z()==t.z()),"Plot collides with scenery");
  }
 }
 private boolean result(int gathered,int dug,int serviced,int stored,int damage){
  return SeedFarmLesson.success(180,gathered,dug,1,1,24,24,24,serviced,stored,48,1,true,true,damage,0,true,true);
 }
 @Test void completeRequiresGatheringConstructionAndEveryPlotServiced(){
  assertTrue(result(24,1,24,24,0));
  assertFalse(result(0,1,24,24,0));assertFalse(result(24,0,24,24,0));
  assertFalse(result(24,1,23,24,0));assertFalse(result(24,1,24,23,0));assertFalse(result(24,1,24,24,1));
 }
}
