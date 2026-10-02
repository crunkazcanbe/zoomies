package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.RandomMobs;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** every mob renderer binds its skin through Render.bindEntityTexture: swap in a texture-pack variant there */
@Mixin(Render.class)
public abstract class MixinRandomMobs {
    @Shadow(remap = false) protected abstract ResourceLocation func_110775_a(Entity e); // getEntityTexture

    @Redirect(method = "func_180548_c", remap = false,
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;func_110775_a(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/ResourceLocation;", remap = false))
    private ResourceLocation zoomies$randomMob(Render<Entity> self, Entity e) {
        return RandomMobs.pick(e, func_110775_a(e));
    }
}
