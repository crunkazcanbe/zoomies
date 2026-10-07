package com.dogpound.zoomies.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons runs its server-thread work until "tick start + 25 ms". the pack's ticks are already past that when
 * DH gets its turn, so the deadline has always passed: exactly ONE chunk request per tick (none when a cleanup job takes
 * the slot) - the real ~1-7 chunks/s ceiling (2026-10-04). Give it a fixed slice per tick instead (dh.serverBudgetMs),
 * and a bigger one while every player has stood still for dh.idleSeconds (AFK, reading, menus) - dh.idleBudgetMs.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.util.threading.ServerThreadTaskHandler", remap = false)
public abstract class MixinDhServerBudget {
    private static double zoomies$lastSig = Double.NaN;
    private static long zoomies$stillSince;

    @Inject(method = "deadlineForTickNano", at = @At("HEAD"), cancellable = true, require = 0)
    private void zoomies$fixedSlice(long tickStart, CallbackInfoReturnable<Long> cir) {
        int ms = com.dogpound.zoomies.ZoomiesConfig.num("dh.serverBudgetMs", 20);
        if (ms <= 0) return;
        long now = System.nanoTime();
        int idleSec = com.dogpound.zoomies.ZoomiesConfig.num("dh.idleSeconds", 30);
        if (idleSec > 0 && zoomies$allStill(now, idleSec))
            ms = Math.max(ms, com.dogpound.zoomies.ZoomiesConfig.num("dh.idleBudgetMs", 120));
        cir.setReturnValue(now + ms * 1_000_000L);
    }

    /** true once no player has moved (whole blocks) or turned since idleSec seconds ago */
    private static boolean zoomies$allStill(long now, int idleSec) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return false;
        double sig = 0;
        for (net.minecraft.entity.player.EntityPlayerMP p : server.getPlayerList().getPlayers())
            sig = sig * 31 + Math.floor(p.posX) * 7 + Math.floor(p.posY) * 13 + Math.floor(p.posZ) * 17 + Math.round(p.rotationYaw / 10f) + Math.round(p.rotationPitch / 10f) * 3;
        if (sig != zoomies$lastSig) { zoomies$lastSig = sig; zoomies$stillSince = now; return false; }
        return now - zoomies$stillSince >= idleSec * 1_000_000_000L;
    }
}
