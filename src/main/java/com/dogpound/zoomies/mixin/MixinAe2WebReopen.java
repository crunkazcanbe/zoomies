package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2026-10-06 (her main pack): AE2 Web Integration opens its web server (port 2324) every time a world starts and never
 * closes it when the world stops, so the SECOND world opened in one session crashes on "Address already in use" and
 * drops straight back to the menu with no message. Close the previous server before opening a new one.
 */
@Pseudo
@Mixin(targets = "pl.kuba6000.ae2webintegration.core.AE2Controller", remap = false)
public class MixinAe2WebReopen {
    @Shadow private static com.sun.net.httpserver.HttpServer server;

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private static void zoomies$closeOldServer(CallbackInfo ci) {
        if (server != null) {
            try { server.stop(0); } catch (Throwable ignored) { }
            server = null;
        }
    }
}
