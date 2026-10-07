package com.dogpound.zoomies;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/**
 * Two startup scans whose answer only changes when the mods or resource packs change, saved to zoomies-cache/boot/:
 *
 * 1. Immersive Vehicles "legacy light/tread compat" (MixinMtsLegacyModelCache): parses EVERY vehicle OBJ model
 *    (7,336 models, 1.3 GB of text, ~15 s) only to find objects named with '&' (lights) or "roller" (treads).
 *    Saved: just those objects per model (most models: none). Key = mod list (jar sizes + dates).
 * 2. VintageFix texture discovery (MixinVintageFixTextureCache): lists every texture in every jar and resource pack
 *    and walks every blockstate/model JSON (~12 s). Saved: the resulting set. Key = mod list + every active resource
 *    pack (file size + date) + resources/ folders + the registered block/item model names.
 *
 * Files are written whole (temp file + rename), so a killed game never leaves a half file. Unreadable = rebuilt.
 * No Immersive Vehicles / VintageFix types here, so this class loads fine without those mods.
 */
public final class BootCaches {
    static final File DIR = new File("zoomies-cache", "boot");
    private static final int MTS_MAGIC = 0x4D545331, VF_MAGIC = 0x56465431;   // "MTS1", "VFT1"

    // ===================== Immersive Vehicles: model objects the legacy compat looks at =====================

    /** the matching objects of one model, as RenderableVertices(name, vertices, cacheVertices) needs them */
    public static final class Model {
        public final String[] names;
        public final boolean[] cacheVertices;
        public final float[][] vertices;
        final long parseNanos;

        public Model(String[] names, boolean[] cacheVertices, float[][] vertices, long parseNanos) {
            this.names = names; this.cacheVertices = cacheVertices; this.vertices = vertices; this.parseNanos = parseNanos;
        }
    }

    private static Map<String, Model> mts;
    private static final Set<String> mtsSeen = new HashSet<>();
    private static String mtsKey;
    private static boolean mtsDirty;
    private static int mtsHits, mtsMisses;
    private static long mtsSavedNanos, mtsMissNanos;

    /** does the legacy compat care about this object? (same tests as LegacyCompatSystem.performModelLegacyCompats) */
    public static boolean mtsWanted(String name) {
        return name.contains("&") || name.toLowerCase(java.util.Locale.ROOT).contains("roller");
    }

    public static synchronized Model mtsGet(String location) {
        mtsLoad();
        Model m = mts.get(location);
        if (m != null && mtsSeen.add(location)) { mtsHits++; mtsSavedNanos += m.parseNanos; }
        return m;
    }

    public static synchronized void mtsPut(String location, Model m) {
        mtsLoad();
        mts.put(location, m);
        mtsSeen.add(location);
        mtsMisses++;
        mtsMissNanos += m.parseNanos;
        if (mtsKey != null) mtsDirty = true;
    }

    private static void mtsLoad() {
        if (mts != null) return;
        mts = new HashMap<>();
        mtsKey = WarmCaches.modListKey();
        if (mtsKey == null) return;
        File f = new File(DIR, "mts-models-" + mtsKey + ".bin");
        if (!f.isFile()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f), 1 << 16)))) {
            if (in.readInt() != MTS_MAGIC) throw new IOException("bad header");
            for (int i = in.readInt(); i > 0; i--) {
                String loc = in.readUTF();
                long nanos = in.readLong();
                int n = in.readInt();
                String[] names = new String[n];
                boolean[] cv = new boolean[n];
                float[][] v = new float[n][];
                for (int j = 0; j < n; j++) {
                    names[j] = in.readUTF();
                    cv[j] = in.readBoolean();
                    v[j] = new float[in.readInt()];
                    for (int k = 0; k < v[j].length; k++) v[j][k] = in.readFloat();
                }
                mts.put(loc, new Model(names, cv, v, nanos));
            }
        } catch (Exception e) {
            mts.clear();
            System.out.println("[Zoomies] Immersive Vehicles model cache unreadable, rebuilding it: " + e);
        }
    }

    private static void mtsSave() throws IOException {
        File f = new File(DIR, "mts-models-" + mtsKey + ".bin");
        write(f, "mts-models-", out -> {
            out.writeInt(MTS_MAGIC);
            out.writeInt(mts.size());
            for (Map.Entry<String, Model> e : mts.entrySet()) {
                Model m = e.getValue();
                out.writeUTF(e.getKey());
                out.writeLong(m.parseNanos);
                out.writeInt(m.names.length);
                for (int j = 0; j < m.names.length; j++) {
                    out.writeUTF(m.names[j]);
                    out.writeBoolean(m.cacheVertices[j]);
                    out.writeInt(m.vertices[j].length);
                    for (float x : m.vertices[j]) out.writeFloat(x);
                }
            }
        });
    }

    /** load complete: log what the cache did and save new entries */
    static synchronized void report() {
        if (mts == null || (mtsHits == 0 && mtsMisses == 0)) return;
        System.out.println("[Zoomies] Immersive Vehicles model compat: " + mtsHits + " models from cache (saved ~"
                + mtsSavedNanos / 1_000_000 + " ms), " + mtsMisses + " parsed (" + mtsMissNanos / 1_000_000 + " ms)");
        if (!mtsDirty) return;
        try {
            mtsSave();
            mtsDirty = false;
        } catch (Exception e) {
            System.out.println("[Zoomies] couldn't save the Immersive Vehicles model cache: " + e);
        }
    }

    // ===================== VintageFix: the set of texture names it discovers =====================

    /** cache key for one texture discovery, or null when something can't be fingerprinted (= no caching) */
    public static String vfKey(List<IResourcePack> packs, File gameDir, long modelNamesHash, String vintageFixVersion) {
        try {
            String mods = WarmCaches.modListKey();
            if (mods == null) return null;
            StringBuilder sb = new StringBuilder(mods).append('\n').append(vintageFixVersion).append('\n').append(modelNamesHash).append('\n');
            for (IResourcePack p : packs) {
                IResourcePack inner = p instanceof LegacyV2Adapter
                        ? ObfuscationReflectionHelper.getPrivateValue(LegacyV2Adapter.class, (LegacyV2Adapter) p, "field_191383_a") : p;
                sb.append(inner.getClass().getName()).append('|').append(p.getPackName()).append('|');
                if (inner instanceof AbstractResourcePack)
                    stat(sb, ObfuscationReflectionHelper.getPrivateValue(AbstractResourcePack.class, (AbstractResourcePack) inner, "field_110597_b"));
                sb.append('\n');
            }
            for (String d : new String[]{"resources", "oresources"}) stat(sb.append(d).append('|'), new File(gameDir, d));
            explainChange(new File(DIR, "vf-key-inputs.txt"), sb.toString());
            return WarmCaches.sha1(sb.toString());
        } catch (Throwable e) {
            System.out.println("[Zoomies] VintageFix texture cache off this time (can't fingerprint the packs: " + e + ")");
            return null;
        }
    }

    /** log which key lines changed since last launch (so a cache that keeps missing says why), then remember these */
    private static void explainChange(File f, String now) {
        try {
            if (f.isFile()) {
                java.util.Set<String> old = new java.util.HashSet<>(Files.readAllLines(f.toPath()));
                StringBuilder diff = new StringBuilder();
                for (String l : now.split("\n")) if (!old.contains(l) && diff.length() < 600) diff.append(l, 0, Math.min(140, l.length())).append(" ; ");
                if (diff.length() > 0) System.out.println("[Zoomies] VintageFix texture list: changed since last launch: " + diff);
            }
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), now.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    /** file: size + date; folder: file count + total size + newest date */
    private static void stat(StringBuilder sb, File f) throws IOException {
        if (f == null) { sb.append("null"); return; }
        if (f.isFile()) {
            sb.append(f.length());
            // generated packs in a mod's own sub-folder (Carpenter's Blocks rewrites its CachedResources.zip every launch
            // with the same content): size only, or the texture list was rebuilt every launch (2026-10-05)
            File parent = f.getParentFile();
            if (parent == null || parent.getParentFile() == null || !parent.getParentFile().getName().equals("mods")) sb.append(':').append(f.lastModified());
            return;
        }
        if (!f.isDirectory()) { sb.append("none"); return; }
        long[] s = new long[3];
        try (Stream<java.nio.file.Path> w = Files.walk(f.toPath())) {
            w.forEach(p -> { File x = p.toFile(); s[0]++; s[1] += x.length(); s[2] = Math.max(s[2], x.lastModified()); });
        }
        sb.append(s[0]).append(':').append(s[1]).append(':').append(s[2]);
    }

    /** the saved set for this key, or null */
    public static Set<ResourceLocation> vfLoad(String key) {
        File f = new File(DIR, "vf-textures-" + key + ".bin");
        if (!f.isFile()) return null;
        long t = System.nanoTime();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f), 1 << 16)))) {
            if (in.readInt() != VF_MAGIC) throw new IOException("bad header");
            long savedNanos = in.readLong();
            int n = in.readInt();
            Set<ResourceLocation> set = new HashSet<>(n * 4 / 3 + 16);
            String ns = null;
            for (int i = 0; i < n; i++) {
                String s = in.readUTF();
                if (!s.isEmpty()) ns = s;                    // namespace written only when it changes
                set.add(new ResourceLocation(ns, in.readUTF()));
            }
            long took = System.nanoTime() - t;
            System.out.println("[Zoomies] VintageFix texture list from cache: " + n + " sprites in " + took / 1_000_000
                    + " ms, saved ~" + (savedNanos - took) / 1_000_000 + " ms");
            return set;
        } catch (Exception e) {
            System.out.println("[Zoomies] VintageFix texture cache unreadable, rebuilding it: " + e);
            return null;
        }
    }

    public static void vfSave(String key, Set<ResourceLocation> set, long nanos) {
        try {
            ResourceLocation[] all = set.toArray(new ResourceLocation[0]);
            java.util.Arrays.sort(all);                       // groups each namespace together
            write(new File(DIR, "vf-textures-" + key + ".bin"), "vf-textures-", out -> {
                out.writeInt(VF_MAGIC);
                out.writeLong(nanos);
                out.writeInt(all.length);
                String last = null;
                for (ResourceLocation r : all) {
                    String ns = r.getResourceDomain();
                    out.writeUTF(ns.equals(last) ? "" : ns);
                    out.writeUTF(r.getResourcePath());
                    last = ns;
                }
            });
            System.out.println("[Zoomies] VintageFix texture list saved (" + all.length + " sprites, search took " + nanos / 1_000_000 + " ms)");
        } catch (Exception e) {
            System.out.println("[Zoomies] couldn't save the VintageFix texture cache: " + e);
        }
    }

    // ===================== shared =====================

    interface Body { void write(DataOutputStream out) throws IOException; }

    /** write f whole via a temp file, then delete older files with the same prefix (other keys) */
    private static void write(File f, String prefix, Body body) throws IOException {
        DIR.mkdirs();
        File tmp = new File(DIR, f.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp), 1 << 16)))) {
            body.write(out);
        }
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        File[] old = DIR.listFiles((d, n) -> n.startsWith(prefix) && !n.equals(f.getName()));
        if (old != null) for (File o : old) o.delete();
    }

    private BootCaches() {}
}
