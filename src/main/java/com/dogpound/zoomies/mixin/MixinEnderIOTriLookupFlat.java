package com.dogpound.zoomies.mixin;

import com.enderio.core.common.util.NNList;
import crazypants.enderio.base.recipe.lookup.TriItemLookup;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ender IO's Alloy Smelter lookup (the only TriItemLookup user) files every recipe under every ordered pair and triple
 * of its ingredient items. Its synthetic 2x/3x recipes put the same ore-dict ingredient (treeSapling, blockQuartz) in
 * all three slots, so with D items in that entry the tree holds ~D^3 leaf lists — with ~800 mods that was gigabytes
 * of NNList/Object[] and was rebuilt (old + new alive) on every world load (10-07, heap full -> 1-2 TPS).
 * Flat instead: a recipe is filed once under each of its items (one level), and a lookup answers with the shortest of
 * those lists. Every caller re-checks candidates with isInputForRecipe / isValidRecipeComponents, so the matches are
 * the same — just found from a slightly longer list. Memory goes from cubic to linear in the item count.
 */
@Mixin(value = TriItemLookup.class, remap = false)
public abstract class MixinEnderIOTriLookupFlat {

    @Inject(method = "addRecipe(Ljava/lang/Object;Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$add3(Object recipe, Item k1, Item k2, Item k3, CallbackInfo ci) {
        zoomies$file(recipe, k1, k2, k3);
        ci.cancel();
    }

    @Inject(method = "addRecipe(Ljava/lang/Object;Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$add2(Object recipe, Item k1, Item k2, CallbackInfo ci) {
        zoomies$file(recipe, k1, k2, Items.AIR);
        ci.cancel();
    }

    @Inject(method = "getRecipes(Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;)Lcom/enderio/core/common/util/NNList;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$get3(Item k1, Item k2, Item k3, CallbackInfoReturnable<NNList<Object>> cir) {
        cir.setReturnValue(zoomies$shortest(k1, k2, k3));
    }

    @Inject(method = "getRecipes(Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;)Lcom/enderio/core/common/util/NNList;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$get2(Item k1, Item k2, CallbackInfoReturnable<NNList<Object>> cir) {
        cir.setReturnValue(zoomies$shortest(k1, k2, Items.AIR));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void zoomies$file(Object recipe, Item k1, Item k2, Item k3) {
        TriItemLookup<Object> self = (TriItemLookup<Object>) (Object) this;
        java.util.Set<Item> keys = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Item k : new Item[] { k1, k2, k3 }) if (k != Items.AIR) keys.add(k);
        for (Item k : keys) self.addRecipe(recipe, k);                  // one level: root -> item -> recipe list
        boolean any = !keys.isEmpty();
        if (!any) throw new RuntimeException("no items to store recipe for");   // what Ender IO does too
    }

    @Unique
    @SuppressWarnings("unchecked")
    private NNList<Object> zoomies$shortest(Item k1, Item k2, Item k3) {
        TriItemLookup<Object> self = (TriItemLookup<Object>) (Object) this;
        NNList<Object> best = null;
        for (Item k : new Item[] { k1, k2, k3 }) {
            if (k == Items.AIR) continue;
            NNList<Object> l = self.getRecipes(k);
            if (l.isEmpty()) return NNList.emptyList();                // an item no recipe uses: nothing can match
            if (best == null || l.size() < best.size()) best = l;
        }
        return best == null ? NNList.emptyList() : best;
    }
}
