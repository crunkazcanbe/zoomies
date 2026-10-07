package com.dogpound.zoomies.mixin;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.dogpound.zoomies.BootCaches;
import com.dogpound.zoomies.ZoomiesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.embeddedt.vintagefix.dynamicresources.TextureCollector;
import org.embeddedt.vintagefix.dynamicresources.model.ModelLocationInformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VintageFix finds every texture to load by listing every file of every mod jar + resource pack and reading every
 * blockstate/model JSON (413k sprites, ~12 s, the client waits ~8 s of it). The answer depends only on those files
 * and on which block/item models are registered, so Zoomies saves the set and reuses it while the key matches:
 * mod list (sizes + dates) + VintageFix version + each active resource pack (size + date) + resources/ folders +
 * a hash of every registered model name. Miss / no key = VintageFix's own search, then saved.
 * Verify mode (zoomies-verify file) runs the real search on a hit too and logs MISMATCH.
 */
@Mixin(value = TextureCollector.class, remap = false)
public abstract class MixinVintageFixTextureCache {
    @Shadow List<IResourcePack> resourcePackList;
    private String zoomies$key;
    private long zoomies$start;
    private Set<ResourceLocation> zoomies$cached;

    @Inject(method = "getAllTextureLocations", at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$fromCache(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        zoomies$start = System.nanoTime();
        try {
            ModelLocationInformation.initFuture.join();          // VintageFix's own search waits for this too
            long h = 0;
            for (ModelResourceLocation m : ModelLocationInformation.allItemVariants) h += zoomies$fnv(m.toString());
            for (Collection<ModelResourceLocation> c : ModelLocationInformation.validVariantsForBlock.values())
                for (ModelResourceLocation m : c) h += 31 * zoomies$fnv(m.toString());
            for (ResourceLocation r : ModelLocationInformation.inventoryVariantLocations.values()) h += 17 * zoomies$fnv(r.toString());
            ModContainer vf = Loader.instance().getIndexedModList().get("vintagefix");
            zoomies$key = BootCaches.vfKey(resourcePackList, Minecraft.getMinecraft().mcDataDir, h, vf == null ? "?" : vf.getVersion());
            if (zoomies$key == null) return;
            Set<ResourceLocation> saved = BootCaches.vfLoad(zoomies$key);
            if (saved == null) return;
            if (ZoomiesConfig.verify()) { zoomies$cached = saved; return; }    // compare with the real search at RETURN
            cir.setReturnValue(saved);
        } catch (Throwable e) {
            zoomies$key = null;
            System.out.println("[Zoomies] VintageFix texture cache skipped: " + e);
        }
    }

    @Inject(method = "getAllTextureLocations", at = @At("RETURN"), remap = false)
    private void zoomies$save(CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        if (zoomies$key == null) return;
        Set<ResourceLocation> real = cir.getReturnValue();
        if (zoomies$cached != null) {
            if (zoomies$cached.equals(real)) { System.out.println("[Zoomies] VintageFix texture cache verified (" + real.size() + " sprites)"); return; }
            int missing = 0, extra = 0;
            for (ResourceLocation r : real) if (!zoomies$cached.contains(r)) missing++;
            for (ResourceLocation r : zoomies$cached) if (!real.contains(r)) extra++;
            System.out.println("[Zoomies] MISMATCH VintageFix texture cache: " + missing + " missing, " + extra + " extra - saving the fresh list");
        }
        BootCaches.vfSave(zoomies$key, real, System.nanoTime() - zoomies$start);
    }

    private static long zoomies$fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x100000001b3L; }
        return h;
    }
}
