package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.ZoomiesConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Minecraft saves the whole world every 900 ticks (45 s) — a known stutter in big packs. Make it configurable. */
@Mixin(MinecraftServer.class)
public abstract class MixinAutosaveInterval {
    @ModifyConstant(method = "func_71217_p", constant = @Constant(intValue = 900), remap = false) // tick()
    private int zoomies$autosaveTicks(int vanilla) {
        return Math.max(20, ZoomiesConfig.num("performance.autosaveSeconds", 45) * 20);
    }
}
