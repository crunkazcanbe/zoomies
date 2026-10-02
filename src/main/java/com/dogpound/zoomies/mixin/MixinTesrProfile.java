package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.RenderProfiler;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Machine renderers (TESRs): (1) her render list #8 — a machine smaller than a few pixels on screen isn't drawn
 * (never within 16 blocks, never ones with an infinite render box like beacon beams); (2) timing for the report.
 */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTesrProfile {
    @Unique private long zoomies$t;

    @Inject(method = "func_192854_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$start(TileEntity te, double x, double y, double z, float pt, int destroy, float alpha, CallbackInfo ci) {
        if (te != null && (com.dogpound.zoomies.ZoomiesConfig.Live.machineCull || com.dogpound.zoomies.Discovery.on)) {
            boolean tiny = zoomies$tiny(te, x, y, z);
            if (com.dogpound.zoomies.Discovery.on) { com.dogpound.zoomies.Discovery.machineDraws++; if (tiny) com.dogpound.zoomies.Discovery.machineTiny++; }
            if (tiny && com.dogpound.zoomies.ZoomiesConfig.Live.machineCull) { ci.cancel(); return; }
        }
        if (RenderProfiler.on) zoomies$t = System.nanoTime();
    }

    @Unique
    private static boolean zoomies$tiny(TileEntity te, double x, double y, double z) {
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d < 16) return false;
        net.minecraft.util.math.AxisAlignedBB bb = te.getRenderBoundingBox();
        if (bb == null || bb == TileEntity.INFINITE_EXTENT_AABB) return false;
        double size = Math.min(64, Math.max(bb.maxX - bb.minX, Math.max(bb.maxY - bb.minY, bb.maxZ - bb.minZ)));
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        double fov = Math.toRadians(Math.max(30, mc.gameSettings.fovSetting));
        return size / d / (2 * Math.tan(fov / 2)) * mc.displayHeight < com.dogpound.zoomies.ZoomiesConfig.Live.minPixels;
    }

    @Inject(method = "func_192854_a", at = @At("RETURN"), remap = false)
    private void zoomies$end(TileEntity te, double x, double y, double z, float pt, int destroy, float alpha, CallbackInfo ci) {
        if (RenderProfiler.on && zoomies$t != 0 && te != null) { RenderProfiler.add("machine", te.getClass(), System.nanoTime() - zoomies$t); zoomies$t = 0; }
    }
}
