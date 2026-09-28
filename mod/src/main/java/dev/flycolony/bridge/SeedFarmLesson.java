package dev.flycolony.bridge;

import java.util.*;

/** Scenic fixture and native success contract. No learner job selection. */
public final class SeedFarmLesson {
 public static final int TARGET=24,GRASS=384,CHEST_X=5,CHEST_Z=4;
 public record Cell(int x,int z){}
 public static List<SiteLesson.Tile> tiles(long seed){
  return SiteLesson.layout(seed).stream().map(t->new SiteLesson.Tile(t.x()-5,t.z(),t.dirt())).toList();
 }
 public static List<Cell> grass(long seed){
  var plots=tiles(seed);var decor=HomesteadPlan.cells();var choices=new ArrayList<Cell>();
  for(int x=-17;x<=17;x++)for(int z=-17;z<=17;z++){
   final int xx=x,zz=z;
   if(Math.abs(x+5)<=2&&Math.abs(z)<=2||Math.abs(x-CHEST_X)<=2&&Math.abs(z-CHEST_Z)<=2)continue;
   if(plots.stream().anyMatch(t->Math.abs(t.x()-xx)<=1&&Math.abs(t.z()-zz)<=1))continue;
   if(decor.stream().anyMatch(c->c.y()>0&&Math.abs(c.x()-xx)<=1&&Math.abs(c.z()-zz)<=1))continue;
   choices.add(new Cell(x,z));
  }
  Collections.shuffle(choices,new Random(seed^0x534646L));
  if(choices.size()<GRASS)throw new IllegalStateException("Insufficient unobstructed grass field");
  return List.copyOf(choices.subList(0,GRASS));
 }
 public static boolean success(int grassBroken,int gatheredBeforePlant,int dug,int holes,int sources,int planted,int irrigated,int moist,int serviced,int stored,int seedUses,int bucketUses,boolean emptyBucket,boolean hoe,int damage,int otherBreaks,boolean alive,boolean clean){
  return grassBroken>0&&gatheredBeforePlant>=TARGET&&dug==1&&holes==1&&sources==1&&planted>=TARGET&&irrigated>=TARGET&&moist>0&&serviced>=TARGET&&stored>=TARGET&&seedUses>=2*TARGET&&bucketUses==1&&emptyBucket&&hoe&&damage==0&&otherBreaks==0&&alive&&clean;
 }
 private SeedFarmLesson(){}
}
