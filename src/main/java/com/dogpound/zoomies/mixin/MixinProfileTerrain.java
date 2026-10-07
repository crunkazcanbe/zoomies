package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dogpound.zoomies.Profiler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

/** Pride Profiler: terrain shaping + decoration per chunk generator (OTG, Lost Cities, vanilla…), and the
 *  "Preparing spawn area" window that bounds the world-creation report. */
@Mixin(value = {ChunkProviderServer.class}, remap = false)
public abstract class MixinProfileTerrain {
    @Redirect(method = "func_186025_d", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/gen/IChunkGenerator;func_185932_a(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk zoomies$timeTerrain(IChunkGenerator g, int x, int z) {
        long t = System.nanoTime();
        Chunk c = g.generateChunk(x, z);
        Profiler.worldGen(g.getClass(), "Terrain", System.nanoTime() - t);
        return c;
    }
}
