package com.dogpound.zoomies;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * config/zoomies.cfg — every trick has its own switch. Read at the very start (before any patch is applied),
 * so switching a trick off really removes it. Unknown or missing keys fall back to the defaults below, and the
 * file is rewritten with every option + its explanation so new options show up after an update.
 */
public final class ZoomiesConfig {
    /** key -> {default, explanation}; order = order in the file */
    private static final Map<String, String[]> OPTIONS = new LinkedHashMap<>();
    private static final Properties values = new Properties();
    private static boolean loaded;

    static {
        section("general");
        opt("general.enabled", "true", "Master switch. false = Zoomies does nothing at all.");
        opt("general.logTimings", "true", "Write each trick's time saved to the log (search the log for [Zoomies]).");

        section("threads");
        opt("threads.count", "auto", "How many threads parallel tricks may use. auto = every hardware thread minus threads.reserve "
            + "(e.g. a Threadripper 3970X has 64). Or a number, e.g. 32.");
        opt("threads.reserve", "2", "With count = auto: threads left free for the desktop, music, Discord...");
        opt("threads.priority", "normal", "Priority of Zoomies' worker threads: low, normal or high.");
        opt("threads.max", "64", "Upper limit no matter what count says.");

        section("safety");
        opt("safety.verify", "false", "Spot-check tricks against the original slow code and log any difference (MISMATCH). "
            + "Also switched on by an empty file named zoomies-verify in the game folder.");
        opt("safety.verifyEvery", "64", "With verify on: check 1 in this many answers (1 = check every single one, slow).");

        section("oreIndex");
        opt("oreIndex.enabled", "true", "Ore-dictionary ingredients answer 'does this item fit?' from an index instead of "
            + "walking every item under that ore name. Helps Tinkers, Ender IO and any mod that checks recipes a lot.");

        section("enderio");
        opt("enderio.alloyDedupe", "true", "Ender IO alloy recipes: find already-made combinations by bucket instead of "
            + "scanning them all. Measured: 173 s -> 51 s of loading.");
    }

    private ZoomiesConfig() {}

    private static void section(String name) { OPTIONS.put("#section " + name, new String[]{"", ""}); }
    private static void opt(String key, String def, String why) { OPTIONS.put(key, new String[]{def, why}); }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        File f = new File("config", "zoomies.cfg");
        try {
            if (f.isFile()) try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) { values.load(r); }
        } catch (Exception e) {
            System.out.println("[Zoomies] couldn't read config/zoomies.cfg, using defaults: " + e);
        }
        save(f);
    }

    /** rewrite the file: every option with its explanation, keeping the player's values */
    private static void save(File f) {
        try {
            f.getParentFile().mkdirs();
            try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
                w.println("# Zoomies - load-speed tricks for big modpacks. true/false switches; restart the game after changing.");
                w.println("# Delete a line to get its default back.");
                for (Map.Entry<String, String[]> e : OPTIONS.entrySet()) {
                    if (e.getKey().startsWith("#section ")) { w.println(); w.println("# ===== " + e.getKey().substring(9) + " ====="); continue; }
                    w.println("# " + e.getValue()[1] + " [default: " + e.getValue()[0] + "]");
                    w.println(e.getKey() + "=" + values.getProperty(e.getKey(), e.getValue()[0]).trim());
                }
            }
        } catch (Exception e) {
            System.out.println("[Zoomies] couldn't write config/zoomies.cfg: " + e);
        }
    }

    public static String get(String key) {
        load();
        String[] o = OPTIONS.get(key);
        return values.getProperty(key, o == null ? "" : o[0]).trim();
    }

    public static boolean on(String key) {
        return "true".equalsIgnoreCase(get(key));
    }

    public static int num(String key, int fallback) {
        try { return Integer.parseInt(get(key)); } catch (NumberFormatException e) { return fallback; }
    }

    /** threads.count resolved: auto = all hardware threads minus the reserve, never above threads.max, never below 1 */
    public static int threads() {
        int hw = Runtime.getRuntime().availableProcessors();
        String c = get("threads.count");
        int n = "auto".equalsIgnoreCase(c) ? hw - Math.max(0, num("threads.reserve", 2)) : num("threads.count", hw - 2);
        return Math.max(1, Math.min(n, Math.min(num("threads.max", 64), hw)));
    }

    public static int priority() {
        switch (get("threads.priority").toLowerCase()) {
            case "low": return Thread.MIN_PRIORITY + 2;
            case "high": return Thread.MAX_PRIORITY - 1;
            default: return Thread.NORM_PRIORITY;
        }
    }

    public static boolean verify() {
        return on("safety.verify") || Boolean.getBoolean("zoomies.verify") || new File("zoomies-verify").isFile();
    }

    /** which config switch controls each patch (mixin class simple name) */
    static boolean mixinAllowed(String mixin) {
        if (!on("general.enabled")) return false;
        switch (mixin) {
            case "MixinOreIngredient": return on("oreIndex.enabled");
            case "MixinEnderIOAlloyDedupe": return on("enderio.alloyDedupe");
            default: return true;
        }
    }
}
