package com.antaurora.apofirstlight.client.skeleton;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * AFL Block-Mesh V2 (docs/dev/block_mesh_v2_b_ingame.md 8.1): the HD body skin's texture and render type. A 256 x 256 skin has 4
 * texels per model px, so without mip levels a body a few blocks away samples one texel in 16 and its colour blocks shimmer
 * (vanilla's 64 x 64 skins have 1). The skin is loaded with {@link #MIP_LEVELS} levels (128, 64: down to vanilla's density)
 * and drawn nearest-pixel up close, the two nearest levels blended far off (GL_NEAREST_MIPMAP_LINEAR, vanilla's own
 * mipmapped pixel-art filter); the atlas keeps its regions on 4-texel blocks with filled gutters (bm2_game.py), so a level
 * does not mix a region with its neighbour. The render type is entityCutoutNoCull's with the mipmapped texture state, the
 * same shader, so shader packs draw it as an entity. A holder of the protected render state shards; never instantiated.
 */
public final class BlockMeshSkin extends RenderType {
    static final int MIP_LEVELS = 2;
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(BlockMeshSkin::create);

    private BlockMeshSkin(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder of render types");
    }

    /** The body's render type for its skin (the texture registered with its mip levels on first use). */
    static RenderType body(ResourceLocation texture) {
        return TYPES.apply(texture);
    }

    private static RenderType create(ResourceLocation texture) {
        Minecraft.getInstance().getTextureManager().register(texture, new Texture(texture));
        return RenderType.create("apocalypse_firstlight_block_mesh_body", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                true, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, true))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true));
    }

    /** A PNG with MIP_LEVELS mip levels (MipmapGenerator, as the block atlas builds its own). */
    private static final class Texture extends AbstractTexture {
        private final ResourceLocation location;

        Texture(ResourceLocation location) {
            this.location = location;
        }

        @Override
        public void load(ResourceManager resources) throws IOException {
            NativeImage base;
            try (InputStream in = resources.open(location)) {
                base = NativeImage.read(NativeImage.Format.RGBA, in);
            }
            NativeImage[] levels = MipmapGenerator.generateMipLevels(new NativeImage[]{base}, MIP_LEVELS);
            TextureUtil.prepareImage(getId(), MIP_LEVELS, base.getWidth(), base.getHeight());
            for (int i = 0; i < levels.length; i++)
                levels[i].upload(i, 0, 0, 0, 0, levels[i].getWidth(), levels[i].getHeight(), false, false, true, true);
        }
    }
}
