package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.Discovery;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Discovery Mode counters on the matrix stack and texture binds. One boolean check when discovery is off. */
@Mixin(GlStateManager.class)
public abstract class MixinDiscoveryGl {
    @Shadow(remap = false) private static int field_179162_o; // activeTextureUnit

    @Inject(method = "func_179094_E()V", at = @At("HEAD"), remap = false) // pushMatrix
    private static void zoomies$push(CallbackInfo ci) { if (Discovery.on) { Discovery.pushPop++; Discovery.matrixOps++; } }

    @Inject(method = "func_179121_F()V", at = @At("HEAD"), remap = false) // popMatrix
    private static void zoomies$pop(CallbackInfo ci) { if (Discovery.on) { Discovery.pushPop++; Discovery.matrixOps++; } }

    @Inject(method = "func_179144_i(I)V", at = @At("HEAD"), remap = false) // bindTexture
    private static void zoomies$bind(int tex, CallbackInfo ci) {
        if (!Discovery.on) return;
        Discovery.texBinds++;
        int unit = Math.max(0, Math.min(31, field_179162_o - 33984));   // GL_TEXTURE0 = 33984
        if (Discovery.lastTex[unit] == tex) Discovery.texRedundant++;
        Discovery.lastTex[unit] = tex;
    }
}
