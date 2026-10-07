package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capsule's own template cache never hits for reward structures (stored under another key than it looks up), so every
 * loot chest re-read and data-fixed a structure file: 10 of 10 server-thread samples in her new world (2026-10-04;
 * Distant Horizons stuck at 1 chunk/s, land cut off ~8 chunks out). Remember every answer per id, loaded or not.
 */
@Mixin(targets = "capsule.structure.CapsuleTemplateManager", remap = false)
public abstract class MixinCapsuleTemplateMiss {
    private static final Object ZOOMIES_NONE = new Object();
    private static final java.util.Map<String, Object> ZOOMIES_CACHE = new java.util.concurrent.ConcurrentHashMap<String, Object>();

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$cached(net.minecraft.server.MinecraftServer server, net.minecraft.util.ResourceLocation id, CallbackInfoReturnable<Object> cir) {
        if (id == null) return;
        Object c = ZOOMIES_CACHE.get(System.identityHashCode(this) + "|" + id);
        if (c != null) cir.setReturnValue(c == ZOOMIES_NONE ? null : c);
    }

    @Inject(method = "get", at = @At("RETURN"), require = 0)
    private void zoomies$remember(net.minecraft.server.MinecraftServer server, net.minecraft.util.ResourceLocation id, CallbackInfoReturnable<Object> cir) {
        if (id == null) return;
        Object v = cir.getReturnValue();
        if (ZOOMIES_CACHE.size() > 4096) ZOOMIES_CACHE.clear();
        ZOOMIES_CACHE.put(System.identityHashCode(this) + "|" + id, v == null ? ZOOMIES_NONE : v);
    }
}
