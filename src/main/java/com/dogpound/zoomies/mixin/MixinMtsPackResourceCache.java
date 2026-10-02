package com.dogpound.zoomies.mixin;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Her render list #12 (resource index), found by the run 7/8 profiles: Immersive Vehicles answers every resource
 * question for its packs with Class.getResourceAsStream — a walk through all ~580 mod jars, one zip at a time.
 * Most answers are "not here", and a miss walks EVERY jar (2026-10-01 dumps: 3/3 samples of a 5.7 min reload).
 *
 * v2: read every classpath jar's table of contents once (64-bit hashes of the assets/ entry names, a sorted long[]
 * ≈ 8 bytes per file) and answer misses instantly, even the first time. Fail-open: a directory or unreadable source
 * on the classpath turns the index off and only the old repeat-miss cache stays.
 */
@Mixin(targets = "mcinterface1122.InterfaceCore", remap = false)
public abstract class MixinMtsPackResourceCache {
    private static final Set<String> zoomies$missing = ConcurrentHashMap.newKeySet();
    private static volatile long[] zoomies$index;          // sorted hashes of every "assets/..." entry; null = not built
    private static volatile boolean zoomies$indexDead;

    @Inject(method = "getPackResource(Ljava/lang/String;)Ljava/io/InputStream;", at = @At("HEAD"), cancellable = true, remap = false)
    private void zoomies$knownMissing(String path, CallbackInfoReturnable<InputStream> cir) {
        if (zoomies$missing.contains(path)) { cir.setReturnValue(null); return; }
        String entry = path.startsWith("/") ? path.substring(1) : "mcinterface1122/" + path;
        if (!entry.startsWith("assets/") || entry.contains("..")) return;
        long[] idx = zoomies$index();
        if (idx != null && Arrays.binarySearch(idx, zoomies$hash(entry)) < 0) {
            zoomies$missing.add(path);
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getPackResource(Ljava/lang/String;)Ljava/io/InputStream;", at = @At("RETURN"), remap = false)
    private void zoomies$remember(String path, CallbackInfoReturnable<InputStream> cir) {
        if (cir.getReturnValue() == null) zoomies$missing.add(path);
    }

    private static long[] zoomies$index() {
        long[] idx = zoomies$index;
        if (idx != null || zoomies$indexDead) return idx;
        synchronized (MixinMtsPackResourceCache.class) {
            if (zoomies$index != null || zoomies$indexDead) return zoomies$index;
            long t = System.nanoTime();
            try {
                List<URL> mods = zoomies$sources();
                if (mods == null) throw new IllegalStateException("no classpath list");
                java.util.LinkedHashSet<URL> urls = new java.util.LinkedHashSet<>(mods);
                for (String p : System.getProperty("java.class.path", "").split(File.pathSeparator))   // parent loader: MC jar + libraries
                    if (!p.isEmpty()) urls.add(new File(p).toURI().toURL());
                long[] buf = new long[1 << 20];
                int n = 0, jars = 0;
                for (URL u : urls) {
                    if ("asmgen".equals(u.getProtocol())) continue;               // FML in-memory event handlers: classes only, no assets
                    if (!"file".equals(u.getProtocol())) throw new IllegalStateException("non-file source " + u);
                    File f = new File(u.toURI());
                    if (f.isDirectory()) throw new IllegalStateException("directory on classpath " + f);
                    if (!f.isFile()) continue;                            // missing entries never served anything
                    try (ZipFile z = new ZipFile(f)) {
                        jars++;
                        for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                            String name = e.nextElement().getName();
                            if (!name.startsWith("assets/")) continue;
                            if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
                            buf[n++] = zoomies$hash(name);
                        }
                    }
                }
                long[] out = Arrays.copyOf(buf, n);
                Arrays.sort(out);
                zoomies$index = out;
                System.out.println("[Zoomies] MTS resource index: " + n + " asset files in " + jars + " jars, "
                        + (System.nanoTime() - t) / 1_000_000 + " ms");
            } catch (Throwable e) {
                zoomies$indexDead = true;
                System.out.println("[Zoomies] MTS resource index off (" + e + ") — repeat-miss cache only");
            }
            return zoomies$index;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<URL> zoomies$sources() {
        try {                                                         // LaunchWrapper / Cleanroom: every mod jar
            Object cl = Class.forName("net.minecraft.launchwrapper.Launch").getField("classLoader").get(null);
            return (List<URL>) cl.getClass().getMethod("getSources").invoke(cl);
        } catch (Throwable ignored) { }
        ClassLoader cl = MixinMtsPackResourceCache.class.getClassLoader();
        return cl instanceof URLClassLoader ? Arrays.asList(((URLClassLoader) cl).getURLs()) : null;
    }

    private static long zoomies$hash(String s) {                     // FNV-1a 64; a collision only costs a normal lookup
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x100000001b3L; }
        return h;
    }
}
