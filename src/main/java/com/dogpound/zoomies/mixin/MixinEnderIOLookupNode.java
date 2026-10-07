package com.dogpound.zoomies.mixin;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.enderio.core.common.util.NNList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ender IO's recipe lookup tree (ItemRecipeNode / IntRecipeNode.makeNext) files every recipe under every order of its
 * ingredients and asks "already in this list?" with list.contains — a full walk each time, so building the tree slows
 * down with the square of the recipe count (measured ~44 s of a big pack's load after the alloy fix).
 * Same answer, found fast: each list gets an identity set beside it. Used only for recipe classes that keep Java's
 * default equals (identity) — anything that defines its own equality gets Ender IO's original walk. If a list was
 * changed some other way (sizes disagree), the set is rebuilt from the list before answering.
 */
@Mixin(targets = {"crazypants.enderio.base.recipe.lookup.ItemRecipeNode", "crazypants.enderio.base.recipe.lookup.IntRecipeNode"}, remap = false)
public abstract class MixinEnderIOLookupNode {
    // weak + identity keys: a list Ender IO drops frees its set too (10-07: holding them all = several GB with ~800 mods)
    @Unique private static final Map<NNList<?>, Set<Object>> zoomies$sets = new com.google.common.collect.MapMaker().weakKeys().concurrencyLevel(4).makeMap();
    /** short lists are walked as fast as a set lookup; only long ones get a set (most of Ender IO's lists are tiny) */
    @Unique private static final int ZOOMIES$MIN = 64;
    @Unique private static final Map<Class<?>, Boolean> zoomies$identity = new java.util.concurrent.ConcurrentHashMap<>();

    @Unique
    private static boolean zoomies$usesIdentity(Object o) {
        return zoomies$identity.computeIfAbsent(o.getClass(), c -> {
            try { return c.getMethod("equals", Object.class).getDeclaringClass() == Object.class; } catch (Throwable t) { return false; }
        });
    }

    @Unique
    private static Set<Object> zoomies$set(NNList<?> list) {
        Set<Object> set = zoomies$sets.get(list);
        if (set == null || set.size() != list.size()) {          // new list, or changed behind our back: rebuild from the list
            set = Collections.newSetFromMap(new IdentityHashMap<>());
            set.addAll(list);
            zoomies$sets.put(list, set);
        }
        return set;
    }

    @Redirect(method = "makeNext", at = @At(value = "INVOKE", target = "Lcom/enderio/core/common/util/NNList;contains(Ljava/lang/Object;)Z"), remap = false)
    private boolean zoomies$contains(NNList<?> list, Object recipe) {
        if (list.size() < ZOOMIES$MIN || !zoomies$usesIdentity(recipe)) return list.contains(recipe); // short list or own equals: the original walk
        return zoomies$set(list).contains(recipe);
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "makeNext", at = @At(value = "INVOKE", target = "Lcom/enderio/core/common/util/NNList;add(Ljava/lang/Object;)Z"), remap = false)
    private boolean zoomies$add(NNList<?> list, Object recipe) {
        boolean r = ((NNList<Object>) list).add(recipe);
        Set<Object> set = list.size() > ZOOMIES$MIN ? zoomies$sets.get(list) : null;
        if (set != null && set.size() + 1 == list.size()) set.add(recipe); // keep in step; otherwise rebuilt on next look
        return r;
    }
}
