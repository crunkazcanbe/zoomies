package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.ZoomiesConfig;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BuildCraft lists a facade for EVERY block in the pack in its creative tab — 82,637 items in the Pride pack
 * (2026-10-02 creative sweep). Opening that tab froze the game, and EMI indexes all of them. The tab now shows only
 * the first N (creative.bcFacadeLimit); every facade is still made in the Assembly Table exactly as before.
 */
@Mixin(targets = "buildcraft.silicon.item.ItemPluggableFacade", remap = false)
public abstract class MixinBcFacadeTabCap {
    @Unique private static final ThreadLocal<Integer> zoomies$start = new ThreadLocal<>();

    @Inject(method = "func_150895_a", at = @At("HEAD"), require = 0)
    private void zoomies$mark(CreativeTabs tab, NonNullList<ItemStack> list, CallbackInfo ci) { zoomies$start.set(list.size()); }

    @Inject(method = "func_150895_a", at = @At("RETURN"), require = 0)
    private void zoomies$cap(CreativeTabs tab, NonNullList<ItemStack> list, CallbackInfo ci) {
        Integer start = zoomies$start.get();
        int keep = ZoomiesConfig.num("creative.bcFacadeLimit", 64);
        if (start == null || keep < 0) return;
        while (list.size() > start + keep) list.remove(list.size() - 1);
    }
}
