package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class WoodLessonTest {
 @Test void gatheringRequiresBothRealMiningAndInventory(){assertTrue(WoodLesson.success(4,4,true));assertFalse(WoodLesson.success(4,0,true));assertFalse(WoodLesson.success(0,4,true));assertFalse(WoodLesson.success(4,4,false));}
 @Test void layoutsAreReproducibleAndLeaveSafeCentralSpace(){for(int seed=0;seed<100;seed++){var trees=WoodLesson.trees(seed);assertEquals(trees,WoodLesson.trees(seed));assertEquals(2,trees.size());for(var t:trees){assertTrue(Math.abs(t.x())>=2&&Math.abs(t.x())<=3);assertTrue(Math.abs(t.z())>=2&&Math.abs(t.z())<=3);}}}
 @Test void targetLatchCannotResumeAfterTargetLoss(){var latch=new MiningLatch<String>();latch.bind("log-one");assertTrue(latch.allows("log-one"));assertFalse(latch.allows("log-two"));assertFalse(latch.allows("log-one"));latch.bind("log-two");assertTrue(latch.allows("log-two"));latch.clear();assertFalse(latch.allows("log-two"));}
}
