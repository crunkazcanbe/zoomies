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

    static void checkModList() {
        if (!ZoomiesConfig.on("caches.invalidateOnModChange")) return;
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

    private WarmCaches() {}
}
