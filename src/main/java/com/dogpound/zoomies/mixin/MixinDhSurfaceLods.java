package com.dogpound.zoomies.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons' 1.12 port has no "surface" generator: for every far LOD chunk it force-generates that chunk and its
 * 8 neighbours for real (every mod decorates them, they're saved to disk). In her ~770-mod pack that is ~1-7 chunks/s,
 * so after 10 minutes the LODs barely reach past the normal view distance (2026-10-04).
 * <p>
 * Never-generated chunks farther than {@code dh.fullDetailRadius} from every player now get terrain only: the world's
 * own chunk generator shapes the land (biomes, caves, surface blocks) into a throwaway chunk that never enters the world
 * or the save. No trees or structures in those LODs until the real chunks load near you — then DH replaces them.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.worldGeneration.DhInternalServerGenerator", remap = false)
public abstract class MixinDhSurfaceLods {
    private static volatile boolean zoomies$broken;

    private static java.lang.reflect.Constructor<?> zoomies$dhPos;
    private static java.lang.reflect.Method zoomies$acquire;

    /**
     * Take DH's own reference on the centre chunk, as its normal path does: its release step then also takes the chunk
     * off the "ignore chunk updates" list, so the real chunk (trees, structures) replaces this LOD when you get there.
     */
    private boolean zoomies$ref(ChunkPos pos) {
        try {
            if (zoomies$acquire == null) {
                Class<?> c = Class.forName("com.seibel.distanthorizons.core.pos.DhChunkPos");
                zoomies$dhPos = c.getConstructor(int.class, int.class);
                java.lang.reflect.Method m = getClass().getDeclaredMethod("acquireChunkRef", c);
                m.setAccessible(true);
                zoomies$acquire = m;
            }
            zoomies$acquire.invoke(this, zoomies$dhPos.newInstance(pos.x, pos.z));
            return true;
        } catch (Throwable t) {
            zoomies$broken = true;
            System.out.println("[Zoomies] DH surface LODs off for this session (DH changed?): " + t);
            return false;
        }
    }

    @Inject(method = "lambda$requestChunkFromServerAsync$0", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$surface(WorldServer world, ChunkPos pos, CallbackInfoReturnable<Chunk> cir) {
        if (zoomies$broken || !com.dogpound.zoomies.ZoomiesConfig.on("dh.surfaceLods")) return;
        ChunkProviderServer provider = world.getChunkProvider();
        Chunk loaded = provider.getLoadedChunk(pos.x, pos.z);
        if (loaded != null) { if (zoomies$ref(pos)) cir.setReturnValue(loaded); return; } // already real: free
        if (provider.isChunkGeneratedAt(pos.x, pos.z)) return;            // on disk: let DH load the real one
        int full = com.dogpound.zoomies.ZoomiesConfig.num("dh.fullDetailRadius", 0);
        if (full > 0) {
            for (EntityPlayer p : world.playerEntities) {
                int dx = (((int) Math.floor(p.posX)) >> 4) - pos.x, dz = (((int) Math.floor(p.posZ)) >> 4) - pos.z;
                if (dx * dx + dz * dz <= full * full) return;              // close by: real chunk with trees/structures
            }
        }
        try {
            Chunk c = provider.chunkGenerator.generateChunk(pos.x, pos.z);
            if (c == null) return;
            c.generateSkylightMap();
            if (zoomies$ref(pos)) cir.setReturnValue(c);
        } catch (Throwable t) {
            zoomies$broken = true; // one generator that can't do this -> back to DH's normal path for the session
            System.out.println("[Zoomies] DH surface LODs off for this session: " + t);
        }
    }

    // ---- 2026-10-05: terrain-only LODs made on DH's own worker thread (com.dogpound.zoomies.DhOffThread) - the server
    //      thread never sees these chunks: no request task, no release task.
    private final java.util.Set<Long> zoomies$offThread = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static java.lang.reflect.Field zoomies$params, zoomies$level;

    private WorldServer zoomies$world() throws Exception {
        if (zoomies$level == null) {
            java.lang.reflect.Field f = getClass().getDeclaredField("params");
            f.setAccessible(true);
            java.lang.reflect.Field l = f.getType().getField("mcServerLevel");
            zoomies$params = f; zoomies$level = l;
        }
        return (WorldServer) zoomies$level.get(zoomies$params.get(this));
    }

    @Inject(method = "requestChunkFromServerAsync", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$offThreadRequest(ChunkPos pos, CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Chunk>> cir) {
        if (zoomies$broken || !com.dogpound.zoomies.ZoomiesConfig.on("dh.surfaceLods") || !com.dogpound.zoomies.DhOffThread.enabled()) return;
        try {
            WorldServer world = zoomies$world();
            ChunkProviderServer provider = world.getChunkProvider();
            Chunk loaded = provider.getLoadedChunk(pos.x, pos.z);
            if (loaded != null) {                                    // already in memory: DH reads it as is (its own path hands loaded chunks to this thread too)
                zoomies$offThread.add(ChunkPos.asLong(pos.x, pos.z));
                cir.setReturnValue(java.util.concurrent.CompletableFuture.completedFuture(loaded));
                return;
            }
            if (provider.isChunkGeneratedAt(pos.x, pos.z)) {          // saved on disk: read it here, not on the server
                Chunk saved = com.dogpound.zoomies.DhOffThread.readSaved(world, pos.x, pos.z);
                if (saved == null) return;
                zoomies$offThread.add(ChunkPos.asLong(pos.x, pos.z));
                cir.setReturnValue(java.util.concurrent.CompletableFuture.completedFuture(saved));
                return;
            }
            int full = com.dogpound.zoomies.ZoomiesConfig.num("dh.fullDetailRadius", 0);
            if (full > 0) for (EntityPlayer p : new java.util.ArrayList<EntityPlayer>(world.playerEntities)) {
                int dx = (((int) Math.floor(p.posX)) >> 4) - pos.x, dz = (((int) Math.floor(p.posZ)) >> 4) - pos.z;
                if (dx * dx + dz * dz <= full * full) return;
            }
            Chunk c = com.dogpound.zoomies.DhOffThread.generate(world, pos.x, pos.z);
            if (c == null) return;
            zoomies$offThread.add(ChunkPos.asLong(pos.x, pos.z));
            cir.setReturnValue(java.util.concurrent.CompletableFuture.completedFuture(c));
        } catch (Throwable t) {
            // a read of the server's chunk map raced it, or DH changed: just use DH's own path for this chunk
        }
    }

    @Inject(method = "releaseChunkFromServerAsync", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$offThreadRelease(WorldServer level, ChunkPos pos, CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> cir) {
        if (zoomies$offThread.remove(ChunkPos.asLong(pos.x, pos.z))) cir.setReturnValue(java.util.concurrent.CompletableFuture.completedFuture(null));
    }
}
