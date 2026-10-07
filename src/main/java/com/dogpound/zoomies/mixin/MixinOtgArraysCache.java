package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** OTG keeps only 4 biome scratch caches and returns null (-> NPE) when all are taken; with DH workers too, make a spare one instead. */
@Pseudo
@Mixin(targets = "com.pg85.otg.generator.biome.ArraysCacheManager", remap = false)
public abstract class MixinOtgArraysCache {
    @Inject(method = "getCache", at = @At("RETURN"), cancellable = true, require = 0)
    private static void zoomies$spare(CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() == null) cir.setReturnValue(com.dogpound.zoomies.DhOffThread.spareOtgCache());
    }
}
