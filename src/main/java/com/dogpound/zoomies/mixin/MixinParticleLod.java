package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.ZoomiesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle level of detail: every particle close to you, fewer the further away (you can't tell 300 far-off smoke
 * puffs from 75), none past the last distance. Only what's DRAWN changes — nothing in the world does.
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleLod {
    @Unique private int zoomies$n;

    @Inject(method = "func_78873_a", at = @At("HEAD"), cancellable = true, remap = false) // addEffect
    private void zoomies$lod(Particle p, CallbackInfo ci) {
        if (!ZoomiesConfig.Live.particles) return;
        Entity cam = Minecraft.getMinecraft().getRenderViewEntity();
        if (p == null || cam == null) return;
        int FULL = ZoomiesConfig.Live.particleFull, HALF = ZoomiesConfig.Live.particleHalf, QUARTER = ZoomiesConfig.Live.particleQuarter;
        net.minecraft.util.math.AxisAlignedBB b = p.getBoundingBox();          // public; its middle is where the particle is
        double dx = (b.minX + b.maxX) / 2 - cam.posX, dy = (b.minY + b.maxY) / 2 - cam.posY, dz = (b.minZ + b.maxZ) / 2 - cam.posZ;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 <= (double) FULL * FULL) return;
        int n = ++zoomies$n;
        if (d2 <= (double) HALF * HALF) { if ((n & 1) != 0) ci.cancel(); return; }      // keep 1 in 2
        if (d2 <= (double) QUARTER * QUARTER) { if ((n & 3) != 0) ci.cancel(); return; } // keep 1 in 4
        ci.cancel();                                                                      // too far to see
    }
}
