package com.antaurora.apofirstlight.client.creature;

import com.antaurora.apofirstlight.creature.deer.DeerCorpse;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * A deer's carcass (DeerCorpse): the death clip played from the moment of the death (0.9 s: the forelegs fold, the hind
 * legs give, it rolls onto its side), then its last pose, along the death yaw; the eyes glazed under a half-shut lid
 * (the *_dead texture). Not darkened and not rotting yet.
 */
public final class DeerCorpseRenderer extends EntityRenderer<DeerCorpse> {
    private Matrix4f[] mats;

    public DeerCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.7F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public void render(DeerCorpse corpse, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        DeerModel model = DeerModel.of(corpse.doe());
        if (model == null) return;
        if (mats == null || mats.length != model.bones.length) mats = model.newMatrices();
        model.dead((corpse.level().getGameTime() - corpse.diedAt() + partial) / 20F, mats);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.bodyYaw()));
        pose.scale(-DeerModel.SCALE, -DeerModel.SCALE, DeerModel.SCALE);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.draw(mats, pose, buffers.getBuffer(RenderType.entityCutout(model.deadTexture)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(corpse, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DeerCorpse corpse) {
        DeerModel model = DeerModel.of(corpse.doe());
        return model != null ? model.deadTexture : new ResourceLocation("missingno");
    }
}
