package com.dogpound.zoomies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Render cost report. While on (/zoomies profile), times every machine renderer (TESR) and entity renderer call,
 * grouped by renderer class, and every whole frame — so each shows up as ms per frame and % of the frame.
 * Off = one boolean check per call.
 */
public final class RenderProfiler {
    public static volatile boolean on;
    static final Map<String, long[]> stats = new ConcurrentHashMap<>(); // key -> {nanos, calls}
    static long frames, frameNanos, startedAt;
    private static long frameStart;

    private RenderProfiler() {}

    public static void start() { stats.clear(); frames = 0; frameNanos = 0; startedAt = System.currentTimeMillis(); on = true; }
    public static void stop() { on = false; }

    public static void add(String kind, Class<?> c, long nanos) {
        long[] s = stats.computeIfAbsent(kind + " " + c.getName(), k -> new long[2]);
        s[0] += nanos;
        s[1]++;
    }

    static void frameStart() { if (on) frameStart = System.nanoTime(); }
    static void frameEnd() { if (on && frameStart != 0) { frameNanos += System.nanoTime() - frameStart; frames++; } }

    /** one line per renderer, worst first: {name, mod, ms/frame, % of frame, calls/frame, µs per call} */
    static List<String[]> report(int top) {
        List<Map.Entry<String, long[]>> list = new ArrayList<>(stats.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        List<String[]> out = new ArrayList<>();
        long f = Math.max(1, frames);
        for (Map.Entry<String, long[]> e : list.subList(0, Math.min(top, list.size()))) {
            String key = e.getKey();
            String kind = key.substring(0, key.indexOf(' ')), cls = key.substring(key.indexOf(' ') + 1);
            long[] v = e.getValue();
            double msPerFrame = v[0] / 1e6 / f;
            double pct = frameNanos > 0 ? 100.0 * v[0] / frameNanos : 0;
            out.add(new String[]{kind + ": " + cls.substring(cls.lastIndexOf('.') + 1), mod(cls), String.format("%.2f", msPerFrame),
                String.format("%.1f%%", pct), String.format("%.0f", v[1] / (double) f), String.format("%.0f", v[0] / 1e3 / Math.max(1, v[1]))});
        }
        return out;
    }

    /** "blusunrize.immersiveengineering.client..." -> "immersiveengineering" (good enough to know whose it is) */
    static String mod(String cls) {
        String[] p = cls.split("\\.");
        if (p.length >= 2 && (p[0].equals("com") || p[0].equals("net") || p[0].equals("org") || p[0].equals("me") || p[0].equals("io") || p[0].equals("dev")))
            return p.length > 2 ? p[2] : p[1];
        return p.length > 1 ? p[1] : p[0];
    }

    static String summary() {
        return String.format("%d frames, avg %.2f ms/frame (%.0f FPS)", frames, frameNanos / 1e6 / Math.max(1, frames),
            frames == 0 ? 0 : 1000.0 / (frameNanos / 1e6 / frames));
    }
}
