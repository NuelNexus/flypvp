package dev.flycolony.bridge.mixin;
import dev.flycolony.bridge.FlyBridge;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(RenderTickCounter.Dynamic.class)
public abstract class RenderClockMixin {
 @Inject(method="beginRenderTick(JZ)I",at=@At("RETURN"),cancellable=true)
 private void flyTickBudget(long now,boolean tick,CallbackInfoReturnable<Integer> cir){if(FlyBridge.INSTANCE.pairedActive())cir.setReturnValue(FlyBridge.INSTANCE.hasWork()?1:0);}
}
