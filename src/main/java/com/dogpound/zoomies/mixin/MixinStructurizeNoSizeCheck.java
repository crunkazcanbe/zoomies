package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structurize (MineColonies) re-reads and gzips each of its ~2,900 blueprints a second time at every server start just
 * to check that StructureUtils.compress didn't return null - and it never can (it always returns the byte array).
 * Each read searches the whole 768-jar classpath. Same answer (true), without the second read (~1.3 s of each join).
 */
@Pseudo
@Mixin(targets = "com.ldtteam.structurize.management.Structures", remap = false)
public abstract class MixinStructurizeNoSizeCheck {
    @Inject(method = "isSchematicSizeValid", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zoomies$alwaysValid(String structureName, CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(true); }
}
