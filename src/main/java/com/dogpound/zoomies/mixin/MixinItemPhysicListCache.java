package com.dogpound.zoomies.mixin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ItemPhysic asks its burn/swim/fuel lists about EVERY dropped item EVERY tick (client and server), and each ask walks
 * the whole list — the fuel entries call every mod's fuel handler. Measured 2026-10-02: ~30% of the big pack's render
 * thread with a few hundred items on the ground.
 *
 * For a plain stack (no NBT) the answer depends only on item + meta, so remember it per list. The list's size is
 * snapshotted; if entries are added/removed the memory is dropped.
 * ponytail: a same-size in-place edit of a list isn't noticed — ItemPhysic only builds these from config at load.
 */
@Mixin(targets = "com.creativemd.creativecore.common.utils.type.SortingList", remap = false)
public abstract class MixinItemPhysicListCache {
    @Shadow public List<?> entries;

    @Unique private final Map<Item, Map<Integer, Boolean>> zoomies$seen = new ConcurrentHashMap<>();
    @Unique private volatile int zoomies$size = -1;

    @Inject(method = "canBeFoundInList(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$known(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty() || stack.hasTagCompound()) return;
        if (zoomies$size != entries.size()) { zoomies$seen.clear(); zoomies$size = entries.size(); return; }
        Map<Integer, Boolean> byMeta = zoomies$seen.get(stack.getItem());
        Boolean b = byMeta == null ? null : byMeta.get(stack.getMetadata());
        if (b != null) cir.setReturnValue(b);
    }

    @Inject(method = "canBeFoundInList(Lnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"), require = 0)
    private void zoomies$remember(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty() || stack.hasTagCompound() || zoomies$size != entries.size()) return;
        zoomies$seen.computeIfAbsent(stack.getItem(), k -> new ConcurrentHashMap<>()).put(stack.getMetadata(), cir.getReturnValue());
    }
}
