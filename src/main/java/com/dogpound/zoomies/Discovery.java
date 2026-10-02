package com.dogpound.zoomies;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Optimization Discovery Mode (her idea 2026-09-28): watch the real game for N seconds with cheap counters (off
 * the rest of the time), then say which optimizations this scene would actually gain from — instead of 150
 * checkboxes and hoping. /zoomies discover [seconds]
 */
public final class Discovery {
    public static volatile boolean on;
    public static boolean skipNoop = true;          // live copy of graphics.skipNoopTransforms (read in hot GL paths)

    // counters (render thread only, while on)
    public static long matrixOps, noopMatrix, pushPop, stateCalls, stateRedundant, texBinds, texRedundant;
    public static long bufferGrows, entityDraws, entityTiny, machineDraws, machineTiny, frames, ticks;
    public static final int[] lastTex = new int[32];
    private static final List<Long> frameTimes = new ArrayList<>();
    private static long lastFrame, until;
    private static int seconds;
    private static boolean autoTried, testRun;
    static Result result;

    public static void start(int secs) {
        machineDraws = machineTiny = matrixOps = noopMatrix = pushPop = stateCalls = stateRedundant = texBinds = texRedundant = bufferGrows = entityDraws = entityTiny = frames = ticks = 0;
        frameTimes.clear(); lastFrame = 0;
        seconds = secs;
        until = System.currentTimeMillis() + secs * 1000L;
        RenderProfiler.start();
        on = true;
    }

    static void register() { net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Discovery()); }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !on) return;
        long now = System.nanoTime();
        if (lastFrame != 0) frameTimes.add(now - lastFrame);
        lastFrame = now;
        frames++;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        skipNoop = ZoomiesConfig.on("graphics.skipNoopTransforms");
        Minecraft mc0 = Minecraft.getMinecraft();
        if (!autoTried && mc0.player != null) {                  // dev check: file "zoomies-discover-test" = run once, report to the log
            autoTried = true;
            if (new java.io.File("zoomies-discover-test").isFile()) { testRun = true; start(10); }
        }
        if (!on) return;
        ticks++;
        if (System.currentTimeMillis() < until) return;
        on = false;
        RenderProfiler.stop();
        result = analyse();
        System.out.println("[Zoomies] DISCOVERY " + result.seconds + " s:");
        for (Row row : result.stats) System.out.println("[Zoomies] DISCOVERY   " + row.name + " = " + row.value + " (" + row.note + ")");
        for (Idea i : result.ideas) System.out.println("[Zoomies] DISCOVERY   idea " + i.name + " = " + i.rating + " (" + i.why + ")");
        System.out.println("[Zoomies] DISCOVERY verdict: " + result.verdict);
        if (testRun) { testRun = false; return; }                // the dev check doesn't open screens over the other tests
        Minecraft.getMinecraft().displayGuiScreen(new GuiDiscovery(result));
    }

    // ---------------- the verdict ----------------
    public static final class Row {
        public final String name, value, note;
        Row(String name, String value, String note) { this.name = name; this.value = value; this.note = note; }
    }

    public static final class Idea {
        public final String name, rating, why, setting;   // setting: a Zoomies config key it can switch on, or null
        public final boolean already;
        final int score;
        Idea(String name, int score, String why, String setting, boolean already) {
            this.name = name; this.score = score; this.why = why; this.setting = setting; this.already = already;
            this.rating = already ? "ON ✓" : score >= 3 ? "HIGH" : score == 2 ? "MEDIUM" : score == 1 ? "LOW" : "NONE";
        }
    }

    public static final class Result {
        public final List<Row> stats = new ArrayList<>();
        public final List<Idea> ideas = new ArrayList<>();
        public String verdict;
        public int seconds;
    }

    static Result analyse() {
        Result r = new Result();
        r.seconds = seconds;
        Minecraft mc = Minecraft.getMinecraft();
        long[] ft = new long[frameTimes.size()];
        for (int i = 0; i < ft.length; i++) ft[i] = frameTimes.get(i);
        java.util.Arrays.sort(ft);
        double avg = 0;
        for (long f : ft) avg += f;
        avg = ft.length == 0 ? 0 : avg / ft.length / 1e6;
        double low1 = ft.length == 0 ? 0 : ft[Math.max(0, (int) (ft.length * 0.99) - 1)] / 1e6;
        long perF = Math.max(1, frames);
        int particles = 0;
        try { particles = Integer.parseInt(mc.effectRenderer.getStatistics().trim()); } catch (Exception ignored) {}
        int animated = 0;
        try {
            java.lang.reflect.Field f = net.minecraftforge.fml.common.ObfuscationReflectionHelper.findField(net.minecraft.client.renderer.texture.TextureMap.class, "field_94258_i");
            animated = ((List<?>) f.get(mc.getTextureMapBlocks())).size();
        } catch (Throwable ignored) {}
        double tesrMs = 0, entMs = 0;
        for (String[] row : RenderProfiler.report(500)) {
            // row: kind, class, ms/frame, calls/frame (see RenderProfiler.report)
            try { double ms = Double.parseDouble(row[2]); if (row[0].startsWith("machine")) tesrMs += ms; else entMs += ms; } catch (Exception ignored) {}
        }

        r.stats.add(new Row("Average FPS", String.format("%.0f", avg == 0 ? 0 : 1000 / avg), String.format("%.1f ms a frame · 1%% low %.0f FPS", avg, low1 == 0 ? 0 : 1000 / low1)));
        r.stats.add(new Row("Matrix operations", fmt(matrixOps / perF) + "/frame", fmt(noopMatrix / perF) + " did nothing · " + fmt(pushPop / perF) + " push/pop"));
        r.stats.add(new Row("GL state changes", fmt(stateCalls / perF) + "/frame", fmt(stateRedundant / perF) + " asked for a state already set"));
        r.stats.add(new Row("Texture binds", fmt(texBinds / perF) + "/frame", fmt(texRedundant / perF) + " re-bound the same texture"));
        r.stats.add(new Row("Vertex buffer growth", fmt(bufferGrows), "times a buffer had to be re-allocated bigger"));
        r.stats.add(new Row("Mobs drawn", fmt(entityDraws / perF) + "/frame", fmt(entityTiny / perF) + " smaller than a few pixels"));
        r.stats.add(new Row("Machines drawn", fmt(machineDraws / perF) + "/frame", fmt(machineTiny / perF) + " smaller than a few pixels"));
        r.stats.add(new Row("Mob / machine draw time", String.format("%.2f / %.2f ms", entMs, tesrMs), "per frame (Mob / Machine renderers)"));
        List<String[]> worst = RenderProfiler.report(3);                // row: name, mod, ms/frame, %, calls, µs each
        StringBuilder w = new StringBuilder();
        for (String[] row : worst) w.append(w.length() == 0 ? "" : " · ").append(row[0]).append(" (").append(row[1]).append(") ").append(row[2]).append(" ms");
        if (w.length() > 0) r.stats.add(new Row("Slowest renderers", "", w.toString()));
        r.stats.add(new Row("Particles alive", fmt(particles), ""));
        r.stats.add(new Row("Animated textures", fmt(animated), "block textures that animate"));

        double noopShare = matrixOps == 0 ? 0 : noopMatrix / (double) matrixOps;
        r.ideas.add(new Idea("Skip do-nothing transforms", noopShare > 0.15 ? 3 : noopShare > 0.05 ? 2 : noopShare > 0 ? 1 : 0,
            String.format("%.0f%% of matrix calls change nothing", noopShare * 100), "graphics.skipNoopTransforms", skipNoop));
        double tinyShare = entityDraws == 0 ? 0 : entityTiny / (double) entityDraws;
        r.ideas.add(new Idea("Tiny-mob culling", tinyShare > 0.25 ? 3 : tinyShare > 0.1 ? 2 : tinyShare > 0 ? 1 : 0,
            String.format("%.0f%% of mobs drawn are only a few pixels tall", tinyShare * 100), "entities.screenSizeCull", ZoomiesConfig.on("entities.screenSizeCull")));
        double mTiny = machineDraws == 0 ? 0 : machineTiny / (double) machineDraws;
        r.ideas.add(new Idea("Tiny-machine culling", mTiny > 0.25 ? 3 : mTiny > 0.1 ? 2 : mTiny > 0 ? 1 : 0,
            String.format("%.0f%% of machines drawn are only a few pixels on screen", mTiny * 100), "machines.screenSizeCull", ZoomiesConfig.on("machines.screenSizeCull")));
        r.ideas.add(new Idea("Particle distance LOD", particles > 3000 ? 3 : particles > 1000 ? 2 : particles > 200 ? 1 : 0,
            particles + " particles alive", "particles.lod", ZoomiesConfig.on("particles.lod")));
        r.ideas.add(new Idea("Machine (TESR) distance budget", tesrMs > 4 ? 3 : tesrMs > 1.5 ? 2 : tesrMs > 0.3 ? 1 : 0,
            String.format("machines take %.1f ms a frame — see /zoomies report for which mod (budget: coming soon)", tesrMs), null, false));
        long growsPerSec = bufferGrows / Math.max(1, seconds);
        r.ideas.add(new Idea("Vertex buffer recycling", growsPerSec > 20 ? 3 : growsPerSec > 5 ? 2 : growsPerSec > 0 ? 1 : 0,
            growsPerSec + " re-allocations a second (pooling: coming soon)", null, false));
        double redundant = stateCalls == 0 ? 0 : stateRedundant / (double) stateCalls;
        r.ideas.add(new Idea("GL state prediction", 0,
            String.format("%.0f%% of state calls were redundant — Minecraft already skips those itself, nothing to gain", redundant * 100), null, false));
        r.ideas.add(new Idea("Animated texture limits", animated > 400 ? 2 : animated > 100 ? 1 : 0,
            animated + " animated textures — Celeritas Extra's Animations page can turn off the ones you don't care about", null, false));

        Idea best = null;
        for (Idea i : r.ideas) if (!i.already && i.score > 0 && (best == null || i.score > best.score)) best = i;
        r.verdict = best == null ? "This scene is already running lean — nothing big left to gain here 💖"
            : "Your current scene would benefit most from: " + best.name;
        return r;
    }

    static String fmt(long n) { return n >= 1_000_000 ? String.format("%.1fM", n / 1e6) : n >= 10_000 ? String.format("%.1fk", n / 1e3) : String.valueOf(n); }
}
