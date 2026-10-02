package com.dogpound.zoomies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;

/** A "Zoomies" page in Celeritas's video settings. Only loaded when Celeritas is installed. */
final class CeleritasPage {
    private CeleritasPage() {}

    /** Celeritas saves through this after the player presses Apply/Done */
    static final OptionStorage<Object> STORAGE = new OptionStorage<Object>() {
        @Override public Object getData() { return this; }
        @Override public void save() { ZoomiesConfig.save(); }
    };

    static void register() {
        OptionGUIConstructionEvent.BUS.addListener(e -> e.addPage(page()));
    }

    static OptionPage page() {
        Map<String, OptionGroup.Builder> groups = new LinkedHashMap<>();
        for (String[] s : SettingsList.ALL) {
            OptionGroup.Builder g = groups.computeIfAbsent(s[6], k -> OptionGroup.createBuilder().setId(OptionIdentifier.create("zoomies", "group_" + k.toLowerCase().replace(' ', '_'))));
            String key = s[0];
            boolean restart = "1".equals(s[7]);
            String tip = ZoomiesConfig.why(key) + (restart ? "\n\nTakes effect after restarting the game." : "");
            if ("b".equals(s[2])) {
                OptionImpl.Builder<Object, Boolean> b = OptionImpl.createBuilder(Boolean.class, STORAGE)
                    .setId(OptionIdentifier.create("zoomies", key, Boolean.class))
                    .setName(TextComponent.literal(s[1]))
                    .setTooltip(TextComponent.literal(tip))
                    .setControl(TickBoxControl::new)
                    .setBinding((st, v) -> ZoomiesConfig.set(key, String.valueOf(v)), st -> SettingsList.bool(key))
                    .setImpact(restart ? OptionImpact.VARIES : OptionImpact.MEDIUM);
                if (restart) b.setFlags(OptionFlag.REQUIRES_GAME_RESTART);
                g.add(b.build());
            } else {
                int min = Integer.parseInt(s[3]), max = Integer.parseInt(s[4]), step = Integer.parseInt(s[5]);
                OptionImpl.Builder<Object, Integer> b = OptionImpl.createBuilder(Integer.class, STORAGE)
                    .setId(OptionIdentifier.create("zoomies", key, Integer.class))
                    .setName(TextComponent.literal(s[1]))
                    .setTooltip(TextComponent.literal(tip))
                    .setControl(o -> new SliderControl(o, min, max, step, v -> TextComponent.literal(key.equals("threads.count") && v == 0 ? "auto" : String.valueOf(v))))
                    .setBinding((st, v) -> SettingsList.setNumber(key, v), st -> SettingsList.number(key))
                    .setImpact(OptionImpact.LOW);
                if (restart) b.setFlags(OptionFlag.REQUIRES_GAME_RESTART);
                g.add(b.build());
            }
        }
        List<OptionGroup> built = new ArrayList<>();
        for (OptionGroup.Builder b : groups.values()) built.add(b.build());
        return new OptionPage(OptionIdentifier.create("zoomies", "page"), TextComponent.literal("Zoomies"), built);
    }
}
