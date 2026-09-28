package dev.flycolony.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/** Read-only, bounded world survey for the observatory. Never selects an action. */
public final class WorldScene {
 public static final int RADIUS=10, MIN_Y=-3, MAX_Y=12;
 private static JsonObject vector(Vec3d v){var j=new JsonObject();j.addProperty("x",v.x);j.addProperty("y",v.y);j.addProperty("z",v.z);return j;}
 public static JsonObject capture(ServerPlayerEntity p){
  var world=p.getServerWorld();var center=p.getBlockPos();var scene=new JsonObject();
  scene.addProperty("schema","fly-world-scene-1");scene.addProperty("capturedAt",System.currentTimeMillis());scene.addProperty("worldTick",world.getTime());
  scene.addProperty("dimension",world.getRegistryKey().getValue().toString());scene.addProperty("radius",RADIUS);scene.addProperty("minY",MIN_Y);scene.addProperty("maxY",MAX_Y);
  scene.addProperty("timeOfDay",world.getTimeOfDay());scene.addProperty("raining",world.isRaining());scene.addProperty("thundering",world.isThundering());scene.addProperty("bottomY",world.getBottomY());scene.addProperty("topY",world.getTopYInclusive());
  scene.add("origin",vector(new Vec3d(center.getX(),center.getY(),center.getZ())));
  var player=new JsonObject();player.add("position",vector(p.getPos()));player.add("eye",vector(p.getEyePos()));player.add("velocity",vector(p.getVelocity()));
  player.addProperty("yaw",p.getYaw());player.addProperty("pitch",p.getPitch());player.addProperty("health",p.getHealth());player.addProperty("food",p.getHungerManager().getFoodLevel());
  player.addProperty("held",Registries.ITEM.getId(p.getMainHandStack().getItem()).toString());player.addProperty("heldCount",p.getMainHandStack().getCount());
  player.addProperty("blockReach",p.getBlockInteractionRange());player.addProperty("entityReach",p.getEntityInteractionRange());scene.add("player",player);
  player.addProperty("air",p.getAir());player.addProperty("onGround",p.isOnGround());player.addProperty("inWater",p.isTouchingWater());player.addProperty("onFire",p.isOnFire());player.addProperty("sleeping",p.isSleeping());player.addProperty("alive",p.isAlive());player.addProperty("hotbar",p.getInventory().selectedSlot);
  player.addProperty("horizontalCollision",p.horizontalCollision);player.addProperty("verticalCollision",p.verticalCollision);
  player.addProperty("width",p.getWidth());player.addProperty("height",p.getHeight());player.addProperty("stepHeight",p.getStepHeight());
  scene.addProperty("physicsVersion",1);
  var inventory=new JsonArray();for(int i=0;i<p.getInventory().size();i++)if(!p.getInventory().getStack(i).isEmpty())inventory.add(WorldObservation.item(p.getInventory().getStack(i),i));scene.add("inventory",inventory);
  var handler=p.currentScreenHandler;var slots=new JsonArray();for(int i=0;i<handler.slots.size();i++){var slot=handler.slots.get(i);var row=WorldObservation.item(slot.getStack(),i);row.addProperty("x",slot.x);row.addProperty("y",slot.y);row.addProperty("role",slot.inventory==p.getInventory()?"player":slot instanceof net.minecraft.screen.slot.CraftingResultSlot||slot instanceof net.minecraft.screen.slot.FurnaceOutputSlot?"output":"container");row.addProperty("canTake",slot.canTakeItems(p));row.addProperty("canInsert",!handler.getCursorStack().isEmpty()&&slot.canInsert(handler.getCursorStack()));slots.add(row);}scene.add("slots",slots);scene.add("cursor",WorldObservation.item(handler.getCursorStack(),-1));
  scene.addProperty("window",handler==p.playerScreenHandler?"player":Registries.SCREEN_HANDLER.getId(handler.getType()).toString());
  if(handler instanceof net.minecraft.screen.AbstractFurnaceScreenHandler furnace){var f=new JsonObject();f.addProperty("cookProgress",furnace.getCookProgress());f.addProperty("fuelProgress",furnace.getFuelProgress());f.addProperty("burning",furnace.isBurning());scene.add("furnace",f);}
  var palette=new JsonArray();var blocks=new JsonArray();var unknown=new JsonArray();Map<String,Integer> indices=new HashMap<>();
  for(int z=-RADIUS;z<=RADIUS;z++)for(int x=-RADIUS;x<=RADIUS;x++){
   if(!world.isChunkLoaded(center.add(x,0,z))){var column=new JsonArray();column.add(x);column.add(z);unknown.add(column);continue;}
   for(int y=MIN_Y;y<=MAX_Y;y++){
    var at=center.add(x,y,z);if(world.isOutOfHeightLimit(at))continue;var block=world.getBlockState(at);if(block.isAir())continue;
    var shape=block.getCollisionShape(world,at,net.minecraft.block.ShapeContext.of(p));
    var boxes=new JsonArray();for(var box:shape.getBoundingBoxes()){var bounds=new JsonArray();for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})bounds.add(value);boxes.add(bounds);}
    // Some shapes depend on neighbours or location, so state alone is not a palette key.
    String key=block.toString()+"|"+boxes;Integer index=indices.get(key);
    if(index==null){index=palette.size();indices.put(key,index);var kind=new JsonObject();kind.addProperty("name",Registries.BLOCK.getId(block.getBlock()).toString());
     kind.addProperty("state",block.toString());kind.addProperty("collision",!shape.isEmpty());kind.add("collisionBoxes",boxes);kind.addProperty("fluid",!block.getFluidState().isEmpty());
     kind.addProperty("hardness",block.getHardness(world,at));kind.addProperty("toolRequired",block.isToolRequired());palette.add(kind);}
    var row=new JsonArray();row.add(x);row.add(y);row.add(z);row.add(index);blocks.add(row);
   }
  }
  scene.add("palette",palette);scene.add("blocks",blocks);scene.add("unknownColumns",unknown);
  var entities=new JsonArray();
  for(var e:world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(RADIUS),e->e!=p&&e.isAlive())){
   var j=new JsonObject();j.addProperty("id",e.getId());j.addProperty("name",Registries.ENTITY_TYPE.getId(e.getType()).toString());j.add("position",vector(e.getPos()));j.add("velocity",vector(e.getVelocity()));
   j.addProperty("health",e.getHealth());j.addProperty("maxHealth",e.getMaxHealth());j.addProperty("visible",p.canSee(e));j.addProperty("distance",p.getEyePos().distanceTo(e.getPos()));
   j.addProperty("yaw",e.getYaw());j.addProperty("width",e.getWidth());j.addProperty("height",e.getHeight());entities.add(j);
  }
  scene.add("entities",entities);var drops=new JsonArray();
  for(var e:world.getEntitiesByClass(ItemEntity.class,p.getBoundingBox().expand(RADIUS),e->e.isAlive())){
   var j=new JsonObject();j.addProperty("id",e.getId());j.addProperty("name",Registries.ITEM.getId(e.getStack().getItem()).toString());j.addProperty("count",e.getStack().getCount());j.add("position",vector(e.getPos()));
   j.addProperty("distance",p.getPos().distanceTo(e.getPos()));j.add("velocity",vector(e.getVelocity()));
   j.addProperty("onGround",e.isOnGround());j.addProperty("ageTicks",e.getItemAge());j.addProperty("pickupDelayActive",e.cannotPickup());
   // Overlap is a geometric fact, not a promise of pickup (inventory/ownership also matter).
   j.addProperty("pickupBoxOverlap",p.getBoundingBox().expand(1.0,0.5,1.0).intersects(e.getBoundingBox()));
   j.addProperty("width",e.getWidth());j.addProperty("height",e.getHeight());drops.add(j);
  }
  scene.add("drops",drops);
  var hit=p.raycast(p.getBlockInteractionRange(),0,false);
  if(hit instanceof BlockHitResult b&&hit.getType()==HitResult.Type.BLOCK){var block=world.getBlockState(b.getBlockPos());var target=new JsonObject();
   target.addProperty("name",Registries.BLOCK.getId(block.getBlock()).toString());target.add("position",vector(new Vec3d(b.getBlockPos().getX(),b.getBlockPos().getY(),b.getBlockPos().getZ())));
   target.add("hit",vector(b.getPos()));target.addProperty("face",b.getSide().asString());target.addProperty("distance",p.getEyePos().distanceTo(b.getPos()));target.addProperty("canHarvest",p.canHarvest(block));
   target.addProperty("breakDelta",BlockDamage.fraction(block.calcBlockBreakingDelta(p,world,b.getBlockPos())));scene.add("crosshair",target);
  }
  scene.addProperty("assistance","Read-only mod facts including occluded blocks, collision shapes, entities and item motion. No route or control is chosen by the survey.");
  scene.addProperty("completedAt",System.currentTimeMillis());
  var farm=FarmArena.observe(p);if(farm!=null)scene.add("farm",farm);var wood=WoodArena.observe(p);if(wood!=null)scene.add("wood",wood);var seeds=SeedArena.observe(p);if(seeds!=null)scene.add("seeds",seeds);var craft=CraftArena.observe(p);if(craft!=null)scene.add("craft",craft);var equipment=EquipmentArena.observe(p);if(equipment!=null)scene.add("equipment",equipment);var site=SiteArena.observe(p);if(site!=null)scene.add("site",site);var pipeline=PipelineArena.observe(p);if(pipeline!=null)scene.add("pipeline",pipeline);var seedFarm=SeedFarmArena.observe(p);if(seedFarm!=null)scene.add("seedFarm",seedFarm);return scene;
 }
}
