package com.dogpound.zoomies.mixin;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dogpound.zoomies.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Pride Profiler: time every mod world generator (ores, structures, trees…) while a world is created. */
@Mixin(value = GameRegistry.class, remap = false)
public abstract class MixinProfileWorldGen {
    @Redirect(method = "generateWorld", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/common/IWorldGenerator;generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/gen/IChunkGenerator;Lnet/minecraft/world/chunk/IChunkProvider;)V"))
    private static void zoomies$timeGen(IWorldGenerator g, Random r, int x, int z, World w, IChunkGenerator cg, IChunkProvider cp) {
        long t = System.nanoTime();
        g.generate(r, x, z, w, cg, cp);
        Profiler.worldGen(g.getClass(), "World generator", System.nanoTime() - t);
    }
}
