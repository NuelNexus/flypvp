package dev.flycolony.bridge;

import java.util.HashSet;
import java.util.Set;

/** Keeps button edges separate from request boundaries. No Minecraft classes are needed. */
public final class HeldInputs {
 private Set<String> held=Set.of();
 public record Frame(Set<String> keys,boolean attackPressed,boolean usePressed) {}
 public void release(){held=Set.of();}
 public Frame update(StepRequest request){return update(request,true);}
 public Frame update(StepRequest request,boolean first){
  Set<String> desired=new HashSet<>(request.keys());
  switch(request.action()){
   case "forward","back","left","right","jump","attack","use" -> desired.add(request.action());
   case "mine_block" -> desired.add("attack");
   case "jump_forward" -> desired.addAll(Set.of("forward","jump"));
   case "sprint_forward" -> desired.addAll(Set.of("forward","sprint"));
   case "sneak_forward" -> desired.addAll(Set.of("forward","sneak"));
   default -> {}
  }
  // A tap invokes vanilla's attack once, with no held attack key in this or
  // subsequent observation ticks. Held mining keeps its existing edge behavior.
  var frame=new Frame(Set.copyOf(desired),(request.action().equals("attack_once")&&first)||(desired.contains("attack")&&!held.contains("attack")),desired.contains("use")&&!held.contains("use"));
  held=frame.keys();return frame;
 }
}
