package com.dogpound.zoomies.mixin;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better With Mods' BWOreDictionary.listContains(stack, oreList) walks the ore list to the FIRST entry that is the same
 * item+damage, or the same item with "any damage", then answers from that entry's NBT. Called for masses of items while
 * loading (measured ~17 s). Zoomies keeps, per list, each item's first position (per damage, and for "any damage") and
 * takes the earliest — the very same entry the walk would stop at — then applies the same NBT rule.
 * Rebuilt whenever the list's size changes; empty stacks and anything odd go to the original walk.
 */
@Mixin(targets = "betterwithmods.common.BWOreDictionary", remap = false)
public abstract class MixinBwmListContains {
    @Unique private static final Map<List<?>, Object[]> zoomies$idx = Collections.synchronizedMap(new IdentityHashMap<>()); // {size, byMeta, wild}
    @Unique private static final boolean VERIFY = com.dogpound.zoomies.ZoomiesConfig.verify();
    @Unique private static final int EVERY = Math.max(1, com.dogpound.zoomies.ZoomiesConfig.num("safety.verifyEvery", 64));
    @Unique private static int zoomies$calls, zoomies$wrong;

    @SuppressWarnings("unchecked")
    @Inject(method = "listContains(Lnet/minecraft/item/ItemStack;Ljava/util/List;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zoomies$fast(ItemStack check, List<ItemStack> list, CallbackInfoReturnable<Boolean> cir) {
        if (list == null || list.isEmpty() || check == null || check.isEmpty()) return; // original walk
        Object[] ix = zoomies$idx.get(list);
        if (ix == null || (Integer) ix[0] != list.size()) ix = zoomies$build(list);
        Map<Long, Integer> byMeta = (Map<Long, Integer>) ix[1];
        Map<Item, Integer> wild = (Map<Item, Integer>) ix[2];
        Integer a = byMeta.get(zoomies$key(check.getItem(), check.getItemDamage())), b = wild.get(check.getItem());
        boolean fast;
        if (a == null && b == null) fast = false;
        else {
            ItemStack item = list.get(a == null ? b : b == null ? a : Math.min(a, b));
            fast = !item.hasTagCompound() || ItemStack.areItemStackTagsEqual(check, item);
        }
        if (VERIFY && ++zoomies$calls % EVERY == 0) {
            boolean slow = false;
            for (ItemStack item : list)
                if (ItemStack.areItemsEqual(check, item) || (check.getItem() == item.getItem() && item.getItemDamage() == OreDictionary.WILDCARD_VALUE)) {
                    slow = !item.hasTagCompound() || ItemStack.areItemStackTagsEqual(check, item);
                    break;
                }
            if (slow != fast && zoomies$wrong++ < 50) System.out.println("[Zoomies] MISMATCH BWM listContains " + check + " fast=" + fast);
        }
        cir.setReturnValue(fast);
    }

    @Unique
    private static long zoomies$key(Item item, int damage) {
        return ((long) System.identityHashCode(item) << 32) ^ (damage & 0xFFFFFFFFL);
    }

    @Unique
    private static Object[] zoomies$build(List<ItemStack> list) {
        Map<Long, Integer> byMeta = new HashMap<>();
        Map<Item, Integer> wild = new IdentityHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s == null || s.isEmpty()) continue;                            // an empty entry never matches a real stack
            if (s.getItemDamage() == OreDictionary.WILDCARD_VALUE) wild.putIfAbsent(s.getItem(), i);
            byMeta.putIfAbsent(zoomies$key(s.getItem(), s.getItemDamage()), i); // same item+damage (areItemsEqual)
        }
        Object[] ix = {list.size(), byMeta, wild};
        zoomies$idx.put(list, ix);
        return ix;
    }
}
