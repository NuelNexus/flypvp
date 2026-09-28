package dev.flycolony.bridge;

import java.util.*;

/** Reset geometry only: no supplies, goals or learner action selection. */
public final class ResourceHomesteadPlan {
 public static final int GRASS=384;
 public record Cell(int x,int z){}
 public static List<SiteLesson.Tile> tiles(long seed){return SeedFarmLesson.tiles(seed);}
 public static List<Cell> trees(){return List.of(new Cell(-4,-4),new Cell(-4,4));}
 public static List<Cell> sites(){return List.of(new Cell(-2,-2),new Cell(2,-2),new Cell(2,2),new Cell(-2,2));}
 public static List<Cell> grass(long seed){
  var plots=tiles(seed);var decor=HomesteadPlan.cells();var choices=new ArrayList<Cell>();
  for(int x=-17;x<=17;x++)for(int z=-17;z<=17;z++){
   final int xx=x,zz=z;
   if(Math.abs(x)<=3&&Math.abs(z)<=3)continue;
   if(plots.stream().anyMatch(t->Math.abs(t.x()-xx)<=1&&Math.abs(t.z()-zz)<=1))continue;
   if(trees().stream().anyMatch(t->Math.abs(t.x()-xx)<=2&&Math.abs(t.z()-zz)<=2))continue;
   if(decor.stream().anyMatch(c->c.y()>0&&Math.abs(c.x()-xx)<=1&&Math.abs(c.z()-zz)<=1))continue;
   choices.add(new Cell(x,z));
  }
  Collections.shuffle(choices,new Random(seed^0x524646L));
  if(choices.size()<GRASS)throw new IllegalStateException("Insufficient unobstructed resource field: "+choices.size());
  return List.copyOf(choices.subList(0,GRASS));
 }
 private ResourceHomesteadPlan(){}
}
