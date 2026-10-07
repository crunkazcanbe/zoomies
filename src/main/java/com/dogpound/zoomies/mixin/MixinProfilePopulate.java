package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dogpound.zoomies.Profiler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

/** Pride Profiler: decoration (trees, lakes, structures placed by the chunk generator) per generator. */
@Mixin(value = Chunk.class, remap = false)
public abstract class MixinProfilePopulate {
    @Redirect(method = "func_186034_a", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/gen/IChunkGenerator;func_185931_b(II)V"))
    private void zoomies$timePopulate(IChunkGenerator g, int x, int z) {
        long t = System.nanoTime();
        g.populate(x, z);
        Profiler.worldGen(g.getClass(), "Decoration", System.nanoTime() - t);
    }
}
