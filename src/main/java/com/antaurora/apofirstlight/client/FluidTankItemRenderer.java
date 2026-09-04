package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.fluid.FluidTankStoredFluid;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTankItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final BlockRenderDispatcher blockRenderer;

    public FluidTankItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        blockRenderer = Minecraft.getInstance().getBlockRenderer();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState shellState = AflBlocks.FLUID_TANK.get().defaultBlockState();
        blockRenderer.renderSingleBlock(shellState, poseStack, bufferSource, packedLight, packedOverlay,
                ModelData.EMPTY, RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));

        FluidStack storedFluid = FluidTankStoredFluid.read(stack);
        if (storedFluid.isEmpty() || storedFluid.getAmount() <= 0) {
            return;
        }

        float fillRatio = Mth.clamp(
                (float) storedFluid.getAmount() / FluidTankBlockEntity.CAPACITY_MB,
                0.0F, 1.0F);
        float fluidTop = FluidTankRenderGeometry.singleTankFluidTop(fillRatio);
        FluidRenderHelper.renderTankCuboid(storedFluid, poseStack, bufferSource, packedLight, packedOverlay,
                FluidTankRenderGeometry.MIN_X, FluidTankRenderGeometry.MIN_Y, FluidTankRenderGeometry.MIN_Z,
                FluidTankRenderGeometry.MAX_X, fluidTop, FluidTankRenderGeometry.MAX_Z, true);
    }
}
