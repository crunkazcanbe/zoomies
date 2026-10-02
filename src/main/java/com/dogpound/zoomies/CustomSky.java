package com.dogpound.zoomies;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * OptiFine Custom Sky: resource packs with optifine/sky/world0/sky1.properties (or mcpatcher/sky/...) draw sky-box
 * layers — stars, nebulas, auroras — that fade in and out with the time of day. Supports source, startFadeIn,
 * endFadeIn, startFadeOut, endFadeOut, blend, rotate, speed and axis.
 * ponytail: the weather/biomes/heights/days/transition keys are ignored; add when a pack we use needs them.
 */
public final class CustomSky {
    private static final Map<Integer, List<Layer>> BY_DIM = new HashMap<>();
    private static boolean listening;

    /** called from RenderGlobal.renderSky just before the sun is drawn (matrix already turned -90 on Y) */
    public static void render(WorldClient w, float partialTicks) {
        if (!ZoomiesConfig.on("textures.customSky")) return;
        List<Layer> layers = layers(w.provider.getDimension());
        if (layers.isEmpty()) return;
        float angle = w.getCelestialAngle(partialTicks), clear = 1 - w.getRainStrength(partialTicks);
        int time = (int) (w.getWorldTime() % 24000L);
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.disableAlpha();
        for (Layer l : layers) {
            float b = l.brightness(time) * clear;
            if (b <= 0.001f) continue;
            mc.getTextureManager().bindTexture(l.tex);
            l.blend(b);
            GlStateManager.pushMatrix();
            if (l.rotate) GlStateManager.rotate(angle * 360f * l.speed, l.axis[0], l.axis[1], l.axis[2]);
            box();
            GlStateManager.popMatrix();
        }
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO); // what vanilla had set
        GlStateManager.color(1, 1, 1, clear);
        GlStateManager.enableAlpha();
    }

    /** OptiFine's layout: the texture is a 3x2 grid of the six cube faces */
    private static void box() {
        GlStateManager.rotate(90, 1, 0, 0); GlStateManager.rotate(-90, 0, 0, 1); side(4);
        GlStateManager.pushMatrix(); GlStateManager.rotate(90, 1, 0, 0); side(1); GlStateManager.popMatrix();
        GlStateManager.pushMatrix(); GlStateManager.rotate(-90, 1, 0, 0); side(0); GlStateManager.popMatrix();
        GlStateManager.rotate(90, 0, 0, 1); side(5);
        GlStateManager.rotate(90, 0, 0, 1); side(2);
        GlStateManager.rotate(90, 0, 0, 1); side(3);
    }

    private static void side(int s) {
        double u = (s % 3) / 3.0, v = (s / 3) / 2.0;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(-100, -100, -100).tex(u, v).endVertex();
        b.pos(-100, -100, 100).tex(u, v + 0.5).endVertex();
        b.pos(100, -100, 100).tex(u + 1 / 3.0, v + 0.5).endVertex();
        b.pos(100, -100, -100).tex(u + 1 / 3.0, v).endVertex();
        t.draw();
    }

    private static synchronized List<Layer> layers(int dim) {
        IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        if (!listening && rm instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) rm).registerReloadListener(r -> { synchronized (CustomSky.class) { BY_DIM.clear(); } });
            listening = true;
        }
        return BY_DIM.computeIfAbsent(dim, d -> load(rm, d));
    }

    private static List<Layer> load(IResourceManager rm, int dim) {
        List<Layer> out = new ArrayList<>();
        for (String root : new String[]{"optifine/sky/world" + dim + "/", "mcpatcher/sky/world" + dim + "/"}) {
            for (int n = 1; n < 100; n++) {
                Properties p = new Properties();
                try (InputStream in = rm.getResource(new ResourceLocation(root + "sky" + n + ".properties")).getInputStream()) { p.load(in); }
                catch (Exception missing) { if (n > 1) break; else continue; }
                try { out.add(new Layer(p, root, n)); }
                catch (Exception bad) { System.out.println("[Zoomies] custom sky " + root + "sky" + n + ".properties skipped: " + bad); }
            }
            if (!out.isEmpty()) { System.out.println("[Zoomies] custom sky: " + out.size() + " layers for dimension " + dim); break; }
        }
        return out;
    }

    static final class Layer {
        final ResourceLocation tex;
        final int startIn, endIn, startOut, endOut;
        final boolean always, rotate;
        final float speed;
        final float[] axis = {0, 0, 1};
        final String blend;

        Layer(Properties p, String root, int n) {
            String src = p.getProperty("source", "sky" + n + ".png").trim();
            if (src.startsWith("./")) src = root + src.substring(2);
            else if (!src.contains("/")) src = root + src;
            tex = src.contains(":") ? new ResourceLocation(src) : new ResourceLocation("minecraft", src);
            always = p.getProperty("startFadeIn") == null;
            startIn = ticks(p.getProperty("startFadeIn", "0:00"));
            endIn = ticks(p.getProperty("endFadeIn", "0:00"));
            endOut = ticks(p.getProperty("endFadeOut", "0:00"));
            startOut = p.getProperty("startFadeOut") != null ? ticks(p.getProperty("startFadeOut")) : endOut - (endIn - startIn);
            rotate = Boolean.parseBoolean(p.getProperty("rotate", "true").trim());
            speed = Float.parseFloat(p.getProperty("speed", "1").trim());
            String[] a = p.getProperty("axis", "0 0 1").trim().split("\\s+");
            if (a.length == 3) for (int i = 0; i < 3; i++) axis[i] = Float.parseFloat(a[i]);
            blend = p.getProperty("blend", "add").trim().toLowerCase();
        }

        /** "HH:MM" -> world ticks (6:00 = tick 0) */
        static int ticks(String hhmm) {
            String[] s = hhmm.trim().split(":");
            int h = Integer.parseInt(s[0]), m = s.length > 1 ? Integer.parseInt(s[1]) : 0;
            return Math.floorMod(h * 1000 + m * 1000 / 60 - 6000, 24000);
        }

        float brightness(int t) {
            if (always) return 1;
            if (in(t, startIn, endIn)) return frac(t, startIn, endIn);
            if (in(t, endIn, startOut)) return 1;
            if (in(t, startOut, endOut)) return 1 - frac(t, startOut, endOut);
            return 0;
        }

        private static boolean in(int t, int a, int b) { return a <= b ? t >= a && t < b : t >= a || t < b; }
        private static float frac(int t, int a, int b) {
            int len = Math.floorMod(b - a, 24000);
            return len == 0 ? 1 : Math.floorMod(t - a, 24000) / (float) len;
        }

        void blend(float b) {
            int s, d;
            switch (blend) {
                case "alpha": s = GL11.GL_SRC_ALPHA; d = GL11.GL_ONE_MINUS_SRC_ALPHA; break;
                case "replace": s = GL11.GL_ONE; d = GL11.GL_ZERO; break;
                case "subtract": s = GL11.GL_ONE_MINUS_DST_COLOR; d = GL11.GL_ZERO; break;
                case "multiply": s = GL11.GL_DST_COLOR; d = GL11.GL_ONE_MINUS_SRC_ALPHA; break;
                case "dodge": s = GL11.GL_ONE; d = GL11.GL_ONE; break;
                case "burn": s = GL11.GL_ZERO; d = GL11.GL_ONE_MINUS_SRC_COLOR; break;
                case "screen": s = GL11.GL_ONE; d = GL11.GL_ONE_MINUS_SRC_COLOR; break;
                case "overlay": s = GL11.GL_DST_COLOR; d = GL11.GL_SRC_COLOR; break;
                default: s = GL11.GL_SRC_ALPHA; d = GL11.GL_ONE; // add
            }
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(s, d);
            boolean alphaFade = s == GL11.GL_SRC_ALPHA;
            GlStateManager.color(alphaFade ? 1 : b, alphaFade ? 1 : b, alphaFade ? 1 : b, alphaFade ? b : 1);
        }
    }
}
