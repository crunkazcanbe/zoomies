package com.dogpound.zoomies;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Asks config/zoomies.cfg before each patch is applied: a trick switched off is never woven in at all. */
public class ZoomiesPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { ZoomiesConfig.load(); }
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        boolean ok = ZoomiesConfig.mixinAllowed(simple);
        if (!ok) System.out.println("[Zoomies] " + simple + " switched off in config/zoomies.cfg");
        return ok;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
