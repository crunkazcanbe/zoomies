package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.Discovery;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Discovery Mode: each time a vertex buffer is too small and gets re-allocated bigger (and copied). */
@Mixin(BufferBuilder.class)
public abstract class MixinDiscoveryBuffer {
    @Inject(method = "func_181670_b(I)V", remap = false,
            at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/client/renderer/GLAllocation;func_74524_c(I)Ljava/nio/ByteBuffer;"))
    private void zoomies$grow(int more, CallbackInfo ci) { if (Discovery.on) Discovery.bufferGrows++; }
}
