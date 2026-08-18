package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RetailShelfSingleBlockEntityRenderer implements BlockEntityRenderer<RetailShelfSingleBlockEntity> {
    private static final float ITEM_SCALE = 0.28F;
    private static final double FRONT_DEPTH = 0.78125D;
    private static final double[] COLUMN_X = {0.78D, 0.50D, 0.22D};
    private static final double[] LAYER_Y = {0.55D, 0.925D, 1.30D, 1.675D};

    private final ItemRenderer itemRenderer;

    public RetailShelfSingleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(RetailShelfSingleBlockEntity shelf, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (shelf.getLevel() == null
                || shelf.getBlockState().getValue(RetailShelfSingleBlock.HALF)
                != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getModelRotation(shelf.getBlockState().getValue(RetailShelfSingleBlock.FACING))));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        for (int slot = 0; slot < RetailShelfSingleBlockEntity.SIZE; slot++) {
            ItemStack stack = shelf.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            int layer = slot / 3;
            int column = slot % 3;
            poseStack.pushPose();
            poseStack.translate(COLUMN_X[column], LAYER_Y[layer], FRONT_DEPTH);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    poseStack, buffer, shelf.getLevel(), slot);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static float getModelRotation(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }
}
