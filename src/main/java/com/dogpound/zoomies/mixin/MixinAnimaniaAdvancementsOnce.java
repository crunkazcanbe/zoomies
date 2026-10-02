package com.dogpound.zoomies.mixin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.advancements.AdvancementManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World creation, run 10 (2026-09-28): Animania rebuilds ALL ~3,860 advancements every time ANY dimension loads —
 * 126 times for this pack's 122 dimensions, ~113 s of a ~3 min world creation. Every dimension shares the one
 * AdvancementManager and the result is identical each time, so build it once per manager.
 */
@Mixin(targets = "com.animania.common.handler.AddonHandler", remap = false)
public abstract class MixinAnimaniaAdvancementsOnce {
    private static final Map<AdvancementManager, Boolean> zoomies$done = Collections.synchronizedMap(new WeakHashMap<>());

    @Inject(method = "onWorldLoad(Lnet/minecraftforge/event/world/WorldEvent$Load;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zoomies$once(WorldEvent.Load e, CallbackInfo ci) {
        if (e.getWorld().isRemote || !(e.getWorld() instanceof WorldServer)) return;
        AdvancementManager m = ((WorldServer) e.getWorld()).getAdvancementManager();
        if (m != null && zoomies$done.put(m, Boolean.TRUE) != null) ci.cancel();   // already built for this world
    }
}
