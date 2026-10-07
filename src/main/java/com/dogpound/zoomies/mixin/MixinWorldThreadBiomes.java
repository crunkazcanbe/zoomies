package com.dogpound.zoomies.mixin;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A DH worker making an off-thread LOD sees its own biome provider (com.dogpound.zoomies.DhOffThread); everyone else the world's. */
@Mixin(World.class)
public abstract class MixinWorldThreadBiomes {
    @Inject(method = "func_72959_q", at = @At("HEAD"), cancellable = true, remap = false) // getBiomeProvider
    private void zoomies$threadBiomes(CallbackInfoReturnable<BiomeProvider> cir) {
        if (com.dogpound.zoomies.DhOffThread.ACTIVE.get() == 0) return;
        BiomeProvider p = com.dogpound.zoomies.DhOffThread.provider();
        if (p != null) cir.setReturnValue(p);
    }

    // World.getBiome -> getBiomeForCoordsBody -> WorldProvider.getBiomeForCoords reads the world's provider FIELD,
    // skipping getBiomeProvider above: a worker (seen: Caves' CaveGeneratorManager) then wrote into the server's
    // BiomeCache from several threads, corrupting its map -> server tick AIOOBE crash (10-07, NightTest at x 6000).
    @Inject(method = "getBiomeForCoordsBody", at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$threadBiomeAt(BlockPos pos, CallbackInfoReturnable<Biome> cir) {
        if (com.dogpound.zoomies.DhOffThread.ACTIVE.get() == 0) return;
        BiomeProvider p = com.dogpound.zoomies.DhOffThread.provider();
        if (p != null) cir.setReturnValue(p.getBiome(pos, Biomes.PLAINS));
    }
}
