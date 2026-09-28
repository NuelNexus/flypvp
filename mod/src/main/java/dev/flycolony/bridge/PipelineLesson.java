package dev.flycolony.bridge;
import java.util.*;
/** Pure layout and outcome contract for the continuous resource-to-farm lesson. */
public final class PipelineLesson {
 public static final int GRASS=384,TARGET=24,HARVESTS=8;
 public record Cell(int x,int z){}
 public static void validate(String world,long seed){ResourceArena.validate(world,seed);}
 public static List<Cell> grass(long seed){
  var reserved=new HashSet<String>();for(var t:SiteLesson.layout(seed))reserved.add(t.x()+":"+t.z());
  var cells=new ArrayList<Cell>();
  for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++){
   if(Math.abs(x)<=2||Math.abs(z)<=2||reserved.contains(x+":"+z))continue;
   if(Math.abs(x+4)<=2&&Math.abs(z+4)<=2||Math.abs(x-4)<=2&&Math.abs(z-4)<=2)continue;
   cells.add(new Cell(x,z));
  }
  Collections.shuffle(cells,new Random(seed^3415));return List.copyOf(cells.subList(0,GRASS));
 }
 public static boolean success(int logs,int grass,int gatheredSeeds,int planks,int sticks,int tables,int hoes,int chests,int placedTables,int placedChests,int dug,int holes,int water,int planted,int irrigated,int moist,int serviced,int stored,int seedUses,int bucketUses,boolean emptyBucket,boolean heldHoe,int damage,int otherBreaks,boolean alive,boolean clean){
  return logs>=4&&grass>0&&gatheredSeeds>=TARGET&&planks>=16&&sticks>=4&&tables>=1&&hoes>=1&&chests>=1&&placedTables>=1&&placedChests>=1&&dug==1&&holes==1&&water==1&&planted>=TARGET&&irrigated>=TARGET&&moist>0&&serviced>=HARVESTS&&stored>=HARVESTS&&seedUses>=TARGET+HARVESTS&&bucketUses>=1&&emptyBucket&&heldHoe&&damage==0&&otherBreaks==0&&alive&&clean;
 }
 private PipelineLesson(){}
}
