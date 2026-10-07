package com.dogpound.zoomies;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loading ETA. Every stage Forge announces (ProgressManager.push) is timed; when loading finishes the timeline is saved
 * (config/zoomies-timeline.txt). Next launch, "time left" = last launch's total minus where THIS stage started last
 * time — stage-matched, so it stays right even if one mod is slow today. Loading screens call Eta.text() (by reflection
 * from PrideCanvas); null = no estimate yet (first launch).
 */
public final class Eta {
    private static final long START = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final List<Object[]> now = new ArrayList<>();          // {offsetMs, title}
    private static final Map<String, Integer> seen = new HashMap<>();
    private static List<Object[]> last;                                     // previous launch
    private static long lastTotal = -1;
    private static String lastTitle;
    private static long lastPushAt, currentPushAt, currentPrevOffset = -1;
    private static boolean finished;

    private Eta() {}

    private static File file() { return new File("config", "zoomies-timeline.txt"); }

    private static void loadLast() {
        if (last != null) return;
        last = new ArrayList<>();
        try {
            for (String l : Files.readAllLines(file().toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", 2);
                if (p.length < 2) continue;
                if (p[1].equals("#END")) lastTotal = Long.parseLong(p[0]);
                else last.add(new Object[]{Long.parseLong(p[0]), p[1]});
            }
        } catch (Exception ignored) { }
    }

    /** a stage started */
    static synchronized void stage(String title) {
        if (finished || title == null) return;
        long t = System.currentTimeMillis();
        if (title.equals(lastTitle) && t - lastPushAt < 5) return;          // one push overload calling another
        lastTitle = title; lastPushAt = t;
        long off = t - START;
        now.add(new Object[]{off, title});
        int n = seen.merge(title, 1, Integer::sum);
        loadLast();
        currentPushAt = t;
        currentPrevOffset = -1;
        for (Object[] o : last) if (title.equals(o[1]) && --n == 0) { currentPrevOffset = (Long) o[0]; break; } // same stage, same occurrence
    }

    /** loading is over (main menu): save this launch's timeline for next time */
    static synchronized void finish() {
        if (finished) return;
        finished = true;
        long total = System.currentTimeMillis() - START;
        StringBuilder sb = new StringBuilder();
        for (Object[] o : now) sb.append(o[0]).append('\t').append(o[1]).append('\n');
        sb.append(total).append("\t#END\n");
        try { file().getParentFile().mkdirs(); Files.write(file().toPath(), sb.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) { }
        try { Profiler.startupDone(new java.util.ArrayList<>(now)); } catch (Throwable t) { System.out.println("[Zoomies] profiler: " + t); }
        if (ZoomiesConfig.on("general.logTimings"))
            System.out.println("[Zoomies] loading took " + fmt(total) + (lastTotal > 0 ? " (last time " + fmt(lastTotal) + ")" : ""));
    }

    /** "about 3m 20s left", or null when there's nothing to go on yet */
    public static synchronized String text() {
        loadLast();
        if (finished || lastTotal <= 0 || !ZoomiesConfig.on("eta.enabled")) return null;
        long t = System.currentTimeMillis();
        long left = currentPrevOffset >= 0 ? lastTotal - currentPrevOffset - (t - currentPushAt) : lastTotal - (t - START);
        if (left < 5000) return "almost done";
        return "about " + fmt(left) + " left";
    }

    public static void stageHook(String t) { stage(t); }
    public static void finishHook() { finish(); }

    static String fmt(long ms) {
        long s = ms / 1000;
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }
}
