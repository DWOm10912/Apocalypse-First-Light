package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
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
 * Vending Machine V2: the goods (the shared goods library, drinks and snacks, one product per lane that shows), then the
 * cabinet, glass and lights through the generic AFL Animated Block Mesh renderer. The goods are flushed first, because the
 * generic renderer flushes the translucent glass immediately: drawn after it, they would show untinted in front of it.
 */
public final class VendingMachineRenderer implements BlockEntityRenderer<VendingMachineBlockEntity> {
    private static final int LIT_ITEM_BLOCK_LIGHT = 14;
    /** A lane is 3.2 px wide (the coil 2.5 px) and 4.45 px clear: the nominal products (4 px wide) at 0.76. */
    private static final float GOODS_SCALE = 0.76F;
    /** Lane frame: the block's bottom centre turned by FACING, source px / 16. */
    private static final AflGoodsLibrary.CellPosition LANES = lane -> new double[]{
            VendingMachineBlockEntity.laneX(lane) / 16.0D, VendingMachineBlockEntity.laneY(lane) / 16.0D,
            VendingMachineBlockEntity.LANE_FRONT_Z / 16.0D};
    private final AflAnimatedBlockMeshRenderer<VendingMachineBlockEntity> cabinet;

    public VendingMachineRenderer(BlockEntityRendererProvider.Context context) {
        this.cabinet = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public void render(VendingMachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (machine.getLevel() == null) return;
        var spots = machine.shownGoods();
        if (!spots.isEmpty()) {
            // lit cabinet: the goods take the LED strip's light (block light 14) whatever the room's light
            int goodsLight = machine.getBlockState().getValue(VendingMachineBlock.LIT)
                    ? LightTexture.pack(Math.max(LightTexture.block(packedLight), LIT_ITEM_BLOCK_LIGHT), LightTexture.sky(packedLight))
                    : packedLight;
            pose.pushPose();
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(machine.getBlockState().getValue(VendingMachineBlock.FACING))));
            AflGoodsLibrary.draw(spots, LANES, GOODS_SCALE, pose, buffers, goodsLight, packedOverlay);
            pose.popPose();
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        }
        cabinet.render(machine, partialTick, pose, buffers, packedLight, packedOverlay);
    }
}
