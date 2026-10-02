package com.dogpound.zoomies;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** The in-game side of Zoomies: /zoomies profile | report | stop, and the render cost report screen. */
@Mod(modid = "zoomies", name = "Zoomies", version = "0.2.0", acceptableRemoteVersions = "*")
public class ZoomiesMod {
    /** model swaps must be listening before the first model bake (between preInit and init) */
    @Mod.EventHandler
    public void pre(net.minecraftforge.fml.common.event.FMLPreInitializationEvent e) {
        WarmCaches.checkModList();   // before any mod reads a saved cache (ProjectE EMC is read at server start)
        if (FMLCommonHandler.instance().getSide().isClient()) MinecraftForge.EVENT_BUS.register(BetterGrass.class);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        JavaCache.register(FMLCommonHandler.instance().getSide().isClient());
        if (FMLCommonHandler.instance().getSide().isClient()) {
            ClientCommandHandler.instance.registerCommand(new Command());
            MinecraftForge.EVENT_BUS.register(new Frames());
            Graphics.register();
            Details.register();
            Discovery.register();
            // her ask: settings inside Celeritas's video menu if it's there, otherwise a button in Video Settings
            if (net.minecraftforge.fml.common.Loader.isModLoaded("celeritas")) {
                try { CeleritasPage.register(); } catch (Throwable t) { System.out.println("[Zoomies] couldn't add the Celeritas page: " + t); MinecraftForge.EVENT_BUS.register(new VideoButton()); }
            } else MinecraftForge.EVENT_BUS.register(new VideoButton());
        }
    }

    /** "Zoomies..." in the vanilla Video Settings screen (when Celeritas isn't installed) */
    public static final class VideoButton {
        private static final int ID = 0x5A4F4F4D;

        @SubscribeEvent
        public void init(net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post e) {
            if (e.getGui() instanceof net.minecraft.client.gui.GuiVideoSettings)
                e.getButtonList().add(new net.minecraft.client.gui.GuiButton(ID, 6, 6, 80, 20, "Zoomies..."));
        }

        @SubscribeEvent
        public void click(net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre e) {
            if (e.getButton().id == ID) { Minecraft.getMinecraft().displayGuiScreen(new GuiZoomiesOptions(e.getGui())); e.setCanceled(true); }
        }
    }

    public static final class Frames {
        @SubscribeEvent
        public void onRender(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START) RenderProfiler.frameStart(); else RenderProfiler.frameEnd();
        }
    }

    static final class Command extends CommandBase {
        @Override public String getName() { return "zoomies"; }
        @Override public String getUsage(ICommandSender s) { return "/zoomies discover [seconds] | profile | report | stop"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] a) {
            String sub = a.length == 0 ? "report" : a[0];
            if (sub.equals("discover")) {
                int secs = 30;
                try { if (a.length > 1) secs = Math.max(5, Math.min(300, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) {}
                Discovery.start(secs);
                sender.sendMessage(new TextComponentString("§d✦ Optimization Discovery: watching the game for " + secs + " s. Play normally — look around, walk past your machines. The report opens by itself."));
                return;
            }
            if (sub.equals("profile")) {
                RenderProfiler.start();
                sender.sendMessage(new TextComponentString("§d✦ Zoomies is timing every machine and entity renderer. Play normally for 30 s, then /zoomies report"));
            } else if (sub.equals("stop")) {
                RenderProfiler.stop();
                sender.sendMessage(new TextComponentString("§dZoomies profiling stopped. " + RenderProfiler.summary()));
            } else {
                Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new Report()));
            }
        }

        @Override public List<String> getTabCompletions(MinecraftServer s, ICommandSender c, String[] a, net.minecraft.util.math.BlockPos p) {
            return a.length == 1 ? getListOfStringsMatchingLastWord(a, Arrays.asList("discover", "profile", "report", "stop")) : java.util.Collections.emptyList();
        }
    }

    /** the report: worst renderers first, with whose mod they are and what share of each frame they eat */
    static final class Report extends GuiScreen {
        private int scroll, visible, total;

        @Override
        public void handleMouseInput() throws java.io.IOException {
            super.handleMouseInput();
            int d = org.lwjgl.input.Mouse.getEventDWheel();
            if (d != 0) scroll = Math.max(0, Math.min(Math.max(0, total - visible), scroll + (d > 0 ? -3 : 3)));
        }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            PrideFrame f = PrideFrame.fit(width, height);
            f.draw(this, "Zoomies render cost report", RenderProfiler.on ? "§a● recording" : "");
            int x = f.cx;
            drawString(fontRenderer, "§7" + (RenderProfiler.frames == 0 ? "No data yet: run /zoomies profile and play for a bit" : RenderProfiler.summary()
                + (RenderProfiler.on ? "  §a(still recording)" : "")), x, f.cy, 0xFFFFFF);
            int cy = f.cy + 14, right = f.cx + f.cw - 6;             // 6px kept free for the scroll bar
            String[] head = {"renderer", "mod", "ms/frame", "% frame", "calls/frame", "µs/call"};
            int[] col = {x, right - 300, right - 210, right - 150, right - 95, right - 40};
            for (int i = 0; i < head.length; i++) drawString(fontRenderer, "§8" + head[i], col[i], cy, 0xFFFFFF);
            cy += 12;
            int areaTop = cy, areaH = f.cy + f.ch - areaTop;
            List<String[]> rows = RenderProfiler.report(Integer.MAX_VALUE);   // all of them; the list scrolls
            visible = Math.max(1, areaH / 11); total = rows.size();
            scroll = Math.max(0, Math.min(scroll, total - visible));
            double worst = rows.isEmpty() ? 1 : Math.max(0.01, Double.parseDouble(rows.get(0)[2]));
            PrideFrame.clip(f.cx - 4, areaTop - 1, f.cw + 8, areaH + 1);
            for (int n = scroll; n < rows.size() && n < scroll + visible + 1; n++) {
                String[] r = rows.get(n);
                double ms = Double.parseDouble(r[2]);
                drawRect(x - 2, cy - 1, x - 2 + (int) ((right - x + 4) * ms / worst), cy + 9, 0x30F5A9B8);   // bar = share of the worst one
                drawString(fontRenderer, fontRenderer.trimStringToWidth(r[0], col[1] - col[0] - 6), col[0], cy, r[0].startsWith("machine") ? 0xFFFFD27F : 0xFF9FD8FF);
                for (int i = 1; i < r.length; i++) drawString(fontRenderer, r[i], col[i], cy, 0xFFDDDDDD);
                cy += 11;
            }
            PrideFrame.unclip();
            PrideFrame.scrollbar(f.cx + f.cw - 3, areaTop, areaH, scroll, visible, total);
            super.drawScreen(mx, my, pt);
        }

        @Override public boolean doesGuiPauseGame() { return false; }
    }
}
