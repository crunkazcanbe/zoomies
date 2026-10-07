package com.dogpound.zoomies.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Why: 2026-10-04 21:27 a dropped "Moss" item got a ~7 billion block/tick push, Valkyrien Skies skipped its collision
 * ("tried going extremely fast"), it flew to x=7,130,714,727 and the server thread never ticked again - land stopped
 * loading, DH stopped, and quitting froze forever (Forge waits for the stopped server). A non-player entity asked to
 * move more than entities.runawayMaxBlocksPerTick (or NaN) is stopped in place instead.
 */
@Mixin(Entity.class)
public abstract class MixinRunawayEntity {
    private static double zoomies$limit = -1;
    private static int zoomies$logged;

    @Inject(method = "func_70091_d", at = @At("HEAD"), cancellable = true, remap = false) // move(MoverType, x, y, z)
    private void zoomies$stopRunaway(MoverType type, double x, double y, double z, CallbackInfo ci) {
        if (zoomies$limit < 0) zoomies$limit = Math.max(16, com.dogpound.zoomies.ZoomiesConfig.num("entities.runawayMaxBlocksPerTick", 10000));
        double lim = zoomies$limit;
        if (Math.abs(x) <= lim && Math.abs(y) <= lim && Math.abs(z) <= lim) return; // also false for NaN -> stopped
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityPlayer) return;
        self.motionX = self.motionY = self.motionZ = 0;
        ci.cancel();
        if (zoomies$logged++ < 20)
            System.out.println("[Zoomies] stopped runaway " + self + " asked to move " + (float) x + ", " + (float) y + ", " + (float) z + " blocks in one tick");
    }
}
