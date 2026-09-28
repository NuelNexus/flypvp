package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class PipelineLessonTest {
 @Test void rawResourcesDoNotOverlapFarmOrWorkstations(){
  for(long seed:new long[]{3400010,3400200,3401000,3401017,3404001,3408002,3408019}){
   var grass=PipelineLesson.grass(seed);assertEquals(384,grass.size());assertEquals(384,new HashSet<>(grass).size());
   for(var g:grass){assertTrue(Math.abs(g.x())>2&&Math.abs(g.z())>2);assertFalse(SiteLesson.layout(seed).stream().anyMatch(t->t.x()==g.x()&&t.z()==g.z()));}
  }
 }
 private boolean pass(int seeds,int table,int hoe,int chest,int serviced,int stored,int damage,int other){return PipelineLesson.success(4,180,seeds,16,4,1,1,1,table,chest,1,1,1,24,24,8,serviced,stored,32,1,true,hoe==1,damage,other,true,true);}
 @Test void constructionAloneDoesNotPass(){assertFalse(pass(24,1,1,1,0,0,0,0));assertTrue(pass(24,1,1,1,8,8,0,0));}
 @Test void nativeGatherCraftStorageAndCropIntegrityAreRequired(){
  for(int i=0;i<8;i++){int[] a={24,1,1,1,8,8,0,0};a[i]=i<6?0:1;assertFalse(pass(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7]));}
 }
 @Test void repeatedServiceOfOnePlotIsNotEightPlots(){var l=new FarmLedger();l.initialize("a",new FarmLedger.Cell(true,true,7,7));for(int i=0;i<8;i++){l.update("a",new FarmLedger.Cell(true,false,0,7),true);l.update("a",new FarmLedger.Cell(true,true,0,7),false);l.update("a",new FarmLedger.Cell(true,true,7,7),false);}assertEquals(8,l.matureHarvests);assertEquals(1,l.servicedPlots());assertFalse(pass(24,1,1,1,l.servicedPlots(),8,0,0));}
}
