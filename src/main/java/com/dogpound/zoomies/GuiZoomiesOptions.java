package com.dogpound.zoomies;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/** Zoomies settings without Celeritas: toggles and ◀ ▶ steppers, grouped like the Celeritas page. Saves on close. */
public class GuiZoomiesOptions extends GuiScreen {
    private final GuiScreen parent;
    private int scroll;
    private static final int ROW = 22;

    public GuiZoomiesOptions(GuiScreen parent) { this.parent = parent; }

    private PrideFrame f;
    private int areaTop, areaH;

    @Override
    public void initGui() {
        f = PrideFrame.sized(width, height, 460, 520);
        areaTop = f.cy;
        areaH = f.ch - 26;                         // the list scrolls above the Done button
        scroll = Math.max(0, Math.min(scroll, contentH() - areaH));
        buttonList.clear();
        int y = areaTop - scroll, x = f.cx + f.cw - 128;
        String group = "";
        for (int i = 0; i < SettingsList.ALL.length; i++) {
            String[] s = SettingsList.ALL[i];
            if (!s[6].equals(group)) { group = s[6]; y += 14; }
            if (y >= areaTop && y + 20 <= areaTop + areaH) {
                if ("b".equals(s[2])) buttonList.add(new GuiButton(100 + i, x, y, 120, 20, SettingsList.bool(s[0]) ? "§aON" : "§cOFF"));
                else {
                    buttonList.add(new GuiButton(200 + i, x, y, 20, 20, "◀"));
                    buttonList.add(new GuiButton(300 + i, x + 100, y, 20, 20, "▶"));
                }
            }
            y += ROW;
        }
        int dw = Math.min(200, f.cw);
        buttonList.add(new GuiButton(1, f.x + (f.w - dw) / 2, f.cy + f.ch - 20, dw, 20, "Done"));
    }

    /** height of the whole settings list: a 14px header per group + one ROW per setting */
    private static int contentH() {
        int h = 0;
        String group = "";
        for (String[] s : SettingsList.ALL) { if (!s[6].equals(group)) { group = s[6]; h += 14; } h += ROW; }
        return h;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) { mc.displayGuiScreen(parent); return; }
        int i = b.id % 100;
        String[] s = SettingsList.ALL[i];
        if (b.id < 200) ZoomiesConfig.set(s[0], String.valueOf(!SettingsList.bool(s[0])));
        else {
            int step = Integer.parseInt(s[5]), v = SettingsList.number(s[0]) + (b.id < 300 ? -step : step);
            SettingsList.setNumber(s[0], Math.max(Integer.parseInt(s[3]), Math.min(Integer.parseInt(s[4]), v)));
        }
        ZoomiesConfig.save();
        initGui();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) { scroll += d > 0 ? -ROW : ROW; initGui(); }   // initGui clamps it
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        f.draw(this, "Zoomies", "§7(* = after restarting the game)");
        int y = areaTop - scroll, x = f.cx + f.cw - 128;
        String group = "";
        PrideFrame.clip(f.cx, areaTop, f.cw, areaH);
        for (String[] s : SettingsList.ALL) {
            if (!s[6].equals(group)) { group = s[6]; if (y + 2 >= areaTop && y + 12 <= areaTop + areaH) drawString(fontRenderer, "§d" + group, f.cx, y + 2, 0xFFFFFF); y += 14; }
            if (y >= areaTop && y + 20 <= areaTop + areaH) {
                drawString(fontRenderer, s[1] + ("1".equals(s[7]) ? " §8*" : ""), f.cx + 10, y + 6, 0xFFDDDDDD);
                if ("i".equals(s[2])) {
                    int v = SettingsList.number(s[0]);
                    drawCenteredString(fontRenderer, s[0].equals("threads.count") && v == 0 ? "auto" : String.valueOf(v), x + 60, y + 6, 0xFFFFFFFF);
                }
            }
            y += ROW;
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + f.cw - 3, areaTop, areaH, scroll, areaH, contentH());
        super.drawScreen(mx, my, pt);
    }
}
