package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.cabin.BoxStove;
import com.antaurora.apofirstlight.cabin.BoxStoveBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Box Stove V1 (docs/models/box_stove_v1.md): what cooks on a box stove's top, lying flat on its two spots (the display
 * frame model at 18 cm, each a little turned), in the stove's north frame turned to its facing; and the loading door, an
 * animated mesh (2026-10-15), through the generic renderer. The fire, its glow and the smoke are drawn in the world pass
 * ({@link BoxStoveFire}).
 */
public final class BoxStoveRenderer implements BlockEntityRenderer<BoxStoveBlockEntity> {
    private final AflAnimatedBlockMeshRenderer<BoxStoveBlockEntity> door;

    public BoxStoveRenderer(BlockEntityRendererProvider.Context context) {
        door = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public int getViewDistance() {
        return AflAnimatedBlockMeshRenderer.VIEW_DISTANCE;
    }

    @Override
    public void render(BoxStoveBlockEntity stove, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        door.render(stove, partialTick, pose, buffers, light, overlay);
        int turn = (stove.getBlockState().getValue(BoxStove.Stove.FACING).get2DDataValue() + 2) & 3;
        for (int k = 0; k < BoxStove.SPOTS.length; k++) {
            ItemStack s = stove.item(k);
            if (s.isEmpty()) continue;
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90 * turn));
            pose.translate(BoxStove.SPOTS[k][0] - 0.5, BoxStove.TOP + 0.0135, BoxStove.SPOTS[k][1] - 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(k == 0 ? 12 : -9));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.18F, 0.18F, 0.18F);
            Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    stove.getLevel(), (int) stove.getBlockPos().asLong() + k);
            pose.popPose();
        }
    }
}
