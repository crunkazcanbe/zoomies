package com.dogpound.zoomies;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/**
 * Pride Profiler report. Tabs: Startup (every mod's loading time), Loading steps (Forge's bars in order),
 * World creation (which mod's generators the world spent its time in). Click a mod to open its breakdown.
 */
public class GuiProfiler extends GuiScreen {
    private final GuiScreen parent;
    private int tab;                    // 0 startup, 1 steps, 2 world
    private int scroll;
    private String open;                // expanded mod id
    private PrideFrame f;
    private Profiler.Report startup, world;

    private static final int ROW = 16;
    private static final String[] TABS = {"⏱ Startup", "☰ Loading steps", "🌍 World creation"};

    public GuiProfiler(GuiScreen parent, int tab) {
        this.parent = parent;
        this.tab = tab;
        startup = Profiler.STARTUP.mods.isEmpty() ? Profiler.loadLast("startup") : Profiler.STARTUP;
        world = Profiler.world != null && !Profiler.world.mods.isEmpty() ? Profiler.world : Profiler.loadLast("world");
    }

    @Override public boolean doesGuiPauseGame() { return true; }
    @Override public void initGui() { f = PrideFrame.fit(width, height); }

    private Profiler.Report report() { return tab == 2 ? world : startup; }

    private static String name(String modid) {
        ModContainer mc = Loader.instance().getIndexedModList().get(modid);
        return mc != null ? mc.getName() : modid;
    }

    private static String secs(long nanos) {
        double s = nanos / 1e9;
        return s >= 60 ? String.format("%dm %02ds", (int) s / 60, (int) s % 60) : s >= 10 ? String.format("%.0f s", s) : String.format("%.1f s", s);
    }

    /** rows to draw for the current tab: {label, nanos, isMod, modid} */
    private List<Object[]> rows() {
        List<Object[]> out = new ArrayList<>();
        if (tab == 1) {
            List<Object[]> st = startup == null ? null : startup.steps;
            if (st != null) for (int i = 0; i < st.size(); i++) {
                long at = ((Number) st.get(i)[0]).longValue();
                long next = i + 1 < st.size() ? ((Number) st.get(i + 1)[0]).longValue() : startup.totalMs;
                out.add(new Object[]{String.valueOf(st.get(i)[1]), Math.max(0, next - at) * 1_000_000L, false, null});
            }
            out.sort((a, b) -> Long.compare((Long) b[1], (Long) a[1]));
            return out;
        }
        Profiler.Report r = report();
        if (r == null) return out;
        for (String m : r.ranked()) {
            out.add(new Object[]{name(m), r.modNanos(m), true, m});
            if (m.equals(open)) {
                Map<String, Long> steps = r.mods.get(m);
                List<Map.Entry<String, Long>> l = new ArrayList<>(steps.entrySet());
                l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                for (Map.Entry<String, Long> e : l) out.add(new Object[]{"    " + e.getKey(), e.getValue(), false, null});
            }
        }
        return out;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        Profiler.Report r = report();
        String right = r == null ? "" : (tab == 2 ? "world " : "loaded in ") + secs(r.totalMs * 1_000_000L) + " · " + r.started;
        f.draw(this, "Pride Profiler", right);
        // tabs
        int tw = Math.min(150, (f.cw - 8) / 3);
        for (int i = 0; i < TABS.length; i++) {
            int x = f.cx + i * (tw + 4);
            boolean hov = mx >= x && mx < x + tw && my >= f.cy && my < f.cy + 18;
            PrideFrame.tile(x, f.cy, tw, 18, PrideFrame.RAINBOW[i * 2 % PrideFrame.RAINBOW.length], hov, tab == i);
            drawCenteredString(fontRenderer, TABS[i], x + tw / 2, f.cy + 5, 0xFFFFFF);
        }
        int top = f.cy + 24, h = f.ch - 50, x = f.cx, w = f.cw;
        List<Object[]> rows = rows();
        if (rows.isEmpty()) {
            drawCenteredString(fontRenderer, tab == 2 ? "No world report yet — create or load a world and it appears here." :
                    "No startup report yet — it fills in on the next launch.", x + w / 2, top + 30, 0xA79FBF);
        }
        long max = 1;
        for (Object[] o : rows) max = Math.max(max, (Long) o[1]);
        long sum = 0;
        for (Object[] o : rows) if ((Boolean) o[2] || tab == 1) sum += (Long) o[1];
        int total = rows.size() * ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - h)));
        PrideFrame.clip(x, top, w, h);
        int labelW = Math.min(240, w / 3), barX = x + labelW + 8, barW = w - labelW - 110;
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW - scroll;
            if (y + ROW < top || y > top + h) continue;
            Object[] o = rows.get(i);
            long n = (Long) o[1];
            boolean mod = (Boolean) o[2];
            boolean hov = mod && mx >= x && mx < x + w && my >= y && my < y + ROW && my >= top && my < top + h;
            if (hov) Gui.drawRect(x, y, x + w, y + ROW, 0x30FFFFFF);
            String label = (mod ? (o[3].equals(open) ? "▾ " : "▸ ") : "") + o[0];
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(label, labelW), x + 4, y + 4, mod ? 0xFFFFFF : 0xB8B0D0);
            int bw = (int) (barW * (double) n / max);
            int col = PrideFrame.RAINBOW[(i) % 6];
            Gui.drawRect(barX, y + 4, barX + barW, y + ROW - 4, 0xFF1C1530);
            Gui.drawRect(barX, y + 4, barX + Math.max(1, bw), y + ROW - 4, mod || tab == 1 ? col : 0xFF8A8499);
            String t = secs(n) + (sum > 0 && (mod || tab == 1) ? String.format("  %d%%", Math.round(100.0 * n / sum)) : "");
            fontRenderer.drawStringWithShadow(t, x + w - 6 - fontRenderer.getStringWidth(t), y + 4, 0xFFFFFF);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 3, top, h, scroll, h, total);
        int by = f.cy + f.ch - 22;
        String tip = tab == 1 ? "Forge's loading bars, slowest first." : tab == 2 ? "Which mods' world generators the new world spent its time in. Click a mod for details."
                : "Every mod's loading time (construct, pre-init, init, post-init, resource reloads). Click a mod for details.";
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(tip, w - 110), x + 2, by + 7, 0xC8C0DC);
        PrideFrame.button(x + w - 100, by, 100, 20, "◀ Done", PrideFrame.BUTTON, mx, my);
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        int by = f.cy + f.ch - 22;
        if (mx >= f.cx + f.cw - 100 && mx < f.cx + f.cw && my >= by && my < by + 20) { mc.displayGuiScreen(parent); return; }
        int tw = Math.min(150, (f.cw - 8) / 3);
        for (int i = 0; i < TABS.length; i++) {
            int x = f.cx + i * (tw + 4);
            if (mx >= x && mx < x + tw && my >= f.cy && my < f.cy + 18) { tab = i; scroll = 0; open = null; return; }
        }
        int top = f.cy + 24, h = f.ch - 50;
        if (my < top || my >= top + h) return;
        List<Object[]> rows = rows();
        int i = (my - top + scroll) / ROW;
        if (i >= 0 && i < rows.size() && (Boolean) rows.get(i)[2]) {
            String id = (String) rows.get(i)[3];
            open = id.equals(open) ? null : id;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scroll -= Integer.signum(d) * ROW * 3;
    }
}
