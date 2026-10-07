package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dogpound.zoomies.Profiler;
import net.minecraft.server.MinecraftServer;

/** Pride Profiler: the world-creation report covers "Preparing spawn area" — and Zoomies can skip that step entirely. */
@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MixinProfileSpawnPrep {
    @Inject(method = "func_71222_d", at = @At("HEAD"), require = 0)
    private void zoomies$spawnStart(CallbackInfo ci) { Profiler.worldStart(((MinecraftServer) (Object) this).getFolderName()); }

    /** Zoomies instant join: no "Preparing spawn area" — the chunks around you load as you play (requested feature) */
    @Inject(method = "func_71222_d", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$instantJoin(CallbackInfo ci) {
        if (com.dogpound.zoomies.ZoomiesConfig.on("worldgen.instantJoin")) {
            System.out.println("[Zoomies] instant join: skipped Preparing spawn area");
            ci.cancel();
        }
    }

    @Inject(method = "func_71222_d", at = @At("RETURN"), require = 0)
    private void zoomies$spawnEnd(CallbackInfo ci) { Profiler.worldDone(); }
}
