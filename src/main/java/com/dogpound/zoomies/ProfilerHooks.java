package com.dogpound.zoomies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Pride Profiler buttons (Esc menu + Options), the startup pop-up and the "world created" notice. */
public final class ProfilerHooks {
    static final int ID = 0x50524F46;   // "PROF"

    @SubscribeEvent(priority = EventPriority.LOW)
    public void open(GuiOpenEvent e) {
        if (!Profiler.showStartup || e.getGui() == null || !e.getGui().getClass().getSimpleName().contains("MainMenu")) return;
        Profiler.showStartup = false;
        e.setGui(new GuiProfiler(e.getGui(), 0));
    }

    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post e) {
        String n = e.getGui().getClass().getSimpleName();
        boolean esc = e.getGui() instanceof GuiIngameMenu;
        if (!esc && !(e.getGui() instanceof GuiOptions) && !n.equals("DPOptions")) return;
        int w = e.getGui().width, h = e.getGui().height;
        e.getButtonList().add(new GuiButton(ID, w / 2 - 100, esc ? 28 : h - 76, 200, 20, "⏱ Pride Profiler"));
    }

    @SubscribeEvent
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (e.getButton().id != ID) return;
        Minecraft.getMinecraft().displayGuiScreen(new GuiProfiler(e.getGui(), e.getGui() instanceof GuiIngameMenu ? 2 : 0));
        e.setCanceled(true);
    }

    private int inWorldTicks;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        // instant join skips the end of "preparing spawn", where the world report normally closes: close it after
        // 30 s in the world instead, so it covers the first land generated around you
        if (Profiler.worldOpen() && Minecraft.getMinecraft().player != null) { if (++inWorldTicks > 600) { inWorldTicks = 0; Profiler.worldDone(); } }
        else inWorldTicks = 0;
        if (!Profiler.worldReady) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) return;
        Profiler.worldReady = false;
        if (!ZoomiesConfig.on("profiler.popup")) return;
        Profiler.Report r = Profiler.world;
        TextComponentString msg = new TextComponentString("§d⏱ Pride Profiler: §fworld ready in §e" + (r == null ? "?" : r.totalMs / 1000 + " s")
                + "§f. §b[click to see which mods took the time]");
        msg.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/zoomies profiler world"));
        msg.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString("Open the world-creation report")));
        mc.player.sendMessage(msg);
    }
}
