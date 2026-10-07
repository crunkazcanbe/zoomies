package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On a DH worker building an OTG LOD: no default structures (they write the real world's structure data) and OTG's own caves instead of a shared modded cave generator. */
@Pseudo
@Mixin(targets = "com.pg85.otg.forge.world.ForgeWorld", remap = false)
public abstract class MixinOtgWorldOffThread {
    @Inject(method = "prepareDefaultStructures", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$noStructures(CallbackInfo ci) { if (com.dogpound.zoomies.DhOffThread.otgWorker()) ci.cancel(); }

    @Inject(method = "generateModdedCaveGen", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$ownCaves(CallbackInfoReturnable<Boolean> cir) { if (com.dogpound.zoomies.DhOffThread.otgWorker()) cir.setReturnValue(false); }
}
