package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.Discovery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Discovery Mode: every blend/depth/cull/... switch, and whether it was already in that state. */
@Mixin(targets = "net.minecraft.client.renderer.GlStateManager$BooleanState")
public abstract class MixinDiscoveryState {
    @Shadow(remap = false) private boolean field_179201_b; // currentState

    @Inject(method = "func_179199_a(Z)V", at = @At("HEAD"), remap = false) // setState
    private void zoomies$state(boolean to, CallbackInfo ci) {
        if (!Discovery.on) return;
        Discovery.stateCalls++;
        if (to == field_179201_b) Discovery.stateRedundant++;
    }
}
