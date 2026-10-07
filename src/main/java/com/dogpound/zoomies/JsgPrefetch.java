package com.dogpound.zoomies;

import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Just Stargate decodes its ~340 textures one after another on the main thread every resource reload — and four of
 * them (the animated event horizons) are 13107x13107 JPEGs, ~8 s each: ~50 s per reload in the Pride pack. Here every
 * file in JSG's list is decoded at once on worker threads the moment JSG gets the list; when JSG then asks for a
 * picture it gets the finished one (or waits for just that one). Same pictures, same GL upload on the main thread.
 * Lives outside the mixin package (classes there can't be loaded at runtime).
 */
public final class JsgPrefetch {
    private JsgPrefetch() {}

    private static final Map<String, Future<BufferedImage>> PENDING = new ConcurrentHashMap<>();
    private static ExecutorService pool;
    private static long started;
    public static IResourceManager manager;
    public static volatile String current;                     // path JSG's loadTexture/loadEH is working on

    /** key both JSG's file list entries and its resource paths agree on */
    private static String key(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/');
        int i = p.indexOf("textures/");
        return i < 0 ? p : p.substring(i);
    }

    public static void prefetch(List<?> files) {
        if (manager == null || files == null || files.isEmpty()) return;
        clear();
        started = System.currentTimeMillis();
        int threads = Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors() - 2));
        pool = Executors.newFixedThreadPool(threads, r -> { Thread t = new Thread(r, "Zoomies-JSG-decode"); t.setDaemon(true); return t; });
        IResourceManager rm = manager;
        // biggest first: the four huge event horizons start right away instead of last
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (Object o : files) { String k = key(String.valueOf(o)); if (k != null && (k.endsWith(".png") || k.endsWith(".jpg") || k.endsWith(".jpeg"))) keys.add(k); }
        keys.sort((a, b) -> Boolean.compare(!a.contains("event_horizon_animated"), !b.contains("event_horizon_animated")));
        for (String k : keys) {
            PENDING.put(k, pool.submit(() -> {
                try (IResource r = rm.getResource(new ResourceLocation("jsg", k)); InputStream in = r.getInputStream()) {
                    return TextureUtil.readBufferedImage(in);
                }
            }));
        }
        System.out.println("[Zoomies] Just Stargate: decoding " + keys.size() + " textures on " + threads + " threads");
    }

    /** JSG's own decode call: hand over the pre-decoded picture, or decode as JSG would if we don't have it */
    public static BufferedImage read(InputStream in) throws java.io.IOException {
        String k = key(current);
        Future<BufferedImage> f = k == null ? null : PENDING.remove(k);
        if (f != null) {
            try {
                BufferedImage img = f.get(120, TimeUnit.SECONDS);
                if (img != null) { in.close(); return img; }
            } catch (Throwable ignored) { /* fall back to JSG's own way below */ }
        }
        return TextureUtil.readBufferedImage(in);
    }

    public static void done() {
        if (started > 0) System.out.println("[Zoomies] Just Stargate textures ready " + (System.currentTimeMillis() - started) + " ms after the list came in");
        clear();
    }

    private static void clear() {
        for (Future<BufferedImage> f : PENDING.values()) f.cancel(true);
        PENDING.clear();
        if (pool != null) { pool.shutdownNow(); pool = null; }
        started = 0;
    }
}
