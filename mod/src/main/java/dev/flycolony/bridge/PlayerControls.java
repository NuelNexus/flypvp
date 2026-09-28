package dev.flycolony.bridge;

import dev.flycolony.bridge.mixin.ClientAccess;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import net.minecraft.client.option.KeyBinding;

import net.minecraft.screen.slot.SlotActionType;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

import net.minecraft.util.math.BlockPos;

import net.minecraft.util.math.Direction;



public final class PlayerControls {

 private final HeldInputs inputs=new HeldInputs();

 private record MiningTarget(BlockPos position,net.minecraft.block.Block block){}
 private final MiningLatch<MiningTarget> mining=new MiningLatch<>();
 private int selectedSlot;

 public int selectedSlot(){return selectedSlot;}

 public void release(MinecraftClient c){mining.clear();inputs.release();for(KeyBinding key:new KeyBinding[]{c.options.forwardKey,c.options.backKey,c.options.leftKey,c.options.rightKey,c.options.jumpKey,c.options.sprintKey,c.options.sneakKey,c.options.attackKey,c.options.useKey})key.setPressed(false);}

 public void apply(MinecraftClient c,StepRequest r,boolean first){

  if(c.player==null||c.interactionManager==null)throw new IllegalStateException("No local player");

  boolean menuAction=r.action().startsWith("slot_")||r.action().equals("craft_output")||r.action().startsWith("gui_");

  if(menuAction&&!(c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>))throw new IllegalStateException("Open a native inventory or container screen first");

  if(c.currentScreen!=null&&(!r.keys().isEmpty()||(!menuAction&&!java.util.Set.of("wait","inventory","close_screen","respawn","release_use").contains(r.action()))))throw new IllegalStateException("Close the screen before using world controls");

  var frame=inputs.update(r,first);
  if(r.action().equals("mine_block")){
   MiningTarget target=null;
   if(c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK)
    target=new MiningTarget(hit.getBlockPos().toImmutable(),c.world.getBlockState(hit.getBlockPos()).getBlock());
   if(first)mining.bind(target);
   if(!mining.allows(target)){inputs.release();frame=new HeldInputs.Frame(java.util.Set.of(),false,false);c.interactionManager.cancelBlockBreaking();}
  }else mining.clear();

  c.options.forwardKey.setPressed(frame.keys().contains("forward"));c.options.backKey.setPressed(frame.keys().contains("back"));

  c.options.leftKey.setPressed(frame.keys().contains("left"));c.options.rightKey.setPressed(frame.keys().contains("right"));

  c.options.jumpKey.setPressed(frame.keys().contains("jump"));c.options.sprintKey.setPressed(frame.keys().contains("sprint"));

  c.options.sneakKey.setPressed(frame.keys().contains("sneak"));c.options.attackKey.setPressed(frame.keys().contains("attack"));c.options.useKey.setPressed(frame.keys().contains("use"));

  if(r.action().equals("control")&&FlyBridge.INSTANCE.pairedActive())pLook(c,r);

  if(frame.attackPressed())((ClientAccess)c).flyAttack();if(frame.usePressed())((ClientAccess)c).flyUse();

  switch(r.action()){

   case "mine_block","control","wait","forward","back","left","right","jump","jump_forward","sprint_forward","sneak_forward","attack","attack_once","use" -> {}

   case "release_use" -> {if(first)c.interactionManager.stopUsingItem(c.player);}

   default -> {if(first)oneShot(c,r);}

  }

 }

 private void pLook(MinecraftClient c,StepRequest r){c.player.setYaw(c.player.getYaw()+r.yaw());c.player.setPitch(Math.max(-90,Math.min(90,c.player.getPitch()+r.pitch())));}

 private void oneShot(MinecraftClient c,StepRequest r){var p=c.player;var manager=c.interactionManager;var handler=p.currentScreenHandler;int size=handler.slots.size();selectedSlot=Math.floorMod(selectedSlot,Math.max(1,size));

  switch(r.action()){

   case "look" -> {p.setYaw(p.getYaw()+r.yaw());p.setPitch(Math.max(-90,Math.min(90,p.getPitch()+r.pitch())));}

   case "inventory" -> {if(c.currentScreen==null)c.setScreen(new InventoryScreen(p));else p.closeHandledScreen();}

   case "close_screen" -> p.closeHandledScreen();

   case "hotbar" -> {if(r.slot()>8)throw new IllegalArgumentException("Hotbar slot must be 0-8");p.getInventory().selectedSlot=r.slot();p.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(r.slot()));}

   case "hotbar_next" -> {p.getInventory().selectedSlot=(p.getInventory().selectedSlot+1)%9;p.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(p.getInventory().selectedSlot));}

   case "hotbar_previous" -> {p.getInventory().selectedSlot=(p.getInventory().selectedSlot+8)%9;p.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(p.getInventory().selectedSlot));}

   case "slot_select" -> {if(r.slot()>=size)throw new IllegalArgumentException("Slot outside current handler");selectedSlot=r.slot();}

   case "slot_next" -> selectedSlot=(selectedSlot+1)%size;

   case "slot_previous" -> selectedSlot=(selectedSlot+size-1)%size;

   case "slot_left" -> manager.clickSlot(handler.syncId,selectedSlot,0,SlotActionType.PICKUP,p);

   case "slot_right" -> manager.clickSlot(handler.syncId,selectedSlot,1,SlotActionType.PICKUP,p);

   case "slot_shift" -> manager.clickSlot(handler.syncId,selectedSlot,0,SlotActionType.QUICK_MOVE,p);

   case "slot_swap" -> manager.clickSlot(handler.syncId,selectedSlot,r.button(),SlotActionType.SWAP,p);

   case "craft_output" -> {if(!(handler instanceof net.minecraft.screen.CraftingScreenHandler)&&!(handler instanceof net.minecraft.screen.PlayerScreenHandler))throw new IllegalStateException("Open the player or crafting-table grid first");manager.clickSlot(handler.syncId,0,0,SlotActionType.PICKUP,p);}

   case "drop" -> p.dropSelectedItem(false);

   case "swap_offhand" -> p.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,BlockPos.ORIGIN,Direction.DOWN));

   case "respawn" -> {if(p.isAlive())throw new IllegalStateException("Player is alive");p.requestRespawn();}

   case "gui_left","gui_right" -> {if(c.currentScreen==null)throw new IllegalStateException("No open screen");double x=r.x()*c.getWindow().getScaledWidth(),y=r.y()*c.getWindow().getScaledHeight();c.currentScreen.mouseClicked(x,y,r.action().equals("gui_right")?1:0);c.currentScreen.mouseReleased(x,y,r.action().equals("gui_right")?1:0);}

   default -> throw new IllegalArgumentException("Unsupported action");

  }

 }

}

