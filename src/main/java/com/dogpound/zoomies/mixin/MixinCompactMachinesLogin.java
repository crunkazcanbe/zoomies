package com.dogpound.zoomies.mixin;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Compact Machines loads its whole machine dimension when a player logs in (~1.7 s, undoing Zoomies' lazy dimensions)
 * only to send that dimension's WorldInfo. A mod dimension's WorldInfo is a DerivedWorldInfo that writes exactly the
 * overworld's data, so while the machine dimension isn't loaded, send the overworld's instead.
 */
@Pseudo
@Mixin(targets = "org.dave.compactmachines3.misc.PlayerEventHandler", remap = false)
public abstract class MixinCompactMachinesLogin {
    @Redirect(method = "onLogin", require = 0,
              at = @At(value = "INVOKE", target = "Lorg/dave/compactmachines3/world/tools/DimensionTools;getServerMachineWorld()Lnet/minecraft/world/WorldServer;"))
    private static WorldServer zoomies$noLoad() {
        try {
            int dim = Class.forName("org.dave.compactmachines3.misc.ConfigurationHandler$Settings").getField("dimensionId").getInt(null);
            WorldServer loaded = DimensionManager.getWorld(dim);
            if (loaded != null) return loaded;
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld != null) return overworld;
        } catch (Throwable ignored) { }
        try {
            return (WorldServer) Class.forName("org.dave.compactmachines3.world.tools.DimensionTools").getMethod("getServerMachineWorld").invoke(null);
        } catch (Throwable e) { throw new RuntimeException(e); }
    }
}
