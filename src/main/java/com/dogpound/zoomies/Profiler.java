package com.dogpound.zoomies;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Pride Profiler: how long every mod took while the game loaded (each FML step + resource reloads) and while a world
 * was created (chunk generators, world generators, population). Saved as JSON in config/zoomies-profile/ and shown by
 * GuiProfiler when loading finishes / from the Esc and Options menus.
 */
public final class Profiler {
    private Profiler() {}

    /** one report: mod id → step → nanoseconds */
    public static final class Report {
        public String kind, started;
        public long totalMs;
        public final Map<String, Map<String, Long>> mods = new LinkedHashMap<>();
        public final List<Object[]> steps = new ArrayList<>();      // Forge loading bars: {offsetMs, title} (startup only)

        synchronized void add(String mod, String step, long nanos) {
            mods.computeIfAbsent(mod, k -> new LinkedHashMap<>()).merge(step, nanos, Long::sum);
        }

        public synchronized long modNanos(String mod) {
            long t = 0;
            for (long v : mods.getOrDefault(mod, java.util.Collections.emptyMap()).values()) t += v;
            return t;
        }

        public synchronized List<String> ranked() {
            List<String> l = new ArrayList<>(mods.keySet());
            l.sort((a, b) -> Long.compare(modNanos(b), modNanos(a)));
            return l;
        }
    }

    public static final Report STARTUP = new Report();
    public static volatile Report world;              // the world being created right now (or the last one)
    public static volatile boolean showStartup;       // client: pop the report up on the main menu once
    public static volatile boolean worldReady;        // client: a fresh world report waits to be announced
    private static long jvmStart;

    static {
        STARTUP.kind = "startup";
        try { jvmStart = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(); } catch (Throwable t) { jvmStart = System.currentTimeMillis(); }
        STARTUP.started = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(jvmStart));
    }

    // ------------------------------------------------------------------ which mod owns a class
    private static final Map<String, String> JAR_TO_MOD = new HashMap<>();
    private static final Map<Class<?>, String> CLASS_TO_MOD = new HashMap<>();

    public static synchronized String modOf(Class<?> c) {
        if (c == null) return "?";
        String m = CLASS_TO_MOD.get(c);
        if (m != null) return m;
        m = byJar(c);
        if (m == null) {
            String n = c.getName();
            m = n.startsWith("net.minecraft.") ? "minecraft" : n.startsWith("net.minecraftforge.") ? "forge" : n.substring(0, Math.max(0, n.lastIndexOf('.')));
        }
        CLASS_TO_MOD.put(c, m);
        return m;
    }

    private static String byJar(Class<?> c) {
        try {
            URL u = c.getProtectionDomain() == null || c.getProtectionDomain().getCodeSource() == null ? null : c.getProtectionDomain().getCodeSource().getLocation();
            if (u == null) return null;
            String jar = new File(u.toURI()).getName();
            if (JAR_TO_MOD.isEmpty()) {
                for (net.minecraftforge.fml.common.ModContainer mc : net.minecraftforge.fml.common.Loader.instance().getActiveModList())
                    if (mc.getSource() != null) JAR_TO_MOD.putIfAbsent(mc.getSource().getName(), mc.getModId());
            }
            return JAR_TO_MOD.get(jar);
        } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------ hooks (called by mixins)
    public static void modStep(String modId, String step, long nanos) { STARTUP.add(modId, step, nanos); }

    public static void reload(Class<?> listener, long nanos) {
        STARTUP.add(modOf(listener), "Resource reload", nanos);
        Report w = world;
        if (w != null && worldOpen) w.add(modOf(listener), "Resource reload", nanos);
    }

    private static volatile boolean worldOpen;
    public static boolean worldOpen() { return worldOpen; }

    public static void worldGen(Class<?> owner, String step, long nanos) {
        Report w = world;
        if (w != null && worldOpen) w.add(modOf(owner), step, nanos);
    }

    public static void worldStart(String name) {
        Report r = new Report();
        r.kind = "world";
        r.started = name + " · " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        r.totalMs = System.currentTimeMillis();       // start stamp until finished
        world = r;
        worldOpen = true;
    }

    public static void worldDone() {
        Report r = world;
        if (r == null || !worldOpen) return;
        worldOpen = false;
        r.totalMs = System.currentTimeMillis() - r.totalMs;
        save(r, "world");
        worldReady = true;
    }

    /** loading finished (FML's outer bar closed): stamp, attach Eta's step list, save, pop up on the menu */
    public static void startupDone(List<Object[]> steps) {
        STARTUP.totalMs = System.currentTimeMillis() - jvmStart;
        if (steps != null) STARTUP.steps.addAll(steps);
        save(STARTUP, "startup");
        showStartup = ZoomiesConfig.on("profiler.popup");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static void save(Report r, String name) {
        try {
            File dir = new File("config/zoomies-profile");
            dir.mkdirs();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(new java.util.Date());
            String json;
            synchronized (r) { json = GSON.toJson(r); }
            for (File f : new File[]{new File(dir, name + "-" + stamp + ".json"), new File(dir, "last-" + name + ".json")})
                try (Writer w = new FileWriter(f)) { w.write(json); }
            System.out.println("[Zoomies] profiler: " + name + " report saved (" + r.totalMs / 1000 + " s, " + r.mods.size() + " mods)");
        } catch (Exception e) {
            System.out.println("[Zoomies] profiler: couldn't save the " + name + " report: " + e);
        }
    }

    public static Report loadLast(String name) {
        try (java.io.Reader rd = new java.io.FileReader(new File("config/zoomies-profile/last-" + name + ".json"))) {
            return GSON.fromJson(rd, Report.class);
        } catch (Exception e) { return null; }
    }
}
