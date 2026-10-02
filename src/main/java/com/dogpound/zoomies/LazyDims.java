package com.dogpound.zoomies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Her ask 2026-09-28: "make the dimensions only load when you go to them". A world start loads every registered
 * dimension (122 in this pack); Forge can already load one the first time anything needs it (travel, a mod asking
 * for it), and unloads idle ones anyway. So start with only the ones in the list.
 */
public final class LazyDims {
    private LazyDims() {}

    public static Integer[] filter(Integer[] all) {
        if (!ZoomiesConfig.on("worldgen.lazyDimensions")) return all;
        Set<Integer> keep = new HashSet<>();
        for (String s : ZoomiesConfig.get("worldgen.startDimensions").split(","))
            try { keep.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
        keep.add(0);                                            // the overworld always
        List<Integer> out = new ArrayList<>();
        for (Integer d : all) if (keep.contains(d)) out.add(d);
        System.out.println("[Zoomies] world start: loading " + out.size() + " of " + all.length + " dimensions now, the rest when first visited");
        return out.toArray(new Integer[0]);
    }
}
