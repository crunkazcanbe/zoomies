package com.dogpound.zoomies.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tails Legacy lists its part files by walking EVERY entry of EVERY mod jar, once per resource namespace, on every
 * resource reload (measured 2026-10-02: 23 s of the big pack's load — some jars hold 30,000 files).
 *
 * Same entries, found by index: each jar's entry names are filed once by "assets/<ns>/<first folder>/", and the
 * listing walks only the entries under the folder it asked for. Tails' own checks still run on each of them.
 * Applies after Tails' mixin (priority 1500) to the method Tails added; does nothing if Tails isn't installed.
 */
@Mixin(value = FileResourcePack.class, priority = 1500, remap = false)
public abstract class MixinTailsZipListing {
    @Unique private static final Map<ZipFile, Map<String, List<ZipEntry>>> zoomies$index = new WeakHashMap<>();
    @Unique private static final ThreadLocal<String> zoomies$path = new ThreadLocal<>();

    @Inject(method = "tailslegacy$listResources", require = 0, at = @At("HEAD"))
    private void zoomies$rememberPath(String path, Predicate<ResourceLocation> filter, CallbackInfoReturnable<java.util.Collection<ResourceLocation>> cir) {
        zoomies$path.set(path);
    }

    @Redirect(method = "tailslegacy$listResources", require = 0,
              at = @At(value = "INVOKE", target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;"))
    private Enumeration<? extends ZipEntry> zoomies$entriesUnder(ZipFile zip) {
        String path = zoomies$path.get();
        if (path == null || path.isEmpty() || path.startsWith("/")) return zip.entries();   // unusual ask: the original full walk
        Map<String, List<ZipEntry>> byFolder;
        synchronized (zoomies$index) {
            byFolder = zoomies$index.get(zip);
            if (byFolder == null) {
                byFolder = new HashMap<>();
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry z = e.nextElement();
                    byFolder.computeIfAbsent(zoomies$key(z.getName()), k -> new ArrayList<>()).add(z);
                }
                zoomies$index.put(zip, byFolder);
            }
        }
        // the asked-for path's first folder decides the bucket; anything outside assets/<ns>/ can't match Tails' check
        String first = path.indexOf('/') >= 0 ? path.substring(0, path.indexOf('/')) : path;
        List<ZipEntry> out = new ArrayList<>();
        for (Map.Entry<String, List<ZipEntry>> b : byFolder.entrySet())
            if (b.getKey().endsWith("/" + first) || b.getKey().endsWith("/" + first + "/")) out.addAll(b.getValue());
        return Collections.enumeration(out);
    }

    /** "assets/<ns>/<folder>" for entries inside a namespace folder, "" for everything else */
    @Unique
    private static String zoomies$key(String name) {
        if (!name.startsWith("assets/")) return "";
        int ns = name.indexOf('/', 7);
        if (ns < 0) return "";
        int folder = name.indexOf('/', ns + 1);
        return folder < 0 ? name.substring(0, ns) + "/" + name.substring(ns + 1) : name.substring(0, folder);
    }
}
