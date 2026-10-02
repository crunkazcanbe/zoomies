package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.PackAdd;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Immersive Vehicles: add its vehicle-pack resources without a full resource reload (15 s, see PackAdd). */
@Mixin(targets = "mcinterface1122.InterfaceEventsModelLoader", remap = false)
public abstract class MixinMtsSkipReload {
    @Redirect(method = "registerModels", require = 0,
              at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/FMLClientHandler;refreshResources([Lnet/minecraftforge/client/resource/IResourceType;)V"))
    private static void zoomies$packOnly(FMLClientHandler h, IResourceType[] types) {
        if (!PackAdd.addNewPacksOnly()) h.refreshResources(types);
    }
}
