package dev.flycolony.bridge;
import java.util.*;
/** Seeded construction fixture and pure native-result contract for Test 14. */
public final class SiteLesson {
 public static final int TARGET=24;
 public record Tile(int x,int z,boolean dirt){}
 public static void validate(String world,long seed){ResourceArena.validate(world,seed);}
 public static List<Tile> layout(long seed){
  var corners=new ArrayList<int[]>(List.of(new int[]{-7,-7},new int[]{7,-7},new int[]{7,7},new int[]{-7,7}));
  Collections.rotate(corners,(int)Math.floorMod(seed,4));var out=new ArrayList<Tile>();
  for(int patch=0;patch<3;patch++){int r=patch==0?2:1;int[] c=corners.get(patch);
   for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)out.add(new Tile(c[0]+x,c[1]+z,patch!=2));
  }
  return List.copyOf(out);
 }
 public static boolean success(int dug,int holes,int sources,int tilled,int planted,int irrigated,int moist,int seedUses,int bucketUses,boolean emptyBucket,int damage,boolean alive,boolean clean){
  return dug==1&&holes==1&&sources==1&&tilled>=TARGET&&planted>=TARGET&&irrigated>=TARGET&&moist>0&&seedUses>=TARGET&&bucketUses>=1&&emptyBucket&&damage==0&&alive&&clean;
 }
 private SiteLesson(){}
}
