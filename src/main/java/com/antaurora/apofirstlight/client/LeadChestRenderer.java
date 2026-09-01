package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public class LeadChestRenderer implements BlockEntityRenderer<LeadChestBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "textures/entity/storage/lead_chest.png");
    private final LeadChestModel model = new LeadChestModel();

    public LeadChestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LeadChestBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(ChestBlock.FACING)
                ? state.getValue(ChestBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        // The source model's latch is on NORTH (-Z). Rotate that authored front to block FACING.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.render(poseStack, consumer, packedLight, packedOverlay, blockEntity.getOpenNess(partialTick));
        poseStack.popPose();
    }
}
