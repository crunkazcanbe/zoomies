package com.dogpound.zoomies;

/**
 * Pride Rail monorail beams / maglev guideways (IR Extras track styles "pride_*") are elevated structures: they span
 * between pillars instead of lying on the ground. IR wants solid blocks under track (and breaks track that floats more
 * than trackFloatingPercent); for these styles only, that rule is lifted (MixinIrTrackSupport, MixinIrRailFloating).
 * Reflection only: Zoomies doesn't compile against Immersive Railroading.
 */
public final class IrPrideTrack {
    private IrPrideTrack() {}

    private static java.lang.reflect.Field info, settings, track, builder;

    /** RailInfo -> settings.track starts with "pride_" */
    public static boolean pride(Object railInfo) {
        try {
            if (railInfo == null) return false;
            if (settings == null) { settings = railInfo.getClass().getField("settings"); }
            Object s = settings.get(railInfo);
            if (track == null) track = s.getClass().getField("track");
            Object t = track.get(s);
            return t instanceof String && ((String) t).contains("/pride_");
        } catch (Throwable e) { return false; }
    }

    public static boolean prideTile(Object tileRail) {
        try {
            if (info == null) info = tileRail.getClass().getField("info");
            return pride(info.get(tileRail));
        } catch (Throwable e) { return false; }
    }

    public static boolean prideTrackBase(Object trackBase) {
        try {
            if (builder == null) builder = trackBase.getClass().getField("builder");
            Object b = builder.get(trackBase);
            return b != null && pride(b.getClass().getField("info").get(b));
        } catch (Throwable e) { return false; }
    }
}
