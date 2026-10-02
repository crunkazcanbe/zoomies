package com.dogpound.zoomies.mixin;

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fexcraft.app.json.JsonArray;
import net.fexcraft.app.json.JsonHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Run 16 profile: FVTM looks for addon packs in EVERY jar of the mods folder by streaming each one start to finish
 * (ZipInputStream = decompress every file) just to compare names — 28 s, 76 % inside the inflater. A zip's table of
 * contents lists every name for free; read that, and only decompress the few files that actually match. Same
 * results, same order, same limit.
 */
@Mixin(targets = "net.fexcraft.mod.fvtm.util.ZipUtils", remap = false)
public abstract class MixinFvtmZipIndex {
    @Inject(method = "getValuesAt(Ljava/io/File;Ljava/lang/String;Ljava/lang/String;I)Lnet/fexcraft/app/json/JsonArray;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void zoomies$byDirectory(File file, String path, String suffix, int limit, CallbackInfoReturnable<JsonArray> cir) {
        JsonArray out = new JsonArray();
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements() && (limit <= 0 || out.size() < limit)) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.startsWith(path) || !n.endsWith(suffix)) continue;
                try (InputStream in = zip.getInputStream(e)) { out.add(JsonHandler.parse(in, true)); }
            }
        } catch (Exception e) {
            e.printStackTrace();                                   // FVTM's own behaviour: report, return what was found
        }
        cir.setReturnValue(out);
    }
}
