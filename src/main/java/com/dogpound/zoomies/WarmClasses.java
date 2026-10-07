package com.dogpound.zoomies;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * Warm Classes: a disk cache of what the coremod/Forge transformer chain makes of each class, so a repeat launch of an
 * unchanged pack skips that work.
 *
 * How it hooks in: Cleanroom's class loader (Foundation's ActualClassLoader.findClass) runs every class through
 * {@code TransformerHolder.runTransformersFunction} - a public, swappable field. We swap in our own function that runs
 * the same transformer list itself, split in two:
 * <ul>
 *   <li>CACHED PREFIX: every transformer before Mixin's {@code Proxy} (and before any warmClasses.liveTransformers
 *       entry). Its output is saved, keyed by class name + hash of the original bytes + fingerprint of the prefix's
 *       transformer list.</li>
 *   <li>LIVE SUFFIX: Mixin's Proxy and everything after it run on EVERY launch, on the (cached or fresh) prefix output.
 *       So Mixin sees exactly the bytes it would have seen, and all its bookkeeping - applied-mixin state, @Unique
 *       renames, synthetic Args/inner classes, nest-host fixups, accessor coprocessing, IMixinConfigPlugin
 *       pre/postApply hooks, late (MixinBooter) configs - happens as normal.</li>
 * </ul>
 * Never cached: classes with no bytes (generated at runtime - Mixin synthetic classes, code generators: input is null),
 * classes whose prefix output is null, warmClasses.skipClasses. Foundation's explicit transformers and transformer
 * exclusions are outside this function and untouched.
 *
 * Invalidation: the whole cache file is named after a key over the Zoomies + cache format version, the Java VM, the
 * JVM arguments, every class-path library (path/size/mtime), every jar/zip under mods/ (path/size/mtime) and the
 * content of every text config file under config/ (minus warmClasses.ignoreConfigs). Any change = new key = old file
 * deleted, and the log names what changed. Per class, a hit also needs the same original-bytes hash/length and the
 * same prefix fingerprint (transformers registered later in the launch give a different fingerprint, so a class is
 * only reused for the transformer set it was made with).
 *
 * File: zoomies-cache/warmclasses/classes-KEY.bin, append-only records [len][record][crc32]; a torn tail (game killed
 * mid-write) is ignored at the first bad record. Read through a memory map, so the cache costs page cache, not heap.
 *
 * KNOWN RISK: a prefix transformer that keeps state from seeing classes (rather than just rewriting them) won't see
 * cached classes. Put it in warmClasses.liveTransformers. warmClasses.verify finds output differences, not hidden state.
 */
public final class WarmClasses {
    private static final String FORMAT = "WC1";
    private static final int MAGIC = 0x57434C31;
    private static final String MIXIN_PROXY = "org.spongepowered.asm.mixin.transformer.Proxy";

    static final AtomicLong hits = new AtomicLong(), misses = new AtomicLong(), uncacheable = new AtomicLong(),
            mismatches = new AtomicLong(), liveNanos = new AtomicLong(), hitNanos = new AtomicLong(), written = new AtomicLong();
    private static volatile boolean installed, verify;
    private static List<?> transformers;
    private static MethodHandle holderSetter;
    private static Object holder;
    private static String[] live = new String[0], skip = new String[0];
    private static File dir, file;
    private static ByteBuffer map;
    private static final Map<String, long[]> index = new ConcurrentHashMap<>();   // tname|fp -> {outOffset, outLen, inHash, inLen}
    private static final Map<String, String> indexName = new ConcurrentHashMap<>(); // tname|fp -> untransformed name
    private static final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private static long maxBytes;
    private static final AtomicLong fileBytes = new AtomicLong();
    private static volatile Snapshot snap = new Snapshot(new Object[0], 0, 0);

    /** transformer list as last seen: [0, end) is the cached prefix, fp its fingerprint */
    static final class Snapshot {
        final Object[] arr; final int end; final long fp;
        Snapshot(Object[] arr, int end, long fp) { this.arr = arr; this.end = end; this.fp = fp; }
    }

    /** IClassTransformer.transform for any transformer, whichever class loader its interface came from */
    private static final ClassValue<MethodHandle> TRANSFORM = new ClassValue<MethodHandle>() {
        @Override protected MethodHandle computeValue(Class<?> c) {
            try {
                Class<?> iface = find(c);
                return MethodHandles.publicLookup().findVirtual(iface, "transform",
                        MethodType.methodType(byte[].class, String.class, String.class, byte[].class))
                        .asType(MethodType.methodType(byte[].class, Object.class, String.class, String.class, byte[].class));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        private Class<?> find(Class<?> c) {
            for (Class<?> k = c; k != null; k = k.getSuperclass())
                for (Class<?> i : k.getInterfaces()) {
                    if (i.getName().equals("net.minecraft.launchwrapper.IClassTransformer")) return i;
                    Class<?> deeper = find(i);
                    if (deeper != null) return deeper;
                }
            return null;
        }
    };

    private WarmClasses() {}

    // ------------------------------------------------------------------------------------------------ install

    /** from the coremod's injectData, after the config is read */
    static synchronized void install() {
        if (installed) return;
        try {
            if (!ZoomiesConfig.on("general.enabled") || !ZoomiesConfig.on("warmClasses.enabled")) return;
            if (Boolean.getBoolean("foundation.debug_transformer")) { log("off: foundation.debug_transformer is set (keeping its debug chain)"); return; }
            ClassLoader cl = WarmClasses.class.getClassLoader();
            Class<?> acl = Class.forName("top.outlands.foundation.boot.ActualClassLoader", false, cl);
            Class<?> th = Class.forName("top.outlands.foundation.boot.TransformerHolder", false, cl);
            Class<?> fn = Class.forName("top.outlands.foundation.function.TransformerFunction", false, cl);
            // No Class.getField/getMethod here: those resolve the type of EVERY field/method of the class, and
            // TransformerHolder.renameTransformer is an IClassNameTransformer - the app loader would then try to load
            // its own copy of a launchwrapper type that LaunchClassLoader already defined -> LinkageError (loader
            // constraint violation). Method handles resolve just the one named member.
            MethodHandles.Lookup lk = MethodHandles.publicLookup();
            holder = lk.findStatic(acl, "getTransformerHolder", MethodType.methodType(th)).invoke();
            holderSetter = lk.findSetter(th, "runTransformersFunction", fn);
            transformers = (List<?>) lk.findStaticGetter(th, "transformers", List.class).invoke();
            if (holder == null || transformers == null) { log("off: Foundation's transformer holder isn't set up"); return; }

            verify = ZoomiesConfig.on("warmClasses.verify");
            live = list(ZoomiesConfig.get("warmClasses.liveTransformers"));
            skip = list(ZoomiesConfig.get("warmClasses.skipClasses"));
            maxBytes = Math.max(0, ZoomiesConfig.num("warmClasses.maxSizeMB", 2048)) * 1024L * 1024L;

            // everything we touch while a class is being transformed must already be loaded (else we'd re-enter ourselves)
            Snapshot.class.getName(); TRANSFORM.getClass().getName();
            Class.forName(WarmClasses.class.getName() + "$Handler", true, cl);

            long t0 = System.nanoTime();
            openStore();
            log("key ready in " + ms(System.nanoTime() - t0) + " ms; " + index.size() + " saved classes" + (verify ? " (VERIFY mode)" : ""));

            Object mine = Proxy.newProxyInstance(fn.getClassLoader(), new Class<?>[]{fn}, new Handler());
            holderSetter.invoke(holder, mine);
            installed = true;
            startWriter();
        } catch (Throwable t) {
            log("off (couldn't hook Cleanroom's class loader - normal on non-Cleanroom Forge): " + t);
        }
    }

    /** the TransformerFunction we put into Foundation */
    static final class Handler implements InvocationHandler {
        @Override public Object invoke(Object proxy, Method m, Object[] a) throws Throwable {
            switch (m.getName()) {
                case "apply": return run((String) a[0], (String) a[1], (byte[]) a[2]);
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                case "toString": return "Zoomies WarmClasses";
                default: throw new UnsupportedOperationException(m.getName());
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ per class

    static byte[] run(String name, String tname, byte[] basic) throws Throwable {
        Snapshot s = snapshot();
        if (basic == null || name == null || tname == null || s.end == 0 || skipped(tname)) {
            uncacheable.incrementAndGet();
            return chain(s.arr, 0, s.arr.length, name, tname, basic);
        }
        long inHash = hash(basic);
        int inLen = basic.length;
        String key = tname + '|' + Long.toHexString(s.fp);
        byte[] prefixOut = null;
        long[] e = index.get(key);
        if (e != null && e[2] == inHash && e[3] == inLen && name.equals(indexName.get(key))) {
            long t = System.nanoTime();
            prefixOut = read(e);
            hitNanos.addAndGet(System.nanoTime() - t);
            if (prefixOut != null) hits.incrementAndGet();
        }
        if (prefixOut != null && (verify || hits.get() % 64 == 0)) {   // verify mode: every class; otherwise a 1-in-64 spot check
            byte[] fresh = chain(s.arr, 0, s.end, name, tname, basic.clone());
            if (!Arrays.equals(fresh, prefixOut)) {
                mismatches.incrementAndGet();
                log("MISMATCH " + tname + ": saved " + prefixOut.length + " bytes, fresh " + (fresh == null ? "null" : fresh.length + " bytes")
                        + " - using the fresh one");
                dumpMismatch(tname, prefixOut, fresh);
                if (fresh != null) save(s.fp, name, tname, inHash, inLen, fresh);
                prefixOut = fresh;
            }
        }
        if (prefixOut == null) {
            long t = System.nanoTime();
            prefixOut = chain(s.arr, 0, s.end, name, tname, basic);
            liveNanos.addAndGet(System.nanoTime() - t);
            misses.incrementAndGet();
            if (prefixOut != null) save(s.fp, name, tname, inHash, inLen, prefixOut);
        }
        return chain(s.arr, s.end, s.arr.length, name, tname, prefixOut);
    }

    /** the same loop as Foundation's own runTransformersFunction, over [from, to) */
    static byte[] chain(Object[] arr, int from, int to, String name, String tname, byte[] b) throws Throwable {
        for (int i = from; i < to; i++) b = (byte[]) TRANSFORM.get(arr[i].getClass()).invokeExact(arr[i], name, tname, b);
        return b;
    }

    /** current transformer list; the prefix fingerprint is only recomputed when the list changed */
    static Snapshot snapshot() {
        Object[] now = transformers.toArray();
        Snapshot s = snap;
        if (sameItems(now, s.arr)) return new Snapshot(now, s.end, s.fp);
        int end = 0;
        long fp = 0xcbf29ce484222325L;
        for (; end < now.length; end++) {
            String n = now[end].getClass().getName();
            if (n.equals(MIXIN_PROXY) || matches(n, live)) break;
            fp = fnv(fp, n);
            fp = fnv(fp, "\n");
        }
        Snapshot fresh = new Snapshot(now, end, fp);
        snap = fresh;
        return fresh;
    }

    static boolean sameItems(Object[] a, Object[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    static boolean matches(String transformer, String[] parts) {
        for (String p : parts) if (transformer.contains(p)) return true;
        return false;
    }

    static boolean skipped(String tname) {
        for (String p : skip) if (tname.startsWith(p)) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------------ store

    static void openStore() throws IOException {
        dir = new File("zoomies-cache", "warmclasses");
        dir.mkdirs();
        Map<String, String> manifest = new TreeMap<>();
        String key = key(manifest);
        file = new File(dir, "classes-" + key + ".bin");
        File[] old = dir.listFiles((d, n) -> n.startsWith("classes-") && n.endsWith(".bin") && !n.equals(file.getName()));
        if (old != null && old.length > 0) {
            log("pack changed since the classes were saved - starting a fresh cache. What changed: " + diff(manifest));
            for (File f : old) f.delete();
        }
        writeManifest(manifest);
        if (!file.isFile()) return;
        try (FileChannel ch = FileChannel.open(file.toPath())) {
            long size = ch.size();
            if (size > Integer.MAX_VALUE) { log("cache file over 2 GB, starting fresh"); file.delete(); return; }
            map = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
        }
        long good = loadIndex(map, index, indexName);
        fileBytes.set(good);
        if (good < map.capacity()) {
            log("cut " + (map.capacity() - good) + " bytes of an unfinished record (game closed while saving)");
            try (FileChannel ch = FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.WRITE)) { ch.truncate(good); }
        }
    }

    /** reads records until the first damaged one; returns where the good part ends */
    static long loadIndex(ByteBuffer buf, Map<String, long[]> idx, Map<String, String> names) {
        ByteBuffer b = buf.duplicate();
        int pos = 0;
        CRC32 crc = new CRC32();
        while (pos + 8 <= b.capacity()) {
            b.position(pos);
            int len = b.getInt();
            if (len <= 0 || pos + 4L + len + 8 > b.capacity()) break;
            byte[] rec = new byte[len];
            b.get(rec);
            long stored = b.getLong();
            crc.reset(); crc.update(rec, 0, len);
            if (crc.getValue() != stored) break;
            try {
                DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(rec));
                if (in.readInt() != MAGIC) break;
                String name = in.readUTF(), tname = in.readUTF();
                long fp = in.readLong(), inHash = in.readLong();
                int inLen = in.readInt(), outLen = in.readInt();
                int headerLen = len - outLen;   // out bytes are the tail of the record
                String key = tname + '|' + Long.toHexString(fp);
                idx.put(key, new long[]{pos + 4L + headerLen, outLen, inHash, inLen});
                names.put(key, name);
            } catch (IOException e) { break; }
            pos += 4 + len + 8;
        }
        return pos;
    }

    static byte[] read(long[] e) {
        ByteBuffer m = map;
        if (m == null || e[0] + e[1] > m.capacity()) return null;   // written this launch: not in the map, just a miss
        ByteBuffer b = m.duplicate();
        b.position((int) e[0]);
        byte[] out = new byte[(int) e[1]];
        b.get(out);
        return out;
    }

    static void save(long fp, String name, String tname, long inHash, int inLen, byte[] out) {
        try {
            byte[] rec = record(name, tname, fp, inHash, inLen, out);
            if (maxBytes > 0 && fileBytes.addAndGet(rec.length + 12L) > maxBytes) return;
            queue.offer(rec);
        } catch (IOException ignored) { }
    }

    static byte[] record(String name, String tname, long fp, long inHash, int inLen, byte[] out) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(out.length + 128);
        DataOutputStream d = new DataOutputStream(bo);
        d.writeInt(MAGIC); d.writeUTF(name); d.writeUTF(tname);
        d.writeLong(fp); d.writeLong(inHash); d.writeInt(inLen); d.writeInt(out.length);
        d.write(out);
        return bo.toByteArray();
    }

    static void writeRecord(OutputStream os, byte[] rec) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(rec, 0, rec.length);
        DataOutputStream d = new DataOutputStream(os);
        d.writeInt(rec.length); d.write(rec); d.writeLong(crc.getValue());
    }

    /** one daemon thread appends records; flushes whenever it catches up, so kill -9 loses at most a torn tail */
    static void startWriter() {
        Thread t = new Thread(() -> {
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file, true), 1 << 20)) {
                while (true) {
                    byte[] rec = queue.take();
                    writeRecord(os, rec);
                    written.incrementAndGet();
                    if (queue.isEmpty()) os.flush();
                }
            } catch (InterruptedException ignored) {
            } catch (Throwable e) {
                log("stopped saving classes: " + e);
            }
        }, "Zoomies WarmClasses writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    // ------------------------------------------------------------------------------------------------ key

    /** hex key over everything that can change what the transformers produce; fills manifest (thing -> value) for the change log */
    static String key(Map<String, String> m) {
        m.put("format", FORMAT + " zoomies " + Zoomies.class.getPackage().getImplementationVersion());
        m.put("java", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        try {                                                   // JVM tuning flags (-XX, -Xm, -Xlog) don't change class bytes: only -D properties count
            StringBuilder a = new StringBuilder();
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) if (arg.startsWith("-D")) a.append(arg).append(' ');
            m.put("jvmArgs", a.toString());
        } catch (Throwable ignored) { }
        for (String p : System.getProperty("java.class.path", "").split(File.pathSeparator)) if (!p.isEmpty()) stat(m, "classpath:", new File(p));
        walk(new File("mods"), f -> { String n = f.getName().toLowerCase(); if ((n.endsWith(".jar") || n.endsWith(".zip")) && f.getParentFile().getName().equals("mods")) stat(m, "", f); });   // top-level only: mods keep generated caches in sub-folders (Carpenter's Blocks rewrites a zip every launch)
        String[] ignore = list(ZoomiesConfig.get("warmClasses.ignoreConfigs"));
        // only configs of mods that patch code before Mixin (coremods) can change what we save; hundreds of other mods
        // rewrite their own config every launch, which threw the whole cache away each time (2026-10-04)
        java.util.Set<String> core = coremodTokens();
        walk(new File("config"), f -> {
            String p = f.getPath().replace('\\', '/');
            if (matches(p, ignore) || !textConfig(f.getName())) return;
            String lp = p.toLowerCase();
            boolean coreCfg = false;
            for (String t : core) if (lp.contains(t)) { coreCfg = true; break; }
            if (!coreCfg) return;
            if (f.length() > (4 << 20)) { stat(m, "", f); return; }
            try { m.put(p, Long.toHexString(hash(Files.readAllBytes(f.toPath())))); } catch (IOException e) { stat(m, "", f); }
        });
        long h = 0xcbf29ce484222325L;
        for (Map.Entry<String, String> e : m.entrySet()) { h = fnv(h, e.getKey()); h = fnv(h, "="); h = fnv(h, e.getValue()); h = fnv(h, "\n"); }
        return String.format("%016x", h);
    }

    /** short names of every coremod jar in mods/ (manifest FMLCorePlugin), e.g. "loliasm", "fixeroo", "quark" */
    static java.util.Set<String> coremodTokens() {
        java.util.Set<String> out = new java.util.TreeSet<>(Arrays.asList(list(ZoomiesConfig.get("warmClasses.keyConfigs"))));
        walk(new File("mods"), f -> {
            String n = f.getName().toLowerCase();
            if (!n.endsWith(".jar")) return;
            try (java.util.jar.JarFile j = new java.util.jar.JarFile(f)) {
                java.util.jar.Manifest mf = j.getManifest();
                if (mf == null || mf.getMainAttributes().getValue("FMLCorePlugin") == null) return;
            } catch (IOException e) { return; }
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^[a-z]+").matcher(n.replace("_", "").replace("-", ""));
            if (mt.find() && mt.group().length() >= 4) out.add(mt.group());
        });
        return out;
    }

    static boolean textConfig(String n) {
        n = n.toLowerCase();
        for (String x : new String[]{".cfg", ".toml", ".json", ".json5", ".properties", ".conf", ".txt", ".yml", ".yaml", ".ini", ".xml", ".zs"})
            if (n.endsWith(x)) return true;
        return false;
    }

    static void stat(Map<String, String> m, String prefix, File f) {
        m.put(prefix + f.getPath().replace('\\', '/'), f.length() + "@" + f.lastModified());
    }

    interface Visit { void file(File f); }

    static void walk(File d, Visit v) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) { if (f.isDirectory()) walk(f, v); else v.file(f); }
    }

    static void writeManifest(Map<String, String> m) {
        try (PrintWriter w = new PrintWriter(new File(dir, "key-inputs.txt"), "UTF-8")) {
            for (Map.Entry<String, String> e : m.entrySet()) w.println(e.getKey() + "\t" + e.getValue());
        } catch (IOException e) { log("couldn't write key-inputs.txt: " + e); }
    }

    /** what differs from the last launch's key-inputs.txt (first 15) */
    static String diff(Map<String, String> now) {
        Map<String, String> before = new TreeMap<>();
        try {
            for (String line : Files.readAllLines(new File(dir, "key-inputs.txt").toPath(), StandardCharsets.UTF_8)) {
                int t = line.indexOf('\t');
                if (t > 0) before.put(line.substring(0, t), line.substring(t + 1));
            }
        } catch (IOException e) { return "(no record of the last launch)"; }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : now.entrySet()) {
            String b = before.remove(e.getKey());
            if (b == null) out.add("+" + e.getKey()); else if (!b.equals(e.getValue())) out.add("~" + e.getKey());
        }
        for (String gone : before.keySet()) out.add("-" + gone);
        int n = out.size();
        return (n > 15 ? String.join(", ", out.subList(0, 15)) + " ... (" + n + " total)" : String.join(", ", out));
    }

    // ------------------------------------------------------------------------------------------------ report

    static void dumpMismatch(String tname, byte[] saved, byte[] fresh) {
        if (mismatches.get() > 50) return;
        try {
            File d = new File(dir, "mismatch");
            d.mkdirs();
            Files.write(new File(d, tname + ".saved.class").toPath(), saved);
            if (fresh != null) Files.write(new File(d, tname + ".fresh.class").toPath(), fresh);
        } catch (IOException ignored) { }
    }

    /** from ZoomiesMod at load complete */
    static void report() {
        if (!installed) return;
        long h = hits.get(), mi = misses.get();
        String avg = mi == 0 ? "" : String.format(" (%.2f ms each)", liveNanos.get() / 1e6 / mi);
        log(h + " classes reused, " + mi + " patched and saved, " + uncacheable.get() + " not cacheable (generated/skipped)"
                + "; patching the misses took " + ms(liveNanos.get()) + " ms" + avg + ", reading the reused ones " + ms(hitNanos.get()) + " ms"
                + (mi > 0 && h > 0 ? String.format("; ~%.0f s saved", h * (liveNanos.get() / 1e9 / mi)) : "")
                + "; " + (verify ? "VERIFY" : "spot check") + ": " + mismatches.get() + " MISMATCH"
                + "; " + written.get() + " written so far");
    }

    // ------------------------------------------------------------------------------------------------ small helpers

    static long hash(byte[] b) {
        CRC32 c = new CRC32();
        c.update(b, 0, b.length);
        return (c.getValue() << 32) ^ (Arrays.hashCode(b) & 0xffffffffL);
    }

    static long fnv(long h, String s) {
        for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x100000001b3L; }
        return h;
    }

    static String[] list(String csv) {
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out.toArray(new String[0]);
    }

    static long ms(long nanos) { return nanos / 1_000_000; }

    static void log(String s) { System.out.println("[Zoomies] Warm Classes: " + s); }

    /** test hook: record round-trip + torn tail (checks/WarmClassesCheck) */
    static List<String> selfTest(byte[] file) {
        Map<String, long[]> idx = new TreeMap<>();
        Map<String, String> names = new TreeMap<>();
        long end = loadIndex(ByteBuffer.wrap(file), idx, names);
        List<String> out = new ArrayList<>();
        out.add(String.valueOf(end));
        for (Map.Entry<String, long[]> e : idx.entrySet()) {
            long[] v = e.getValue();
            out.add(e.getKey() + "=" + names.get(e.getKey()) + ":" + new String(file, (int) v[0], (int) v[1], StandardCharsets.UTF_8));
        }
        return Collections.unmodifiableList(out);
    }

}
