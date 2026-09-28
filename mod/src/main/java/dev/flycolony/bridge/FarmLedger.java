package dev.flycolony.bridge;

import java.util.*;

/** Tick-observed transitions; collection/deposit loops cannot mint harvest credit. */
public final class FarmLedger {
 public record Cell(boolean farmland,boolean crop,int age,int maxAge){public boolean mature(){return crop&&age>=maxAge;}}
 private final Map<String,Cell> previous=new HashMap<>();
 private final Set<String> everTilled=new HashSet<>(),everPlanted=new HashSet<>(),pendingReplant=new HashSet<>();
 private final Map<String,Integer> harvestsByTile=new HashMap<>(),replantsByTile=new HashMap<>();
 private final Set<String> growingReplant=new HashSet<>(),observedRegrowth=new HashSet<>();
 public int regrownHarvests;
 public int matureHarvests,immatureBreaks,replants,tramples;
 public void initialize(String id,Cell cell){previous.put(id,cell);}
 public void update(String id,Cell now,boolean playerBrokeCrop){
  Cell old=previous.put(id,now);if(old==null)return;
  if(!old.farmland&&now.farmland)everTilled.add(id);
  if(!old.crop&&now.crop){everPlanted.add(id);if(pendingReplant.remove(id)){replants++;replantsByTile.merge(id,1,Integer::sum);growingReplant.add(id);observedRegrowth.remove(id);}}
  if(growingReplant.contains(id)&&old.crop&&now.crop&&now.age>old.age)observedRegrowth.add(id);
  if(old.crop&&!now.crop){
   if(playerBrokeCrop){if(old.mature()){matureHarvests++;harvestsByTile.merge(id,1,Integer::sum);if(observedRegrowth.contains(id))regrownHarvests++;pendingReplant.add(id);}else immatureBreaks++;}
   else if(!now.farmland)tramples++;
   growingReplant.remove(id);observedRegrowth.remove(id);
  }
 }
 public int tilled(){return everTilled.size();}
 public int planted(){return everPlanted.size();}
 public int harvests(String id){return harvestsByTile.getOrDefault(id,0);}
 public int replants(String id){return replantsByTile.getOrDefault(id,0);}
 public int servicedPlots(){return replantsByTile.size();}
 public int completedRounds(){return previous.keySet().stream().mapToInt(id->Math.min(harvests(id),replants(id))).min().orElse(0);}
}
