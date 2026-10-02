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
    @Unique private static final Map<NNList<?>, Set<Object>> zoomies$sets = Collections.synchronizedMap(new IdentityHashMap<>());
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
        if (!zoomies$usesIdentity(recipe)) return list.contains(recipe); // its own equals: the original walk
        return zoomies$set(list).contains(recipe);
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = "makeNext", at = @At(value = "INVOKE", target = "Lcom/enderio/core/common/util/NNList;add(Ljava/lang/Object;)Z"), remap = false)
    private boolean zoomies$add(NNList<?> list, Object recipe) {
        boolean r = ((NNList<Object>) list).add(recipe);
        Set<Object> set = zoomies$sets.get(list);
        if (set != null && set.size() + 1 == list.size()) set.add(recipe); // keep in step; otherwise rebuilt on next look
        return r;
    }
}
