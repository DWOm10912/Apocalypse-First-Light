package com.antaurora.apofirstlight.client.creature;

import com.antaurora.apofirstlight.client.survivor.CorpsePoses;
import com.antaurora.apofirstlight.creature.infected.InfectedCorpse;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * An infected's body (InfectedCorpse): the shambler's mesh in its ragdoll pose (the server's snapshots, interpolated as a
 * survivor's, client/survivor/CorpsePoses, mapped onto the shambler's bones by InfectedModel#fromRagdoll), or without one
 * its rest pose lying on its back along the death yaw. Not darkened and not rotting yet (Z1d).
 */
public final class InfectedCorpseRenderer extends EntityRenderer<InfectedCorpse> {
    /** Lifts the lying body's back (2 px half depth) onto the ground. */
    private static final float LIFT = 0.2F;

    private final SkeletonPose skeleton = new SkeletonPose(SkeletonRig.get());
    private Matrix4f[] mats;

    public InfectedCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public void render(InfectedCorpse corpse, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        InfectedModel model = InfectedModel.shambler();
        if (model == null) return;
        if (mats == null) mats = model.newMatrices();
        float[] now = corpse.pose(), before = corpse.posePrevious();
        pose.pushPose();
        if (now != null && before != null) {
            CorpsePoses.pose(now, corpse.poseOrigin(), before, corpse.poseOriginPrevious(), partial, skeleton);
            model.fromRagdoll(skeleton, mats);
            // the pose is for a model drawn at the pose origin facing yaw 0 (SurvivorCorpseRenderer's transform)
            Vec3 o = corpse.poseOrigin();
            pose.translate(o.x - Mth.lerp(partial, corpse.xOld, corpse.getX()), o.y - Mth.lerp(partial, corpse.yOld, corpse.getY()),
                    o.z - Mth.lerp(partial, corpse.zOld, corpse.getZ()));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        } else {
            model.rest(mats);
            pose.translate(0.0F, LIFT, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.bodyYaw()));
            pose.translate(0.0F, 0.0F, -1.0F * InfectedModel.SCALE);
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        pose.scale(-InfectedModel.SCALE, -InfectedModel.SCALE, InfectedModel.SCALE);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.draw(mats, pose, buffers.getBuffer(RenderType.entityCutout(InfectedModel.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                1F, 1F, 1F, 1F);
        pose.popPose();
        super.render(corpse, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(InfectedCorpse corpse) {
        return InfectedModel.TEXTURE;
    }
}
