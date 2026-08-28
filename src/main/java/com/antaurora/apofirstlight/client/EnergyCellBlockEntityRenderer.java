package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.EnergyCellBlock;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class EnergyCellBlockEntityRenderer implements BlockEntityRenderer<EnergyCellBlockEntity> {
    private static final ResourceLocation ENERGY_BAR_TEXTURE = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "textures/block/energy_cell_bar.png");

    private static final float TEXTURE_SIZE = 32.0F;
    private static final float BAR_LEFT = 13.0F / TEXTURE_SIZE;
    private static final float BAR_RIGHT = 19.0F / TEXTURE_SIZE;
    private static final float BAR_TOP_V = 4.0F / TEXTURE_SIZE;
    private static final float BAR_BOTTOM_V = 26.0F / TEXTURE_SIZE;
    private static final float BAR_BOTTOM_Y = 1.0F - BAR_BOTTOM_V;
    private static final float FRONT_OFFSET = -0.0015F;

    public EnergyCellBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EnergyCellBlockEntity cell, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        float ratio = cell.getDisplayedEnergyRatio();
        if (ratio <= 0.0F) {
            return;
        }

        float topV = BAR_BOTTOM_V - (BAR_BOTTOM_V - BAR_TOP_V) * ratio;
        float topY = 1.0F - topV;
        Direction facing = cell.getBlockState().getValue(EnergyCellBlock.FACING);
        int frontLight = cell.getLevel() == null
                ? packedLight
                : LevelRenderer.getLightColor(cell.getLevel(), cell.getBlockPos().relative(facing));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getModelRotation(facing)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(ENERGY_BAR_TEXTURE));
        vertex(vertices, pose, BAR_LEFT, BAR_BOTTOM_Y, FRONT_OFFSET, BAR_LEFT, BAR_BOTTOM_V,
                frontLight, packedOverlay);
        vertex(vertices, pose, BAR_RIGHT, BAR_BOTTOM_Y, FRONT_OFFSET, BAR_RIGHT, BAR_BOTTOM_V,
                frontLight, packedOverlay);
        vertex(vertices, pose, BAR_RIGHT, topY, FRONT_OFFSET, BAR_RIGHT, topV,
                frontLight, packedOverlay);
        vertex(vertices, pose, BAR_LEFT, topY, FRONT_OFFSET, BAR_LEFT, topV,
                frontLight, packedOverlay);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int packedLight, int packedOverlay) {
        vertices.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(pose.normal(), 0.0F, 0.0F, -1.0F)
                .endVertex();
    }

    private static float getModelRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }
}
