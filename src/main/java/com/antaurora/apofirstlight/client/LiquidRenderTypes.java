package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Render types of liquid stains (2026-10-05): entity-translucent drawing from the block atlas that writes colour only,
 * not depth. Stains lie coplanar just off a surface; written into the depth buffer, overlapping ones fought over the same
 * depth and flickered (user, 2026-10-05). Without depth writes they are tested against the world only and simply blend in
 * the order drawn (oldest first, so a newer stain lies over an older one). A holder of the protected render state shards;
 * never instantiated.
 */
public final class LiquidRenderTypes extends RenderType {
    public static final RenderType DECAL = RenderType.create("apocalypse_firstlight_liquid_decal", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Burning fuel flames (FuelFlames): the flame sheet, the emissive entity shader (full bright, emissive under shaders),
     * added onto what is behind it in proportion to its alpha (LIGHTNING_TRANSPARENCY: SRC_ALPHA, ONE), no depth writes.
     */
    public static final RenderType FLAME = RenderType.create("apocalypse_firstlight_fuel_flame", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/fuel_flame.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Bullet holes (BulletHoles): the hole sheet (textures/effect/bullet_holes), the entity-translucent shader, lit like
     * the cell in front, colour only like the stains (lying on a surface, they must not fight it over depth).
     */
    public static final RenderType HOLE = RenderType.create("apocalypse_firstlight_bullet_hole", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/bullet_holes.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private LiquidRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                              Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder of render types");
    }
}
