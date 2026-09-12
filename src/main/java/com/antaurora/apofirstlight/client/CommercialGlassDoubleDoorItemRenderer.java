package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CommercialGlassDoubleDoorItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final CommercialGlassDoubleDoorBlockEntity door = new CommercialGlassDoubleDoorBlockEntity(
            BlockPos.ZERO, AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR.get().defaultBlockState());

    public CommercialGlassDoubleDoorItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
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
