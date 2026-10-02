package com.dogpound.zoomies;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Small files mods download at every startup (supporter lists, news) — kept in config/zoomies-cache/. With a saved copy
 * the mod gets it instantly and a background thread refreshes it for next time; without one it's fetched once with
 * short time limits. Nothing here can hang loading: a failed fetch throws IOException, which the mods already handle.
 */
public final class WebCache {
    private static final File DIR = new File("config/zoomies-cache");

    private WebCache() {}

    public static InputStream open(URL url, String name) throws IOException {
        File f = new File(DIR, name);
        if (f.isFile()) {
            byte[] saved = Files.readAllBytes(f.toPath());
            Thread t = new Thread(() -> { try { save(f, fetch(url)); } catch (Throwable ignored) {} }, "Zoomies web cache");
            t.setDaemon(true);
            t.start();
            return new ByteArrayInputStream(saved);
        }
        byte[] fresh = fetch(url);
        try { save(f, fresh); } catch (IOException ignored) {}
        return new ByteArrayInputStream(fresh);
    }

    static byte[] fetch(URL url) throws IOException {
        URLConnection c = url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > 4 << 20) throw new IOException("too big for the web cache");   // small files only
            }
            return out.toByteArray();
        }
    }

    private static void save(File f, byte[] data) throws IOException {
        if (data.length == 0) return;
        DIR.mkdirs();
        File tmp = new File(DIR, f.getName() + ".tmp");
        Files.write(tmp.toPath(), data);
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** network.connectTimeoutSeconds / readTimeoutSeconds: defaults for every web request that doesn't set its own */
    static void applyDefaultTimeouts() {
        try {
            int c = ZoomiesConfig.num("network.connectTimeoutSeconds", 5), r = ZoomiesConfig.num("network.readTimeoutSeconds", 10);
            if (c > 0 && System.getProperty("sun.net.client.defaultConnectTimeout") == null)
                System.setProperty("sun.net.client.defaultConnectTimeout", String.valueOf(c * 1000));
            if (r > 0 && System.getProperty("sun.net.client.defaultReadTimeout") == null)
                System.setProperty("sun.net.client.defaultReadTimeout", String.valueOf(r * 1000));
        } catch (Throwable ignored) {}
    }
}
