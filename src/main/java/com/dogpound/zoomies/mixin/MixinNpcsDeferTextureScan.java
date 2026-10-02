package com.dogpound.zoomies.mixin;

import net.minecraft.client.Minecraft;
import noppes.npcs.client.gui.select.SubGuiTextureSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Custom NPCs builds its texture picker's file list at startup (ClientProxy.load makes a SubGuiTextureSelection just
 * for that; its constructor calls resetFiles(), which unzips every texture in every mod) — measured ~70 s of a big
 * pack's load, for a screen admins open in the NPC editor. While the game is still LOADING, Zoomies skips only that
 * scan (the object is still made normally) and runs the same scan in the background once the main menu is up.
 * Opening the picker later scans as it always did if the list isn't there yet.
 * (v1 skipped the whole object with a null @Redirect — Mixin refuses null for constructors: crash. Don't.)
 */
@Mixin(value = SubGuiTextureSelection.class, remap = false)
public abstract class MixinNpcsDeferTextureScan {
    @Shadow private void resetFiles() { }

    @Unique private static volatile boolean zoomies$scheduled;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/select/SubGuiTextureSelection;resetFiles()V"), remap = false)
    private void zoomies$scanLaterIfLoading(SubGuiTextureSelection self) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean loading = mc.world == null && mc.currentScreen == null;
        if (!loading) { resetFiles(); return; }                   // a real picker being opened: normal
        if (zoomies$scheduled) return;
        zoomies$scheduled = true;
        Thread t = new Thread(() -> {
            try {
                while (mc.currentScreen == null) Thread.sleep(1000);  // main menu up = loading finished
                long t0 = System.currentTimeMillis();
                new SubGuiTextureSelection(0, null, "", "png", 0);     // same call Custom NPCs makes; now not "loading", so it scans
                System.out.println("[Zoomies] Custom NPCs texture list built in the background in " + (System.currentTimeMillis() - t0) + " ms");
            } catch (Throwable e) {
                System.out.println("[Zoomies] background Custom NPCs texture list skipped (" + e + "); it builds when the picker opens");
            }
        }, "Zoomies-CNPC-textures");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
