package com.dogpound.zoomies.mixin;

import net.minecraft.world.gen.layer.IntCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** IntCache pool per thread (com.dogpound.zoomies.ThreadIntCache) so biome layers can run off the server thread. */
@Mixin(IntCache.class)
public abstract class MixinIntCacheThreadLocal {
    @Inject(method = "func_76445_a", at = @At("HEAD"), cancellable = true, remap = false) // getIntCache
    private static void zoomies$get(int size, CallbackInfoReturnable<int[]> cir) { cir.setReturnValue(com.dogpound.zoomies.ThreadIntCache.get(size)); }

    @Inject(method = "func_76446_a", at = @At("HEAD"), cancellable = true, remap = false) // resetIntCache
    private static void zoomies$reset(CallbackInfo ci) { com.dogpound.zoomies.ThreadIntCache.reset(); ci.cancel(); }

    @Inject(method = "func_85144_b", at = @At("HEAD"), cancellable = true, remap = false) // getCacheSizes
    private static void zoomies$sizes(CallbackInfoReturnable<String> cir) { cir.setReturnValue(com.dogpound.zoomies.ThreadIntCache.sizes()); }
}
