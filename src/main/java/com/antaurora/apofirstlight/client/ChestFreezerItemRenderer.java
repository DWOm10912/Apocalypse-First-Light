package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ChestFreezerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final ChestFreezerBlockEntity freezer = new ChestFreezerBlockEntity(
            BlockPos.ZERO, AflBlocks.CHEST_FREEZER.get().defaultBlockState());

    public ChestFreezerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0);
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(freezer, pose, buffers, light, overlay);
        pose.popPose();
    }
}
