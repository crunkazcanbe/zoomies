package com.dogpound.zoomies;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Auto-Tune (requested feature). Once the game has
 * loaded it looks at the PC (threads, RAM, GPU maker / VRAM) and sets Celeritas + Distant Horizons to fit, for the
 * next start. It never fights the player: a value she changed by hand after we set it is left alone.
 * Off switch: config/zoomies-autotune.off
 */
public final class AutoTune {
    private AutoTune() {}

    private static final File STATE = new File("config/zoomies-autotune.json");
    private static String summary = "";

    public static String summary() { return summary; }

    public static void run() {
        try {
            if (new File("config/zoomies-autotune.off").exists()) return;
            int threads = Runtime.getRuntime().availableProcessors();
            long ram = 8192;
            try { ram = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize() / 1048576L; } catch (Throwable ignored) { }
            String vendor = gl(GL11.GL_VENDOR), renderer = gl(GL11.GL_RENDERER), both = (vendor + " " + renderer).toLowerCase(Locale.ROOT);
            int vram = vramMb();
            String kind = both.contains("nvidia") ? "NVIDIA" : both.contains("amd") || both.contains("ati") || both.contains("radeon") ? "AMD" : both.contains("intel") ? "Intel" : "Other";
            boolean igpu = kind.equals("Intel") || both.contains("780m") || both.contains("680m") || both.contains("760m") || (both.contains("vega") && vram > 0 && vram < 3000) || (kind.equals("AMD") && both.contains(" graphics") && !both.contains("rx "));

            int tier;   // 0 low, 1 mid, 2 high, 3 ultra
            if (threads <= 4 || ram < 12000 || igpu || (vram > 0 && vram < 3000)) tier = 0;
            else if (threads >= 24 && (vram >= 12000 || vram < 0) && ram >= 32000) tier = 3;
            else if (threads >= 12) tier = 2;
            else tier = 1;
            String[] names = {"Light", "Balanced", "High", "Ultra"};
            int chunkThreads = Math.max(1, new int[]{2, Math.min(6, threads - 2), Math.min(12, threads - 2), Math.min(24, threads - 4)}[tier]);
            int dhThreads = Math.max(1, new int[]{2, 4, 6, 8}[tier]);   // 8 + 0.75 = her proven setting; more starved the server tick (TPS 1.4 while DH generated)
            String dhRatio = new String[]{"0.5", "0.75", "0.75", "0.75"}[tier];

            JsonObject state = STATE.isFile() ? new JsonParser().parse(read(STATE)).getAsJsonObject() : new JsonObject();
            JsonObject applied = state.has("applied") ? state.getAsJsonObject("applied") : new JsonObject();
            List<String> changed = new ArrayList<String>(), kept = new ArrayList<String>();

            // Celeritas
            File cel = new File("config/celeritas-options.json");
            if (cel.isFile()) {
                JsonObject j = new JsonParser().parse(read(cel)).getAsJsonObject();
                boolean dirty = false;
                dirty |= set(j, "performance", "chunk_builder_threads", String.valueOf(chunkThreads), applied, changed, kept);
                dirty |= set(j, "performance", "animate_only_visible_textures", "true", applied, changed, kept);
                dirty |= set(j, "performance", "use_entity_culling", "true", applied, changed, kept);
                dirty |= set(j, "performance", "use_block_face_culling", "true", applied, changed, kept);
                dirty |= set(j, "advanced", "cpu_render_ahead_limit", tier == 0 ? "2" : "3", applied, changed, kept);
                if (dirty) write(cel, new GsonBuilder().setPrettyPrinting().create().toJson(j));
            }
            // Distant Horizons
            File dh = new File("config/DistantHorizons.toml");
            if (dh.isFile()) {
                List<String> lines = new ArrayList<String>(Files.readAllLines(dh.toPath(), StandardCharsets.UTF_8));
                boolean dirty = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i), t = line.trim(), indent = line.substring(0, line.length() - line.replaceAll("^\\s+", "").length());
                    if (t.startsWith("numberOfThreads =")) {
                        String cur = t.substring(t.indexOf('=') + 1).trim();
                        String v = decide("dh.numberOfThreads", cur, String.valueOf(dhThreads), applied, changed, kept);
                        if (!v.equals(cur)) { lines.set(i, indent + "numberOfThreads = " + v); dirty = true; }
                    } else if (t.startsWith("threadRunTimeRatio =")) {
                        String cur = t.substring(t.indexOf('=') + 1).trim().replace("\"", "");
                        String v = decide("dh.threadRunTimeRatio", cur, dhRatio, applied, changed, kept);
                        if (!v.equals(cur)) { lines.set(i, indent + "threadRunTimeRatio = \"" + v + "\""); dirty = true; }
                    }
                }
                if (dirty) Files.write(dh.toPath(), lines, StandardCharsets.UTF_8);
            }

            String gpu = renderer.isEmpty() ? kind : renderer.replaceAll("\\s*\\(.*", "");
            summary = "Auto-tuned for " + threads + " threads · " + gpu + (vram > 0 ? " (" + Math.round(vram / 1024.0) + " GB)" : "")
                    + " · " + Math.round(ram / 1024.0) + " GB RAM → " + names[tier] + ": Celeritas " + chunkThreads
                    + " chunk threads, Distant Horizons " + dhThreads + " threads"
                    + (kept.isEmpty() ? "" : " · kept your " + kept.size() + " own setting(s)")
                    + (changed.isEmpty() ? "" : " (takes effect next start)");
            state.add("applied", applied);
            state.addProperty("summary", summary);
            write(STATE, new GsonBuilder().setPrettyPrinting().create().toJson(state));
            LogManager.getLogger("Zoomies").info("[Zoomies] " + summary + (changed.isEmpty() ? "" : " changed " + changed));
        } catch (Throwable t) {
            LogManager.getLogger("Zoomies").warn("[Zoomies] Auto-Tune skipped: " + t);
        }
    }

    /** the value to use: ours, unless she changed what we set last time */
    private static String decide(String key, String cur, String target, JsonObject applied, List<String> changed, List<String> kept) {
        if (applied.has(key) && !applied.get(key).getAsString().equals(cur)) { kept.add(key); return cur; }
        applied.addProperty(key, target);
        if (!target.equals(cur)) changed.add(key + "=" + target);
        return target;
    }

    private static boolean set(JsonObject root, String sec, String k, String target, JsonObject applied, List<String> changed, List<String> kept) {
        if (!root.has(sec) || !root.getAsJsonObject(sec).has(k)) return false;
        JsonObject o = root.getAsJsonObject(sec);
        String cur = o.get(k).getAsString();
        String v = decide("celeritas." + k, cur, target, applied, changed, kept);
        if (v.equals(cur)) return false;
        if (v.equals("true") || v.equals("false")) o.addProperty(k, Boolean.parseBoolean(v));
        else o.addProperty(k, Integer.parseInt(v));
        return true;
    }

    private static int vramMb() {
        try {
            if (GLContext.getCapabilities().GL_NVX_gpu_memory_info) return GL11.glGetInteger(0x9048) / 1024;
            if (GLContext.getCapabilities().GL_ATI_meminfo) {
                IntBuffer b = BufferUtils.createIntBuffer(16);
                GL11.glGetInteger(0x87FB, b);
                return b.get(0) / 1024;
            }
        } catch (Throwable ignored) {
        } finally {
            try { while (GL11.glGetError() != 0) { } } catch (Throwable ignored) { }
        }
        return -1;
    }

    private static String gl(int what) {
        try { String s = GL11.glGetString(what); return s == null ? "" : s; } catch (Throwable t) { return ""; }
    }

    private static String read(File f) throws java.io.IOException { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    private static void write(File f, String s) throws java.io.IOException { Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8)); }

    @SuppressWarnings("unused") private static final Gson GSON = new Gson();
}
