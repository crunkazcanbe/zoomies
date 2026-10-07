package com.dogpound.zoomies;

import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/** a dimension that loads after the world started gets its stargate address then (see MixinJsgLazyDims) */
public final class JsgLazyGates {
    private final Set<Integer> done = new HashSet<Integer>();
    static volatile boolean started;

    @SubscribeEvent
    public void load(WorldEvent.Load e) {
        if (!started || e.getWorld().isRemote) return;
        int dim = e.getWorld().provider.getDimension();
        if (!done.add(dim)) return;
        net.minecraft.server.MinecraftServer srv = e.getWorld().getMinecraftServer();
        if (srv == null) return;
        srv.addScheduledTask(() -> {
            try {
                Class.forName("tauri.dev.jsg.worldgen.StargateDimensionGenerator").getMethod("tryGenerate", net.minecraft.world.World.class)
                        .invoke(null, net.minecraftforge.common.DimensionManager.getWorld(0));
            } catch (Throwable t) { System.out.println("[Zoomies] JSG gate for dim " + dim + " failed: " + t); }
        });
    }
}
