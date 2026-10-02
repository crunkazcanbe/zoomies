package com.dogpound.zoomies;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.util.vector.Vector3f;

/**
 * OptiFine "Better Grass" (Fast): grass, snowy grass and mycelium show their top on all four sides instead of dirt with
 * a fringe. Works with Celeritas (it just asks the model for quads). Toggling it rebuilds the loaded chunks.
 * ponytail: Fast mode only — OptiFine's Fancy (only where the side touches more grass) needs world access per quad.
 */
public final class BetterGrass {
    private static final String[][] BLOCKS = {           // block, variant, side texture, tinted by biome
        {"minecraft:grass", "snowy=false", "minecraft:blocks/grass_top", "1"},
        {"minecraft:grass", "snowy=true", "minecraft:blocks/snow", "0"},
        {"minecraft:mycelium", "snowy=false", "minecraft:blocks/mycelium_top", "0"},
        {"minecraft:mycelium", "snowy=true", "minecraft:blocks/snow", "0"},
    };
    private static boolean shown;

    @SubscribeEvent
    public static void bake(ModelBakeEvent e) {
        TextureMap atlas = e.getModelManager().getTextureMap();
        FaceBakery fb = new FaceBakery();
        for (String[] b : BLOCKS) {
            ModelResourceLocation where = new ModelResourceLocation(b[0], b[1]);
            IBakedModel old = e.getModelRegistry().getObject(where);
            if (old == null) continue;
            TextureAtlasSprite sprite = atlas.getAtlasSprite(b[2]);
            Map<EnumFacing, List<BakedQuad>> sides = new EnumMap<>(EnumFacing.class);
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                BlockPartFace face = new BlockPartFace(f, "1".equals(b[3]) ? 0 : -1, b[2], new BlockFaceUV(new float[]{0, 0, 16, 16}, 0));
                sides.put(f, Collections.singletonList(fb.makeBakedQuad(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16), face, sprite, f, ModelRotation.X0_Y0, null, false, true)));
            }
            e.getModelRegistry().putObject(where, new Wrapped(old, sides));
        }
    }

    /** the switch lives in the config; flipping it redraws the chunks so the change shows right away */
    static void onToggle() {
        boolean on = ZoomiesConfig.on("textures.betterGrass");
        if (on == shown) return;
        shown = on;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.renderGlobal != null && mc.world != null) mc.renderGlobal.loadRenderers();
    }

    static final class Wrapped implements IBakedModel {
        final IBakedModel in;
        final Map<EnumFacing, List<BakedQuad>> sides;

        Wrapped(IBakedModel in, Map<EnumFacing, List<BakedQuad>> sides) { this.in = in; this.sides = sides; }

        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            if (side != null && side.getAxis().isHorizontal() && ZoomiesConfig.on("textures.betterGrass")) return sides.get(side);
            return in.getQuads(state, side, rand);
        }
        @Override public boolean isAmbientOcclusion() { return in.isAmbientOcclusion(); }
        @Override public boolean isAmbientOcclusion(IBlockState s) { return in.isAmbientOcclusion(s); }
        @Override public boolean isGui3d() { return in.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return in.isBuiltInRenderer(); }
        @Override public TextureAtlasSprite getParticleTexture() { return in.getParticleTexture(); }
        @Override @SuppressWarnings("deprecation") public ItemCameraTransforms getItemCameraTransforms() { return in.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return in.getOverrides(); }
    }
}
