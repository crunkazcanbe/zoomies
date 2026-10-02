package com.dogpound.zoomies.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Railcraft's ore generator asks RailcraftConfig.isWorldGenEnabled(name) for EVERY block it considers replacing, and
 * each call cleans the name with a regex (String.replaceFirst) — showed up in the fresh-world server profile
 * (2026-10-02). The answer per name never changes after config load, so remember it.
 */
@Mixin(targets = "mods.railcraft.common.core.RailcraftConfig", remap = false)
public abstract class MixinRailcraftWorldGenCache {
    @Unique private static final Map<String, Boolean> zoomies$known = new ConcurrentHashMap<>();

    @Inject(method = "isWorldGenEnabled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zoomies$cached(String name, CallbackInfoReturnable<Boolean> cir) {
        if (name == null) return;
        Boolean b = zoomies$known.get(name);
        if (b != null) cir.setReturnValue(b);
    }

    @Inject(method = "isWorldGenEnabled", at = @At("RETURN"), require = 0)
    private static void zoomies$remember(String name, CallbackInfoReturnable<Boolean> cir) {
        if (name != null) zoomies$known.put(name, cir.getReturnValue());
    }
}
