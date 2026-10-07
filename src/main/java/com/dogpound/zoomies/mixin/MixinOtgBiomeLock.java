package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** OTG's (cached) biome generator, one caller at a time: DH workers build OTG LODs off the server thread (com.dogpound.zoomies.DhOffThread). */
@Pseudo
@Mixin(targets = "com.pg85.otg.generator.biome.CachedBiomeGenerator", remap = false)
public abstract class MixinOtgBiomeLock {
    @Inject(method = {"getBiome", "getBiomes", "getBiomesUnZoomed", "cleanupCache"}, at = @At("HEAD"), require = 0)
    private void zoomies$enter(CallbackInfo ci) { com.dogpound.zoomies.DhOffThread.otgBiomesEnter(); }

    @Inject(method = {"getBiome", "getBiomes", "getBiomesUnZoomed", "cleanupCache"}, at = @At("RETURN"), require = 0)
    private void zoomies$exit(CallbackInfo ci) { com.dogpound.zoomies.DhOffThread.otgBiomesExit(); }
}
