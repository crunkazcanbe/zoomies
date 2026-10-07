package com.dogpound.zoomies;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * "Player first" for Distant Horizons (MixinDhPlayerFirst). In 1.12 DH builds far LODs by generating REAL chunks on the
 * server thread, and one such chunk can take a second in the pack - the same thread that must generate the land you're
 * flying into. So DH gets no new chunk work while any player still has chunks in view waiting to be loaded/generated
 * (PlayerChunkMap.entriesWithoutChunks), or is moving fast (flying), and for dh.playerFirstHoldMs after that.
 * Server thread only (DH asks once per tick, only when it has work queued).
 */
public final class DhPlayerFirst {
    private static Field waitingField;
    private static boolean broken;
    private static final Map<Integer, double[]> lastPos = new HashMap<>(); // entityId -> {x, z, nanos}
    private static long holdUntil, streakTicks, logAt;
    private static int pausedTicks, allowedTicks, lastWaiting;

    private DhPlayerFirst() { }

    /** true = DH must not start new chunk work this tick */
    public static boolean brake() {
        if (broken) return false;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return false;
        long now = System.nanoTime();
        int waiting = 0;
        boolean fast = false;
        try {
            if (waitingField == null) waitingField = ReflectionHelper.findField(PlayerChunkMap.class, "entriesWithoutChunks", "field_187311_h");
            double minSpeed = ZoomiesConfig.num("dh.playerFirstSpeed", 8);
            for (WorldServer w : server.worlds) {
                if (w == null || w.playerEntities.isEmpty()) continue;
                waiting += ((List<?>) waitingField.get(w.getPlayerChunkMap())).size();
                for (EntityPlayer p : w.playerEntities) fast |= minSpeed > 0 && movedFast(p, now, minSpeed);
            }
        } catch (Throwable t) {
            broken = true; // MC internals not as expected -> DH back to its own rules for this session
            System.out.println("[Zoomies] DH player-first off for this session: " + t);
            return false;
        }
        if (waiting > 0 || fast) holdUntil = now + ZoomiesConfig.num("dh.playerFirstHoldMs", 2000) * 1_000_000L;
        boolean brake = now < holdUntil;
        // never starve DH completely (e.g. a spectator's chunks that are never generated): after 20 s of pausing,
        // let one tick per second through
        streakTicks = brake ? streakTicks + 1 : 0;
        if (brake && streakTicks > 400 && streakTicks % 20 == 0) brake = false;
        if (brake) { pausedTicks++; lastWaiting = waiting; } else allowedTicks++;
        if (now - logAt > 60_000_000_000L) {
            if (pausedTicks > 0)
                System.out.println("[Zoomies] DH paused " + pausedTicks + " ticks (ran " + allowedTicks + ") in the last minute so the land around you loads first"
                    + (lastWaiting > 0 ? " (last: " + lastWaiting + " of your chunks waiting)" : " (you were moving fast)"));
            logAt = now; pausedTicks = allowedTicks = 0;
        }
        return brake;
    }

    /** horizontal blocks/second over the last >= 1 s */
    private static boolean movedFast(EntityPlayer p, long now, double minSpeed) {
        double[] last = lastPos.get(p.getEntityId());
        if (last == null || lastPos.size() > 256) {
            if (lastPos.size() > 256) lastPos.clear();
            lastPos.put(p.getEntityId(), new double[] { p.posX, p.posZ, now });
            return false;
        }
        double dt = (now - last[2]) / 1e9;
        if (dt < 1) return false;
        double dx = p.posX - last[0], dz = p.posZ - last[1];
        last[0] = p.posX; last[1] = p.posZ; last[2] = now;
        return dt < 5 && Math.sqrt(dx * dx + dz * dz) / dt > minSpeed; // >5 s gap = DH had nothing queued, not a speed sample
    }
}
