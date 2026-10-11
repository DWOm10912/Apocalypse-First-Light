package com.antaurora.apofirstlight.dev.highway.r4;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types of the R4 pavement mesh (docs/worldgen/highway_r4_sample_v1.md, C): the terrain solid program and the
 * mipmapped block atlas (the same lighting and shader-pack path as the surrounding terrain), each with a polygon offset
 * that scales with the depth precision at its distance:
 * <ul>
 *   <li>ASPHALT (-1, -10, vanilla's layering offset): the turf 5 cm and the collision columns' block tops 3 cm below the
 *   pavement would show through it beyond about 150 m (the depth step there is several centimetres);</li>
 *   <li>MARKING (-2, -30): lines lie exactly on the asphalt and must win against the already offset asphalt.</li>
 * </ul>
 * A holder of the protected render state shards; never instantiated.
 */
public final class R4RenderTypes extends RenderType {
    private static final LayeringStateShard MARKING_LAYERING = new LayeringStateShard("afl_dev_r4_marking_layering", () -> {
        RenderSystem.polygonOffset(-2.0F, -30.0F);
        RenderSystem.enablePolygonOffset();
    }, () -> {
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
    });

    public static final RenderType ASPHALT = create("afl_dev_r4_asphalt", POLYGON_OFFSET_LAYERING);
    public static final RenderType MARKING = create("afl_dev_r4_marking", MARKING_LAYERING);

    private static RenderType create(String name, LayeringStateShard layering) {
        return RenderType.create(name, DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 262144, true, false, CompositeState.builder()
                .setLightmapState(LIGHTMAP)
                .setShaderState(RENDERTYPE_SOLID_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setLayeringState(layering)
                .createCompositeState(true));
    }

    private R4RenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
