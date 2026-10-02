package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.RetailShelfLayout;
import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.antaurora.apofirstlight.client.goods.AflGoodsLibrary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Retail shelf goods (2026-10-01): the shelf itself is a baked static model; this draws the shared goods library on the
 * deck cells that show (RetailShelfSingleBlockEntity#shownGoods), one product per cell standing at the front of its deck,
 * at the nominal size (a cell is 4.16 px wide and 4.9 px clear).
 */
public class RetailShelfSingleBlockEntityRenderer implements BlockEntityRenderer<RetailShelfSingleBlockEntity> {
    private static final float GOODS_SCALE = 1.0F;
    private static final AflGoodsLibrary.CellPosition CELLS = cell -> new double[]{
            RetailShelfLayout.columnX(cell % RetailShelfLayout.COLUMNS),
            RetailShelfLayout.deckTopUnits(cell / RetailShelfLayout.COLUMNS) / 16.0D,
            RetailShelfLayout.DECK_FRONT_Z + 0.1D / 16.0D};

    public RetailShelfSingleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RetailShelfSingleBlockEntity shelf, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (shelf.getLevel() == null || shelf.getBlockState().getValue(RetailShelfSingleBlock.HALF) != DoubleBlockHalf.LOWER) return;
        var spots = shelf.shownGoods();
        if (spots.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getModelRotation(shelf.getBlockState().getValue(RetailShelfSingleBlock.FACING))));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        AflGoodsLibrary.draw(spots, CELLS, GOODS_SCALE, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
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
