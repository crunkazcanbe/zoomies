package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Just Stargate gives every dimension a stargate address at world start by LOADING every dimension: 161 of them in her
 * pack (2026-10-04: ~3 minutes of every new world, terrain generation in 161 worlds). Only dimensions that are already
 * loaded take part now (JSG skips a null world by itself); JsgLazyGates does the others when they first load.
 */
@Pseudo
@Mixin(targets = "tauri.dev.jsg.worldgen.StargateDimensionGenerator", remap = false)
public abstract class MixinJsgLazyDims {
    @Redirect(method = "tryGenerate", require = 0,
              at = @At(value = "INVOKE", target = "Ltauri/dev/jsg/stargate/teleportation/TeleportHelper;getWorld(I)Lnet/minecraft/world/World;"))
    private static net.minecraft.world.World zoomies$onlyLoaded(int dim) {
        if (!com.dogpound.zoomies.ZoomiesConfig.on("speed.jsgLazyDimensions")) {
            try { return (net.minecraft.world.World) Class.forName("tauri.dev.jsg.stargate.teleportation.TeleportHelper").getMethod("getWorld", int.class).invoke(null, dim); }
            catch (Throwable t) { return null; }
        }
        return net.minecraftforge.common.DimensionManager.getWorld(dim);
    }
}
