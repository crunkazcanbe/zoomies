package com.dogpound.zoomies.mixin;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Realistic Physics turns each ore-dictionary tag into item names by walking EVERY registered item name for every tag
 * (each time a dimension's physics loads, ~1.3 s per dimension here). Same set straight from the tag's own items:
 * the registry name of each item that is really registered under that name. A missing tag still goes to the original
 * code so it throws its usual error.
 */
@Pseudo
@Mixin(targets = "xbigellx.realisticphysics.internal.util.BlockResolver", remap = false)
public abstract class MixinRealisticPhysicsTags {
    @Inject(method = "resolveLocationsFromTag", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zoomies$fromTag(String tag, CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        if (!OreDictionary.doesOreNameExist(tag)) return;
        Set<ResourceLocation> out = new HashSet<>();
        for (ItemStack stack : OreDictionary.getOres(tag)) {
            Item item = stack.getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null && ForgeRegistries.ITEMS.getValue(key) == item) out.add(key);
        }
        cir.setReturnValue(out);
    }
}
