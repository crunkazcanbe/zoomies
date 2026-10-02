package com.dogpound.zoomies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The OptiFine-style video options that Celeritas / Celeritas Extra don't have: anisotropic filtering, FXAA
 * (Minecraft's own shaders/post/fxaa.json), mipmap smoothing, zoom, and dynamic FOV on/off. All live.
 */
public final class Graphics {
    private static final int GL_MAX_ANISOTROPY = 0x84FE, GL_MAX_ANISOTROPY_LIMIT = 0x84FF;
    public static final KeyBinding ZOOM = new KeyBinding("Zoom (hold, scroll to adjust)", Keyboard.KEY_GRAVE, "Zoomies");
    private static float zoom = 4f;
    private static boolean fxaaOn;
    private static int appliedAniso = -1, appliedTexture = -1;

    static void register() {
        ClientRegistry.registerKeyBinding(ZOOM);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Graphics());
    }

    /** anisotropic filtering + mipmap smoothing live on the block atlas; FXAA is a post shader */
    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getTextureMapBlocks() == null) return;
        int tex = mc.getTextureMapBlocks().getGlTextureId();
        int aniso = ZoomiesConfig.num("graphics.anisotropic", 1);
        boolean smooth = ZoomiesConfig.on("graphics.smoothMipmaps");
        GlStateManager.bindTexture(tex);
        if (tex != appliedTexture || aniso != appliedAniso) {                 // (re)apply after a resource reload or a change
            float max = GL11.glGetFloat(GL_MAX_ANISOTROPY_LIMIT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL_MAX_ANISOTROPY, Math.max(1f, Math.min(max > 0 ? max : 16f, aniso)));
            appliedTexture = tex; appliedAniso = aniso;
        }
        if (smooth && mc.gameSettings.mipmapLevels > 0)                        // Minecraft resets filters when it draws items: set every frame
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        boolean fxaa = ZoomiesConfig.on("graphics.fxaa");
        boolean active = mc.entityRenderer.isShaderActive();
        if (fxaa && !active) { mc.entityRenderer.loadShader(new ResourceLocation("shaders/post/fxaa.json")); fxaaOn = true; }
        else if (!fxaa && fxaaOn && active) { mc.entityRenderer.stopUseShader(); fxaaOn = false; }
    }

    /** dynamic FOV off = sprinting/speed/bows don't stretch the view (zoom still works) */
    @SubscribeEvent
    public void onFov(FOVUpdateEvent e) {
        if (!ZoomiesConfig.on("graphics.dynamicFov")) e.setNewfov(1.0f);
    }

    /** hold the zoom key: FOV divided by the zoom level; scroll while holding to zoom in/out */
    @SubscribeEvent
    public void onViewFov(EntityViewRenderEvent.FOVModifier e) {
        if (ZOOM.isKeyDown() && Minecraft.getMinecraft().currentScreen == null) e.setFOV(e.getFOV() / zoom);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.getDwheel() != 0 && ZOOM.isKeyDown()) {
            zoom = Math.max(1.5f, Math.min(20f, zoom * (e.getDwheel() > 0 ? 1.2f : 1 / 1.2f)));
            e.setCanceled(true);                                                // don't also change the hotbar slot
        }
    }

    private static boolean smoothWas;

    /** smoother, slower mouse while zoomed (like OptiFine's cinematic zoom) */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean z = ZOOM.isKeyDown() && mc.currentScreen == null && ZoomiesConfig.on("graphics.smoothZoom");
        if (z && !smoothWas) { smoothWas = true; mc.gameSettings.smoothCamera = true; }
        else if (!z && smoothWas) { smoothWas = false; mc.gameSettings.smoothCamera = false; }
    }
}
