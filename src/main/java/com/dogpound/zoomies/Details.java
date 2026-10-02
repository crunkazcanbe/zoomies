package com.dogpound.zoomies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * OptiFine "Details" options Celeritas/Celeritas Extra don't have. All client-side and visual only: the world, its
 * weather and its time keep running normally on the server.
 */
public final class Details {
    private static final long[] frames = new long[240];
    private static int fi;
    private static long last;

    static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Details());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.entityShadows = ZoomiesConfig.on("details.entityShadows");
        mc.gameSettings.heldItemTooltips = ZoomiesConfig.on("details.heldItemTooltips");
        boolean capes = ZoomiesConfig.on("details.capes");
        if (mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.CAPE) != capes) mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.CAPE, capes);
        if (mc.world == null) return;
        BetterGrass.onToggle();
        if (!ZoomiesConfig.on("details.weather")) { mc.world.setRainStrength(0); mc.world.setThunderStrength(0); }   // this client's view only
        int hour = ZoomiesConfig.num("details.timeLock", 0);
        if (hour > 0) mc.world.setWorldTime(((hour - 6 + 24) % 24) * 1000L);                                        // 6:00 = tick 0
    }

    /** clear water: see much further underwater */
    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.FogDensity e) {
        if (!ZoomiesConfig.on("details.clearWater") || e.getState().getMaterial() != Material.WATER) return;
        e.setDensity(0.015f);
        e.setCanceled(true);
    }

    /** lagometer: frame times of the last 240 frames, bottom-left; green < 16 ms, yellow < 33, red above */
    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        if (last != 0) frames[fi++ % frames.length] = now - last;
        last = now;
    }

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || !ZoomiesConfig.on("details.lagometer")) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = e.getResolution();
        int x0 = 2, y0 = sr.getScaledHeight() - 2, h = 40;
        Gui.drawRect(x0 - 1, y0 - h - 1, x0 + frames.length / 2 + 1, y0 + 1, 0x60000000);
        long sum = 0;
        int n = 0;
        for (int i = 0; i < frames.length; i += 2) {
            long ns = Math.max(frames[(fi + i) % frames.length], frames[(fi + i + 1) % frames.length]);
            if (ns == 0) continue;
            double ms = ns / 1e6;
            sum += ns; n++;
            int bar = (int) Math.min(h, ms / 50.0 * h);
            int col = ms < 16.7 ? 0xFF3AD27F : ms < 33.3 ? 0xFFFFD700 : 0xFFE40303;
            Gui.drawRect(x0 + i / 2, y0 - bar, x0 + i / 2 + 1, y0, col);
        }
        double avg = n == 0 ? 0 : sum / 1e6 / n;
        mc.fontRenderer.drawStringWithShadow(String.format("%.0f FPS  %.1f ms", avg > 0 ? 1000 / avg : 0, avg), x0 + 2, y0 - h - 10, 0xFFFFFFFF);
    }
}
