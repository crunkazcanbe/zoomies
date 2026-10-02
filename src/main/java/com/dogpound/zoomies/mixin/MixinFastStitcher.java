package com.dogpound.zoomies.mixin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft's texture stitcher places each sprite by trying every existing slot in turn (recursively), so with tens
 * of thousands of sprites it slows down badly as the sheet fills — measured ~15% of a big pack's whole load.
 * Zoomies packs them in rows ("shelves"), tallest first, into a power-of-two sheet sized from their total area:
 * one pass, no searching. It hands Minecraft the same kind of slot records its own packer makes, so sprite UVs,
 * mipmaps and everything downstream work unchanged. If it can't fit within the card's limit, Minecraft's own
 * packer runs instead (never worse than before).
 */
@Mixin(Stitcher.class)
public abstract class MixinFastStitcher {
    private static Field zoomies$holders, zoomies$slots, zoomies$w, zoomies$h, zoomies$maxW, zoomies$maxH;

    @SuppressWarnings("unchecked")
    @Inject(method = "func_94305_f", at = @At("HEAD"), cancellable = true, remap = false) // doStitch
    private void zoomies$shelfPack(CallbackInfo ci) {
        try {
            if (zoomies$holders == null) {
                zoomies$holders = ObfuscationReflectionHelper.findField(Stitcher.class, "field_94319_a"); // setStitchHolders
                zoomies$slots = ObfuscationReflectionHelper.findField(Stitcher.class, "field_94317_b");   // stitchSlots
                zoomies$w = ObfuscationReflectionHelper.findField(Stitcher.class, "field_94318_c");       // currentWidth
                zoomies$h = ObfuscationReflectionHelper.findField(Stitcher.class, "field_94315_d");       // currentHeight
                zoomies$maxW = ObfuscationReflectionHelper.findField(Stitcher.class, "field_94316_e");    // maxWidth
                zoomies$maxH = ObfuscationReflectionHelper.findField(Stitcher.class, "field_94313_f");    // maxHeight
            }
            Object self = this;
            Set<Stitcher.Holder> set = (Set<Stitcher.Holder>) zoomies$holders.get(self);
            if (set.size() < 64) return;                                   // tiny sheets: not worth it, keep vanilla
            Stitcher.Holder[] holders = set.toArray(new Stitcher.Holder[0]);
            Arrays.sort(holders);                                          // Minecraft's own order: tallest, then widest
            int maxW = zoomies$maxW.getInt(self), maxH = zoomies$maxH.getInt(self);
            long area = 0;
            int widest = 1;
            for (Stitcher.Holder h : holders) { area += (long) h.getWidth() * h.getHeight(); widest = Math.max(widest, h.getWidth()); }
            int w = Math.max(pow2(widest), pow2((int) Math.ceil(Math.sqrt(area))));
            int[] xs = new int[holders.length], ys = new int[holders.length];
            while (w <= maxW) {
                int x = 0, y = 0, shelf = 0;
                for (int i = 0; i < holders.length; i++) {
                    int hw = holders[i].getWidth(), hh = holders[i].getHeight();
                    if (x + hw > w) { y += shelf; x = 0; shelf = 0; }
                    xs[i] = x; ys[i] = y;
                    x += hw;
                    shelf = Math.max(shelf, hh);
                }
                int h = pow2(y + shelf);
                if (h <= maxH && h <= w * 2) {                              // fits, and not a silly tall strip
                    java.util.ArrayList<Stitcher.Slot> made = new java.util.ArrayList<>(holders.length);
                    for (int i = 0; i < holders.length; i++) {
                        Stitcher.Slot s = new Stitcher.Slot(xs[i], ys[i], holders[i].getWidth(), holders[i].getHeight());
                        if (!s.addSlot(holders[i])) return;                   // exact-size slot always takes it; if not, vanilla packs (nothing added yet)
                        made.add(s);
                    }
                    ((List<Stitcher.Slot>) zoomies$slots.get(self)).addAll(made); // all at once: never half a packing
                    zoomies$w.setInt(self, w);
                    zoomies$h.setInt(self, h);
                    ci.cancel();
                    return;
                }
                w *= 2;
            }
            // didn't fit within the card's limit: fall through to Minecraft's packer (it will report the error)
        } catch (Throwable t) {
            System.out.println("[Zoomies] fast stitch skipped: " + t);
        }
    }

    private static int pow2(int v) {
        int p = 1;
        while (p < v) p <<= 1;
        return p;
    }
}
