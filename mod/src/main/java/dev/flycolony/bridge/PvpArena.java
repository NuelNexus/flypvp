package dev.flycolony.bridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
/**
 * Disposable sword-duel arena for the PvP brain, separate from the farm and school arenas.
 * A walled 25 x 25 stone floor (walls at +-12 blocks, the simulator's size), the player in full
 * iron armour with a diamond sword, and one armed sparring opponent. Nothing is granted or moved
 * during the duel; the brain's keys, clicks and turns are the only inputs.
 */
public final class PvpArena {
 public static final int CX=512,FLOOR=249,R=12;
  private static MinecraftServer owner;private static UUID player;private static UUID opponent;private static long seed,startTick;
 private static int initialDeaths;private static float opponentMax;private static String opponentKind="zombie";
 private static Box bounds(){return new Box(CX-R-2,FLOOR-2,-R-2,CX+R+3,FLOOR+8,R+3);}
 private static EntityType<? extends MobEntity> type(String kind){
  return switch(kind){case "husk"->EntityType.HUSK;case "vindicator"->EntityType.VINDICATOR;case "piglin_brute"->EntityType.PIGLIN_BRUTE;case "wither_skeleton"->EntityType.WITHER_SKELETON;default->EntityType.ZOMBIE;};
 }
 public static JsonObject reset(MinecraftServer s,ServerPlayerEntity p,long trialSeed,String kind){
  PvpLesson.validate(s.getSaveProperties().getLevelName(),trialSeed,kind);
  if(p.getServerWorld()!=s.getOverworld()||!p.isAlive())throw new IllegalStateException("Open the school Overworld alive");
  FarmArena.restore(s);owner=s;player=p.getUuid();seed=trialSeed;opponentKind=kind;var w=s.getOverworld();
  w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
  w.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,s);
  // Hostile sparring partners only exist off Peaceful. The school world is a disposable test world.
  if(s.getSaveProperties().getDifficulty()==Difficulty.PEACEFUL)s.setDifficulty(Difficulty.NORMAL,true);
  w.setTimeOfDay(6000);w.setWeather(6000,0,false,false);
  for(var e:w.getOtherEntities(p,bounds(),e->!(e instanceof net.minecraft.entity.player.PlayerEntity)))e.discard();
  for(int x=-R-1;x<=R+1;x++)for(int z=-R-1;z<=R+1;z++)for(int y=FLOOR-1;y<=FLOOR+6;y++){
   boolean edge=Math.abs(x)==R+1||Math.abs(z)==R+1;
   var b=y==FLOOR-1?Blocks.BEDROCK:y==FLOOR?Blocks.SMOOTH_STONE:edge&&y<=FLOOR+4?Blocks.GLASS:edge?Blocks.BARRIER:Blocks.AIR;
   w.setBlockState(new BlockPos(CX+x,y,z),b.getDefaultState(),3);
  }
  var at=PvpLesson.spawn(seed);var random=new Random(seed^0x5057L);
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);
  p.getInventory().setStack(0,new ItemStack(Items.DIAMOND_SWORD));
  p.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));p.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
  p.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));p.equipStack(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
  p.setHealth(20);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);p.getInventory().selectedSlot=0;p.setVelocity(0,0,0);p.fallDistance=0;
  float yaw=PvpLesson.yawTowards(at.ax(),at.az(),at.bx(),at.bz())+(random.nextFloat()-.5f)*120;
  p.teleport(w,CX+at.ax(),FLOOR+1,at.az(),Set.of(),yaw,0,true);
  p.currentScreenHandler.sendContentUpdates();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
  var mob=type(kind).create(w,SpawnReason.COMMAND);if(mob==null)throw new IllegalStateException("Could not create the sparring opponent");
  mob.refreshPositionAndAngles(CX+at.bx(),FLOOR+1,at.bz(),PvpLesson.yawTowards(at.bx(),at.bz(),at.ax(),at.az()),0);
  mob.equipStack(EquipmentSlot.MAINHAND,new ItemStack(kind.equals("vindicator")?Items.IRON_AXE:Items.DIAMOND_SWORD));
  mob.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));mob.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
  mob.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));mob.equipStack(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
  for(var slot:EquipmentSlot.values())mob.setEquipmentDropChance(slot,0);
  mob.setCanPickUpLoot(false);mob.setPersistent();mob.setCustomName(Text.literal("Sparring Partner"));
  if(mob instanceof net.minecraft.entity.mob.ZombieEntity z)z.setBaby(false);
  w.spawnEntity(mob);mob.setTarget(p);opponent=mob.getUuid();opponentMax=mob.getMaxHealth();
  for(var e:w.getEntitiesByClass(ItemEntity.class,bounds(),e->true))e.discard();
  initialDeaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));startTick=w.getTime();
  var out=new JsonObject();out.addProperty("schema","fly-pvp-fixture-1");out.addProperty("seed",seed);out.addProperty("opponent",kind);out.addProperty("opponentId",mob.getId());
  out.addProperty("opponentMaxHealth",opponentMax);out.add("center",at(new BlockPos(CX,FLOOR+1,0)));out.addProperty("radius",R);out.addProperty("trainingOnly",true);
  out.addProperty("assistance","Walled flat arena, diamond sword and full iron armour for both fighters, natural regeneration off. The mod reports positions, health and timers; pitch is aimed by the runner's motor layer, yaw, movement and clicks come from the brain.");
  return out;
 }
 private static JsonObject at(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
 private static boolean available(ServerPlayerEntity p){return p!=null&&owner==p.getServer()&&p.getUuid().equals(player)&&p.getServerWorld()==owner.getOverworld();}
 private static LivingEntity rival(MinecraftServer s){return opponent==null?null:s.getOverworld().getEntity(opponent) instanceof LivingEntity l?l:null;}
 /** Server-side facts: authoritative health and timers. Positions/velocities are taken client-side by the caller. */
 public static JsonObject senses(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the PvP arena first");
  var out=new JsonObject();out.addProperty("schema","fly-pvp-senses-1");out.addProperty("tick",s.getOverworld().getTime()-startTick);
  out.add("center",at(new BlockPos(CX,FLOOR+1,0)));
  var me=new JsonObject();me.addProperty("health",p.getHealth());me.addProperty("timeUntilRegen",p.timeUntilRegen);me.addProperty("alive",p.isAlive());out.add("player",me);
  var mob=rival(s);var them=new JsonObject();them.addProperty("alive",mob!=null&&mob.isAlive());
  if(mob!=null){them.addProperty("id",mob.getId());them.addProperty("health",mob.getHealth());them.addProperty("maxHealth",mob.getMaxHealth());them.addProperty("timeUntilRegen",mob.timeUntilRegen);}
  out.add("opponent",them);return out;
 }
 /** Client-side motion facts for the local player and the opponent (what the screen shows). */
 public static void overlay(MinecraftClient c,JsonObject out,int opponentId){
  var p=c.player;if(p==null)return;var me=out.getAsJsonObject("player");
  put(me,p);me.addProperty("cooldown",p.getAttackCooldownProgress(.5f));me.addProperty("sprinting",p.isSprinting());me.addProperty("fallDistance",p.fallDistance);
  me.addProperty("pitch",p.getPitch());
  var them=out.getAsJsonObject("opponent");
  if(c.world!=null&&c.world.getEntityById(opponentId) instanceof LivingEntity e){put(them,e);them.addProperty("handSwinging",e.handSwinging);them.addProperty("handSwingTicks",e.handSwingTicks);them.addProperty("sprinting",e.isSprinting());}
  boolean onTarget=c.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit&&hit.getEntity().getId()==opponentId;out.addProperty("onTarget",onTarget);
 }
 private static void put(JsonObject j,Entity e){j.addProperty("x",e.getX());j.addProperty("y",e.getY());j.addProperty("z",e.getZ());var v=e.getVelocity();j.addProperty("vx",v.x);j.addProperty("vy",v.y);j.addProperty("vz",v.z);j.addProperty("yaw",e.getYaw());j.addProperty("onGround",e.isOnGround());if(e instanceof LivingEntity l)j.addProperty("hurtTime",l.hurtTime);}
 public static JsonObject evaluate(MinecraftServer s,ServerPlayerEntity p){
  if(!available(p))throw new IllegalStateException("Prepare the PvP arena first");
  var mob=rival(s);boolean mobAlive=mob!=null&&mob.isAlive();int deaths=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS))-initialDeaths;
  var out=new JsonObject();out.addProperty("schema","fly-pvp-evaluation-1");out.addProperty("privilegedEvaluatorOnly",true);out.addProperty("seed",seed);out.addProperty("opponent",opponentKind);
  out.addProperty("elapsedTicks",s.getOverworld().getTime()-startTick);out.addProperty("playerHealth",p.getHealth());out.addProperty("opponentHealth",mobAlive?mob.getHealth():0);out.addProperty("opponentMaxHealth",opponentMax);
  out.addProperty("deaths",deaths);out.addProperty("result",PvpLesson.outcome(p.isAlive(),mobAlive,deaths));return out;
 }
 public static int opponentId(MinecraftServer s){var mob=rival(s);return mob==null?-1:mob.getId();}
 private PvpArena(){}
}
