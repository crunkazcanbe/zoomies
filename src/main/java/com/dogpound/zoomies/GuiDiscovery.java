package com.dogpound.zoomies;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/** The Discovery report: what the scene did, what would help most, and a switch for each fix Zoomies has. */
public class GuiDiscovery extends GuiScreen {
    private final Discovery.Result r;
    private PrideFrame f;
    private GuiButton[] ideaBtn;
    private List<String> verdict;
    private int scroll, areaTop, areaH;

    public GuiDiscovery(Discovery.Result r) { this.r = r; }

    @Override
    public void initGui() {
        f = PrideFrame.fit(width, height);
        buttonList.clear();
        ideaBtn = new GuiButton[r.ideas.size()];
        for (int i = 0; i < r.ideas.size(); i++) {
            Discovery.Idea idea = r.ideas.get(i);
            if (idea.setting != null && !idea.already && !idea.rating.equals("NONE"))
                buttonList.add(ideaBtn[i] = new GuiButton(i, f.cx + f.cw - 68, 0, 60, 11, "Turn on"));
        }
        int by = f.cy + f.ch - 16;
        buttonList.add(new GuiButton(100, f.x + f.w / 2 - 105, by, 100, 16, "↻ Run again"));
        buttonList.add(new GuiButton(101, f.x + f.w / 2 + 5, by, 100, 16, "Close"));
        verdict = fontRenderer.listFormattedStringToWidth("§d" + r.verdict, f.cw - 4);
        areaTop = f.cy;
        areaH = by - 4 - verdict.size() * 10 - 4 - areaTop;
        layout();
    }

    /** stats + ideas scroll together; everything is positioned relative to the top of that list */
    private int contentH() { return r.stats.size() * 11 + 24 + r.ideas.size() * 12; }
    private int ideasTop() { return areaTop - scroll + r.stats.size() * 11 + 24; }

    /** move the Turn-on buttons with the list; hide (and so disable clicks on) any not fully in view */
    private void layout() {
        scroll = Math.max(0, Math.min(scroll, contentH() - areaH));
        int y = ideasTop();
        for (int i = 0; i < ideaBtn.length; i++) {
            if (ideaBtn[i] == null) continue;
            ideaBtn[i].y = y + i * 12 - 2;
            ideaBtn[i].visible = ideaBtn[i].y >= areaTop && ideaBtn[i].y + 11 <= areaTop + areaH;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) { scroll += d > 0 ? -12 : 12; layout(); }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 100) { mc.displayGuiScreen(null); Discovery.start(r.seconds); mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("§d✦ Watching again for " + r.seconds + " s…")); return; }
        if (b.id == 101) { mc.displayGuiScreen(null); return; }
        Discovery.Idea idea = r.ideas.get(b.id);
        ZoomiesConfig.set(idea.setting, "true");
        ZoomiesConfig.save();
        b.displayString = "✓ On";
        b.enabled = false;
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        layout();
        f.draw(this, "ZOOMIES RENDER ANALYZER", "§8(" + r.seconds + " s of your real game)");
        int x = f.cx;
        PrideFrame.clip(x, areaTop, f.cw, areaH);
        int y = areaTop - scroll;
        for (Discovery.Row row : r.stats) {
            drawString(fontRenderer, "§7" + row.name, x + 2, y, 0xFFFFFF);
            drawString(fontRenderer, "§f" + row.value, x + 140, y, 0xFFFFFF);
            drawString(fontRenderer, "§8" + fontRenderer.trimStringToWidth(row.note, f.cw - 230), x + 220, y, 0xFFFFFF);
            y += 11;
        }
        y += 4;
        drawRect(x - 2, y, x + f.cw + 2, y + 1, 0x40FFFFFF);
        drawString(fontRenderer, "§d§lPOSSIBLE OPTIMIZATIONS", x + 2, y + 6, 0xFFFFFF);
        y = ideasTop();
        for (Discovery.Idea idea : r.ideas) {
            int c = idea.already ? 0xFF3AD27F : idea.rating.equals("HIGH") ? 0xFFE40303 : idea.rating.equals("MEDIUM") ? 0xFFFF8C00 : idea.rating.equals("LOW") ? 0xFFFFED00 : 0xFF666666;
            drawString(fontRenderer, "§f" + idea.name, x + 2, y, 0xFFFFFF);
            drawRect(x + 180, y - 1, x + 234, y + 9, c);
            drawCenteredString(fontRenderer, idea.rating, x + 207, y, 0xFFFFFF);
            drawString(fontRenderer, "§8" + fontRenderer.trimStringToWidth(idea.why, f.cw - 320), x + 240, y, 0xFFFFFF);
            y += 12;
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(f.cx + f.cw - 3, areaTop, areaH, scroll, areaH, contentH());
        int vy = areaTop + areaH + 4;
        for (int i = 0; i < verdict.size(); i++) drawCenteredString(fontRenderer, verdict.get(i), f.x + f.w / 2, vy + i * 10, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }
}
