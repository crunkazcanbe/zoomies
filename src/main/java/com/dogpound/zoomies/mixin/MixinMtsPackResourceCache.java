package com.dogpound.zoomies.mixin;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Her render list #12 (resource index), found by the run 7/8 profiles: Immersive Vehicles answers every resource
 * question for its packs with Class.getResourceAsStream — a walk through all ~500 mod jars, one zip at a time
 * (jdk zipfs normalize/pathHasDotOrDotDot = the #1 hot spot of whole load phases). Most answers are "not here".
 * The jars can't change while the game runs, so remember the misses and answer those instantly.
 */
@Mixin(targets = "mcinterface1122.InterfaceCore", remap = false)
public abstract class MixinMtsPackResourceCache {
    private static final Set<String> zoomies$missing = ConcurrentHashMap.newKeySet();

    @Inject(method = "getPackResource(Ljava/lang/String;)Ljava/io/InputStream;", at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$knownMissing(String path, CallbackInfoReturnable<InputStream> cir) {
        if (zoomies$missing.contains(path)) cir.setReturnValue(null);
    }

    @Inject(method = "getPackResource(Ljava/lang/String;)Ljava/io/InputStream;", at = @At("RETURN"), remap = false)
    private void zoomies$remember(String path, CallbackInfoReturnable<InputStream> cir) {
        if (cir.getReturnValue() == null) zoomies$missing.add(path);
    }
}
