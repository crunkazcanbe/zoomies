package com.dogpound.zoomies.mixin;

import java.util.IdentityHashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge checks "does this item fit this ore slot" by walking EVERY item registered under the ore name
 * (OreDictionary.itemMatches in a loop). Tinkers/Ender IO ask that millions of times while loading.
 * This answers from an index instead: item -> metas (or "any meta"). Same result as the loop
 * (itemMatches with strict=false); rebuilt whenever the ore list changes size.
 */
@Mixin(value = OreIngredient.class, remap = false)
public abstract class MixinOreIngredient {
    @Shadow private NonNullList<ItemStack> ores;

    @Unique private Map<Item, IntOpenHashSet> zoomies$index; // null set = any meta (wildcard)
    @Unique private int zoomies$size = -1;

    @Inject(method = "apply(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void zoomies$fastApply(ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        if (input == null || input.isEmpty()) return; // rare edge cases: let Forge's loop answer
        Map<Item, IntOpenHashSet> idx = zoomies$index;
        if (idx == null || zoomies$size != ores.size()) idx = zoomies$rebuild();
        if (!idx.containsKey(input.getItem())) { cir.setReturnValue(false); return; }
        IntOpenHashSet metas = idx.get(input.getItem());
        boolean fast = metas == null || metas.contains(input.getMetadata());
        if (VERIFY && ++zoomies$calls % EVERY == 0) zoomies$check(input, fast); // spot-check 1 in 64
        cir.setReturnValue(fast);
    }

    /** -Dzoomies.verify=true or a "zoomies-verify" file in the game folder: spot-check against Forge's original loop. */
    @Unique private static final boolean VERIFY = com.dogpound.zoomies.ZoomiesConfig.verify();
    @Unique private static final int EVERY = Math.max(1, com.dogpound.zoomies.ZoomiesConfig.num("safety.verifyEvery", 64));
    @Unique private static int zoomies$calls, zoomies$checked, zoomies$wrong;

    @Unique
    private void zoomies$check(ItemStack input, boolean fast) {
        boolean slow = false;
        for (ItemStack target : ores) if (OreDictionary.itemMatches(target, input, false)) { slow = true; break; }
        zoomies$checked++;
        if (slow != fast && zoomies$wrong++ < 50)
            System.out.println("[Zoomies] MISMATCH " + input + " fast=" + fast + " forge=" + slow);
        if ((zoomies$checked & 0xFFFF) == 0)
            System.out.println("[Zoomies] verify: " + zoomies$checked + " checks, " + zoomies$wrong + " mismatches");
    }

    @Unique
    private Map<Item, IntOpenHashSet> zoomies$rebuild() {
        Map<Item, IntOpenHashSet> idx = new IdentityHashMap<>();
        int size = ores.size();
        for (ItemStack ore : ores) {
            if (ore.isEmpty()) continue; // an empty ore entry never matches a real item
            Item item = ore.getItem();
            int meta = ore.getMetadata();
            if (meta == OreDictionary.WILDCARD_VALUE) { idx.put(item, null); continue; }
            if (idx.containsKey(item) && idx.get(item) == null) continue; // already "any meta"
            idx.computeIfAbsent(item, k -> new IntOpenHashSet()).add(meta);
        }
        zoomies$size = size;
        zoomies$index = idx;
        return idx;
    }
}
