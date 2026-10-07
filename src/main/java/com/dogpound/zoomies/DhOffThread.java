package com.dogpound.zoomies;

import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorOverworld;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distant Horizons far LODs built on DH's own worker threads instead of the server thread (requested feature).
 * <p>
 * Measured 10-05: the big pack's server thread is 100% busy with ~770 mods, DH got ~12% of it and each terrain-only LOD
 * chunk cost ~40 ms -> 2-4 chunks/s, 74,800 queued (~10 h); DH's 8 worker threads sat idle the whole time.
 * <p>
 * Each worker gets its own BiomeProvider (own GenLayers + BiomeCache) and its own ChunkGeneratorOverworld with structures
 * off; World.getBiomeProvider() answers with the worker's provider while it generates (MixinWorldThreadBiomes) and
 * IntCache is per thread (MixinIntCacheThreadLocal), so nothing the server thread uses is shared. The chunk is a
 * throwaway: it never enters the world or the save. Only worlds whose generator and biome provider are exactly vanilla's
 * are done this way; everything else keeps the server-thread path.
 */
public final class DhOffThread {
    private DhOffThread() {}

    /** > 0 while any worker is inside generate(): the World hook only looks at its ThreadLocal then */
    public static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final ThreadLocal<BiomeProvider> PROVIDER = new ThreadLocal<BiomeProvider>();
    private static final ThreadLocal<Map<WorldServer, Object[]>> GENS = ThreadLocal.withInitial(WeakHashMap::new);
    private static final AtomicInteger FAILS = new AtomicInteger();
    private static volatile boolean off;
    private static final java.util.Set<String> SAID = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final AtomicInteger MADE = new AtomicInteger();

    /** the biome provider this thread should see, or null for the world's own */
    public static BiomeProvider provider() { return PROVIDER.get(); }

    public static boolean enabled() { return !off && ZoomiesConfig.on("dh.offThreadLods"); }

    /** a terrain-only chunk made on this thread; null = can't here (caller uses DH's normal path) */
    public static Chunk generate(WorldServer w, int x, int z) {
        Object real = w.getChunkProvider().chunkGenerator;
        if (real != null && OTG_GEN.equals(real.getClass().getName())) return generateOtg(w, real, x, z);
        Map<WorldServer, Object[]> m = GENS.get();
        Object[] g = m.get(w);
        if (g == null) {
            String why = unsupported(w);
            if (why != null) { if (SAID.add(w.provider.getDimension() + why)) System.out.println("[Zoomies] DH off-thread LODs not used in dim " + w.provider.getDimension() + ": " + why); m.put(w, g = new Object[0]); return null; }
            BiomeProvider bp = new BiomeProvider(w.getWorldInfo());
            PROVIDER.set(bp); ACTIVE.incrementAndGet();
            try { g = new Object[]{ bp, new ChunkGeneratorOverworld(w, w.getSeed(), false, w.getWorldInfo().getGeneratorOptions()) }; }
            catch (Throwable t) { fail(t); g = new Object[0]; }
            finally { PROVIDER.remove(); ACTIVE.decrementAndGet(); }
            m.put(w, g);
            if (g.length > 0 && SAID.add("on" + w.provider.getDimension())) System.out.println("[Zoomies] DH off-thread LODs ON for dim " + w.provider.getDimension());
        }
        if (g.length == 0) return null;
        PROVIDER.set((BiomeProvider) g[0]); ACTIVE.incrementAndGet();
        try {
            Chunk c = ((ChunkGeneratorOverworld) g[1]).generateChunk(x, z);
            if (c == null) return null;
            c.generateSkylightMap();
            MADE.incrementAndGet();
            return c;
        } catch (Throwable t) {
            fail(t);
            m.put(w, new Object[0]);   // this thread's generator may be in a bad state: drop it, rebuild never (cheap fallback)
            return null;
        } finally { PROVIDER.remove(); ACTIVE.decrementAndGet(); }
    }

    // ================================================================== OTG (her Dregora worlds)
    // OTG's terrain builder (ChunkProviderOTG: noise, caves, ravines) is per instance -> one per worker thread. Its biome
    // generator is NOT thread-safe (layers keep seeds in fields, the cache is a plain LinkedHashMap) and the FromImage one
    // holds the 10000x10000 Dregora map (~400 MB), so it stays shared and every call into it takes OTG_BIOMES
    // (MixinOtgBiomeLock - the server thread takes it too). Default structures and modded caves touch the real world:
    // skipped on workers (MixinOtgWorldOffThread). The chunk is a throwaway, like the vanilla path.
    public static final String OTG_GEN = "com.pg85.otg.forge.generator.OTGChunkGenerator";
    public static final java.util.concurrent.locks.ReentrantLock OTG_BIOMES = new java.util.concurrent.locks.ReentrantLock();
    private static final ThreadLocal<Boolean> OTG_WORKER = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Map<WorldServer, Object[]>> OTG_GENS = ThreadLocal.withInitial(WeakHashMap::new);
    private static volatile Object[] otgReflect;   // field world, getConfigs, ChunkProviderOTG ctor, generate, buffer ctor, toChunk, fromChunkCoords, getBiomeGenerator, getBiomes, DEFAULT_FOR_WORLD, getBiomeByOTGIdOrNull, getIds, getSavedId

    private static volatile java.lang.reflect.Constructor<?> otgCacheNew;

    /** a fresh OTG ArraysCache, marked in use (MixinOtgArraysCache) */
    public static Object spareOtgCache() {
        try {
            if (otgCacheNew == null) {
                java.lang.reflect.Constructor<?> c = Class.forName("com.pg85.otg.generator.biome.ArraysCache").getDeclaredConstructor();
                c.setAccessible(true);
                otgCacheNew = c;
            }
            Object o = otgCacheNew.newInstance();
            java.lang.reflect.Field f = o.getClass().getDeclaredField("isFree");
            f.setAccessible(true);
            f.setBoolean(o, false);
            return o;
        } catch (Throwable t) { return null; }
    }

    /** true on a DH worker while it builds an OTG LOD */
    public static boolean otgWorker() { return OTG_WORKER.get() != null; }

    /** biome generator calls: the server thread waits for its turn; a worker gives up after 2 s (then the LOD falls back) */
    public static void otgBiomesEnter() {
        if (OTG_WORKER.get() == null) { OTG_BIOMES.lock(); return; }
        try { if (!OTG_BIOMES.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("OTG biome lock busy"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    public static void otgBiomesExit() { if (OTG_BIOMES.isHeldByCurrentThread()) OTG_BIOMES.unlock(); }

    private static Object[] otgReflect(ClassLoader cl) throws Exception {
        Object[] r = otgReflect;
        if (r != null) return r;
        Class<?> gen = Class.forName(OTG_GEN, false, cl), fw = Class.forName("com.pg85.otg.forge.world.ForgeWorld", false, cl),
                cpo = Class.forName("com.pg85.otg.generator.ChunkProviderOTG", false, cl), buf = Class.forName("com.pg85.otg.forge.generator.ForgeChunkBuffer", false, cl),
                cbuf = Class.forName("com.pg85.otg.generator.ChunkBuffer", false, cl), cc = Class.forName("com.pg85.otg.util.ChunkCoordinate", false, cl),
                cfg = Class.forName("com.pg85.otg.network.ConfigProvider", false, cl), lw = Class.forName("com.pg85.otg.common.LocalWorld", false, cl),
                bg = Class.forName("com.pg85.otg.generator.biome.BiomeGenerator", false, cl), ot = Class.forName("com.pg85.otg.generator.biome.OutputType", false, cl);
        java.lang.reflect.Field world = gen.getDeclaredField("world"); world.setAccessible(true);
        java.lang.reflect.Constructor<?> cpoNew = cpo.getConstructor(cfg, lw);
        java.lang.reflect.Constructor<?> bufNew = buf.getDeclaredConstructor(cc); bufNew.setAccessible(true);
        java.lang.reflect.Method toChunk = buf.getDeclaredMethod("toChunk", net.minecraft.world.World.class); toChunk.setAccessible(true);
        java.lang.reflect.Method getBiomeBy = fw.getMethod("getBiomeByOTGIdOrNull", int.class);
        java.lang.reflect.Method getIds = getBiomeBy.getReturnType().getMethod("getIds");
        r = new Object[]{ world, fw.getMethod("getConfigs"), cpoNew, cpo.getMethod("generate", cbuf), bufNew, toChunk,
                cc.getMethod("fromChunkCoords", int.class, int.class), fw.getMethod("getBiomeGenerator"),
                bg.getMethod("getBiomes", int[].class, int.class, int.class, int.class, int.class, ot), ot.getField("DEFAULT_FOR_WORLD").get(null),
                getBiomeBy, getIds, getIds.getReturnType().getMethod("getSavedId") };
        otgReflect = r;
        return r;
    }

    private static Chunk generateOtg(WorldServer w, Object real, int x, int z) {
        Map<WorldServer, Object[]> m = OTG_GENS.get();
        Object[] g = m.get(w);
        OTG_WORKER.set(Boolean.TRUE);
        try {
            Object[] r = otgReflect(real.getClass().getClassLoader());
            if (g == null) {
                Object forgeWorld = ((java.lang.reflect.Field) r[0]).get(real);
                Object configs = ((java.lang.reflect.Method) r[1]).invoke(forgeWorld);
                g = new Object[]{ forgeWorld, ((java.lang.reflect.Constructor<?>) r[2]).newInstance(configs, forgeWorld) };
                m.put(w, g);
                if (SAID.add("otg" + w.provider.getDimension())) System.out.println("[Zoomies] DH off-thread LODs ON for OTG dim " + w.provider.getDimension());
            }
            if (g.length == 0) return null;
            Object buffer = ((java.lang.reflect.Constructor<?>) r[4]).newInstance(((java.lang.reflect.Method) r[6]).invoke(null, x, z));
            ((java.lang.reflect.Method) r[3]).invoke(g[1], buffer);
            Chunk c = (Chunk) ((java.lang.reflect.Method) r[5]).invoke(buffer, w);
            if (c == null) return null;
            int[] ids = (int[]) ((java.lang.reflect.Method) r[8]).invoke(((java.lang.reflect.Method) r[7]).invoke(g[0]), null, x * 16, z * 16, 16, 16, r[9]);
            byte[] biomes = c.getBiomeArray();
            for (int i = 0; i < biomes.length && i < ids.length; i++) {
                Object biome = ((java.lang.reflect.Method) r[10]).invoke(g[0], ids[i]);
                if (biome != null) biomes[i] = (byte) (int) (Integer) ((java.lang.reflect.Method) r[12]).invoke(((java.lang.reflect.Method) r[11]).invoke(biome));
            }
            c.generateSkylightMap();
            MADE.incrementAndGet();
            return c;
        } catch (Throwable t) {
            fail(t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t);
            m.put(w, new Object[0]);
            return null;
        } finally {
            while (OTG_BIOMES.isHeldByCurrentThread()) OTG_BIOMES.unlock();   // a throw inside OTG must never keep the server waiting
            OTG_WORKER.remove();
        }
    }

    private static volatile java.lang.reflect.Method readNbt;
    private static volatile boolean readOff;
    public static final AtomicInteger READ = new AtomicInteger();

    /**
     * A chunk that is saved on disk but not loaded, read straight from its region file on this thread (blocks, light,
     * biomes; no entities / tile entities) - the server never loads it, so no neighbours get loaded or generated either.
     * null = can't (caller uses DH's normal path).
     */
    public static Chunk readSaved(WorldServer w, int x, int z) {
        if (readOff) return null;
        try {
            if (!(w.getChunkProvider().chunkLoader instanceof net.minecraft.world.chunk.storage.AnvilChunkLoader)) return null;
            net.minecraft.world.chunk.storage.AnvilChunkLoader loader = (net.minecraft.world.chunk.storage.AnvilChunkLoader) w.getChunkProvider().chunkLoader;
            java.io.DataInputStream in = net.minecraft.world.chunk.storage.RegionFileCache.getChunkInputStream(loader.chunkSaveLocation, x, z);
            if (in == null) return null;
            net.minecraft.nbt.NBTTagCompound tag;
            try { tag = net.minecraft.nbt.CompressedStreamTools.read(in); } finally { in.close(); }
            if (tag == null || !tag.hasKey("Level", 10)) return null;
            net.minecraft.nbt.NBTTagCompound level = tag.getCompoundTag("Level");
            if (level.getInteger("xPos") != x || level.getInteger("zPos") != z || !level.hasKey("Sections", 9)) return null;
            if (readNbt == null) {
                java.lang.reflect.Method m;
                try { m = net.minecraft.world.chunk.storage.AnvilChunkLoader.class.getDeclaredMethod("func_75823_a", net.minecraft.world.World.class, net.minecraft.nbt.NBTTagCompound.class); }
                catch (NoSuchMethodException e) { m = net.minecraft.world.chunk.storage.AnvilChunkLoader.class.getDeclaredMethod("readChunkFromNBT", net.minecraft.world.World.class, net.minecraft.nbt.NBTTagCompound.class); }
                m.setAccessible(true);
                readNbt = m;
            }
            Chunk c = (Chunk) readNbt.invoke(loader, w, level);
            if (c != null) READ.incrementAndGet();
            return c;
        } catch (Throwable t) {
            int n = FAILS.incrementAndGet();
            if (n <= 3) { System.out.println("[Zoomies] DH off-thread read of a saved chunk failed (falls back to the server thread): " + t); t.printStackTrace(); }
            if (n >= 20) { readOff = true; System.out.println("[Zoomies] DH off-thread saved-chunk reads OFF for this session"); }
            return null;
        }
    }

    private static String unsupported(WorldServer w) {
        Object gen = w.getChunkProvider().chunkGenerator;
        if (gen == null || gen.getClass() != ChunkGeneratorOverworld.class) return "generator " + (gen == null ? "none" : gen.getClass().getName());
        if (w.getBiomeProvider().getClass() != BiomeProvider.class) return "biome provider " + w.getBiomeProvider().getClass().getName();
        return null;
    }

    private static void fail(Throwable t) {
        int n = FAILS.incrementAndGet();
        if (n <= 3) { System.out.println("[Zoomies] DH off-thread LOD failed (falls back to the server thread): " + t); t.printStackTrace(); }
        if (n >= 20) { off = true; System.out.println("[Zoomies] DH off-thread LODs OFF for this session after " + n + " failures"); }
    }
}
