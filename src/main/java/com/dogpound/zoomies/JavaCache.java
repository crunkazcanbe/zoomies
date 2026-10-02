package com.dogpound.zoomies;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.management.ManagementFactory;

/**
 * java.openj9Cache: a class + compiled-code cache kept on disk between launches. That comes from the JAVA, not from a
 * mod: IBM Semeru (OpenJ9) started with -Xshareclasses. Zoomies only checks that it's really running and says so.
 * On any other Java (or if anything here fails) it does nothing — never a crash.
 */
public final class JavaCache {
    enum State { OFF, NOT_OPENJ9, NO_CACHE_ARG, ACTIVE }

    private static State state = State.OFF;
    private static boolean told;

    private JavaCache() {}

    static State detect() {
        try {
            if (!ZoomiesConfig.on("java.openj9Cache")) return State.OFF;
            if (!String.valueOf(System.getProperty("java.vm.name")).contains("OpenJ9")) return State.NOT_OPENJ9;
            try {                                                    // OpenJ9's own answer, if its API is there
                Object on = Class.forName("com.ibm.oti.shared.Shared").getMethod("isSharingEnabled").invoke(null);
                if (Boolean.TRUE.equals(on)) return State.ACTIVE;
            } catch (Throwable ignored) {}
            for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments())
                if (a.startsWith("-Xshareclasses") && !a.contains("none")) return State.ACTIVE;
            return State.NO_CACHE_ARG;
        } catch (Throwable t) {
            return State.OFF;
        }
    }

    static String message(State s) {
        switch (s) {
            case NOT_OPENJ9: return "Java cache is ON in zoomies.cfg, but this Java isn't IBM Semeru (OpenJ9) — it's "
                    + System.getProperty("java.vm.name") + ". Nothing changes; the game runs normally. See java.openj9Cache in config/zoomies.cfg.";
            case NO_CACHE_ARG: return "Running on OpenJ9, but its cache isn't turned on. Add the Java argument "
                    + "-Xshareclasses:name=pride,cacheDir=<a folder> to this instance.";
            case ACTIVE: return "OpenJ9 class cache active — classes and compiled code are reused between launches.";
            default: return null;
        }
    }

    /** once at init: log it; on the client, also say it in chat once when you're in a world (only if something's missing) */
    static void register(boolean client) {
        try {
            state = detect();
            String m = message(state);
            if (m == null) return;
            System.out.println("[Zoomies] " + m);
            if (client && state != State.ACTIVE) MinecraftForge.EVENT_BUS.register(JavaCache.class);
        } catch (Throwable t) {
            System.out.println("[Zoomies] Java cache check skipped: " + t);
        }
    }

    @SubscribeEvent
    public static void tell(TickEvent.ClientTickEvent e) {
        if (told || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.ticksExisted < 100) return;
        told = true;
        try { mc.player.sendMessage(new TextComponentString("§d✦ Zoomies: §f" + message(state))); } catch (Throwable ignored) {}
        MinecraftForge.EVENT_BUS.unregister(JavaCache.class);
    }
}
