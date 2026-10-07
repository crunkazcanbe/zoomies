package com.dogpound.zoomies;

import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Slow "work out values" jobs that mods run while a world opens, moved to after you're in (requested feature). ProjectE's EMC mapping was 39 s of every join after a mod change.
 */
public final class BackgroundJobs {
    private BackgroundJobs() {}

    // ---- one worker for jobs that must run in order (Ender IO alloy lookup rebuilds) ----
    private static final java.util.concurrent.ExecutorService SERIAL = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Zoomies background");
        t.setDaemon(true);
        return t;
    });
    private static volatile Thread serialThread;
    private static volatile java.util.concurrent.Future<?> lastSerial;

    /** run off the server thread, after any earlier serial job */
    public static synchronized void serial(String what, Runnable job) {
        lastSerial = SERIAL.submit(() -> {
            serialThread = Thread.currentThread();
            long t0 = System.currentTimeMillis();
            try {
                job.run();
                System.out.println("[Zoomies] " + what + " done in the background in " + (System.currentTimeMillis() - t0) + " ms");
            } catch (Throwable e) {
                System.out.println("[Zoomies] " + what + " failed in the background: " + e);
                e.printStackTrace();
            }
        });
    }

    /** wait for queued serial jobs (something is about to change what they read) */
    public static void awaitSerial() {
        java.util.concurrent.Future<?> f = lastSerial;
        if (f == null || f.isDone() || Thread.currentThread() == serialThread) return;
        try { f.get(); } catch (Exception ignored) { }
    }
    static volatile boolean projectePending;

    /** called by MixinProjectEMapLater instead of EMCMapper.map() */
    public static void deferProjectE() { projectePending = true; }

    /** FMLServerStartedEvent: everything that registers EMC has run; now map in the background */
    static void serverStarted() {
        if (!projectePending) return;
        projectePending = false;
        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                Class.forName("moze_intel.projecte.emc.EMCMapper").getMethod("map").invoke(null);
                System.out.println("[Zoomies] ProjectE EMC mapped in the background in " + (System.currentTimeMillis() - t0) + " ms; sending it to players");
                net.minecraft.server.MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (srv != null) srv.addScheduledTask(() -> {
                    try { Class.forName("moze_intel.projecte.network.PacketHandler").getMethod("sendFragmentedEmcPacketToAll").invoke(null); }
                    catch (Throwable e) { System.out.println("[Zoomies] couldn't send ProjectE EMC: " + e); }
                });
            } catch (Throwable e) {
                System.out.println("[Zoomies] background ProjectE mapping failed, ProjectE will have no EMC values this session: " + e);
            }
        }, "Zoomies ProjectE EMC");
        t.setDaemon(true);
        t.start();
    }
}
