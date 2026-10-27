package com.antaurora.apofirstlight.client.creature;

import com.antaurora.apofirstlight.creature.infected.InfectedEntity;
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
 * A living infected (InfectedModel): the shambler's mesh in its animated pose, in LivingEntityRenderer's frame (turned to
 * the body's yaw, flipped, 0.9375, the model origin 1.501 up). Not a LivingEntityRenderer: no red hurt tint, no tipping
 * over at death (the body is a corpse entity by then).
 */
public final class InfectedRenderer extends EntityRenderer<InfectedEntity> {
    private Matrix4f[] mats;

    public InfectedRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(InfectedEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        InfectedModel model = InfectedModel.shambler();
        if (model != null) {
            if (mats == null) mats = model.newMatrices();
            model.animate(e, partial, mats);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
            pose.scale(-InfectedModel.SCALE, -InfectedModel.SCALE, InfectedModel.SCALE);
            pose.translate(0.0F, -1.501F, 0.0F);
            model.draw(mats, pose, buffers.getBuffer(RenderType.entityCutout(InfectedModel.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                    1F, 1F, 1F, 1F);
            pose.popPose();
        }
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(InfectedEntity e) {
        return InfectedModel.TEXTURE;
    }
}
