package dev.flycolony.bridge;
import java.util.*;
/** Pure fixture/evaluation contract for the PvP arena. Kept free of game classes so it unit-tests. */
public final class PvpLesson {
 public static final Set<String> OPPONENTS=Set.of("zombie","husk","vindicator","piglin_brute","wither_skeleton");
 public record Spawn(double ax,double az,double bx,double bz){}
 /** Same spawn rule as pvp/sim.py: facing roughly towards each other, 5-10 blocks apart. */
 public static Spawn spawn(long trialSeed){
  var r=new Random(trialSeed^0x50565021L);double cx=(r.nextDouble()-.5)*PvpArena.R*.8,cz=(r.nextDouble()-.5)*PvpArena.R*.8,heading=r.nextDouble()*Math.PI*2,gap=5+r.nextDouble()*5;
  double ox=Math.cos(heading)*gap/2,oz=Math.sin(heading)*gap/2,lim=PvpArena.R-1;
  return new Spawn(clamp(cx-ox,lim),clamp(cz-oz,lim),clamp(cx+ox,lim),clamp(cz+oz,lim));
 }
 private static double clamp(double v,double l){return Math.max(-l,Math.min(l,v));}
 public static float yawTowards(double fx,double fz,double tx,double tz){return (float)Math.toDegrees(Math.atan2(-(tx-fx),tz-fz));}
 public static void validate(String world,long trialSeed,String kind){
  ResourceArena.validate(world,trialSeed);
  if(!OPPONENTS.contains(kind))throw new IllegalArgumentException("Unknown sparring opponent");
 }
 /** Outcome from the evaluator's view: win when the opponent is dead and the player is not. */
 public static String outcome(boolean playerAlive,boolean opponentAlive,int newDeaths){
  if(newDeaths>0||!playerAlive)return opponentAlive?"loss":"draw";
  return opponentAlive?"running":"win";
 }
 private PvpLesson(){}
}
