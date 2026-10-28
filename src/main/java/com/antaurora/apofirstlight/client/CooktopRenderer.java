package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.antaurora.apofirstlight.cabin.CooktopBlockEntity;
import com.antaurora.apofirstlight.cabin.KitchenStoves;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A kitchen stove's cooktop (Cooking V1 K1c, cabin/KitchenStoves): the dutch oven or the skillet standing on it at real
 * size (its mesh, 1 px = 1 cm), on the spot's burner, in the stove's north frame turned to its facing; a skillet's handle
 * turned out to the stove's side and front ({@link KitchenStoves.Stove#handleYaw}). The stove itself is cabin pieces.
 */
public final class CooktopRenderer implements BlockEntityRenderer<CooktopBlockEntity> {
    private static final ResourceLocation OVEN_GEO = mesh("geo/cast_iron_dutch_oven.geo.json"), OVEN_TEX = mesh("textures/item/cast_iron_dutch_oven_mesh.png"),
            SKILLET_GEO = mesh("geo/cast_iron_skillet.geo.json"), SKILLET_TEX = mesh("textures/item/cast_iron_skillet_mesh.png");
    /**
     * The skillet pan's middle from its mesh's origin (px along +x, the handle's way): the mesh is centred on its bounds,
     * the rim's far side 19.04 px out (player_props/cast_iron_skillet bounds), the rim 15.36 px round.
     */
    private static final float SKILLET_MIDDLE = -3.68F;

    public CooktopRenderer(BlockEntityRendererProvider.Context context) {}

    private static ResourceLocation mesh(String path) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path); }

    @Override
    public void render(CooktopBlockEntity top, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(top.getBlockState().getBlock() instanceof KitchenStoves.Stove stove)) return;
        int turn = CabinBlocks.turn(top.getBlockState().getValue(KitchenStoves.Stove.FACING));
        for (int k = 0; k < stove.spots(); k++) {
            ItemStack s = top.item(k);
            boolean oven = s.is(AflItems.CAST_IRON_DUTCH_OVEN.get()), skillet = s.is(AflItems.CAST_IRON_SKILLET.get());
            if (!oven && !skillet) continue;
            MeshModel mesh = VextrisMeshes.byGeometry(oven ? OVEN_GEO : SKILLET_GEO);
            if (mesh == null) continue;
            double[] at = stove.spot(k, top.burner(k));
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90 * turn));
            pose.translate(at[0] - 0.5, at[1], at[2] - 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(oven ? 0 : stove.handleYaw(k)));
            pose.scale(0.16F, 0.16F, 0.16F);   // 1 mesh px = 1 cm
            if (skillet) pose.translate(-SKILLET_MIDDLE / 16F, 0, 0);
            MeshRenderer.renderParts(mesh.allParts(), pose, buffers.getBuffer(RenderType.entityCutoutNoCull(oven ? OVEN_TEX : SKILLET_TEX)), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        }
    }
}
