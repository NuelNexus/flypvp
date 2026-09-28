package dev.flycolony.bridge.mixin;
import dev.flycolony.bridge.FlyBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class ClientTickMixin {
 // Vanilla gates sustained breaking on cursor lock. An authenticated controller
 // owns the key state while active, including when the user is watching OBS.
 // Keep vanilla reach, survival breaking speed, screen and attack-key checks.
 @Redirect(method="handleInputEvents",at=@At(value="INVOKE",target="Lnet/minecraft/client/Mouse;isCursorLocked()Z"))
 private boolean flyAllowHeldMining(Mouse mouse){return FlyBridge.INSTANCE.active()||mouse.isCursorLocked();}
 @Inject(method="render",at=@At("HEAD"))
 private void flyRenderStart(boolean tick,CallbackInfo ci){FlyBridge.INSTANCE.beforeRender();}
 @Inject(method="tick",at=@At("HEAD"),cancellable=true) private void flyBeforeTick(CallbackInfo ci){if(!FlyBridge.INSTANCE.beforeClientTick())ci.cancel();}
 @Inject(method="tick",at=@At("RETURN")) private void flyAfterTick(CallbackInfo ci){FlyBridge.INSTANCE.afterClientTick();}
 @Inject(method="render",at=@At("RETURN")) private void flyAfterRender(boolean tick,CallbackInfo ci){FlyBridge.INSTANCE.afterRender();}
 @Inject(method="stop",at=@At("HEAD")) private void flyStop(CallbackInfo ci){FlyBridge.INSTANCE.shutdown();}
}
