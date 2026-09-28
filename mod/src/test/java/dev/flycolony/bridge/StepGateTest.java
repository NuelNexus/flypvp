package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
class StepGateTest {
 @Test void serverCannotAdvanceWithoutAnAction() throws Exception {var g=new StepGate();g.start();try(var pool=Executors.newSingleThreadExecutor()){var blocked=pool.submit(g::acquire);Thread.sleep(25);assertFalse(blocked.isDone());var tick=g.issue();assertEquals(tick,blocked.get(1,TimeUnit.SECONDS));assertThrows(IllegalStateException.class,g::issue);g.finish(tick);g.await(tick,100);assertEquals(1,g.completed());g.stop();}}
 @Test void timeoutIsReportedInsteadOfInventingACompletedTick(){var g=new StepGate();g.start();var tick=g.issue();assertThrows(TimeoutException.class,()->g.await(tick,10));assertEquals(0,g.completed());g.stop();}
 @Test void stopReleasesBothWaitingThreads()throws Exception{var g=new StepGate();g.start();try(var pool=Executors.newSingleThreadExecutor()){var blocked=pool.submit(g::acquire);g.stop();assertNull(blocked.get(1,TimeUnit.SECONDS));}g.start();var tick=g.issue();g.stop();assertThrows(IllegalStateException.class,()->g.await(tick,10));}
 @Test void staleCompletionsCannotAdvanceANewSession(){var g=new StepGate();g.start();var old=g.issue();g.stop();g.start();g.finish(old);assertEquals(0,g.completed());g.stop();}
 @Test void oneThousandPairedTicksRemainInOrder()throws Exception{var g=new StepGate();g.start();try(var pool=Executors.newSingleThreadExecutor()){var server=pool.submit(()->{try{for(int i=0;i<1000;i++)g.finish(g.acquire());}catch(InterruptedException e){throw new RuntimeException(e);}});for(int i=0;i<1000;i++){var tick=g.issue();g.await(tick,1000);assertEquals(i+1,g.completed());}server.get(2,TimeUnit.SECONDS);g.stop();}}
}
