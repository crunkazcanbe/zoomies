package com.dogpound.zoomies;

/** Reads Villager Backport's "villagesOnly" setting (MixinVillagerBackportNearVillage); true if it can't be read. */
public final class VillageCheck {
    private VillageCheck() {}

    private static volatile Boolean only;

    public static boolean villagesOnly() {
        Boolean b = only;
        if (b == null) {
            try {
                Object structures = Class.forName("com.exiledradio.villagerbackport.ModConfig").getField("structures").get(null);
                b = structures.getClass().getField("villagesOnly").getBoolean(structures);
            } catch (Throwable t) { b = Boolean.TRUE; }
            only = b;
        }
        return b;
    }
}
