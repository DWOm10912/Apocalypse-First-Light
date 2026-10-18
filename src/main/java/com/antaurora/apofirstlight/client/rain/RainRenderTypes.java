package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Rain V1's render types (docs/rendering/rain_v1.md). */
final class RainRenderTypes extends RenderType {
    /**
     * Splashes (RainSplashes): the splash sheet (textures/effect/rain_splash), the entity-translucent program (lit by the
     * world and the dynamic lights), alpha blended, sorted, no depth writes.
     */
    static final RenderType SPLASH = RenderType.create("apocalypse_firstlight_rain_splash", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/effect/rain_splash.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private RainRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                            Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder of render types");
    }
}
