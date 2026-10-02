package com.dogpound.zoomies.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Celeritas Dynamic Lights works out how bright every dropped item is on every client tick (registry-name lookups per
 * item). Measured 2026-10-02: ~15% of the big pack's render thread. A dropped item's glow can only change when it
 * lands in/out of water, so work it out every 4th tick and reuse it in between (0.2 s — not visible).
 * Client thread only, so a plain WeakHashMap (entries go when the item entity does).
 */
@Mixin(targets = "dev.lambdaurora.lambdynlights.api.DynamicLightHandlers", remap = false)
public abstract class MixinDynLightsItemThrottle {
    @Unique private static final Map<Entity, Integer> zoomies$last = new WeakHashMap<>();

    @Inject(method = "getLuminanceFrom(Lnet/minecraft/entity/Entity;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zoomies$reuse(Entity e, CallbackInfoReturnable<Integer> cir) {
        if (!(e instanceof EntityItem) || (e.ticksExisted & 3) == 0) return;
        Integer v = zoomies$last.get(e);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "getLuminanceFrom(Lnet/minecraft/entity/Entity;)I", at = @At("RETURN"), require = 0)
    private static void zoomies$keep(Entity e, CallbackInfoReturnable<Integer> cir) {
        if (e instanceof EntityItem) zoomies$last.put(e, cir.getReturnValue());
    }
}
