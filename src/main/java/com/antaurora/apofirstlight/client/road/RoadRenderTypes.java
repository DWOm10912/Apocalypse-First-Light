package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.client.light.DynamicLitPrograms;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types of the generated road mesh (C3.2; the accepted R4 sample's R4RenderTypes, formalised): the terrain solid
 * program and the mipmapped block atlas (the same lighting and shader-pack path as the surrounding terrain), each with
 * a polygon offset that scales with the depth precision at its distance (while the dynamic lights are drawn, AFL's copy of
 * that program which adds them: client/light/DynamicLitPrograms, docs/rendering/dynamic_lights_v1.md):
 * <ul>
 *   <li>ASPHALT (-1, -10, vanilla's layering offset): the turf 2 cm and the collision tops about 3 cm under the pavement
 *   would show through it far away;</li>
 *   <li>MARKING (-2, -30): lines lie exactly on the asphalt and must win against the already offset asphalt.</li>
 * </ul>
 * A holder of the protected render state shards; never instantiated.
 */
public final class RoadRenderTypes extends RenderType {
    private static final LayeringStateShard MARKING_LAYERING = new LayeringStateShard("afl_road_marking_layering", () -> {
        RenderSystem.polygonOffset(-2.0F, -30.0F);
        RenderSystem.enablePolygonOffset();
    }, () -> {
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
    });

    private static final ShaderStateShard SOLID_SHADER = new ShaderStateShard(DynamicLitPrograms::solid);

    public static final RenderType ASPHALT = create("afl_road_asphalt", POLYGON_OFFSET_LAYERING);
    public static final RenderType MARKING = create("afl_road_marking", MARKING_LAYERING);

    private static RenderType create(String name, LayeringStateShard layering) {
        return RenderType.create(name, DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 262144, true, false, CompositeState.builder()
                .setLightmapState(LIGHTMAP)
                .setShaderState(SOLID_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setLayeringState(layering)
                .createCompositeState(true));
    }

    private RoadRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                            Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
