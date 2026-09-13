package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BeverageCoolerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final BeverageCoolerBlockEntity cooler = new BeverageCoolerBlockEntity(
            BlockPos.ZERO, AflBlocks.BEVERAGE_COOLER.get().defaultBlockState());

    public BeverageCoolerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0);
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(cooler, pose, buffers, light, overlay);
        pose.popPose();
    }
}
