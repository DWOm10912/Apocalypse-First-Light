package com.antaurora.apofirstlight.client.creature;

import com.antaurora.apofirstlight.creature.deer.DeerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A living white-tailed deer (DeerModel): the buck's or the doe's mesh in its layered pose, in LivingEntityRenderer's frame
 * (turned to the body's yaw, flipped, 0.9375, the model origin 1.501 up), as the infected's. No red hurt tint and no
 * tipping over at death (the carcass is its own entity by then).
 */
public final class DeerRenderer extends EntityRenderer<DeerEntity> {
    private Matrix4f[] mats;

    public DeerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(DeerEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        DeerModel model = DeerModel.of(e.doe());
        if (model != null) {
            if (mats == null || mats.length != model.bones.length) mats = model.newMatrices();
            model.animate(e, partial, mats);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
            pose.scale(-DeerModel.SCALE, -DeerModel.SCALE, DeerModel.SCALE);
            pose.translate(0.0F, -1.501F, 0.0F);
            model.draw(mats, pose, buffers.getBuffer(RenderType.entityCutout(model.texture(e, partial))), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DeerEntity e) {
        DeerModel model = DeerModel.of(e.doe());
        return model != null ? model.texture : new ResourceLocation("missingno");
    }
}
