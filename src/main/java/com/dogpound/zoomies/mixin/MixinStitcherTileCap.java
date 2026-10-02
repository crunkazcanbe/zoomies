package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.ZoomiesConfig;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The big pack's main texture sheet sits right at the GPU's 16384x16384 limit, so whether it fits changed from launch
 * to launch ("Unable to fit: craftspeedwheels:... 256x256", 2026-10-02). Vehicle packs load their textures from their
 * own jars, so the Pride16 resource pack can't shrink them. Vanilla's Stitcher already knows how to scale down any
 * single sprite bigger than maxTileDimension — vanilla just passes 0 (no limit). Zoomies passes a cap instead
 * (textures.maxTileSize, default 128): normal 16–64 px textures are untouched, giant ones are shrunk to fit.
 */
@Mixin(Stitcher.class)
public abstract class MixinStitcherTileCap {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private static int zoomies$capTiles(int maxTileDimension) {
        int cap = ZoomiesConfig.num("textures.maxTileSize", 128);
        if (cap <= 0) return maxTileDimension;
        return maxTileDimension <= 0 || maxTileDimension > cap ? cap : maxTileDimension;
    }
}
