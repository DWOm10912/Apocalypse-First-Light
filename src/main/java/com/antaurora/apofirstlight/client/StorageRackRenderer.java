package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.StorageRackBlock;
import com.antaurora.apofirstlight.blockentity.StorageRackBlockEntity;
import com.antaurora.apofirstlight.client.goods.AflGoodsLibrary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Storage Rack V1 goods: the shared goods library on the deck cells that show (StorageRackBlockEntity#shownGoods): the
 * lower four decks x three products, fronts just behind the front beams, slightly larger than the nominal cell
 * (canonical frame: facing north, px; tools/build-storage-rack-v1.mjs RACK: decks at y 1.4 / 8.6 / 15.8 / 23.0, top 0.05 lower).
 */
public class StorageRackRenderer implements BlockEntityRenderer<StorageRackBlockEntity> {
    private static final float[][] CELLS = cells();

    private static float[][] cells() {
        float[][] out = new float[StorageRackBlockEntity.CELLS][];
        int k = 0;
        for (float deck : new float[]{1.35F, 8.55F, 15.75F, 22.95F})
            for (float x : new float[]{-4.4F, 0.0F, 4.4F})
                out[k++] = CheckoutCounterRenderer.cell(x, deck, -1.4F, 0.0F, 1.05F, 1.15F, 1.05F);
        return out;
    }

    public StorageRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StorageRackBlockEntity shelf, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (shelf.getLevel() == null || !(shelf.getBlockState().getBlock() instanceof StorageRackBlock)) return;
        var spots = shelf.shownGoods();
        if (spots.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(CheckoutCounterRenderer.modelRotation(shelf.getBlockState().getValue(StorageRackBlock.FACING))));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        AflGoodsLibrary.drawPlaced(spots, cell -> cell < CELLS.length ? CELLS[cell] : null, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
