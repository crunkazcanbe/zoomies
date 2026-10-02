package com.dogpound.zoomies.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.dogpound.zoomies.WebCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AbyssalCraft downloads its Patreon list from GitHub at every startup with no time limit — measured 64 s of loading
 * stuck in connect. Use the saved copy (refreshed in the background) instead; first time, fetch with short limits.
 */
@Mixin(targets = "com.shinoow.abyssalcraft.common.handlers.InternalNecroDataHandler", remap = false)
public abstract class MixinAbyssalPatrons {
    @Redirect(method = "setupPatreonData", at = @At(value = "INVOKE", target = "Ljava/net/URL;openStream()Ljava/io/InputStream;"), remap = false)
    private InputStream zoomies$cached(URL url) throws IOException {
        return WebCache.open(url, "abyssalcraft-patrons.json");
    }
}
