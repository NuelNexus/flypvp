package dev.flycolony.bridge;

import java.util.*;

/** Geometry-only outcome test. The policy never receives this map. */
public final class RoomCheck {
 public record Cell(int x,int y,int z) {}
 public interface Volume {
  boolean walkable(int x,int y,int z);
  boolean solid(int x,int y,int z);
  boolean door(int x,int y,int z);
 }
 public record Result(boolean enclosed,int floorCells,int doors,String reason) {}
 public static Result check(Volume v,int x,int y,int z) {
  var queue=new ArrayDeque<Cell>();var seen=new HashSet<Cell>();var doors=new HashSet<Cell>();var floor=new HashSet<Cell>();
  queue.add(new Cell(x,y,z));
  while(!queue.isEmpty()) {
   var c=queue.remove();if(!seen.add(c))continue;
   if(seen.size()>864||Math.abs(c.x-x)>24||Math.abs(c.z-z)>24||c.y<y||c.y>y+5)return new Result(false,floor.size(),doors.size(),"Interior leaks outside, through floor or roof");
   if(c.y==y){
    floor.add(c);if(floor.size()>144)return new Result(false,floor.size(),doors.size(),"Room exceeds 144 floor cells");
    if(!v.solid(c.x,y-1,c.z)||!v.walkable(c.x,y+1,c.z))return new Result(false,floor.size(),doors.size(),"Missing solid floor or two-block headroom");
   }
   for(var d:new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}}) {
    int nx=c.x+d[0],ny=c.y+d[1],nz=c.z+d[2];
    if((ny==y||ny==y+1)&&v.door(nx,y,nz)){doors.add(new Cell(nx,y,nz));continue;}
    if(v.walkable(nx,ny,nz))queue.add(new Cell(nx,ny,nz));
    else if(!v.solid(nx,ny,nz))return new Result(false,floor.size(),doors.size(),"Partial boundary block leaves an unverified gap");
   }
  }
  boolean ok=floor.size()>=4&&!doors.isEmpty();
  return new Result(ok,floor.size(),doors.size(),ok?"Enclosed volume with floor, headroom, roof and a door":"Needs interior space and an accessible door");
 }
}
