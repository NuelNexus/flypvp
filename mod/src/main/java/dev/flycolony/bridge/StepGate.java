package dev.flycolony.bridge;

import java.util.concurrent.TimeoutException;

/** One outstanding client/server tick pair. Session generations reject stale completions. */
public final class StepGate {
    public record Ticket(long generation, long sequence) {}
    private boolean active;
    private long generation, issued, consumed, completed;
    public synchronized void start() { generation++; active=true; issued=consumed=completed=0; notifyAll(); }
    public synchronized void stop() { active=false; generation++; notifyAll(); }
    public synchronized boolean active() { return active; }
    public synchronized long completed() { return completed; }
    public synchronized Ticket issue() {
        if(!active) throw new IllegalStateException("Session stopped");
        if(issued!=completed) throw new IllegalStateException("A tick is already outstanding");
        issued++; notifyAll(); return new Ticket(generation, issued);
    }
    public synchronized Ticket acquire() throws InterruptedException {
        while(active && consumed==issued) wait();
        if(!active) return null;
        consumed=issued; return new Ticket(generation,consumed);
    }
    public synchronized void finish(Ticket ticket) {
        if(ticket==null || !active || ticket.generation()!=generation) return;
        if(ticket.sequence()!=consumed || completed>=ticket.sequence()) throw new IllegalStateException("Out-of-order server completion");
        completed=ticket.sequence(); notifyAll();
    }
    public synchronized void await(Ticket ticket, long timeoutMs) throws InterruptedException,TimeoutException {
        long deadline=System.nanoTime()+timeoutMs*1_000_000L;
        while(active && ticket.generation()==generation && completed<ticket.sequence()) {
            long left=deadline-System.nanoTime();if(left<=0)throw new TimeoutException("Server tick did not finish");
            wait(Math.max(1,left/1_000_000L));
        }
        if(!active || ticket.generation()!=generation)throw new IllegalStateException("Session stopped during tick");
    }
}
