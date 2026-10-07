package com.dogpound.zoomies.mixin;

import java.util.Map;
import java.util.Set;

import com.dogpound.zoomies.WarmCaches;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lootr finds which blocks are loot containers by creating a tile entity for EVERY block in the game on the first
 * loot-chest tick after you join (~11 s frozen server, sampled 2026-10-04). Which blocks qualify only depends on the
 * mods' code, so the names are saved with the mod-list fingerprint (WarmCaches); next time only those blocks are
 * checked (still with Lootr's own test), the rest skipped. Any mod change = the full check runs once again.
 */
@Pseudo
@Mixin(targets = "noobanidus.mods.lootr.config.ConfigManager", remap = false)
public abstract class MixinLootrReplaceCache {
    @Shadow private static Map<Block, Block> replacements;

    @Shadow
    private static void addUnsafeReplacement(ResourceLocation location, Block replacement, WorldServer world) { throw new AssertionError(); }

    @Redirect(method = "replacement", require = 0,
              at = @At(value = "INVOKE", target = "Lnoobanidus/mods/lootr/config/ConfigManager;addUnsafeReplacement(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/block/Block;Lnet/minecraft/world/WorldServer;)V"))
    private static void zoomies$cachedCheck(ResourceLocation location, Block replacement, WorldServer world) {
        Set<String> known = WarmCaches.lootrKnown();
        if (known != null) {
            if (location != null && known.contains(location.toString())) addUnsafeReplacement(location, replacement, world);
            return;
        }
        addUnsafeReplacement(location, replacement, world);
        Block block = location == null ? null : ForgeRegistries.BLOCKS.getValue(location);
        WarmCaches.lootrRecord(block != null && replacements != null && replacements.containsKey(block) ? location.toString() : null);
    }

    @Inject(method = "replacement", at = @At("RETURN"), require = 0)
    private static void zoomies$saveAfterFullCheck(IBlockState original, CallbackInfoReturnable<IBlockState> cir) {
        if (WarmCaches.lootrPending()) WarmCaches.lootrSave();
    }
}
