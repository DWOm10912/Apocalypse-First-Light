package com.antaurora.apofirstlight.client.font;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Vanilla's three text_intensity render types with linear filtering, for the atlas pages of font sets that hold the
 * AFL smooth font (docs/ui/afl_font_v1.md). Glyphs are rasterised 1:1 for the GUI, where linear and nearest sample the
 * same texels; scaled text (titles, signs, name tags) is interpolated instead of showing blocky texels. Same shaders as
 * vanilla, so shader packs treat them as text. A holder of the protected render state shards; never instantiated.
 */
public final class AflFontRenderTypes extends RenderType {
    private static final Function<ResourceLocation, GlyphRenderTypes> LINEAR = Util.memoize(AflFontRenderTypes::create);

    private AflFontRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                               Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder of render types");
    }

    public static GlyphRenderTypes linear(ResourceLocation texture) {
        return LINEAR.apply(texture);
    }

    private static GlyphRenderTypes create(ResourceLocation texture) {
        RenderType normal = RenderType.create("apocalypse_firstlight_text_intensity", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_TEXT_INTENSITY_SHADER)
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));
        RenderType seeThrough = RenderType.create("apocalypse_firstlight_text_intensity_see_through",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_TEXT_INTENSITY_SEE_THROUGH_SHADER)
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        RenderType polygonOffset = RenderType.create("apocalypse_firstlight_text_intensity_polygon_offset",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_TEXT_INTENSITY_SHADER)
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false));
        return new GlyphRenderTypes(normal, seeThrough, polygonOffset);
    }
}
