package com.dogpound.zoomies;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Start in a village (requested feature). A new world's spawn moves to
 * the nearest village — searched OUTSIDE Clockwork Phase 2's crater (378 blocks round 0,0) when that mod is there — and
 * "/zoomies village" moves you (and the world spawn) to the nearest village in a world that already exists.
 * Uses the world's own "Village" structure finder, so it works with vanilla terrain and with OTG presets like Dregora
 * (as long as Village Names' replacement village generator is OFF — it can't place villages in OTG biomes).
 */
public final class VillageSpawn {
    private static final int CRATER = 400;

    /** where to start looking: past the crater if Clockwork Phase 2 is installed */
    private static BlockPos searchFrom(World w) {
        BlockPos s = w.getSpawnPoint();
        boolean crater = net.minecraftforge.fml.common.Loader.isModLoaded("clockworkphase2");
        if (crater && Math.abs(s.getX()) < CRATER && Math.abs(s.getZ()) < CRATER) return new BlockPos(CRATER + 120, 64, 0);
        return s;
    }

    /** the middle of the nearest village, standing height, or null */
    public static BlockPos find(World w, BlockPos from) {
        BlockPos v = null;
        try { v = w.findNearestStructure("Village", from, true); } catch (Throwable t) { System.out.println("[Zoomies] village search failed: " + t); }
        if (v == null) return null;
        boolean crater = net.minecraftforge.fml.common.Loader.isModLoaded("clockworkphase2");
        if (crater && Math.abs(v.getX()) < CRATER && Math.abs(v.getZ()) < CRATER) {           // inside the crater: look further out
            try { v = w.findNearestStructure("Village", new BlockPos(Integer.signum(v.getX() == 0 ? 1 : v.getX()) * (CRATER + 200), 64, v.getZ()), true); } catch (Throwable t) { return null; }
            if (v == null) return null;
        }
        BlockPos top = w.getTopSolidOrLiquidBlock(new BlockPos(v.getX() + 8, 0, v.getZ() + 8));
        return top.up();
    }

    /** LOWEST + receiveCanceled: OTG/Dregora pick their own spawn and cancel the event first, which silently skipped this
     *  (her 2026-10-04 "it didn't start me in a village"); now we always get the last word */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST, receiveCanceled = true)
    public void newWorld(WorldEvent.CreateSpawnPosition e) {
        World w = e.getWorld();
        if (w.isRemote || w.provider.getDimension() != 0 || !ZoomiesConfig.on("worldgen.spawnInVillage")) return;
        long t0 = System.currentTimeMillis();
        BlockPos v = find(w, searchFrom(w));
        if (v == null) { System.out.println("[Zoomies] no village found for the spawn; keeping the normal spawn"); return; }
        w.getWorldInfo().setSpawn(v);
        e.setCanceled(true);
        System.out.println("[Zoomies] spawn moved to the village at " + v + " (" + (System.currentTimeMillis() - t0) + " ms)");
    }

    /** /zoomies village: move a player (and the world spawn) to the nearest village */
    static void command(EntityPlayerMP p, boolean setWorldSpawn) {
        WorldServer w = p.getServerWorld();
        p.sendMessage(new TextComponentString("§d✦ Looking for a village…"));
        BlockPos v = find(w, w.provider.getDimension() == 0 ? searchFrom(w) : p.getPosition());
        if (v == null) { p.sendMessage(new TextComponentString("§cNo village found nearby. (Village Names' \"New Village Generator\" must be off for OTG worlds; new villages appear in land you haven't explored yet.)")); return; }
        p.connection.setPlayerLocation(v.getX() + 0.5, v.getY(), v.getZ() + 0.5, p.rotationYaw, p.rotationPitch);
        p.setSpawnPoint(v, true);
        if (setWorldSpawn && w.provider.getDimension() == 0) w.setSpawnPoint(v);
        p.sendMessage(new TextComponentString("§d✦ Welcome to the village! §7(" + v.getX() + ", " + v.getY() + ", " + v.getZ() + ") — it's your spawn now."));
    }
}
