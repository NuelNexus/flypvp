package dev.flycolony.bridge;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceHomesteadPlanTest {
 @Test void resourcesHaveClearSeparateLocationsForEveryLayout(){
  for(long seed=3708000;seed<3708100;seed++){
   var plots=ResourceHomesteadPlan.tiles(seed);var grass=ResourceHomesteadPlan.grass(seed);
   assertEquals(43,plots.size());assertEquals(384,grass.size());assertEquals(384,new HashSet<>(grass).size());
   for(var g:grass){
    assertTrue(Math.abs(g.x())<=17&&Math.abs(g.z())<=17);
    assertFalse(plots.stream().anyMatch(t->Math.abs(t.x()-g.x())<=1&&Math.abs(t.z()-g.z())<=1));
    assertFalse(ResourceHomesteadPlan.trees().stream().anyMatch(t->Math.abs(t.x()-g.x())<=2&&Math.abs(t.z()-g.z())<=2));
    assertFalse(HomesteadPlan.cells().stream().anyMatch(c->c.y()>0&&Math.abs(c.x()-g.x())<=1&&Math.abs(c.z()-g.z())<=1));
   }
   for(var site:ResourceHomesteadPlan.sites()){
    assertFalse(plots.stream().anyMatch(t->t.x()==site.x()&&t.z()==site.z()));
    assertFalse(grass.contains(site));
    assertFalse(ResourceHomesteadPlan.trees().contains(site));
   }
   for(var tree:ResourceHomesteadPlan.trees())assertFalse(plots.stream().anyMatch(t->t.x()==tree.x()&&t.z()==tree.z()));
  }
 }
 @Test void layoutIsRepeatable(){assertEquals(ResourceHomesteadPlan.grass(3708002),ResourceHomesteadPlan.grass(3708002));}
}
