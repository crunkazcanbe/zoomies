package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.JsgPrefetch;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.fml.common.ProgressManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

/** Just Stargate textures decoded in parallel (see JsgPrefetch): ~50 s of every resource reload in the Pride pack. */
@Pseudo
@Mixin(targets = "tauri.dev.jsg.loader.texture.TextureLoader", remap = false)
public abstract class MixinJsgTextureParallel {
    @Inject(method = "reloadTextures", at = @At("HEAD"), remap = false, require = 0)
    private static void zoomies$start(IResourceManager rm, CallbackInfo ci) { JsgPrefetch.manager = rm; }

    @Inject(method = "reloadTextures", at = @At("RETURN"), remap = false, require = 0)
    private static void zoomies$end(IResourceManager rm, CallbackInfo ci) { JsgPrefetch.done(); }

    @Redirect(method = "reloadTextures", remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Ltauri/dev/jsg/loader/FolderLoader;getAllFiles(Ljava/lang/String;[Ljava/lang/String;)Ljava/util/List;"))
    private static List<?> zoomies$list(String dir, String[] ext) throws Exception {
        List<?> files = (List<?>) Class.forName("tauri.dev.jsg.loader.FolderLoader")
                .getMethod("getAllFiles", String.class, String[].class).invoke(null, dir, ext);
        JsgPrefetch.prefetch(files);
        return files;
    }

    @Inject(method = "loadTexture", at = @At("HEAD"), remap = false, require = 0)
    private static void zoomies$path(ProgressManager.ProgressBar bar, String path, IResourceManager rm, CallbackInfo ci) { JsgPrefetch.current = path; }

    @Inject(method = "loadEH", at = @At("HEAD"), remap = false, require = 0)
    private static void zoomies$pathEh(ProgressManager.ProgressBar bar, String path, IResourceManager rm, CallbackInfo ci) { JsgPrefetch.current = path; }

    @Redirect(method = { "loadTexture", "loadEH" }, remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_177053_a(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private static BufferedImage zoomies$decoded(InputStream in) throws java.io.IOException { return JsgPrefetch.read(in); }
}
