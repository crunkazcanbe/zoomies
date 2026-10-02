package com.dogpound.zoomies;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/**
 * Some mods add their own resource pack and then force a FULL resource reload (every texture, sound, language, model
 * listener) just so the game notices it — during start-up, when the game reloads everything again at the end anyway.
 * Measured 2026-10-02 in the big pack: Immersive Vehicles 15 s, UnlimitedChiselWorks 5 s.
 * Instead: hand any pack that isn't registered yet straight to the resource manager (vanilla's reloadResourcePack,
 * which adds a pack without running the listeners). The end-of-load reload still processes everything.
 */
public final class PackAdd {
    /** returns true if it handled the request; false = caller should do the original full reload */
    @SuppressWarnings("unchecked")
    public static boolean addNewPacksOnly() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (!(mc.getResourceManager() instanceof SimpleReloadableResourceManager)) return false;
            SimpleReloadableResourceManager rm = (SimpleReloadableResourceManager) mc.getResourceManager();
            List<IResourcePack> defaults = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, mc, "field_110449_ao");
            Map<String, FallbackResourceManager> domains = ObfuscationReflectionHelper.getPrivateValue(SimpleReloadableResourceManager.class, rm, "field_110548_a");
            Set<IResourcePack> known = Collections.newSetFromMap(new IdentityHashMap<>());
            for (FallbackResourceManager f : domains.values()) {
                Collection<IResourcePack> packs = ObfuscationReflectionHelper.getPrivateValue(FallbackResourceManager.class, f, "field_110540_a");
                known.addAll(packs);
            }
            int added = 0;
            for (IResourcePack p : defaults) if (!known.contains(p)) { rm.reloadResourcePack(p); added++; }
            System.out.println("[Zoomies] skipped a full resource reload — added " + added + " new resource pack(s) directly");
            return true;
        } catch (Throwable t) {
            System.out.println("[Zoomies] pack-add shortcut failed, doing the full reload instead: " + t);
            return false;
        }
    }

    private PackAdd() {}
}
