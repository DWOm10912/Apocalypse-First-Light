package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class CommercialGlassDoubleDoorItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final CommercialGlassDoubleDoorBlockEntity door;

    /** block: the silver or the black door; the model picks the texture from the block entity's state. */
    public CommercialGlassDoubleDoorItemRenderer(Block block) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        door = new CommercialGlassDoubleDoorBlockEntity(BlockPos.ZERO, block.defaultBlockState());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(-0.5, 0.0, 0.0);
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(door, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }
}
