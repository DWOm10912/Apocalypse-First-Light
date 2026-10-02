package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerLayout;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Beverage Cooler V2: the displayed items, then the cabinet through the generic AFL Animated Block Mesh renderer. The
 * items are flushed first, because the generic renderer flushes the translucent door glass immediately: drawn after it,
 * the items would show untinted in front of the glass.
 */
public final class BeverageCoolerRenderer implements BlockEntityRenderer<BeverageCoolerBlockEntity> {
    private static final int LIT_ITEM_BLOCK_LIGHT = 14;
    private final AflAnimatedBlockMeshRenderer<BeverageCoolerBlockEntity> cabinet;
    private final ItemRenderer itemRenderer;

    public BeverageCoolerRenderer(BlockEntityRendererProvider.Context context) {
        this.cabinet = new AflAnimatedBlockMeshRenderer<>(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BeverageCoolerBlockEntity cooler, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (cooler.getLevel() == null) return;
        if (!cooler.isEmpty()) {
            // lit cabinet: the goods take the LED light (block light 14) whatever the room's light
            int itemLight = cooler.getBlockState().getValue(BeverageCoolerBlock.LIT)
                    ? LightTexture.pack(Math.max(LightTexture.block(packedLight), LIT_ITEM_BLOCK_LIGHT), LightTexture.sky(packedLight))
                    : packedLight;
            renderItems(cooler, pose, buffers, itemLight, packedOverlay);
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        }
        cabinet.render(cooler, partialTick, pose, buffers, packedLight, packedOverlay);
    }

    /** Same frame as the mesh: block centre, turned by FACING; source x maps to model x - 16 (the master cell's middle). */
    private void renderItems(BeverageCoolerBlockEntity cooler, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        try {
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(cooler.getBlockState().getValue(BeverageCoolerBlock.FACING))));
            for (int slot = 0; slot < BeverageCoolerBlockEntity.SIZE; slot++) {
                ItemStack stack = cooler.getItem(slot);
                if (stack.isEmpty()) continue;
                int cell = BeverageCoolerLayout.cellOf(slot);
                pose.pushPose();
                pose.translate((BeverageCoolerLayout.columnX(cell % BeverageCoolerLayout.COLUMNS) - 16.0D) / 16.0D,
                        BeverageCoolerLayout.itemY(cell / BeverageCoolerLayout.COLUMNS) / 16.0D,
                        BeverageCoolerLayout.depthZ(BeverageCoolerLayout.depthOf(slot)) / 16.0D);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                pose.scale(BeverageCoolerLayout.ITEM_SCALE, BeverageCoolerLayout.ITEM_SCALE, BeverageCoolerLayout.ITEM_SCALE);
                itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, cooler.getLevel(),
                        (int) cooler.getBlockPos().asLong() + slot);
                pose.popPose();
            }
        } finally {
            pose.popPose();
        }
    }
}
