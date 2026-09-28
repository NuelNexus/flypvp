package dev.flycolony.bridge;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class RoomCheckTest {
 private RoomCheck.Volume room(boolean gap,boolean roof,boolean floor,boolean door){return new RoomCheck.Volume(){
  public boolean solid(int x,int y,int z){if(y==-1)return floor;if(y==2)return roof;return (x==-1||x==3||z==-1||z==3)&&!(x==1&&z==-1&&(door||gap));}
  public boolean walkable(int x,int y,int z){return !solid(x,y,z);}
  public boolean door(int x,int y,int z){return door&&x==1&&z==-1&&y==0;}
 };}
 @Test void acceptsCompleteRoom(){assertTrue(RoomCheck.check(room(false,true,true,true),1,0,1).enclosed());}
 @Test void rejectsOpenWall(){assertFalse(RoomCheck.check(room(true,true,true,false),1,0,1).enclosed());}
 @Test void rejectsNoRoof(){assertFalse(RoomCheck.check(room(false,false,true,true),1,0,1).enclosed());}
 @Test void rejectsNoFloor(){assertFalse(RoomCheck.check(room(false,true,false,true),1,0,1).enclosed());}
 @Test void requiresDoor(){assertFalse(RoomCheck.check(room(false,true,true,false),1,0,1).enclosed());}
 @Test void rejectsGapAboveTwoHighWall(){
  var v=new RoomCheck.Volume(){
   public boolean solid(int x,int y,int z){return y==-1||y==4||y<2&&(x==-1||x==3||z==-1||z==3);}
   public boolean walkable(int x,int y,int z){return !solid(x,y,z);}
   public boolean door(int x,int y,int z){return x==1&&z==-1&&y==0;}
  };assertFalse(RoomCheck.check(v,1,0,1).enclosed());
 }
 @Test void protectsNonSchoolWorld(){assertThrows(IllegalArgumentException.class,()->ResourceArena.validate("My Survival",42));}
}
