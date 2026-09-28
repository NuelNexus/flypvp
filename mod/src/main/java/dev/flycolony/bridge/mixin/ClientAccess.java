package dev.flycolony.bridge.mixin;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(MinecraftClient.class)
public interface ClientAccess {
 @Invoker("doAttack") boolean flyAttack();
 @Invoker("doItemUse") void flyUse();
}
