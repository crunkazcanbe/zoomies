package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Advent of Ascension adds two game rules at server start by calling getWorld() on every registered dimension, which
 * LOADS all of them (~65 dimensions, ~30 s of every new world after LazyDims, 2026-10-04). Other dimensions read the
 * overworld's game rules anyway (DerivedWorldInfo), so only already-loaded worlds need them.
 */
@Pseudo
@Mixin(targets = "net.tslat.aoa3.advent.AdventOfAscension", remap = false)
public abstract class MixinAoaGameRulesNoLoad {
    @Redirect(method = "registerGameRules", require = 0,
              at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;func_71218_a(I)Lnet/minecraft/world/WorldServer;"))
    private static net.minecraft.world.WorldServer zoomies$onlyLoaded(net.minecraft.server.MinecraftServer server, int dim) {
        if (!com.dogpound.zoomies.ZoomiesConfig.on("speed.aoaLazyGameRules")) return server.getWorld(dim);
        return net.minecraftforge.common.DimensionManager.getWorld(dim);
    }
}
