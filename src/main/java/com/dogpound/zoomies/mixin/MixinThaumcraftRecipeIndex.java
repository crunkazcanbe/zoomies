package com.dogpound.zoomies.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 2026-10-01 JFR of the 585-mod pack: Thaumcraft = 11% of the whole main-thread load. For EVERY item it gives aspects
 * to, generateTagsFromCraftingRecipes walks all crafting-recipe keys (tens of thousands) to find the few whose
 * output is that item. Hand it only the keys of recipes whose output Item matches — an index built once (rebuilt if
 * the recipe count changes). Thaumcraft's own damage/size checks still run on what it gets, so results are identical.
 */
@Mixin(targets = "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", remap = false)
public abstract class MixinThaumcraftRecipeIndex {
    private static Map<Item, Set<ResourceLocation>> zoomies$byOutput;
    private static int zoomies$builtFor = -1;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "generateTagsFromCraftingRecipes", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/registry/RegistryNamespaced;func_148742_b()Ljava/util/Set;", remap = false))
    private static Set zoomies$onlyThisItem(RegistryNamespaced registry, ItemStack stack, ArrayList history) {
        Set<ResourceLocation> keys = registry.getKeys();
        if (stack == null || stack.isEmpty()) return keys;
        Map<Item, Set<ResourceLocation>> idx = zoomies$index(registry, keys);
        Set<ResourceLocation> mine = idx.get(stack.getItem());
        return mine == null ? Collections.emptySet() : mine;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static synchronized Map<Item, Set<ResourceLocation>> zoomies$index(RegistryNamespaced registry, Set<ResourceLocation> keys) {
        if (zoomies$byOutput != null && zoomies$builtFor == keys.size()) return zoomies$byOutput;
        long t = System.nanoTime();
        Map<Item, Set<ResourceLocation>> m = new HashMap<>();
        for (ResourceLocation k : keys) {
            Object o = registry.getObject(k);
            if (!(o instanceof IRecipe)) continue;
            ItemStack out;
            try { out = ((IRecipe) o).getRecipeOutput(); } catch (Throwable e) { continue; }
            if (out == null || out.getItem() == null) continue;
            m.computeIfAbsent(out.getItem(), x -> new LinkedHashSet<>()).add(k);
        }
        zoomies$byOutput = m;
        zoomies$builtFor = keys.size();
        System.out.println("[Zoomies] Thaumcraft recipe index: " + keys.size() + " recipes, " + m.size() + " output items, "
                + (System.nanoTime() - t) / 1_000_000 + " ms");
        return m;
    }
}
