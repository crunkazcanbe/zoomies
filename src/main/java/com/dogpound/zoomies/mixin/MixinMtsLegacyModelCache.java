package com.dogpound.zoomies.mixin;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import com.dogpound.zoomies.BootCaches;
import com.dogpound.zoomies.ZoomiesConfig;
import minecrafttransportsimulator.rendering.AModelParser;
import minecrafttransportsimulator.rendering.RenderableVertices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Immersive Vehicles' legacy light/tread compat parses every vehicle's OBJ model at startup (7,336 models, ~15 s)
 * only to look at objects named with '&' (lights) or "roller" (treads); every other object is ignored, and the method
 * only iterates the list in order. So the answer is the same list filtered to those objects (empty = the model has
 * none, which the compat treats exactly like a model without lights/rollers). BootCaches keeps that filtered list on
 * disk per model; a cache miss runs the real parser unchanged (same arguments, so the same in-memory caching).
 * On a hit the full model is no longer parsed at boot — it is parsed the first time that vehicle is drawn, as for
 * any model the compat never touched. Verify mode (zoomies-verify file) re-parses and logs MISMATCH.
 */
@Mixin(targets = "minecrafttransportsimulator.packloading.LegacyCompatSystem", remap = false)
public abstract class MixinMtsLegacyModelCache {
    @Redirect(method = "performModelLegacyCompats", require = 0, remap = false,
              at = @At(value = "INVOKE", target = "Lminecrafttransportsimulator/rendering/AModelParser;parseModel(Ljava/lang/String;Z)Ljava/util/List;"))
    private static List<RenderableVertices> zoomies$cachedModel(String location, boolean returnCached) {
        BootCaches.Model m = BootCaches.mtsGet(location);
        if (m != null) {
            List<RenderableVertices> out = new ArrayList<>(m.names.length);
            for (int i = 0; i < m.names.length; i++)
                out.add(new RenderableVertices(m.names[i], FloatBuffer.wrap(m.vertices[i].clone()), m.cacheVertices[i]));
            if (ZoomiesConfig.verify()) {
                BootCaches.Model fresh = zoomies$filter(AModelParser.parseModel(location, returnCached), 0);
                if (!zoomies$same(m, fresh)) System.out.println("[Zoomies] MISMATCH Immersive Vehicles model cache: " + location);
            }
            return out;
        }
        long t = System.nanoTime();
        List<RenderableVertices> full = AModelParser.parseModel(location, returnCached);   // throws = nothing saved, as before
        BootCaches.mtsPut(location, zoomies$filter(full, System.nanoTime() - t));
        return full;
    }

    private static BootCaches.Model zoomies$filter(List<RenderableVertices> full, long nanos) {
        List<RenderableVertices> keep = new ArrayList<>();
        for (RenderableVertices v : full) if (BootCaches.mtsWanted(v.name)) keep.add(v);
        String[] names = new String[keep.size()];
        boolean[] cv = new boolean[keep.size()];
        float[][] data = new float[keep.size()][];
        for (int i = 0; i < names.length; i++) {
            RenderableVertices v = keep.get(i);
            names[i] = v.name;
            cv[i] = v.cacheVertices;
            FloatBuffer b = v.vertices;
            data[i] = new float[b.capacity()];
            for (int k = 0; k < data[i].length; k++) data[i][k] = b.get(k);      // absolute reads: position untouched
        }
        return new BootCaches.Model(names, cv, data, nanos);
    }

    private static boolean zoomies$same(BootCaches.Model a, BootCaches.Model b) {
        if (a.names.length != b.names.length) return false;
        for (int i = 0; i < a.names.length; i++)
            if (!a.names[i].equals(b.names[i]) || a.cacheVertices[i] != b.cacheVertices[i]
                    || !java.util.Arrays.equals(a.vertices[i], b.vertices[i])) return false;
        return true;
    }
}
