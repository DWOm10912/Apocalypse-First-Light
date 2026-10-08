package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChargingStationBlock;
import com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Charging Station V1: the bench through the generic AFL Animated Block Mesh renderer (lamp set by the powered state), the
 * item lying on the tray, and, while the station has power and holds a chargeable item, the front displays at full
 * brightness: the charge bar (filling from the viewer's left, amber while charging, green when full) and the readout
 * ("NN%" or "FULL"). The bar samples the atlas' reserved white emissive block, tinted by vertex colour.
 * Frame: block centre, turned by FACING; source px (tools/build-charging-station-v1.mjs) map to (x - 16, y, z) / 16.
 */
public final class ChargingStationRenderer implements BlockEntityRenderer<ChargingStationBlockEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/charging_station.png");
    // generator BAR, READOUT, FRONT and GLOW (white block at 500..508 of the 512 atlas)
    private static final float BAR_X0 = 2.0F, BAR_X1 = 20.9F, BAR_Y0 = 10.2F, BAR_Y1 = 11.5F;
    private static final float READOUT_X0 = -3.3F, READOUT_X1 = 1.3F, READOUT_Y0 = 10.0F, READOUT_Y1 = 11.7F;
    private static final float FRONT = -6.72F, INSET = 0.25F, GLOW_UV = 504.0F / 512.0F;
    /** Text cap height on the readout, px; font glyphs are 7 units tall. */
    private static final float TEXT_HEIGHT = 1.15F;
    /** Lying flat, the item is lifted by about the Energy Battery's radius in its fixed view (2.3 px x 0.898). */
    private static final float ITEM_LIFT = 2.1F, ITEM_SCALE = 1.0F;
    private static final int CHARGING = 0xFFB040, FULL = 0x78E678;

    private final AflAnimatedBlockMeshRenderer<ChargingStationBlockEntity> body;
    private final ItemRenderer itemRenderer;
    private final Font font;

    public ChargingStationRenderer(BlockEntityRendererProvider.Context context) {
        this.body = new AflAnimatedBlockMeshRenderer<>(context);
        this.itemRenderer = context.getItemRenderer();
        this.font = context.getFont();
    }

    /** The chunk draws the resting parts at any distance: the glass, lit parts and goods stay with them longer. */
    @Override
    public int getViewDistance() {
        return com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer.VIEW_DISTANCE;
    }

    @Override
    public void render(ChargingStationBlockEntity station, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (station.getLevel() == null || !station.isMaster()) return;
        body.render(station, partialTick, pose, buffers, packedLight, packedOverlay);
        pose.pushPose();
        try {
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(station.meshFacing())));
            renderItem(station, pose, buffers, packedLight, packedOverlay);
            if (station.powered() && station.itemCapacity() > 0 && !AflShaderCompat.activeShadowPass())
                renderDisplays(station, pose, buffers);
        } finally {
            pose.popPose();
        }
    }

    private void renderItem(ChargingStationBlockEntity station, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = station.item();
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate((ChargingStationBlock.ITEM_X - 16.0D) / 16.0D, (ChargingStationBlock.ITEM_Y + ITEM_LIFT) / 16.0D,
                ChargingStationBlock.ITEM_Z / 16.0D);
        // the fixed view turned to face the front (as the cooler's items), then laid down: front up, top away from the viewer
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, station.getLevel(),
                (int) station.getBlockPos().asLong());
        pose.popPose();
    }

    private void renderDisplays(ChargingStationBlockEntity station, PoseStack pose, MultiBufferSource buffers) {
        int percent = ChargingStationBlockEntity.percent(station.itemEnergy(), station.itemCapacity());
        int rgb = percent >= 100 ? FULL : CHARGING;
        if (percent > 0) {
            float x1 = BAR_X1 - INSET, x0 = x1 - (x1 - (BAR_X0 + INSET)) * percent / 100.0F;
            quad(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), x0, BAR_Y0 + INSET, x1, BAR_Y1 - INSET,
                    FRONT - 0.02F, rgb);
        }
        String text = percent >= 100 ? "FULL" : percent + "%";
        float width = font.width(text);
        float scalePx = Math.min(TEXT_HEIGHT / 7.0F, (READOUT_X1 - READOUT_X0 - 0.5F) / width), s = scalePx / 16.0F;
        pose.pushPose();
        pose.translate(((READOUT_X0 + READOUT_X1) / 2.0F - 16.0F) / 16.0F, (READOUT_Y0 + READOUT_Y1) / 2.0F / 16.0F,
                (FRONT - 0.03F) / 16.0F);
        // a half turn about Z (not a mirror): text runs toward the viewer's right and faces -Z, the front
        pose.scale(-s, -s, s);
        font.drawInBatch(text, -width / 2.0F, -3.5F, 0xFF000000 | rgb, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, float x0, float y0, float x1, float y1, float z, int rgb) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float[][] corners = {{x1, y0}, {x0, y0}, {x0, y1}, {x1, y1}};
        for (float[] c : corners) {
            vertices.vertex(matrix, (c[0] - 16.0F) / 16.0F, c[1] / 16.0F, z / 16.0F).color(r, g, b, 255).uv(GLOW_UV, GLOW_UV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 0.0F, -1.0F)
                    .endVertex();
        }
    }
}
