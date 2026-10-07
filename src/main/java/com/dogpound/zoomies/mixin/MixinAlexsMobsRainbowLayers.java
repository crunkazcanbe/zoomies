package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 2026-10-06 crash (her main pack, ~1 min after the world loaded): Alex's Mobs walks every entity renderer each client
 * tick (registerRainbowLayers) while another mod adds renderers -> ConcurrentModificationException -> crash screen.
 * Walk a snapshot instead; a snapshot that itself collides just waits for the next tick. The renderer list is also
 * changed from another thread (13:24 crash: copying it hit an index past its end), so ANY failure while copying = skip
 * this tick. And only redo the walk when the list grew: it was 4,281 renderers, 20 times a second, for nothing.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.client.ClientLayerRegistry", remap = false)
public class MixinAlexsMobsRainbowLayers {
    @Redirect(method = "registerRainbowLayers", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"), require = 0)
    private static Collection<?> zoomies$snapshot(Map<?, ?> map) {
        Integer seen = zoomies$sizes.get(map);
        int size = map.size();
        if (seen != null && seen == size) return Collections.emptyList();
        try {
            Collection<?> copy = new ArrayList<>(map.values());
            zoomies$sizes.put(map, size);
            return copy;
        } catch (RuntimeException e) {          // ConcurrentModification / ArrayIndexOutOfBounds from the other thread
            return Collections.emptyList();
        }
    }

    private static final Map<Map<?, ?>, Integer> zoomies$sizes = new java.util.IdentityHashMap<>();
}
