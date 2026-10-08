package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Chest Freezer V2: the cabinet and lids through the generic AFL Animated Block Mesh renderer (status lights by the
 * powered state), and, while the freezer has power, the set temperature on the status display at full brightness.
 * Frame: block centre, turned by FACING; source px (tools/build-chest-freezer-v2.mjs) map to (x - 16, y, z) / 16.
 */
public final class ChestFreezerRenderer implements BlockEntityRenderer<ChestFreezerBlockEntity> {
    // generator DISPLAY: the window's face, source px
    private static final float DISPLAY_X0 = 18.0F, DISPLAY_X1 = 21.2F, DISPLAY_Y0 = 6.9F, DISPLAY_Y1 = 8.1F, DISPLAY_Z = -7.92F;
    /** Text cap height on the display, px; font glyphs are 7 units tall. */
    private static final float TEXT_HEIGHT = 0.75F;
    private static final String TEXT = "-18°C";
    private static final int TEXT_RGB = 0xA8F4EC;

    private final AflAnimatedBlockMeshRenderer<ChestFreezerBlockEntity> body;
    private final Font font;

    public ChestFreezerRenderer(BlockEntityRendererProvider.Context context) {
        this.body = new AflAnimatedBlockMeshRenderer<>(context);
        this.font = context.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(ChestFreezerBlockEntity x) {
        return true;
    }

    @Override
    public void render(ChestFreezerBlockEntity freezer, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (freezer.getLevel() == null) return;
        PlugCordRenderer.render(freezer.plugCord(), freezer.getLevel(), freezer.getBlockPos(), partialTick, pose, buffers);   // Power Outlets V1: the power cord
        body.render(freezer, partialTick, pose, buffers, packedLight, packedOverlay);
        if (!freezer.powered() || AflShaderCompat.activeShadowPass()) return;
        float width = font.width(TEXT);
        float scalePx = Math.min(TEXT_HEIGHT / 7.0F, (DISPLAY_X1 - DISPLAY_X0 - 0.5F) / width), s = scalePx / 16.0F;
        pose.pushPose();
        try {
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(freezer.meshFacing())));
            pose.translate(((DISPLAY_X0 + DISPLAY_X1) / 2.0F - 16.0F) / 16.0F, (DISPLAY_Y0 + DISPLAY_Y1) / 2.0F / 16.0F,
                    (DISPLAY_Z - 0.03F) / 16.0F);
            // a half turn about Z (not a mirror): text runs toward the viewer's right and faces -Z, the front
            pose.scale(-s, -s, s);
            font.drawInBatch(TEXT, -width / 2.0F, -3.5F, 0xFF000000 | TEXT_RGB, false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        } finally {
            pose.popPose();
        }
    }
}
