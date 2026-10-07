package com.dogpound.zoomies.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villager Backport decorates every new chunk and first asks "nearest village?" (getNearestStructurePos). In OTG worlds
 * that search computes biomes ring by ring around the chunk - measured 2026-10-05: the server thread spent ~100% of its
 * time there, TPS 0.9 in a Dregora test world. Its question is really "is this chunk part of a village", which the
 * generator answers cheaply from villages already planned (isInsideStructure): check the chunk centre and corners.
 */
@Pseudo
@Mixin(targets = "com.exiledradio.villagerbackport.village.StructureWorkstations", remap = false)
public abstract class MixinVillagerBackportNearVillage {
    @Inject(method = "nearVillage", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$cheapVillageCheck(World world, IChunkGenerator generator, int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (!com.dogpound.zoomies.ZoomiesConfig.on("fix.villagerBackportFastVillageCheck") || !com.dogpound.zoomies.VillageCheck.villagesOnly()) return;
        try {
            int x = chunkX << 4, z = chunkZ << 4;
            int[][] pts = {{8, 8}, {0, 0}, {15, 0}, {0, 15}, {15, 15}};
            for (int[] p : pts)
                if (generator.isInsideStructure(world, "Village", new BlockPos(x + p[0], 64, z + p[1]))) { cir.setReturnValue(true); return; }
            cir.setReturnValue(false);
        } catch (RuntimeException e) {
            cir.setReturnValue(false);
        }
    }
}
