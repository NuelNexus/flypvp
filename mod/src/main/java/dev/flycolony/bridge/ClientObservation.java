package dev.flycolony.bridge;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/** Current client pose/target, used only on the Minecraft client thread. */
public final class ClientObservation {
 public static JsonObject vector(Vec3d v){var j=new JsonObject();j.addProperty("x",v.x);j.addProperty("y",v.y);j.addProperty("z",v.z);return j;}
 public static void overlay(MinecraftClient c,JsonObject state){
  var p=c.player;if(p==null||c.world==null)return;
  state.add("position",vector(p.getPos()));state.add("eye",vector(p.getEyePos()));
  state.add("velocity",vector(p.getVelocity()));state.addProperty("velocityY",p.getVelocity().y);
  state.addProperty("onGround",p.isOnGround());state.addProperty("horizontalCollision",p.horizontalCollision);state.addProperty("verticalCollision",p.verticalCollision);
  state.addProperty("yaw",Math.toRadians(p.getYaw()));state.addProperty("pitch",Math.toRadians(p.getPitch()));
  state.addProperty("blockReach",p.getBlockInteractionRange());state.addProperty("entityReach",p.getEntityInteractionRange());
  state.addProperty("clientObservedAt",System.currentTimeMillis());state.remove("crosshairBlock");state.remove("crosshairEntity");
  c.gameRenderer.updateCrosshairTarget(1);
  if(c.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK){
   var target=new JsonObject();var block=c.world.getBlockState(hit.getBlockPos());
   target.addProperty("name",Registries.BLOCK.getId(block.getBlock()).toString());target.addProperty("face",hit.getSide().asString());
   target.add("position",vector(Vec3d.of(hit.getBlockPos())));target.add("hit",vector(hit.getPos()));target.addProperty("distance",p.getEyePos().distanceTo(hit.getPos()));
   target.addProperty("canHarvest",p.canHarvest(block));target.addProperty("breakDelta",BlockDamage.fraction(block.calcBlockBreakingDelta(p,c.world,hit.getBlockPos())));
   state.add("crosshairBlock",target);
  }else if(c.crosshairTarget instanceof EntityHitResult hit){
   var target=new JsonObject();target.addProperty("id",hit.getEntity().getId());target.addProperty("name",Registries.ENTITY_TYPE.getId(hit.getEntity().getType()).toString());state.add("crosshairEntity",target);
  }
 }
}
