package com.dogpound.zoomies.mixin;

import com.dogpound.zoomies.ObjLines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GVCLib's OBJ loader (the gun mods): its per-line regex checks → ObjLines' identical hand-written checks (~20 s) */
@Mixin(targets = "objmodel.WavefrontObject", remap = false)
public abstract class MixinGvcObjLines {
    private static final String H = "HEAD";

    @Inject(method = "isValidVertexLine", at = @At(H), cancellable = true, remap = false)
    private void zoomies$v(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.vertex(s)); }
    @Inject(method = "isValidVertexNormalLine", at = @At(H), cancellable = true, remap = false)
    private void zoomies$vn(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.normal(s)); }
    @Inject(method = "isValidTextureCoordinateLine", at = @At(H), cancellable = true, remap = false)
    private void zoomies$vt(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.texture(s)); }
    @Inject(method = "isValidFace_V_VT_VN_Line", at = @At(H), cancellable = true, remap = false)
    private void zoomies$f3(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.faceVtVn(s)); }
    @Inject(method = "isValidFace_V_VT_Line", at = @At(H), cancellable = true, remap = false)
    private void zoomies$f2(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.faceVt(s)); }
    @Inject(method = "isValidFace_V_VN_Line", at = @At(H), cancellable = true, remap = false)
    private void zoomies$fn(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.faceVn(s)); }
    @Inject(method = "isValidFace_V_Line", at = @At(H), cancellable = true, remap = false)
    private void zoomies$f1(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.faceV(s)); }
    @Inject(method = "isValidGroupObjectLine", at = @At(H), cancellable = true, remap = false)
    private void zoomies$g(String s, CallbackInfoReturnable<Boolean> c) { c.setReturnValue(ObjLines.group(s)); }
}
