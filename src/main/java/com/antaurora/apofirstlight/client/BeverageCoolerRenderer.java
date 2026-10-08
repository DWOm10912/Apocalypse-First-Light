package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerLayout;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.antaurora.apofirstlight.client.goods.AflGoodsLibrary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Beverage Cooler V2: the goods (the shared goods library, drinks, on the shelf cells that show), then the cabinet through
 * the generic AFL Animated Block Mesh renderer. The goods are flushed first, because the generic renderer flushes the
 * translucent door glass immediately: drawn after it, they would show untinted in front of the glass.
 */
public final class BeverageCoolerRenderer implements BlockEntityRenderer<BeverageCoolerBlockEntity> {
    private static final int LIT_ITEM_BLOCK_LIGHT = 14;
    /** A shelf cell is 4.72 px wide and 4.3 px clear: the nominal products a little smaller. */
    private static final float GOODS_SCALE = 0.93F;
    /** Cell frame: the block centre turned by FACING, source px x maps to model x - 16 (the master cell's middle); column centre, deck top, the deck's front edge. */
    private static final AflGoodsLibrary.CellPosition CELLS = cell -> new double[]{
            (BeverageCoolerLayout.columnX(cell % BeverageCoolerLayout.COLUMNS) - 16.0D) / 16.0D,
            BeverageCoolerLayout.shelfTop(cell / BeverageCoolerLayout.COLUMNS) / 16.0D,
            (BeverageCoolerLayout.DECK_Z0 + 0.1D) / 16.0D};
    private final AflAnimatedBlockMeshRenderer<BeverageCoolerBlockEntity> cabinet;

    public BeverageCoolerRenderer(BlockEntityRendererProvider.Context context) {
        this.cabinet = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public boolean shouldRenderOffScreen(BeverageCoolerBlockEntity x) {
        return true;
    }

    /** The chunk draws the resting parts at any distance: the glass, lit parts and goods stay with them longer. */
    @Override
    public int getViewDistance() {
        return com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer.VIEW_DISTANCE;
    }

    @Override
    public void render(BeverageCoolerBlockEntity cooler, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (cooler.getLevel() == null) return;
        PlugCordRenderer.render(cooler.plugCord(), cooler.getLevel(), cooler.getBlockPos(), partialTick, pose, buffers);   // Power Outlets V1: the power cord
        var spots = cooler.shownGoods();
        if (!spots.isEmpty()) {
            // lit cabinet: the goods take the LED light (block light 14) whatever the room's light
            int goodsLight = cooler.getBlockState().getValue(BeverageCoolerBlock.LIT)
                    ? LightTexture.pack(Math.max(LightTexture.block(packedLight), LIT_ITEM_BLOCK_LIGHT), LightTexture.sky(packedLight))
                    : packedLight;
            pose.pushPose();
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(cooler.getBlockState().getValue(BeverageCoolerBlock.FACING))));
            AflGoodsLibrary.draw(spots, CELLS, GOODS_SCALE, pose, buffers, goodsLight, packedOverlay);
            pose.popPose();
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        }
        cabinet.render(cooler, partialTick, pose, buffers, packedLight, packedOverlay);
    }
}
