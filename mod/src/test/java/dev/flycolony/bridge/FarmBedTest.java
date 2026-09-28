package dev.flycolony.bridge;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmBedTest {
 @Test void seededBedsAreConnectedWateredAndInsideSurvey(){
  var layouts=new HashSet<List<FarmBed.Tile>>();
  for(long seed=2700000;seed<2700500;seed++){
   var tiles=FarmBed.layout(seed);layouts.add(tiles);
   assertEquals(9,tiles.size());assertEquals(9,new HashSet<>(tiles).size());
   var reached=new HashSet<FarmBed.Tile>();reached.add(tiles.getFirst());
   for(int i=0;i<9;i++)for(var a:tiles)if(reached.contains(a))for(var b:tiles)
    if(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())==1)reached.add(b);
   assertEquals(9,reached.size());
   for(var tile:tiles){
    assertTrue(Math.abs(tile.x())<=3&&Math.abs(tile.z())<=3);
    assertFalse(tile.x()==0&&tile.z()==0);
    assertTrue(FarmLesson.irrigated(tile.x(),0,tile.z(),0,0,0));
   }
   assertEquals(tiles,FarmBed.layout(seed));
  }
  assertTrue(layouts.size()>8,"Trials must vary the connected plot location");
 }
 @Test void connectedBedRequiresAllNineTilledAndPlanted(){
  FarmLesson.validate("Fly Colony School","bed",2700000,3);
  assertFalse(FarmLesson.success("bed",8,9,0,0,0,0,0,true,9));
  assertFalse(FarmLesson.success("bed",9,8,0,0,0,0,0,true,9));
  assertFalse(FarmLesson.success("bed",9,9,0,1,0,0,0,true,9));
  assertTrue(FarmLesson.success("bed",9,9,0,0,0,0,0,true,9));
 }
}
