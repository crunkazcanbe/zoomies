package com.dogpound.zoomies.mixin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.enderio.core.common.util.NNList;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ender IO splits each alloy recipe into every combination of its ingredient alternatives and skips
 * combinations it has already made. It asked "made this one already?" by walking a plain list of every
 * combination so far (seen.contains) — with a big pack's metals that is tens of millions of comparisons
 * (measured: 151s of the 18-minute load).
 *
 * Same answers, found fast: combinations are also filed in buckets keyed by WHICH items they hold (order
 * ignored). Any two combinations Ender IO calls equal hold the same items, so they always share a bucket;
 * Ender IO's own Tuple.equals then decides inside that one small bucket.
 */
@Mixin(value = crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager.class, remap = false)
public abstract class MixinEnderIOAlloyDedupe {
    @Unique private static Map<Integer, List<Object>> zoomies$buckets = new HashMap<>();
    @Unique private static Field[] zoomies$stacks;

    @Inject(method = "addDedupedRecipe", at = @At("HEAD"))
    private void zoomies$newRecipe(CallbackInfo ci) {
        zoomies$buckets = new HashMap<>(); // `seen` is a fresh list per recipe; so is its index
    }

    @Redirect(method = "addDedupedRecipe",
              at = @At(value = "INVOKE", target = "Lcom/enderio/core/common/util/NNList;contains(Ljava/lang/Object;)Z"))
    private boolean zoomies$seenContains(NNList<?> seen, Object tuple) {
        boolean fast = false;
        List<Object> bucket = zoomies$buckets.get(zoomies$key(tuple));
        if (bucket != null) for (Object other : bucket) if (tuple.equals(other)) { fast = true; break; } // same call as NNList.contains
        if (VERIFY && ++zoomies$calls % EVERY == 0 && seen.contains(tuple) != fast && zoomies$wrong++ < 50)
            System.out.println("[Zoomies] MISMATCH EnderIO alloy dedupe " + tuple + " fast=" + fast);
        return fast;
    }

    @Unique private static final boolean VERIFY = com.dogpound.zoomies.ZoomiesConfig.verify();
    @Unique private static final int EVERY = Math.max(1, com.dogpound.zoomies.ZoomiesConfig.num("safety.verifyEvery", 64));
    @Unique private static int zoomies$calls, zoomies$wrong;

    @Redirect(method = "addDedupedRecipe",
              at = @At(value = "INVOKE", target = "Lcom/enderio/core/common/util/NNList;add(Ljava/lang/Object;)Z"))
    @SuppressWarnings("unchecked")
    private boolean zoomies$seenAdd(NNList<?> seen, Object tuple) {
        zoomies$buckets.computeIfAbsent(zoomies$key(tuple), k -> new ArrayList<>()).add(tuple);
        return ((NNList<Object>) seen).add(tuple); // the real list still gets it: Ender IO reads its size afterwards
    }

    /** Order-free fingerprint of the 3 stacks, matching Tuple's eq (ItemStack.areItemsEqual: same item + damage). */
    @Unique
    private static int zoomies$key(Object tuple) {
        try {
            if (zoomies$stacks == null) {
                Field[] f = new Field[3];
                for (int i = 0; i < 3; i++) { f[i] = tuple.getClass().getDeclaredField("stack" + i); f[i].setAccessible(true); }
                zoomies$stacks = f;
            }
            int sum = 0;
            for (Field f : zoomies$stacks) {
                ItemStack s = (ItemStack) f.get(tuple);
                if (!s.isEmpty()) sum += System.identityHashCode(s.getItem()) * 31 + s.getItemDamage(); // empties only equal themselves
            }
            return sum;
        } catch (ReflectiveOperationException e) {
            return 0; // one big bucket = Ender IO's original behaviour, just not faster
        }
    }
}
