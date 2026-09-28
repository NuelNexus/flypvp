package dev.flycolony.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomesteadPlanTest {
 @Test void sceneryDoesNotObstructLearningAreaOrReplacePlots(){
  var cells=HomesteadPlan.cells();
  assertTrue(cells.size()>500);
  for(var c:cells){
   assertFalse(HomesteadPlan.plot(c.x(),c.z()),"Decor replaces crop tile: "+c);
   if(c.y()>0)assertFalse(HomesteadPlan.workArea(c.x(),c.z()),"Blocked work route: "+c);
   assertFalse(c.block().equals("barrier")||c.block().equals("bedrock"));
  }
 }
 @Test void boundedSceneryHasAHouseAndClearChestColumn(){
  var cells=HomesteadPlan.cells();
  assertEquals(cells.size(),cells.stream().map(c->c.x()+":"+c.y()+":"+c.z()).distinct().count());
  assertTrue(cells.stream().allMatch(c->Math.abs(c.x())<=HomesteadPlan.YARD&&Math.abs(c.z())<=HomesteadPlan.YARD&&c.y()>=0&&c.y()<=12));
  assertTrue(cells.stream().anyMatch(c->c.block().equals("door_lower")));
  assertTrue(cells.stream().anyMatch(c->c.block().equals("spruce_stairs")));
  assertTrue(cells.stream().noneMatch(c->c.x()==5&&c.z()==0&&c.y()>0));
 }
}
