package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pride monorail / maglev track may be placed over air (com.dogpound.zoomies.IrPrideTrack). */
@Pseudo
@Mixin(targets = "cam72cam.immersiverailroading.track.TrackBase", remap = false)
public abstract class MixinIrPrideTrack {
    @Inject(method = "isDownSolid", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$prideSpans(boolean countFill, CallbackInfoReturnable<Boolean> cir) {
        if (com.dogpound.zoomies.IrPrideTrack.prideTrackBase(this)) cir.setReturnValue(true);
    }
}
