package dev.flycolony.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Seeded, connected soil inside the existing seven-by-seven farm survey. */
public final class FarmBed {
 public record Tile(int x,int z){}
 public static List<Tile> layout(long seed){
  var origins=new ArrayList<Tile>();
  for(int z=-3;z<=1;z++)for(int x=-3;x<=1;x++)
   if(!(x<=0&&x+2>=0&&z<=0&&z+2>=0))origins.add(new Tile(x,z));
  Collections.shuffle(origins,new Random(seed));
  var origin=origins.getFirst();
  var tiles=new ArrayList<Tile>();
  for(int z=origin.z();z<origin.z()+3;z++)for(int x=origin.x();x<origin.x()+3;x++)tiles.add(new Tile(x,z));
  return List.copyOf(tiles);
 }
 private FarmBed(){}
}
