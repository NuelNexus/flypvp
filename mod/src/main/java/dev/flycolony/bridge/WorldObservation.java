package dev.flycolony.bridge;

import com.google.gson.JsonArray;

import com.google.gson.JsonObject;

import net.minecraft.registry.Registries;

import net.minecraft.server.MinecraftServer;

import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.item.ItemStack;

import net.minecraft.entity.LivingEntity;

import net.minecraft.util.math.BlockPos;

import net.minecraft.util.hit.BlockHitResult;

import net.minecraft.stat.Stats;



public final class WorldObservation {

 static JsonObject item(ItemStack stack,int slot){JsonObject j=new JsonObject();j.addProperty("slot",slot);j.addProperty("name",Registries.ITEM.getId(stack.getItem()).toString());j.addProperty("count",stack.getCount());j.addProperty("maxCount",stack.getMaxCount());j.addProperty("damage",stack.getDamage());j.addProperty("maxDamage",stack.getMaxDamage());j.addProperty("enchanted",stack.hasEnchantments());j.addProperty("displayName",stack.getName().getString());return j;}
 public static JsonObject capture(MinecraftServer server,ServerPlayerEntity p,int selectedSlot){

  var world=p.getServerWorld();JsonObject state=new JsonObject(),pos=new JsonObject();pos.addProperty("x",p.getX());pos.addProperty("y",p.getY());pos.addProperty("z",p.getZ());state.add("position",pos);

  state.addProperty("yaw",Math.toRadians(p.getYaw()));state.addProperty("pitch",Math.toRadians(p.getPitch()));state.addProperty("health",p.getHealth());state.addProperty("food",p.getHungerManager().getFoodLevel());state.addProperty("air",p.getAir());state.addProperty("onGround",p.isOnGround());state.addProperty("inWater",p.isTouchingWater());state.addProperty("usingItem",p.isUsingItem());state.addProperty("velocityY",p.getVelocity().y);state.addProperty("dimension",world.getRegistryKey().getValue().toString());state.addProperty("gameMode",p.interactionManager.getGameMode().asString());state.addProperty("worldTime",world.getTime());state.addProperty("timeOfDay",world.getTimeOfDay());state.addProperty("hotbar",p.getInventory().selectedSlot);state.addProperty("experience",p.experienceLevel);

  state.addProperty("sleeping",p.isSleeping());

  state.addProperty("held",Registries.ITEM.getId(p.getMainHandStack().getItem()).toString());state.addProperty("heldCount",p.getMainHandStack().getCount());JsonArray inventory=new JsonArray();for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(!stack.isEmpty())inventory.add(item(stack,i));}state.add("inventory",inventory);

  var handler=p.currentScreenHandler;JsonArray slots=new JsonArray();for(int i=0;i<handler.slots.size();i++){var slot=handler.slots.get(i);JsonObject row=item(slot.getStack(),i);row.addProperty("x",slot.x);row.addProperty("y",slot.y);row.addProperty("role",slot.inventory==p.getInventory()?"player":slot instanceof net.minecraft.screen.slot.CraftingResultSlot||slot instanceof net.minecraft.screen.slot.FurnaceOutputSlot?"output":"container");row.addProperty("canTake",slot.canTakeItems(p));row.addProperty("canInsert",!handler.getCursorStack().isEmpty()&&slot.canInsert(handler.getCursorStack()));slots.add(row);}state.add("slots",slots);state.addProperty("slot",Math.floorMod(selectedSlot,Math.max(1,handler.slots.size())));state.addProperty("syncId",handler.syncId);state.addProperty("window",handler==p.playerScreenHandler?"player":Registries.SCREEN_HANDLER.getId(handler.getType()).toString());state.add("cursor",item(handler.getCursorStack(),-1));

  if(handler instanceof net.minecraft.screen.AbstractFurnaceScreenHandler furnace){JsonObject f=new JsonObject();f.addProperty("cookProgress",furnace.getCookProgress());f.addProperty("fuelProgress",furnace.getFuelProgress());f.addProperty("burning",furnace.isBurning());state.add("furnace",f);}

  var hit=p.raycast(4.5,0,false);if(hit instanceof BlockHitResult b&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK){JsonObject target=new JsonObject();target.addProperty("name",Registries.BLOCK.getId(world.getBlockState(b.getBlockPos()).getBlock()).toString());target.addProperty("face",b.getSide().asString());JsonObject bp=new JsonObject();bp.addProperty("x",b.getBlockPos().getX());bp.addProperty("y",b.getBlockPos().getY());bp.addProperty("z",b.getBlockPos().getZ());target.add("position",bp);state.add("crosshairBlock",target);}

  JsonArray entities=new JsonArray();for(var e:world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(8),e->e!=p&&e.isAlive())){JsonObject j=new JsonObject();j.addProperty("name",Registries.ENTITY_TYPE.getId(e.getType()).toString());j.addProperty("id",e.getId());j.addProperty("x",e.getX());j.addProperty("y",e.getY());j.addProperty("z",e.getZ());j.addProperty("health",e.getHealth());j.addProperty("visible",p.canSee(e));entities.add(j);}state.add("entities",entities);

  JsonArray drops=new JsonArray();for(var e:world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,p.getBoundingBox().expand(8),e->e.isAlive())){JsonObject j=item(e.getStack(),-1);j.addProperty("x",e.getX());j.addProperty("y",e.getY());j.addProperty("z",e.getZ());drops.add(j);}state.add("drops",drops);
  JsonObject voxels=new JsonObject(),origin=new JsonObject();BlockPos center=p.getBlockPos();origin.addProperty("x",center.getX());origin.addProperty("y",center.getY());origin.addProperty("z",center.getZ());voxels.add("origin",origin);JsonArray blocks=new JsonArray();for(int z=-5;z<=5;z++)for(int x=-5;x<=5;x++)for(int y=-1;y<=2;y++){var at=center.add(x,y,z);if(!world.isChunkLoaded(at))continue;var block=world.getBlockState(at);if(block.isAir())continue;JsonArray row=new JsonArray();row.add(x);row.add(y);row.add(z);row.add(Registries.BLOCK.getId(block.getBlock()).toString());blocks.add(row);}voxels.add("blocks",blocks);state.add("localVoxels",voxels);

  JsonObject stats=new JsonObject();stats.addProperty("deaths",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)));stats.addProperty("mobKills",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.MOB_KILLS)));stats.addProperty("damageDealt",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DAMAGE_DEALT)));stats.addProperty("sleepInBed",p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.SLEEP_IN_BED)));JsonObject crafted=new JsonObject(),mined=new JsonObject(),used=new JsonObject();for(var i:Registries.ITEM){int u=p.getStatHandler().getStat(Stats.USED.getOrCreateStat(i));if(u>0)used.addProperty(Registries.ITEM.getId(i).toString(),u);int n=p.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(i));if(n>0)crafted.addProperty(Registries.ITEM.getId(i).toString(),n);}for(var b:Registries.BLOCK){int n=p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(b));if(n>0)mined.addProperty(Registries.BLOCK.getId(b).toString(),n);}stats.add("used",used);stats.add("crafted",crafted);stats.add("mined",mined);state.add("stats",stats);

  JsonArray advancements=new JsonArray();for(var a:server.getAdvancementLoader().getAdvancements())if(!a.id().getPath().startsWith("recipes/")&&p.getAdvancementTracker().getProgress(a).isDone())advancements.add(a.id().toString());state.add("advancements",advancements);state.addProperty("fullGameCompletionVerified",false);return state;

 }

}


