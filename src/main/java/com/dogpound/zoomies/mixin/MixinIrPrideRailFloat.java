package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pride monorail / maglev track never counts as "floating", so IR doesn't break it between pillars. */
@Pseudo
@Mixin(targets = "cam72cam.immersiverailroading.tile.TileRail", remap = false)
public abstract class MixinIrPrideRailFloat {
    @Inject(method = "percentFloating", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$prideNeverFloats(CallbackInfoReturnable<Double> cir) {
        if (com.dogpound.zoomies.IrPrideTrack.prideTile(this)) cir.setReturnValue(0.0);
    }
}
