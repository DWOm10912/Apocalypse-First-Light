package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.BackBarShelfBlock;
import com.antaurora.apofirstlight.blockentity.BackBarShelfBlockEntity;
import com.antaurora.apofirstlight.client.goods.AflGoodsLibrary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Back Bar Shelf V1 goods: the shared goods library on the shelf cells that show (BackBarShelfBlockEntity#shownGoods):
 * four shelves x five products, fronts at the price channels, a little shallower than the nominal cell to fit the
 * 5 px shelves (canonical frame: facing north, px; tools/build-checkout-counter-v1.mjs BACK_BAR).
 */
public class BackBarShelfRenderer implements BlockEntityRenderer<BackBarShelfBlockEntity> {
    private static final float[][] CELLS = cells();

    private static float[][] cells() {
        float[][] out = new float[BackBarShelfBlockEntity.CELLS][];
        int k = 0;
        for (float shelf : new float[]{13.5F, 17.8F, 22.1F, 26.4F})
            for (float x : new float[]{-5.92F, -2.96F, 0.0F, 2.96F, 5.92F})
                out[k++] = CheckoutCounterRenderer.cell(x, shelf + 0.4F, 2.25F, 0.0F, 0.68F, 0.72F, 0.6F);
        return out;
    }

    public BackBarShelfRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BackBarShelfBlockEntity shelf, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (shelf.getLevel() == null || !(shelf.getBlockState().getBlock() instanceof BackBarShelfBlock)) return;
        var spots = shelf.shownGoods();
        if (spots.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(CheckoutCounterRenderer.modelRotation(shelf.getBlockState().getValue(BackBarShelfBlock.FACING))));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        AflGoodsLibrary.drawPlaced(spots, cell -> cell < CELLS.length ? CELLS[cell] : null, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
