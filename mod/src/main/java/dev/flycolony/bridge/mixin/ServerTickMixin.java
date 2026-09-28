package dev.flycolony.bridge.mixin;
import dev.flycolony.bridge.FlyBridge;
import dev.flycolony.bridge.StepGate;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftServer.class)
public abstract class ServerTickMixin {
 @Unique private StepGate.Ticket flyTicket;
 @Inject(method="tick",at=@At("HEAD")) private void flyBefore(BooleanSupplier keepTicking,CallbackInfo ci){flyTicket=FlyBridge.INSTANCE.beforeServerTick((MinecraftServer)(Object)this);}
 @Inject(method="tick",at=@At("RETURN")) private void flyAfter(BooleanSupplier keepTicking,CallbackInfo ci){FlyBridge.INSTANCE.afterServerTick((MinecraftServer)(Object)this,flyTicket);flyTicket=null;}
}
