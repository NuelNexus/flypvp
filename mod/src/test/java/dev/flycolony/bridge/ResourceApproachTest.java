package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceApproachTest {
 @Test void startsAreThreeBlocksFromTheSameTreeAndFaceIt(){
  double x=ResourceArena.CX-9.5,z=-10.5;
  for(String name:new String[]{"front","left","right"}){
   var pose=ResourceArena.approach(name);assertEquals(3,Math.hypot(x-pose.x(),z-pose.z()),1e-9);
   assertEquals(Math.toDegrees(Math.atan2(-(x-pose.x()),z-pose.z())),pose.yaw(),1e-9);
  }
 }
 @Test void unexpectedApproachIsRejected(){assertThrows(IllegalArgumentException.class,()->ResourceArena.approach("anywhere"));}
}
