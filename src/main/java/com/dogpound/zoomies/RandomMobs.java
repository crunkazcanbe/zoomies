package com.dogpound.zoomies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * OptiFine "Random Entities": a texture pack can give one mob several looks. Reads OptiFine's own layout so existing
 * packs work: textures/entity/cow/cow.png -> optifine/random/entity/cow/cow2.png, cow3.png... (or the older
 * mcpatcher/mob/cow/cow2.png). Each mob keeps its look for good (picked from its UUID).
 * ponytail: plain numbered variants only — OptiFine's .properties rules (by biome/height/name) aren't read yet.
 */
public final class RandomMobs {
    private static final Map<ResourceLocation, ResourceLocation[]> VARIANTS = new ConcurrentHashMap<>();
    private static boolean listening;

    public static ResourceLocation pick(Entity e, ResourceLocation base) {
        if (base == null || e == null || !ZoomiesConfig.on("textures.randomMobs")) return base;
        ResourceLocation[] v = VARIANTS.computeIfAbsent(base, RandomMobs::find);
        if (v.length == 0) return base;
        long h = e.getUniqueID().getMostSignificantBits() ^ e.getUniqueID().getLeastSignificantBits();
        int i = (int) Math.floorMod(h ^ (h >>> 31), (long) v.length + 1);
        return i == 0 ? base : v[i - 1];
    }

    private static ResourceLocation[] find(ResourceLocation base) {
        Minecraft mc = Minecraft.getMinecraft();
        IResourceManager rm = mc.getResourceManager();
        if (!listening && rm instanceof IReloadableResourceManager) {        // new texture pack = look again
            ((IReloadableResourceManager) rm).registerReloadListener(r -> VARIANTS.clear());
            listening = true;
        }
        String p = base.getResourcePath();
        if (!p.startsWith("textures/entity/") || !p.endsWith(".png")) return new ResourceLocation[0];
        String stem = p.substring("textures/entity/".length(), p.length() - 4);
        for (String dir : new String[]{"optifine/random/entity/", "mcpatcher/mob/"}) {
            List<ResourceLocation> out = new ArrayList<>();
            for (int n = 2; n < 64; n++) {
                ResourceLocation r = new ResourceLocation(base.getResourceDomain(), dir + stem + n + ".png");
                try { rm.getResource(r).close(); } catch (Exception missing) { break; }
                out.add(r);
            }
            if (!out.isEmpty()) return out.toArray(new ResourceLocation[0]);
        }
        return new ResourceLocation[0];
    }
}
