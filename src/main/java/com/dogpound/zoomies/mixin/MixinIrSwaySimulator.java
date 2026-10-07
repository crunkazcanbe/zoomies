package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-10-06 crash (her test pack, placing a train): IR's SwaySimulator keeps its per-car sway in a plain
 * HashMap that the render thread (getEffectRollDegrees) and the client tick (ClientPartDragging ->
 * Control.getBoundingBox) both computeIfAbsent into -> ConcurrentModificationException. Thread-safe map.
 */
@Pseudo
@Mixin(targets = "cam72cam.immersiverailroading.model.part.SwaySimulator", remap = false)
public class MixinIrSwaySimulator {
    @Shadow @Final @Mutable private Map<Object, Object> effects;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void zoomies$threadSafeSway(CallbackInfo ci) {
        this.effects = new ConcurrentHashMap<>(this.effects);
    }
}
