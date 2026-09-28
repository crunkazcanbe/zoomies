package com.dogpound.zoomies;

import java.util.Collections;
import java.util.List;

import zone.rong.mixinbooter.ILateMixinLoader;

/** Patches aimed at MODS (not Forge) must load late, after mods are found, or they poison the target class. */
public class ZoomiesLateMixins implements ILateMixinLoader {
    @Override public List<String> getMixinConfigs() { return Collections.singletonList("zoomies.late.mixins.json"); }
}
