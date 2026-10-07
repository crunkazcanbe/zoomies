package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dogpound.zoomies.Profiler;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

/** Pride Profiler: time every resource-reload listener (textures, models, sounds, mod caches) per mod. */
@Mixin(value = SimpleReloadableResourceManager.class, remap = false)
public abstract class MixinProfileReload {
    @Redirect(method = {"func_110544_b", "func_110542_a"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/IResourceManagerReloadListener;func_110549_a(Lnet/minecraft/client/resources/IResourceManager;)V"))
    private void zoomies$timeListener(IResourceManagerReloadListener l, IResourceManager rm) {
        long t = System.nanoTime();
        l.onResourceManagerReload(rm);
        Profiler.reload(l.getClass(), System.nanoTime() - t);
    }
}
