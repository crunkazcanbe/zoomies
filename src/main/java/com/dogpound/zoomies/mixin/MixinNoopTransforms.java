package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.Discovery;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Her render list #1 (2026-09-28): old renderers call translate(0,0,0), rotate(0°) and scale(1,1,1) thousands of
 * times a frame — each one is a trip into the graphics driver that changes nothing. Skip exactly those; anything
 * that really moves, turns or sizes goes through untouched.
 */
@Mixin(GlStateManager.class)
public abstract class MixinNoopTransforms {
    @Inject(method = "func_179109_b(FFF)V", at = @At("HEAD"), cancellable = true, remap = false) // translate
    private static void zoomies$translateF(float x, float y, float z, CallbackInfo ci) { if (Discovery.on) { Discovery.matrixOps++; if (x == 0 && y == 0 && z == 0) Discovery.noopMatrix++; } if (Discovery.skipNoop && x == 0 && y == 0 && z == 0) ci.cancel(); }

    @Inject(method = "func_179137_b(DDD)V", at = @At("HEAD"), cancellable = true, remap = false) // translate
    private static void zoomies$translateD(double x, double y, double z, CallbackInfo ci) { if (Discovery.on) { Discovery.matrixOps++; if (x == 0 && y == 0 && z == 0) Discovery.noopMatrix++; } if (Discovery.skipNoop && x == 0 && y == 0 && z == 0) ci.cancel(); }

    @Inject(method = "func_179114_b(FFFF)V", at = @At("HEAD"), cancellable = true, remap = false) // rotate
    private static void zoomies$rotate(float angle, float x, float y, float z, CallbackInfo ci) { if (Discovery.on) { Discovery.matrixOps++; if (angle == 0) Discovery.noopMatrix++; } if (Discovery.skipNoop && angle == 0) ci.cancel(); }

    @Inject(method = "func_179152_a(FFF)V", at = @At("HEAD"), cancellable = true, remap = false) // scale
    private static void zoomies$scaleF(float x, float y, float z, CallbackInfo ci) { if (Discovery.on) { Discovery.matrixOps++; if (x == 1 && y == 1 && z == 1) Discovery.noopMatrix++; } if (Discovery.skipNoop && x == 1 && y == 1 && z == 1) ci.cancel(); }

    @Inject(method = "func_179139_a(DDD)V", at = @At("HEAD"), cancellable = true, remap = false) // scale
    private static void zoomies$scaleD(double x, double y, double z, CallbackInfo ci) { if (Discovery.on) { Discovery.matrixOps++; if (x == 1 && y == 1 && z == 1) Discovery.noopMatrix++; } if (Discovery.skipNoop && x == 1 && y == 1 && z == 1) ci.cancel(); }
}
