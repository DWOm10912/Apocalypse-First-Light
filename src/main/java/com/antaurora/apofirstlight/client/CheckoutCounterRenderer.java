package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.CheckoutCounterBlock;
import com.antaurora.apofirstlight.blockentity.CheckoutCounterBlockEntity;
import com.antaurora.apofirstlight.client.goods.AflGoodsLibrary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Checkout Counter V1 goods: the counter itself is a baked static model; this draws the shared goods library on the cells
 * that show (CheckoutCounterBlockEntity#shownGoods), on straight pieces only (corner pieces are blind). Cells in the
 * canonical frame (facing north, px; tools/build-checkout-counter-v1.mjs COUNTER): 0..7 the cubbies (two bays x deck /
 * shelf x two products, fronts toward the cashier at +Z, scale 0.8), 8..22 the display trays (three trays x five,
 * fronts toward the customer, squat and shallow to fit the trays).
 */
public class CheckoutCounterRenderer implements BlockEntityRenderer<CheckoutCounterBlockEntity> {
    private static final float[][] CELLS = cells();

    private static float[][] cells() {
        float[][] out = new float[CheckoutCounterBlockEntity.CUBBY_CELLS + CheckoutCounterBlockEntity.TRAY_CELLS][];
        int k = 0;
        for (float y : new float[]{2.0F, 7.4F})
            for (float x : new float[]{-5.5F, -2.1F, 2.1F, 5.5F}) out[k++] = cell(x, y, 7.2F, 180.0F, 0.8F, 0.8F, 0.8F);
        for (float[] tray : new float[][]{{2.6F, 2.8F}, {6.2F, 2.6F}, {9.8F, 2.4F}})
            for (float x : new float[]{-5.7F, -2.85F, 0.0F, 2.85F, 5.7F})
                out[k++] = cell(x, tray[0] + 0.35F, -5.0F - tray[1] + 0.35F, 0.0F, 0.58F, 0.62F, (tray[1] - 0.45F) / 8.0F);
        return out;
    }

    /** Canonical px -> block units, with the yaw and the per-axis scale. */
    static float[] cell(float x, float y, float z, float yaw, float sx, float sy, float sz) {
        return new float[]{x / 16.0F + 0.5F, y / 16.0F, z / 16.0F + 0.5F, yaw, sx, sy, sz};
    }

    public CheckoutCounterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CheckoutCounterBlockEntity counter, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        var state = counter.getBlockState();
        if (counter.getLevel() == null || !(state.getBlock() instanceof CheckoutCounterBlock)
                || state.getValue(CheckoutCounterBlock.SHAPE) != CheckoutCounterBlock.Shape.STRAIGHT) return;
        var spots = counter.shownGoods();
        if (spots.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(modelRotation(state.getValue(CheckoutCounterBlock.FACING))));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        AflGoodsLibrary.drawPlaced(spots, cell -> cell < CELLS.length ? CELLS[cell] : null, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /** The blockstate's y rotation as a pose rotation (north 0, east 90 -> -90 degrees about +Y). */
    static float modelRotation(Direction facing) {
        return switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }
}
