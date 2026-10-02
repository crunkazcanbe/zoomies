package com.dogpound.zoomies.mixin;

import java.util.function.Predicate;

import com.dogpound.zoomies.PackAdd;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** UnlimitedChiselWorks: add its generated pack without a full resource reload in pre-init (5 s, see PackAdd). */
@Mixin(targets = "pl.asie.ucw.UCWProxyClient", remap = false)
public abstract class MixinUcwSkipReload {
    @Redirect(method = "preInit", require = 0,
              at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/FMLClientHandler;refreshResources(Ljava/util/function/Predicate;)V"))
    private void zoomies$packOnly(FMLClientHandler h, Predicate<IResourceType> types) {
        if (!PackAdd.addNewPacksOnly()) h.refreshResources(types);
    }
}
