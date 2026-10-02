package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.CustomSky;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** OptiFine custom sky layers go between vanilla's sky dome and its sun/moon (the 2nd getCelestialAngle in renderSky) */
@Mixin(RenderGlobal.class)
public abstract class MixinCustomSky {
    @Shadow(remap = false, aliases = "world") private WorldClient field_72769_h;

    @Inject(method = "func_174976_a(FI)V", remap = false,
            at = @At(value = "INVOKE", ordinal = 1, remap = false,
                     target = "Lnet/minecraft/client/multiplayer/WorldClient;func_72826_c(F)F"))
    private void zoomies$customSky(float partialTicks, int pass, CallbackInfo ci) {
        CustomSky.render(field_72769_h, partialTicks);
    }
}
