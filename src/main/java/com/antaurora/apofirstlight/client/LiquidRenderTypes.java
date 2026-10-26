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
     * Bullet holes (BulletHoles): the hole sheet, a block atlas sprite (textures/block/bullet_holes: mipmapped, with LabPBR
     * _s / _n), the entity-translucent shader, lit like the cell in front, colour only like the stains (lying on a surface,
     * they must not fight it over depth).
     */
    public static final RenderType HOLE = RenderType.create("apocalypse_firstlight_bullet_hole", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Embers glowing in burnt ground (Scorches): block atlas sprites (scorch_embers_*), the emissive entity shader, added
     * onto what is behind them like the flames, no depth writes.
     */
    public static final RenderType EMBER = RenderType.create("apocalypse_firstlight_scorch_ember", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** The glow of a fire on the floor under it (FuelFlames#glow): textures/effect/fire_glow, emissive, added, no depth writes. */
    public static final RenderType GLOW = RenderType.create("apocalypse_firstlight_fire_glow", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/fire_glow.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * The T15's beam cones and tail lamp ground glow (client/vehicle/HeadlightFx): as GLOW, with its own smooth flare
     * texture (tools/build-headlight-flare.mjs) drawn linear-filtered; both read only its full white middle now (the
     * flare sprites over the lamps were removed 2026-10-17).
     */
    public static final RenderType HEADLIGHT_GLOW = RenderType.create("apocalypse_firstlight_headlight_glow", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/headlight_flare.png"), true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * A lit lamp's lens over itself (the T15, PickupTruckRenderer; design doc 8.10), as vanilla's glowing eyes: its own
     * texture added onto the lens (ADDITIVE: ONE, ONE), no depth writes, pulled a hair toward the camera so it never
     * fights the lens it lies on. Without a shader pack there is no bloom: this is what makes a lit lens read as a light.
     */
    private static final java.util.function.Function<net.minecraft.resources.ResourceLocation, RenderType> LAMP_GLOW = net.minecraft.Util.memoize(
            texture -> RenderType.create("apocalypse_firstlight_lamp_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType lampGlow(net.minecraft.resources.ResourceLocation texture) { return LAMP_GLOW.apply(texture); }

    /** Burning things (EntityFlames): the burning-body flame sheet, otherwise as FLAME. */
    public static final RenderType BODY_FLAME = RenderType.create("apocalypse_firstlight_body_flame", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/body_flame.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** The flip lighter's flame (FlipLighterRenderer): the lighter flame flipbook, otherwise as BODY_FLAME. */
    public static final RenderType LIGHTER_FLAME = RenderType.create("apocalypse_firstlight_lighter_flame", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/lighter_flame.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Fire smoke (FireFx): the smoke sheet (textures/effect/fire_smoke), the entity-translucent shader (lit by the world),
     * alpha blended, sorted back to front when drawn, no depth writes (overlapping puffs blend, never cut into each other).
     */
    public static final RenderType SMOKE = RenderType.create("apocalypse_firstlight_fire_smoke", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/fire_smoke.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Rock dust off a pick strike (FireFx#rockDust, 2026-10-24): as {@link #SMOKE} with the dust sheet
     * (textures/effect/rock_dust: thin powdery haze with grit specks, tools/rock/build-rock-dust-sprites.mjs).
     */
    public static final RenderType DUST = RenderType.create("apocalypse_firstlight_rock_dust", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/rock_dust.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Sparks off steel (FireFx): streaks along their flight (textures/effect/spark), emissive, added, no depth writes. */
    public static final RenderType SPARK = RenderType.create("apocalypse_firstlight_metal_spark", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "textures/effect/spark.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
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
