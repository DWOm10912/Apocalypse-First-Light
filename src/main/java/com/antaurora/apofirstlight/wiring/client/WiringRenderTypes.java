package com.antaurora.apofirstlight.wiring.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * The wiring view's render types (Building Wiring V1): flat-coloured quads, blended, both sides, no depth writes.
 * <ul>
 *   <li>{@link #XRAY}: no depth test, drawn through the blocks; the buffer sorts its quads back to front (the vertices are
 *   camera relative), so nearer tubes cover farther ones. The wires lie inside solid blocks: this is how they are seen at all.</li>
 *   <li>{@link #XRAY_IN_SIGHT} (phase 5): depth tested; the wiring view pulls its vertices toward the camera before it uses
 *   this, so a wire inside the wall or floor in front of the player passes and one behind more blocks does not.</li>
 * </ul>
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
    public static final RenderType XRAY_IN_SIGHT = RenderType.create("apocalypse_firstlight_wiring_in_sight", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private WiringRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                              Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder of render types");
    }
}
