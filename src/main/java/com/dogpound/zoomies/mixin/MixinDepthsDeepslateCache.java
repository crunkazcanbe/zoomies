package com.dogpound.zoomies.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Depths Update asks "is this deepslate?" on every block a chunk sets, and answers with a registry-name string, a new
 * ItemStack and an ore-dictionary array each time — it showed up in every worldgen profile of her new worlds
 * (2026-10-04). The answer only depends on the block, so remember it per block.
 */
@Pseudo
@Mixin(targets = "sayys.depthsupdate.util.BlockUtils", remap = false)
public abstract class MixinDepthsDeepslateCache {
    private static final java.util.Map<Block, Boolean> ZOOMIES_DEEPSLATE = new java.util.concurrent.ConcurrentHashMap<Block, Boolean>();

    @Inject(method = "isDeepslate", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zoomies$cached(IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state == null) return;
        Boolean b = ZOOMIES_DEEPSLATE.get(state.getBlock());
        if (b != null) cir.setReturnValue(b);
    }

    @Inject(method = "isDeepslate", at = @At("RETURN"), require = 0)
    private static void zoomies$remember(IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        // ore dictionary is complete once a world exists; earlier answers aren't kept
        if (state != null && net.minecraftforge.fml.common.Loader.instance().hasReachedState(net.minecraftforge.fml.common.LoaderState.AVAILABLE))
            ZOOMIES_DEEPSLATE.put(state.getBlock(), cir.getReturnValueZ());
    }
}
