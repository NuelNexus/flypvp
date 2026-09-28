package dev.flycolony.bridge.mixin;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionAccess {
 @Accessor("currentBreakingProgress") float flyBreakingProgress();
}
