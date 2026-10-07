package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** ProjectE maps EMC (39 s) inside serverStarting, holding the world join; do it in the background after it starts. */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "moze_intel.projecte.PECore", remap = false)
public abstract class MixinProjectEMapLater {
    @Redirect(method = "serverStarting", require = 0,
              at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/emc/EMCMapper;map()V"))
    private void zoomies$later() {
        if (com.dogpound.zoomies.ZoomiesConfig.on("speed.projecteBackground")) com.dogpound.zoomies.BackgroundJobs.deferProjectE();
        else {
            try { Class.forName("moze_intel.projecte.emc.EMCMapper").getMethod("map").invoke(null); } catch (Throwable e) { throw new RuntimeException(e); }
        }
    }
}
