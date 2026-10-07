package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Immersive Railroading loads each stock model and then runs System.gc() whenever Runtime.freeMemory() is under a
 * quarter of the max heap. freeMemory() is measured against the heap claimed SO FAR, not the max, so in a big pack the
 * check is almost always true: ~80 back-to-back full collections, ~40 s of every launch (GC log, 2026-10-04).
 * Answer with the real headroom (max - used) instead, so it still collects if memory really runs low.
 */
@Mixin(targets = "cam72cam.immersiverailroading.registry.DefinitionManager", remap = false)
public abstract class MixinIrNoForcedGc {
    @Redirect(method = "lambda$initModels$0", require = 0,
              at = @At(value = "INVOKE", target = "Ljava/lang/Runtime;freeMemory()J"))
    private static long zoomies$realHeadroom(Runtime rt) {
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
    }
}
