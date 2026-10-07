package com.dogpound.zoomies.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gates of the Apocalypse builds one fortress per world and then moves the WORLD SPAWN onto its roof, so every new
 * world started on top of that laggy obsidian/barbed-wire tower (her 2026-10-04: "I'm always spawning on one of
 * these") and it overrode our start-in-a-village spawn. The fortress still generates; the spawn stays where it was.
 */
@Mixin(targets = "com.apocalypsesurvivor.gatesoftheapocalypse.structure.FortressGenerator", remap = false)
public abstract class MixinGoaKeepSpawn {
    @Redirect(method = "generate", require = 0,
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;func_175652_B(Lnet/minecraft/util/math/BlockPos;)V"))
    private static void zoomies$keepSpawn(WorldServer w, BlockPos fortress) {
        System.out.println("[Zoomies] Gates of the Apocalypse fortress at " + fortress + " - spawn left where it was");
    }
}
