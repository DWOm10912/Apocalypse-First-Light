package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshPart;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The rolling office chair (OfficeChairEntity) from its AFL mesh sidecar (meshes/modern_office_chair.aflmesh.json, written
 * by tools/build-office-props-v2.mjs; the same atlas as the placed block's OBJ). Bones: 'base' turned to the chair's base
 * yaw, 'caster_0'..'caster_4' turned about their stems on top of that, 'swivel' turned to the seat yaw (the sitter's view).
 * The model's front is -Z, so a world yaw psi is a model turn of 180 - psi about +Y.
 */
public final class OfficeChairRenderer extends EntityRenderer<OfficeChairEntity> {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/modern_office_chair.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/modern_office_chair.png");
    /** Bone pivots (px), = CHAIR.casterR / casterPivotY / swivelPivotY in the generator; leg k points 72k degrees from +Z toward +X. */
    private static final float CASTER_RADIUS = 5.55F, CASTER_Y = 1.2F, SWIVEL_Y = 4.6F;

    public OfficeChairRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.45F;
    }

    @Override
    public void render(OfficeChairEntity chair, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        AflMeshModel mesh = AflMeshCache.snapshot().get(GEOMETRY);
        if (mesh != null) {
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - chair.baseYaw()));
            draw(mesh, "base", pose, vertices, light);
            for (int k = 0; k < OfficeChairEntity.CASTERS; k++) {
                double a = Math.toRadians(72.0 * k);
                pose.pushPose();
                pose.translate(CASTER_RADIUS * Math.sin(a) / 16.0, CASTER_Y / 16.0, CASTER_RADIUS * Math.cos(a) / 16.0);
                pose.mulPose(Axis.YP.rotationDegrees(chair.casterYaw(k, partialTick)));
                draw(mesh, "caster_" + k, pose, vertices, light);
                pose.popPose();
            }
            pose.popPose();
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - chair.seatYaw(partialTick)));
            pose.translate(0.0, SWIVEL_Y / 16.0, 0.0);
            draw(mesh, "swivel", pose, vertices, light);
            pose.popPose();
        }
        super.render(chair, entityYaw, partialTick, pose, buffers, light);
    }

    private static void draw(AflMeshModel mesh, String bone, PoseStack pose, VertexConsumer vertices, int light) {
        AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(bone, AflMeshPart.Layer.CUTOUT), pose, vertices, light,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, null);
    }

    @Override
    public ResourceLocation getTextureLocation(OfficeChairEntity chair) {
        return TEXTURE;
    }
}
