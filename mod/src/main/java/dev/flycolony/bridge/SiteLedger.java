package dev.flycolony.bridge;
import java.util.*;
/** Tick-level transitions ensure removed crops cannot disappear between HTTP reads. */
public final class SiteLedger {
 private final Map<String,Boolean> crops=new HashMap<>();
 private final Set<String> tilled=new HashSet<>(),planted=new HashSet<>();
 public int damage;
 public void update(String id,boolean farmland,boolean crop){
  if(Boolean.TRUE.equals(crops.get(id))&&!crop)damage++;
  crops.put(id,crop);if(farmland)tilled.add(id);if(crop)planted.add(id);
 }
 public int tilled(){return tilled.size();}
 public int planted(){return planted.size();}
}
