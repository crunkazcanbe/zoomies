package com.dogpound.zoomies;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/**
 * Some mods can save a slow startup calculation to disk and reuse it (ProjectE's EMC values: ~40 s of every world start
 * in her big pack, measured 2026-10-02). The catch is a stale file after mods change. So: fingerprint the exact mod list
 * (id, version, jar size) at startup; when it differs from last time, delete those saved files so they are rebuilt once.
 */
public final class WarmCaches {
    /** files that are only valid for the mod list that made them (relative to the game folder) */
    static final String[] FILES = {
            "config/ProjectE/pregenerated_emc.json",
            "config/realmcoin/worth-cache.json",
    };

    /** SHA-1 of the mod list this session (null until checkModList ran or if it failed) */
    static volatile String modListHash;

    static void checkModList() {
        try {
            List<String> lines = new ArrayList<>();
            for (ModContainer m : Loader.instance().getModList()) {
                File src = m.getSource();
                lines.add(m.getModId() + "@" + m.getVersion() + ":" + (src != null && src.isFile() ? src.length() : 0));
            }
            java.util.Collections.sort(lines);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (String l : lines) md.update((l + "\n").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            modListHash = hex.toString();
            if (!ZoomiesConfig.on("caches.invalidateOnModChange")) return;
            File stamp = new File("config/zoomies/modlist.sha1");
            String old = stamp.isFile() ? new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).trim() : "";
            if (old.equals(hex.toString())) return;
            int gone = 0;
            for (String f : FILES) if (new File(f).delete()) gone++;
            stamp.getParentFile().mkdirs();
            Files.write(stamp.toPath(), hex.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("[Zoomies] mod list changed (" + lines.size() + " mods) — cleared " + gone + " saved cache file(s) so they rebuild");
        } catch (Exception ex) {
            System.out.println("[Zoomies] couldn't check the mod list for stale caches: " + ex);
        }
    }

    // ---- Lootr: which blocks are loot containers it should replace (MixinLootrReplaceCache) ----
    // Lootr finds them by building a tile entity of EVERY block on the first loot-chest tick after you join (~11 s).
    // The answer only depends on the mods' code, so it is saved with the mod-list fingerprint and reused.
    private static final File LOOTR_FILE = new File("config/zoomies/lootr-replaceable.txt");
    private static java.util.Set<String> lootrKnown;
    private static boolean lootrLoaded;
    private static final java.util.Set<String> lootrFound = new java.util.LinkedHashSet<>();
    private static volatile boolean lootrRecording;

    /** names Lootr found last time with this exact mod list, or null (no file / mods changed) */
    public static synchronized java.util.Set<String> lootrKnown() {
        if (lootrLoaded) return lootrKnown;
        lootrLoaded = true;
        try {
            if (modListHash != null && LOOTR_FILE.isFile()) {
                List<String> lines = Files.readAllLines(LOOTR_FILE.toPath(), StandardCharsets.UTF_8);
                if (!lines.isEmpty() && lines.get(0).trim().equals(modListHash)) {
                    lootrKnown = new java.util.HashSet<>(lines.subList(1, lines.size()));
                    System.out.println("[Zoomies] Lootr: reusing the saved list of " + lootrKnown.size() + " replaceable blocks");
                }
            }
        } catch (Exception e) {
            System.out.println("[Zoomies] Lootr: couldn't read the saved block list, Lootr checks every block: " + e);
        }
        return lootrKnown;
    }

    /** Lootr checked one block the slow way; name != null when it turned out replaceable */
    public static synchronized void lootrRecord(String name) {
        lootrRecording = true;
        if (name != null) lootrFound.add(name);
    }

    public static boolean lootrPending() { return lootrRecording; }

    /** Lootr finished its full check: save what it found for next time */
    public static synchronized void lootrSave() {
        if (!lootrRecording) return;
        lootrRecording = false;
        if (modListHash == null) return;
        try {
            List<String> out = new ArrayList<>();
            out.add(modListHash);
            out.addAll(lootrFound);
            LOOTR_FILE.getParentFile().mkdirs();
            Files.write(LOOTR_FILE.toPath(), out, StandardCharsets.UTF_8);
            lootrKnown = new java.util.HashSet<>(lootrFound);
            lootrLoaded = true;
            System.out.println("[Zoomies] Lootr: saved " + lootrFound.size() + " replaceable blocks for next time");
        } catch (Exception e) {
            System.out.println("[Zoomies] Lootr: couldn't save the block list: " + e);
        }
    }

    private static String modListKey;

    /** SHA-1 of every loaded mod (id, version, jar size + mtime): the key for Zoomies' own boot caches */
    public static synchronized String modListKey() {
        if (modListKey != null) return modListKey;
        try {
            List<String> lines = new ArrayList<>();
            for (ModContainer m : Loader.instance().getModList()) {
                File src = m.getSource();
                lines.add(m.getModId() + "@" + m.getVersion() + ":" + (src != null ? src.length() + ":" + src.lastModified() : 0));
            }
            if (lines.isEmpty()) return null;     // too early: mods not discovered yet
            java.util.Collections.sort(lines);
            return modListKey = sha1(String.join("\n", lines));
        } catch (Exception ex) {
            return null;     // no key = no cache
        }
    }

    public static String sha1(String s) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private WarmCaches() {}
}
