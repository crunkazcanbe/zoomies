package com.dogpound.zoomies.mixin;

import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ForgeRegistry.dump only writes every registry entry to the TRACE log (~185,000 lines, twice per world join here:
 * ~3 s of the join, and more at startup). Nothing reads it back; skip it.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class MixinForgeRegistryNoDump {
    @Inject(method = "dump", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$noDump(CallbackInfo ci) { ci.cancel(); }
}
