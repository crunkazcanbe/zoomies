package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dogpound.zoomies.Profiler;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLEvent;

/** Pride Profiler: time each mod's construction / pre-init / init / post-init / load-complete handlers. */
@Mixin(value = FMLModContainer.class, remap = false)
public abstract class MixinProfileModSteps {
    private static final ThreadLocal<long[]> ZOOMIES$T = ThreadLocal.withInitial(() -> new long[1]);

    @Inject(method = "handleModStateEvent", at = @At("HEAD"), require = 0)
    private void zoomies$stepStart(FMLEvent e, CallbackInfo ci) { ZOOMIES$T.get()[0] = System.nanoTime(); }

    @Inject(method = "handleModStateEvent", at = @At("RETURN"), require = 0)
    private void zoomies$stepEnd(FMLEvent e, CallbackInfo ci) {
        long t = System.nanoTime() - ZOOMIES$T.get()[0];
        Profiler.modStep(((FMLModContainer) (Object) this).getModId(), step(e.getClass().getSimpleName()), t);
    }

    @Inject(method = "constructMod", at = @At("HEAD"), require = 0)
    private void zoomies$constructStart(FMLConstructionEvent e, CallbackInfo ci) { ZOOMIES$T.get()[0] = System.nanoTime(); }

    @Inject(method = "constructMod", at = @At("RETURN"), require = 0)
    private void zoomies$constructEnd(FMLConstructionEvent e, CallbackInfo ci) {
        Profiler.modStep(((FMLModContainer) (Object) this).getModId(), "Construct", System.nanoTime() - ZOOMIES$T.get()[0]);
    }

    private static String step(String ev) {
        switch (ev) {
            case "FMLPreInitializationEvent": return "Pre-init";
            case "FMLInitializationEvent": return "Init";
            case "FMLPostInitializationEvent": return "Post-init";
            case "FMLLoadCompleteEvent": return "Load complete";
            case "FMLInterModComms$IMCEvent": case "IMCEvent": return "Mod messages";
            case "FMLModIdMappingEvent": return "ID remap";
            default: return ev.replace("FML", "").replace("Event", "");
        }
    }
}
