package com.dogpound.zoomies;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import zone.rong.mixinbooter.IEarlyMixinLoader;

/**
 * Zoomies — load-speed tricks for the DogPound pack. Same answers as before, found faster.
 * Measure with ~/bin/mcloadtime (per-mod stopwatch) and ~/bin/mcsample (hot-loop sampler).
 */
@IFMLLoadingPlugin.Name("Zoomies")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1002)
public class Zoomies implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static final String CONFIG = "zoomies.mixins.json";

    @Override public void injectData(Map<String, Object> data) {
        try { MixinBootstrap.init(); Mixins.addConfiguration(CONFIG); } catch (Throwable ignored) { }
    }

    @Override public List<String> getMixinConfigs() { return Arrays.asList(CONFIG); }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public String getAccessTransformerClass() { return null; }
}
