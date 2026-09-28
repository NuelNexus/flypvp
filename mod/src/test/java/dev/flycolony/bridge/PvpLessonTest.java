package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class PvpLessonTest {
 @Test void spawnsAreReproducibleInsideTheWallsAndApart(){for(int seed=0;seed<500;seed++){var s=PvpLesson.spawn(seed);assertEquals(s,PvpLesson.spawn(seed));for(double v:new double[]{s.ax(),s.az(),s.bx(),s.bz()})assertTrue(Math.abs(v)<=PvpArena.R-1);double gap=Math.hypot(s.ax()-s.bx(),s.az()-s.bz());assertTrue(gap>1&&gap<=10.0001);}}
 @Test void yawFollowsTheGameConvention(){assertEquals(0,PvpLesson.yawTowards(0,0,0,5),1e-4);assertEquals(90,PvpLesson.yawTowards(0,0,-5,0),1e-4);assertEquals(-90,PvpLesson.yawTowards(0,0,5,0),1e-4);}
 @Test void outcomeNeedsTheOpponentDeadAndThePlayerAlive(){assertEquals("win",PvpLesson.outcome(true,false,0));assertEquals("running",PvpLesson.outcome(true,true,0));assertEquals("loss",PvpLesson.outcome(false,true,1));assertEquals("loss",PvpLesson.outcome(true,true,1));assertEquals("draw",PvpLesson.outcome(false,false,1));}
}
