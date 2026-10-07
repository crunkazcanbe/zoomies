package com.dogpound.zoomies.mixin;

import crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Joining a world remaps item IDs, and Ender IO rebuilds its whole alloy-smelter lookup right there on the server
 * thread (~9.5 s of every join, sampled 2026-10-04). Build the new lookup in the background instead: Ender IO
 * builds it on the side and swaps it in at the end, so until then machines use the old one, and every hit is still
 * checked against the real recipe (isInputForRecipe / isInput), so at worst a smelter idles a few seconds.
 * A recipe added while a rebuild is running waits for it, so nothing is lost.
 */
@Mixin(value = AlloyRecipeManager.class, remap = false)
public abstract class MixinEnderIOAlloyRebuildLater {
    @Redirect(method = "remap", require = 0,
              at = @At(value = "INVOKE", target = "Lcrazypants/enderio/base/recipe/alloysmelter/AlloyRecipeManager;rebuild()I"))
    private static int zoomies$rebuildLater(AlloyRecipeManager mgr) {
        com.dogpound.zoomies.BackgroundJobs.serial("Ender IO alloy lookup rebuild", () -> mgr.rebuild());
        return -1; // only printed in Ender IO's debug log
    }

    @Inject(method = "addRecipe(Lcrazypants/enderio/base/recipe/IManyToOneRecipe;)V", at = @At("HEAD"), require = 0)
    private void zoomies$waitForRebuild(CallbackInfo ci) {
        com.dogpound.zoomies.BackgroundJobs.awaitSerial();
    }
}
