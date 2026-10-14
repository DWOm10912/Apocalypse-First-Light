package com.antaurora.apofirstlight.wiring.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * The wiring view's render type (Building Wiring V1): flat-coloured quads drawn through the blocks (no depth test, no depth
 * writes), blended, both sides. The wires lie inside solid blocks, so any depth test would hide all of them; instead the
 * buffer sorts its quads back to front (the vertices are camera relative), and nearer tubes cover farther ones.
 * A holder of the protected render state shards; never instantiated.
 */
public final class WiringRenderTypes extends RenderType {
    public static final RenderType XRAY = RenderType.create("apocalypse_firstlight_wiring_xray", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private WiringRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                              Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder of render types");
    }
}
