package dev.flycolony.bridge.mixin;
import dev.flycolony.bridge.FlyBridge;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
 @Inject(method="onKey",at=@At("HEAD")) private void flyEmergency(long window,int key,int scan,int action,int mods,CallbackInfo ci){if(key==GLFW.GLFW_KEY_F8&&action==GLFW.GLFW_PRESS)FlyBridge.INSTANCE.stop("F8 manual stop");}
}
