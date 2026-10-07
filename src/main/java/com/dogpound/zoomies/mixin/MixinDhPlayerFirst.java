package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Why: DH's far-LOD chunks are generated on the server thread and fought the player's own chunks - flying around, the
 * land near her stayed invisible (2026-10-04). DH's chunk requests wait while a player's chunks are still loading or
 * she's flying fast (com.dogpound.zoomies.DhPlayerFirst); DH's cleanup work (unloading its chunks) still runs.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.util.threading.ServerThreadTaskHandler", remap = false)
public abstract class MixinDhPlayerFirst {
    @Inject(method = "deferrableTasksCanRun", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$playerFirst(CallbackInfoReturnable<Boolean> cir) {
        if (com.dogpound.zoomies.DhPlayerFirst.brake()) cir.setReturnValue(false);
    }
}
