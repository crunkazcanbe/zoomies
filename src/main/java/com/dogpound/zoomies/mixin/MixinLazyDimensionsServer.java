package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.LazyDims;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** world start only loads the dimensions in worldgen.startDimensions; the rest load when first needed (see LazyDims) */
@Mixin(net.minecraft.server.MinecraftServer.class)
public abstract class MixinLazyDimensionsServer {
    @Redirect(method = "func_71247_a", remap = false,
              at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraftforge/common/DimensionManager;getStaticDimensionIDs()[Ljava/lang/Integer;"))
    private Integer[] zoomies$startDims() {
        return LazyDims.filter(net.minecraftforge.common.DimensionManager.getStaticDimensionIDs());
    }
}
