package com.dogpound.zoomies.mixin;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better Weather answers "is it raining here?" by scanning the block column down from the top — for every entity
 * (getting wet), the rain sounds and the rain renderer, every tick. Measured 2026-10-02: ~7% of the big pack's render
 * thread. A column's rain height only changes when blocks above change, so remember it for 10 ticks (0.5 s).
 * One memory per world, kept apart for the client and server threads so neither touches the other's.
 */
@Mixin(targets = "paulevs.betterweather.api.WeatherAPI", remap = false)
public abstract class MixinBetterWeatherRainHeight {
    @Unique private static final int ZOOMIES_TICKS = 10;
    @SuppressWarnings("unchecked")
    @Unique private static final java.util.Map<World, Long2LongOpenHashMap>[] zoomies$memo =
            new java.util.Map[]{ new java.util.WeakHashMap<>(), new java.util.WeakHashMap<>() };

    /** per world (the server ticks several dimensions in turn); index 0 = client thread, 1 = server thread */
    @Unique
    private static Long2LongOpenHashMap zoomies$map(World w) {
        Long2LongOpenHashMap m = zoomies$memo[w.isRemote ? 0 : 1].computeIfAbsent(w, k -> new Long2LongOpenHashMap());
        if (m.size() > 65536) m.clear();
        return m;
    }

    @Inject(method = "getRainHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zoomies$known(World w, int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (w == null || (!w.isRemote && !w.isBlockLoaded(new net.minecraft.util.math.BlockPos(x, 64, z)))) return;
        Long2LongOpenHashMap m = zoomies$map(w);
        long v = m.getOrDefault(((long) x << 32) ^ (z & 0xffffffffL), Long.MIN_VALUE);
        if (v != Long.MIN_VALUE && w.getTotalWorldTime() - (v >>> 32) < ZOOMIES_TICKS) cir.setReturnValue((int) v);
    }

    @Inject(method = "getRainHeight", at = @At("RETURN"), require = 0)
    private static void zoomies$keep(World w, int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (w == null) return;
        zoomies$map(w).put(((long) x << 32) ^ (z & 0xffffffffL), (w.getTotalWorldTime() << 32) | (cir.getReturnValue() & 0xffffffffL));
    }
}
