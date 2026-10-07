package com.dogpound.zoomies;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla IntCache, but one pool per thread (MixinIntCacheThreadLocal). The vanilla pool is static: resetIntCache() on
 * one thread hands arrays another thread is still filling to the next caller, so biome layers could never run on two
 * threads at once. Per thread they can (DH off-thread LODs, DhOffThread). Same behaviour on any single thread.
 */
public final class ThreadIntCache {
    private ThreadIntCache() {}

    private int size = 256;
    private final List<int[]> freeSmall = new ArrayList<int[]>(), usedSmall = new ArrayList<int[]>(),
            freeLarge = new ArrayList<int[]>(), usedLarge = new ArrayList<int[]>();
    private static final ThreadLocal<ThreadIntCache> T = ThreadLocal.withInitial(ThreadIntCache::new);

    public static int[] get(int want) {
        ThreadIntCache c = T.get();
        if (want <= 256) {
            int[] a = c.freeSmall.isEmpty() ? new int[256] : c.freeSmall.remove(c.freeSmall.size() - 1);
            c.usedSmall.add(a);
            return a;
        }
        if (want > c.size) { c.size = want; c.freeLarge.clear(); c.usedLarge.clear(); }
        int[] a = c.freeLarge.isEmpty() ? new int[c.size] : c.freeLarge.remove(c.freeLarge.size() - 1);
        c.usedLarge.add(a);
        return a;
    }

    public static void reset() {
        ThreadIntCache c = T.get();
        if (!c.freeLarge.isEmpty()) c.freeLarge.remove(c.freeLarge.size() - 1);
        if (!c.freeSmall.isEmpty()) c.freeSmall.remove(c.freeSmall.size() - 1);
        c.freeLarge.addAll(c.usedLarge); c.freeSmall.addAll(c.usedSmall);
        c.usedLarge.clear(); c.usedSmall.clear();
    }

    public static String sizes() {
        ThreadIntCache c = T.get();
        return "cache: " + c.freeLarge.size() + ", tcache: " + c.freeSmall.size() + ", allocated: " + c.usedLarge.size() + ", tallocated: " + c.usedSmall.size();
    }
}
