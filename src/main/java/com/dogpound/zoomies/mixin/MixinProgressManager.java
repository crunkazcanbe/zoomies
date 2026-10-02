package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.Eta;
import net.minecraftforge.fml.common.ProgressManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Times every loading stage for the ETA (Forge's push(title, steps[, ...]) — Cleanroom has an extra overload). */
@Mixin(value = ProgressManager.class, remap = false)
public abstract class MixinProgressManager {
    @Inject(method = "push(Ljava/lang/String;IZ)Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;", at = @At("HEAD"), remap = false, require = 0)
    private static void zoomies$stage3(String title, int steps, boolean timeEachStep, CallbackInfoReturnable<ProgressManager.ProgressBar> cir) {
        Eta.stageHook(title);
    }

    @Inject(method = "push(Ljava/lang/String;I)Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;", at = @At("HEAD"), remap = false, require = 0)
    private static void zoomies$stage2(String title, int steps, CallbackInfoReturnable<ProgressManager.ProgressBar> cir) {
        Eta.stageHook(title);
    }

    /** the outer "Loading" bar closing = FML finished; the menu is next */
    @Inject(method = "pop", at = @At("HEAD"), remap = false)
    private static void zoomies$pop(ProgressManager.ProgressBar bar, CallbackInfo ci) {
        if (bar != null && "Loading".equals(bar.getTitle())) Eta.finishHook();
    }
}
