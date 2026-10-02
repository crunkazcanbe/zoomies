package com.dogpound.zoomies;

/** What the settings screens show: {key, label, type, min, max, step, group, needsRestart}. type b = on/off, i = number. */
final class SettingsList {
    static final String[][] ALL = {
        {"general.enabled", "Zoomies", "b", "", "", "", "Loading tricks", "1"},
        {"oreIndex.enabled", "Ore-dictionary index", "b", "", "", "", "Loading tricks", "1"},
        {"enderio.alloyDedupe", "Ender IO alloy recipes", "b", "", "", "", "Loading tricks", "1"},
        {"enderio.lookupSets", "Ender IO recipe lookup", "b", "", "", "", "Loading tricks", "1"},
        {"textures.fastStitch", "Fast texture stitching", "b", "", "", "", "Loading tricks", "1"},
        {"loadingScreen.noFrameLimit", "Loading screen doesn't wait", "b", "", "", "", "Loading tricks", "1"},
        {"customnpcs.deferTextureScan", "Custom NPCs scan in background", "b", "", "", "", "Loading tricks", "1"},
        {"betterwithmods.oreIndex", "Better With Mods ore index", "b", "", "", "", "Loading tricks", "1"},
        {"eta.enabled", "Loading time estimate", "b", "", "", "", "Loading tricks", "1"},
        {"particles.lod", "Particle level of detail", "b", "", "", "", "Particles", "0"},
        {"particles.fullDistance", "All particles within", "i", "4", "128", "4", "Particles", "0"},
        {"particles.halfDistance", "Half the particles within", "i", "8", "256", "8", "Particles", "0"},
        {"particles.quarterDistance", "A quarter within", "i", "16", "512", "16", "Particles", "0"},
        {"entities.screenSizeCull", "Skip tiny far-off mobs", "b", "", "", "", "Entities", "0"},
        {"entities.minPixels", "Smallest mob drawn (pixels)", "i", "1", "16", "1", "Entities", "0"},
        {"graphics.anisotropic", "Anisotropic filtering", "i", "1", "16", "1", "Graphics", "0"},
        {"graphics.fxaa", "Anti-aliasing (FXAA)", "b", "", "", "", "Graphics", "0"},
        {"graphics.smoothMipmaps", "Smooth mipmaps", "b", "", "", "", "Graphics", "0"},
        {"graphics.dynamicFov", "Dynamic FOV", "b", "", "", "", "Graphics", "0"},
        {"graphics.smoothZoom", "Smooth zoom camera", "b", "", "", "", "Graphics", "0"},
        {"textures.betterGrass", "Better Grass", "b", "", "", "", "Details", "0"},
        {"textures.customSky", "Custom sky (texture packs)", "b", "", "", "", "Details", "0"},
        {"textures.randomMobs", "Random mobs (texture packs)", "b", "", "", "", "Details", "0"},
        {"details.entityShadows", "Entity shadows", "b", "", "", "", "Details", "0"},
        {"details.heldItemTooltips", "Held item names", "b", "", "", "", "Details", "0"},
        {"details.capes", "Show cape", "b", "", "", "", "Details", "0"},
        {"details.clearWater", "Clear water", "b", "", "", "", "Details", "0"},
        {"details.weather", "Weather (rain/snow)", "b", "", "", "", "Details", "0"},
        {"details.timeLock", "Lock sky to hour (0 = off)", "i", "0", "24", "1", "Details", "0"},
        {"details.lagometer", "Lagometer", "b", "", "", "", "Details", "0"},
        {"machines.screenSizeCull", "Skip tiny far-away machines", "b", "", "", "", "Performance", "0"},
        {"graphics.skipNoopTransforms", "Skip do-nothing transforms", "b", "", "", "", "Performance", "0"},
        {"worldgen.lazyDimensions", "Load dimensions only when visited", "b", "", "", "", "Performance", "0"},
        {"worldgen.animaniaAdvancementsOnce", "Faster world creation (Animania fix)", "b", "", "", "", "Performance", "1"},
        {"performance.autosaveSeconds", "World autosave every (seconds)", "i", "15", "600", "15", "Performance", "0"},
        {"threads.count", "Threads for parallel tricks (0 = auto)", "i", "0", "64", "1", "Threads", "1"},
        {"threads.reserve", "Threads left for the desktop", "i", "0", "16", "1", "Threads", "1"},
        {"safety.verify", "Spot-check against the original code", "b", "", "", "", "Safety", "1"},
    };

    private SettingsList() {}

    static boolean bool(String key) { return ZoomiesConfig.on(key); }

    static int number(String key) {
        String v = ZoomiesConfig.get(key);
        if ("auto".equalsIgnoreCase(v)) return 0;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }

    static void setNumber(String key, int v) {
        ZoomiesConfig.set(key, key.equals("threads.count") && v == 0 ? "auto" : String.valueOf(v));
    }
}
