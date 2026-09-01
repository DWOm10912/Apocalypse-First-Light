package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class LeadChestItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final BlockEntityRenderDispatcher dispatcher;
    private final LeadChestBlockEntity leadChest;

    public LeadChestItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        leadChest = new LeadChestBlockEntity(BlockPos.ZERO, AflBlocks.LEAD_CHEST.get().defaultBlockState());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        dispatcher.renderItem(leadChest, poseStack, bufferSource, packedLight, packedOverlay);
    }
}
