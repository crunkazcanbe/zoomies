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

        section("eta");
        opt("eta.enabled", "true", "Remember how long each loading stage took, and show \"about 3m 20s left\" on loading screens that ask "
            + "(PrideCanvas does). Timeline saved in config/zoomies-timeline.txt.");

        section("particles");
        opt("particles.lod", "true", "Particle level of detail: all particles near you, half further out, a quarter further still, none past that.");
        opt("particles.fullDistance", "16", "Blocks: every particle within this.");
        opt("particles.halfDistance", "32", "Blocks: 1 in 2 particles up to here.");
        opt("particles.quarterDistance", "64", "Blocks: 1 in 4 particles up to here; none beyond.");

        section("entities");
        opt("entities.screenSizeCull", "true", "Don't draw mobs that would be tinier than entities.minPixels on screen (far-off chickens). "
            + "Never skips players, named or glowing mobs, bosses, or what you're riding.");
        opt("entities.minPixels", "3", "Pixels tall below which a far mob isn't drawn.");

        section("graphics");
        opt("graphics.anisotropic", "1", "Anisotropic filtering (1 = off, 2/4/8/16): sharper ground and walls seen at an angle. Needs mipmaps on.");
        opt("graphics.fxaa", "false", "Anti-aliasing (FXAA): smooths jagged edges, using Minecraft's own FXAA shader.");
        opt("graphics.smoothMipmaps", "false", "Smooth mipmaps: blend between texture detail levels far away (less shimmer).");
        opt("graphics.dynamicFov", "true", "Dynamic FOV: sprinting, speed and bows widen/narrow the view. Off = the view never stretches.");
        opt("graphics.smoothZoom", "true", "Smooth, slower camera while zooming (hold the zoom key, ` by default; scroll to zoom).");

        opt("textures.customSky", "true", "OptiFine Custom Sky: resource packs with optifine/sky/world0/sky1.properties draw star/nebula sky layers.");
        opt("textures.betterGrass", "false", "OptiFine Better Grass (Fast): grass, snowy grass and mycelium are grass-coloured on every side.");
        opt("thaumcraft.recipeIndex", "true", "Thaumcraft looks up each item's crafting recipes through an index instead of scanning every recipe in the game (was 11% of startup). Restart to change.");
        opt("textures.resourceMissCache", "true", "Remember resources Immersive Vehicles already looked for and didn't find, instead of searching all mod jars again. Restart to change.");
        opt("machines.screenSizeCull", "true", "Don't draw animated machines/blocks (TESRs) smaller than entities.minPixels on screen. Never within 16 blocks or for beams.");
        opt("textures.randomMobs", "true", "OptiFine Random Entities: texture packs with optifine/random/entity/ (or mcpatcher/mob/) give mobs several looks.");

        section("worldgen");
        opt("worldgen.lazyDimensions", "true", "Only load the start dimensions when a world opens; every other dimension loads the first time someone goes there (122 dimensions in this pack).");
        opt("worldgen.startDimensions", "0,-1,1", "Dimensions loaded right away (comma list). 0 = overworld, -1 = Nether, 1 = End.");
        opt("worldgen.animaniaAdvancementsOnce", "true", "Animania rebuilds every advancement each time ANY dimension loads (126x in this pack, ~2 min of world creation). Build it once.");

        opt("graphics.skipNoopTransforms", "true", "Skip translate(0,0,0), rotate(0) and scale(1) calls - they change nothing but still cost a trip to the graphics driver. Switches live.");

        section("fvtm");
        opt("fvtm.zipIndex", "true", "FVTM checks every mod jar for addon packs by unpacking it completely. Read the jar's table of contents instead (~28 s saved).");

        section("models");
        opt("models.fastObjLines", "true", "GVCLib (gun mods) checks every line of every 3D model with slow regex patterns. Use identical hand-written checks (~20 s saved).");

        section("details");
        opt("details.entityShadows", "true", "Round shadows under mobs and items.");
        opt("details.heldItemTooltips", "true", "Show the item's name above the hotbar when you switch to it.");
        opt("details.capes", "true", "Show your own cape.");
        opt("details.clearWater", "false", "See much further underwater.");
        opt("details.weather", "true", "Rain and snow. Off = never see weather (visual only; it still rains for crops and the server).");
        opt("details.timeLock", "0", "Lock the sky to an hour of the day (1-24; 0 = off). Visual only: the real time keeps going.");
        opt("details.lagometer", "false", "Frame-time graph + FPS in the bottom-left corner.");

        section("performance");
        opt("performance.autosaveSeconds", "45", "How often the world saves (Minecraft: every 45 s, which can stutter in big packs).");

        section("network");
        opt("network.connectTimeoutSeconds", "5", "Give up connecting to a website after this many seconds (for every mod's update checks and downloads "
            + "that don't set their own limit). A slow site can't freeze loading for a minute. 0 = Java's default (wait forever).");
        opt("network.readTimeoutSeconds", "10", "Give up on a download that sends NOTHING for this many seconds. A download that keeps "
            + "receiving data is never cut off. 0 = Java's default (wait forever).");
        opt("network.abyssalcraftPatronsCache", "true", "AbyssalCraft downloads its supporter list at every startup (measured 64 s stuck). "
            + "Use a saved copy from config/zoomies-cache/ and refresh it in the background.");
        section("java");
        opt("java.openj9Cache", "false", "Keep a cache of Java classes and compiled code ON DISK between launches, so later launches reuse it. "
            + "REQUIRES A SPECIAL JAVA: IBM Semeru Runtime (OpenJ9) 25, set as this instance's Java, with the Java argument "
            + "-Xshareclasses:name=pride,cacheDir=<a folder>. Normal Java: nothing happens and nothing breaks - Zoomies just tells you once.");
        section("safety");
        opt("safety.verify", "false", "Spot-check tricks against the original slow code and log any difference (MISMATCH). "
            + "Also switched on by an empty file named zoomies-verify in the game folder.");
        opt("safety.verifyEvery", "64", "With verify on: check 1 in this many answers (1 = check every single one, slow).");

        section("oreIndex");
        opt("oreIndex.enabled", "true", "Ore-dictionary ingredients answer 'does this item fit?' from an index instead of "
            + "walking every item under that ore name. Helps Tinkers, Ender IO and any mod that checks recipes a lot.");

        section("extrautils2");
        opt("extrautils2.oreRegisterIndex", "true", "Extra Utilities 2 crusher recipes: when an ore name is registered, check only that "
            + "metal's dust/ore/ingot instead of re-scanning every ore name in the game. Measured: 40 s of the big pack's load.");
        section("tails");
        opt("tails.zipIndex", "true", "Tails Legacy: find its part files through a per-jar folder index instead of walking every file of "
            + "every mod jar on each resource reload. Measured: 23 s of the big pack's load.");
        section("enderio");
        opt("enderio.alloyDedupe", "true", "Ender IO alloy recipes: find already-made combinations by bucket instead of "
            + "scanning them all. Measured: 173 s -> 51 s of loading.");
        opt("enderio.lookupSets", "true", "Ender IO's recipe lookup tree: check 'recipe already filed here?' with a set instead of "
            + "walking the list. Measured ~44 s of loading left after alloyDedupe.");

        section("loadingScreen");
        opt("loadingScreen.noFrameLimit", "true", "Don't frame-limit the LOADING screen (no world, no menu open). RenderLib's limiter "
            + "made every loading refresh wait up to 33 ms - measured ~20% of the whole launch. Menus and play keep their limit.");

        section("textures");
        opt("textures.fastStitch", "true", "Pack the texture sheet in rows (one pass) instead of Minecraft's slot-by-slot search. "
            + "Measured ~15% of a big pack's load. Falls back to Minecraft's packer if the sheet wouldn't fit.");

        section("betterwithmods");
        opt("betterwithmods.oreIndex", "true", "Better With Mods checks 'is this item in that ore list?' by walking the list. Answer from "
            + "an index of each item's first position instead (same answer). Measured ~17 s of loading.");

        section("customnpcs");
        opt("customnpcs.deferTextureScan", "true", "Custom NPCs scans every texture in every mod at startup (~70 s in a big pack) just "
            + "for its NPC-editor texture picker. Do it in the background after the main menu appears instead.");
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

    /** change a value (from the Celeritas page or the Zoomies options screen) */
    public static synchronized void set(String key, String value) {
        load();
        values.setProperty(key, value);
    }

    /** write every option back to config/zoomies.cfg and refresh the live values */
    public static synchronized void save() {
        save(new File("config", "zoomies.cfg"));
        Live.refresh();
    }

    /** values the per-frame tricks read (cached: they run thousands of times a second) */
    public static final class Live {
        public static volatile boolean particles, entityCull, machineCull;
        public static volatile int particleFull, particleHalf, particleQuarter;
        public static volatile double minPixels;
        static { refresh(); }

        public static void refresh() {
            particles = on("particles.lod");
            particleFull = num("particles.fullDistance", 16);
            particleHalf = Math.max(particleFull, num("particles.halfDistance", 32));
            particleQuarter = Math.max(particleHalf, num("particles.quarterDistance", 64));
            entityCull = on("entities.screenSizeCull");
            machineCull = on("machines.screenSizeCull");
            minPixels = Math.max(0.5, num("entities.minPixels", 3));
        }
    }

    /** the explanation shown for an option */
    public static String why(String key) {
        String[] o = OPTIONS.get(key);
        return o == null ? "" : o[1];
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
            case "MixinXu2OreRegister": return on("extrautils2.oreRegisterIndex");
            case "MixinTailsZipListing": return on("tails.zipIndex");
            case "MixinEnderIOLookupNode": return on("enderio.lookupSets");
            case "MixinSmoothSyncLoading": return on("loadingScreen.noFrameLimit");
            case "MixinFastStitcher": return on("textures.fastStitch");
            case "MixinProgressManager": return on("eta.enabled");
            case "MixinAutosaveInterval": return true;     // performance.autosaveSeconds read live
            case "MixinRandomMobs": return true;           // textures.randomMobs switches it live
            case "MixinCustomSky": return true;            // textures.customSky switches it live
            case "MixinParticleLod": return true;          // always woven in; particles.lod switches it live
            case "MixinEntityRenderProfile": return true;  // profiler + live entities.screenSizeCull switch
            case "MixinNpcsDeferTextureScan": return on("customnpcs.deferTextureScan");
            case "MixinBwmListContains": return on("betterwithmods.oreIndex");
            case "MixinMtsPackResourceCache": return on("textures.resourceMissCache");
            case "MixinThaumcraftRecipeIndex": return on("thaumcraft.recipeIndex");
            case "MixinFvtmZipIndex": return on("fvtm.zipIndex");
            case "MixinGvcObjLines": return on("models.fastObjLines");
            case "MixinNoopTransforms": case "MixinDiscoveryGl": case "MixinDiscoveryState": case "MixinDiscoveryBuffer": return true; // live switches / idle counters
            case "MixinLazyDimensionsServer": case "MixinLazyDimensionsClient": return true;   // worldgen.lazyDimensions read live
            case "MixinAbyssalPatrons": return on("network.abyssalcraftPatronsCache");
            case "MixinAnimaniaAdvancementsOnce": return on("worldgen.animaniaAdvancementsOnce");
            default: return true;
        }
    }
}
