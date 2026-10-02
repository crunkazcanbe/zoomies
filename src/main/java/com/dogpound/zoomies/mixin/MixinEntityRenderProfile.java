package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.RenderProfiler;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity drawing: (1) screen-size culling — a mob that would be under entities.minPixels tall on screen isn't drawn
 * (never players, named/glowing mobs, bosses, or what you ride); (2) the render cost report timing (/zoomies profile).
 */
@Mixin(RenderManager.class)
public abstract class MixinEntityRenderProfile {
    @Unique private long zoomies$t;
    @Unique private int zoomies$depth;


    @Inject(method = "func_188391_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$start(Entity e, double x, double y, double z, float yaw, float pt, boolean debug, CallbackInfo ci) {
        if (com.dogpound.zoomies.Discovery.on && e != null && zoomies$depth == 0) {
            com.dogpound.zoomies.Discovery.entityDraws++;
            if (zoomies$tiny(e, x, y, z)) com.dogpound.zoomies.Discovery.entityTiny++;
        }
        if (com.dogpound.zoomies.ZoomiesConfig.Live.entityCull && e != null && zoomies$depth == 0 && zoomies$tiny(e, x, y, z)) { ci.cancel(); return; } // before depth++: RETURN won't run
        if (RenderProfiler.on && zoomies$depth++ == 0) zoomies$t = System.nanoTime(); // riders render inside their mount
    }

    /** x,y,z are camera-relative. pixels tall = size / distance / (2 tan(fov/2)) * screen height */
    @Unique
    private static boolean zoomies$tiny(Entity e, double x, double y, double z) {
        if (e instanceof net.minecraft.entity.player.EntityPlayer || e.hasCustomName() || e.isGlowing() || !e.isNonBoss()) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player != null && (e.isRidingOrBeingRiddenBy(mc.player))) return false;
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d < 16) return false;
        double size = Math.max(e.height, e.width);
        double fov = Math.toRadians(Math.max(30, mc.gameSettings.fovSetting));
        double px = size / d / (2 * Math.tan(fov / 2)) * mc.displayHeight;
        return px < com.dogpound.zoomies.ZoomiesConfig.Live.minPixels;
    }

    @Inject(method = "func_188391_a", at = @At("RETURN"), remap = false)
    private void zoomies$end(Entity e, double x, double y, double z, float yaw, float pt, boolean debug, CallbackInfo ci) {
        if (!RenderProfiler.on) { zoomies$depth = 0; return; }
        if (--zoomies$depth == 0 && e != null) RenderProfiler.add("entity", e.getClass(), System.nanoTime() - zoomies$t);
        if (zoomies$depth < 0) zoomies$depth = 0;
    }
}
