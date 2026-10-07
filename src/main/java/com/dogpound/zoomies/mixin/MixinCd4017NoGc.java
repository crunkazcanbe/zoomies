package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** CD4017BE Lib forces a full garbage collection on every server start (~1.7 s of each join); the JVM collects on its own. */
@Pseudo
@Mixin(targets = "cd4017be.lib.Lib", remap = false)
public abstract class MixinCd4017NoGc {
    @Redirect(method = "afterStart", require = 0, at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"))
    private void zoomies$noGc() { }
}
