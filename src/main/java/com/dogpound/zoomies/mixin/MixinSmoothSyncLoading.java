package com.dogpound.zoomies.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RenderLib's frame limiter (SmoothSync.sync) sleeps and then spins to hold the frame rate, and with no world open
 * Minecraft caps at 30 FPS — so every refresh of the LOADING screen could wait up to 33 ms. Thousands of refreshes:
 * measured ~20% of the main thread's whole launch, a lot of it asleep. While loading (no world, no menu) there is
 * nothing to pace, so don't wait. Menus and gameplay keep their normal frame limit.
 * EARLY patch list on purpose: RenderLib is a coremod, so SmoothSync is loaded before late patches exist.
 */
@Mixin(targets = "meldexun.renderlib.util.SmoothSync", remap = false)
public abstract class MixinSmoothSyncLoading {
    @Inject(method = "sync(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zoomies$noWaitWhileLoading(int fps, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        // run 8 (2026-09-28): still 20 % asleep — PrideCanvas's loading screen IS a screen, so "no screen" never held.
        // Now: skip the wait for the whole mod-loading phase, whatever is on screen; menus/gameplay keep their limit.
        boolean modsLoading = !net.minecraftforge.fml.common.Loader.instance().hasReachedState(net.minecraftforge.fml.common.LoaderState.AVAILABLE);
        if (mc != null && mc.world == null && (modsLoading || mc.currentScreen == null)) ci.cancel();
    }
}
