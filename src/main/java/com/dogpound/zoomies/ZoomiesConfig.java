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

        section("profiler");
        opt("profiler.enabled", "true", "Pride Profiler: time every mod while the game loads and while a world is created.");
        opt("profiler.popup", "true", "Show the startup report on the main menu after loading, and a notice after creating a world.");

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
        opt("entities.runawayGuard", "true", "Stop any non-player entity asked to move absurdly far in one tick (a flung item once froze the server thread for good). Restart to change.");
        opt("entities.runawayMaxBlocksPerTick", "10000", "Runaway guard: blocks per tick above which an entity is stopped.");

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
        opt("worldgen.spawnInVillage", "true", "New worlds start in the nearest village (outside Clockwork Phase 2's crater). '/zoomies village' moves you to one in an existing world.");
        opt("speed.jsgLazyDimensions", "true", "Just Stargate loads all ~160 dimensions at world start to give each a stargate address (~3 min). On: only already-loaded ones at start, the rest when you first go there.");
        opt("speed.aoaLazyGameRules", "true", "Advent of Ascension loads every dimension at world start just to add two game rules (~30 s). On: only loaded worlds (the others share the overworld's rules).");
        section("dh");
        opt("dh.surfaceLods", "true", "Distant Horizons: far chunks you never visited get terrain-only LODs (no trees/structures until you go near) instead of fully generating each one plus 8 neighbours. Many times faster in a big pack.");
        opt("fix.villagerBackportFastVillageCheck", "true", "Villager Backport: decide 'is this new chunk part of a village' from the villages already planned, instead of searching the map for the nearest village on every chunk (made OTG worlds run at 1 TPS).");
        opt("dh.offThreadLods", "true", "Distant Horizons: build the terrain-only LODs on DH's own worker threads (private biome + terrain generator per thread) instead of queueing on the busy server thread. Dozens of times faster; vanilla-style overworlds only.");
        opt("dh.fullDetailRadius", "0", "Distant Horizons: within this many chunks of a player, new LODs still use full real chunks (trees, structures). 0 = always terrain-only for unvisited land.");
        opt("dh.serverBudgetMs", "20", "Distant Horizons: milliseconds of server time it may use every tick to build LODs. DH's own rule gives a busy modpack one chunk per tick. 0 = DH's own rule.");
        opt("dh.idleSeconds", "30", "Distant Horizons: after every player has stood still this long (AFK, reading, in a menu), DH gets the bigger idle slice below. 0 = off.");
        opt("dh.playerFirst", "true", "Distant Horizons: no new LOD chunks while any player still has chunks in view waiting to load, or is flying/moving fast - DH generates on the same server thread as your own land. Restart to change.");
        opt("dh.playerFirstSpeed", "8", "Distant Horizons player-first: blocks per second (sideways) that count as moving fast. Walking 4, sprinting 6, creative flying 11. 0 = only the waiting-chunks check.");
        opt("dh.playerFirstHoldMs", "2000", "Distant Horizons player-first: keep DH paused this long after the last waiting chunk / fast movement.");
        opt("dh.idleBudgetMs", "120", "Distant Horizons: milliseconds per tick while everyone is idle - fills LODs many times faster when you aren't playing.");
        opt("speed.projecteBackground", "true", "ProjectE works out EMC values in the background after the world opens instead of holding the join (39 s after a mod change). EMC shows up a little after you join.");
        opt("worldgen.instantJoin", "true", "Skip 'Preparing spawn area': you are in the world as soon as it opens, and the land around you loads like any other chunks (what the Ksyxis mod does; Ksyxis itself crashes this pack's loader).");
        section("join");
        opt("join.enderioAlloyBackground", "true", "Joining a world re-files Ender IO's alloy smelter recipes under the world's item IDs (~9.5 s of every join). Do it in the background; smelters may idle a few seconds after you join. Restart to change.");
        opt("join.skipRegistryDump", "true", "Forge writes every registry entry to the trace log on each world join (~185,000 lines, ~3 s). Skip writing it. Restart to change.");
        opt("join.structurizeSingleRead", "true", "Structurize/MineColonies reads every blueprint twice at server start; the second read is a size check that can never fail. Skip it (~1.3 s). Restart to change.");
        opt("join.cd4017NoGc", "true", "CD4017BE Lib forces a full garbage collection at every server start (~1.7 s). Skip it. Restart to change.");
        opt("join.lootrBlockCache", "true", "Lootr builds a tile entity of every block in the game on the first loot tick after you join (~11 s server freeze). Remember which blocks qualified (until the mod list changes). Restart to change.");
        opt("join.realisticPhysicsTags", "true", "Realistic Physics walks every item name for each ore tag when a dimension loads (~1.3 s per dimension). Read the tag's items directly (same result). Restart to change.");
        opt("join.compactMachinesNoDimLoad", "true", "Compact Machines loads its machine dimension at every login just to send its world info (~1.7 s), which is the overworld's anyway. Send the overworld's while that dimension isn't loaded. Restart to change.");
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
        section("warmClasses");
        opt("warmClasses.enabled", "false", "Warm Classes (TEST FIRST): save each class as the mod patchers (coremods, Forge, access transformers) left it, "
            + "and reuse that on the next launch instead of patching it again. Mixin always runs live on top, so mixins behave exactly as without it. "
            + "The saved copy is thrown away when any mod jar, library, Java argument or config file changes. Restart to change.");
        opt("warmClasses.verify", "false", "With Warm Classes on: patch every class anyway, compare with the saved copy and log MISMATCH "
            + "(both versions go to zoomies-cache/warmclasses/mismatch/). Uses the freshly patched one. Slow - for testing only.");
        opt("warmClasses.liveTransformers", "", "Comma list of patcher class names (or parts of names) that must always run live. Saving stops "
            + "just before the first one; it and everything after it run every launch. Use if a patcher needs to see every class load.");
        opt("warmClasses.skipClasses", "", "Comma list of class-name starts that are never saved (always patched live), e.g. com.example.");
        opt("warmClasses.ignoreConfigs", "zoomies-timeline.txt,zoomies-cache,zoomies-profile,zoomies.cfg,config/zoomies/,.recall,hbmConfig/_,hbmRecipes/_,astralsorcery/amulet_enchantments,AppliedEnergistics2/CustomRecipes.cfg,zoomies-autotune.json", "Comma list of config/ paths (or parts of paths) whose "
            + "changes should NOT throw the saved classes away. The log names the config files that changed when it does.");
        opt("warmClasses.keyConfigs", "", "Comma list of extra config path parts that should rebuild the cache when they change (configs of coremods are found automatically).");
        opt("warmClasses.maxSizeMB", "2048", "Stop saving new classes once the saved file reaches this size.");
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
        section("caches");
        opt("caches.invalidateOnModChange", "true", "Delete saved startup caches (ProjectE EMC values, RealmCoin prices) when the mod "
            + "list changes, so mods that reuse them never read stale data.");
        section("reloads");
        opt("reloads.packOnly", "false", "When Immersive Vehicles or UnlimitedChiselWorks add their resource pack during start-up, add just that pack "
            + "instead of forcing a full resource reload (the game reloads everything at the end anyway). Measured: 20 s.");
        section("texturecap");
        opt("textures.maxTileSize", "128", "Largest size (pixels) any single texture may take in the main texture sheet; bigger ones are "
            + "scaled down to fit. Stops 'Unable to fit' crashes when giant vehicle textures overflow the sheet. 0 = no limit.");
        section("treasure2");
        opt("worldgen.fortressKeepsSpawn", "true", "Gates of the Apocalypse: build its fortress but DON'T move the world spawn onto its roof (you start in a village instead).");
        opt("immersiverailroading.noForcedGc", "true", "Immersive Railroading: only force a garbage collection while loading train models when memory is really low (it misreads free memory and forced ~80 of them, ~40 s per launch).");
        opt("treasure2.skipFixer", "true", "Treasure2: don't run its already-current (1.12) structure templates through the data fixer on every world load. "
            + "Measured: most of a new world's 13-minute setup in the big pack.");
        section("capsule");
        opt("capsule.skipFixer", "true", "Capsule: don't run its already-current (1.12) reward structures through the data fixer when loot "
            + "chests pick one. Measured: 38% of the server thread while new chunks fill their chests.");
        section("railcraft");
        opt("railcraft.worldGenCache", "true", "Railcraft ore generation: remember the per-ore 'is generation enabled' answer instead of "
            + "running a regex for every block it checks.");
        section("droppeditems");
        opt("droppedItems.itemPhysicListCache", "true", "ItemPhysic: remember per item type whether a dropped item is on its burn/swim/fuel lists "
            + "instead of walking the lists (and every mod's fuel handler) for every item every tick. Measured: ~30% of the render thread.");
        opt("droppedItems.dynLightThrottle", "true", "Celeritas Dynamic Lights: work out a dropped item's glow every 4th tick instead of every tick "
            + "(0.2 s, not visible). Measured: ~15% of the render thread.");
        section("weather");
        opt("weather.rainHeightMemo", "true", "Better Weather: remember each block column's rain height for 0.5 s instead of scanning the column "
            + "for every entity, rain sound and rain drop every tick. Measured: ~7% of the render thread.");
        section("creative");
        opt("creative.bcFacadeLimit", "64", "BuildCraft puts a facade for EVERY block in its creative tab (82,637 in the Pride pack) — opening it froze the "
            + "game and EMI indexed them all. Show only this many there (they're all still craftable). -1 = show all.");
        section("tails");
        opt("tails.zipIndex", "true", "Tails Legacy: find its part files through a per-jar folder index instead of walking every file of "
            + "every mod jar on each resource reload. Measured: 23 s of the big pack's load.");
        section("enderio");
        opt("enderio.alloyDedupe", "true", "Ender IO alloy recipes: find already-made combinations by bucket instead of "
            + "scanning them all. Measured: 173 s -> 51 s of loading.");
        opt("enderio.lookupSets", "true", "Ender IO's recipe lookup tree: check 'recipe already filed here?' with a set instead of "
            + "walking the list. Measured ~44 s of loading left after alloyDedupe.");
        opt("enderio.flatAlloyLookup", "true", "Ender IO Alloy Smelter lookup: file each recipe once per ingredient instead of under "
            + "every pair/triple (synthetic 2x/3x recipes made it cubic: GBs of memory with ~800 mods). Same matches.");

        section("loadingScreen");
        opt("loadingScreen.noFrameLimit", "true", "Don't frame-limit the LOADING screen (no world, no menu open). RenderLib's limiter "
            + "made every loading refresh wait up to 33 ms - measured ~20% of the whole launch. Menus and play keep their limit.");

        section("textures");
        opt("textures.fastStitch", "true", "Pack the texture sheet in rows (one pass) instead of Minecraft's slot-by-slot search. "
            + "Measured ~15% of a big pack's load. Falls back to Minecraft's packer if the sheet wouldn't fit.");

        section("bootCaches");
        opt("bootCaches.mtsModelCompat", "true", "Immersive Vehicles reads all ~7,300 vehicle 3D models at startup just to find lights "
            + "and treads (~15 s). Save what it found per model (zoomies-cache/boot/) and reuse it until a mod jar changes. "
            + "Models are then read the first time a vehicle is drawn instead.");
        opt("bootCaches.vintageFixTextureList", "true", "VintageFix searches every mod jar and resource pack for textures at startup "
            + "(~12 s, the game waits ~8 s). Save the list (zoomies-cache/boot/) and reuse it until mods, resource packs or "
            + "resources/ change. Missing (purple) textures after editing a pack in place? Delete zoomies-cache/boot/.");

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
            case "MixinTreasureTemplateNoFixer": return on("treasure2.skipFixer");
            case "MixinIrNoForcedGc": return on("immersiverailroading.noForcedGc");
            case "MixinGoaKeepSpawn": return on("worldgen.fortressKeepsSpawn");
            case "MixinStitcherTileCap": return true;   // textures.maxTileSize read live (0 = off)
            case "MixinRailcraftWorldGenCache": return on("railcraft.worldGenCache");
            case "MixinCapsuleTemplateNoFixer": return on("capsule.skipFixer");
            case "MixinBcFacadeTabCap": return true;
            case "MixinItemPhysicListCache": return on("droppedItems.itemPhysicListCache");
            case "MixinDynLightsItemThrottle": return on("droppedItems.dynLightThrottle");
            case "MixinBetterWeatherRainHeight": return on("weather.rainHeightMemo");
            case "MixinMtsSkipReload": case "MixinUcwSkipReload": return on("reloads.packOnly");
            case "MixinEnderIOLookupNode": return on("enderio.lookupSets");
            case "MixinEnderIOTriLookupFlat": return on("enderio.flatAlloyLookup");
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
            case "MixinMtsLegacyModelCache": return on("bootCaches.mtsModelCompat");
            case "MixinVintageFixTextureCache": return on("bootCaches.vintageFixTextureList");
            case "MixinMtsPackResourceCache": return on("textures.resourceMissCache");
            case "MixinThaumcraftRecipeIndex": return on("thaumcraft.recipeIndex");
            case "MixinFvtmZipIndex": return on("fvtm.zipIndex");
            case "MixinGvcObjLines": return on("models.fastObjLines");
            case "MixinNoopTransforms": case "MixinDiscoveryGl": case "MixinDiscoveryState": case "MixinDiscoveryBuffer": return true; // live switches / idle counters
            case "MixinLazyDimensionsServer": case "MixinLazyDimensionsClient": return true;   // worldgen.lazyDimensions read live
            case "MixinAbyssalPatrons": return on("network.abyssalcraftPatronsCache");
            case "MixinAnimaniaAdvancementsOnce": return on("worldgen.animaniaAdvancementsOnce");
            case "MixinEnderIOAlloyRebuildLater": return on("join.enderioAlloyBackground");
            case "MixinForgeRegistryNoDump": return on("join.skipRegistryDump");
            case "MixinStructurizeNoSizeCheck": return on("join.structurizeSingleRead");
            case "MixinCd4017NoGc": return on("join.cd4017NoGc");
            case "MixinLootrReplaceCache": return on("join.lootrBlockCache");
            case "MixinRealisticPhysicsTags": return on("join.realisticPhysicsTags");
            case "MixinCompactMachinesLogin": return on("join.compactMachinesNoDimLoad");
            case "MixinDhPlayerFirst": return on("dh.playerFirst");
            case "MixinRunawayEntity": return on("entities.runawayGuard");
            default: return true;
        }
    }
}
