package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.blockmesh.*;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile.*;
import com.antaurora.apofirstlight.client.mesh.*;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Native, asset-independent BER. No GeoBone, weapon controller, packets or mutable shared poses. */
public final class AflAnimatedBlockMeshRenderer<T extends BlockEntity & AflAnimatedMeshHost> implements BlockEntityRenderer<T> {
    public AflAnimatedBlockMeshRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                  int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) return;
        var profile = AflBlockMeshProfiles.get(entity.meshProfile());
        if (profile == null) return;
        var mesh = AflMeshCache.snapshot().get(profile.geometry());
        if (mesh == null) return;
        entity.refreshMeshAnimationTargets(); // Handles resource-generation changes, not a gameplay update.
        double time = entity.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        try {
            pose.translate(0.5, 0, 0.5);
            if (profile.horizontalFacing()) pose.mulPose(Axis.YP.rotationDegrees(AflBlockMeshProfile.facingDegrees(entity.meshFacing())));
            pose.translate(profile.origin().x, profile.origin().y, profile.origin().z);
            pose.scale((float)profile.scale().x, (float)profile.scale().y, (float)profile.scale().z);
            var cutout = RenderType.entityCutoutNoCull(profile.texture());
            draw(profile, mesh, entity.meshAnimation(), time, pose, buffers.getBuffer(cutout),
                    packedLight, packedOverlay, AflMeshPart.Layer.CUTOUT);
            if (mesh.hasTranslucent() && !AflShaderCompat.activeShadowPass()) {
                if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(cutout);
                var translucent = RenderType.entityNoOutline(profile.texture());
                draw(profile, mesh, entity.meshAnimation(), time, pose, buffers.getBuffer(translucent),
                        packedLight, packedOverlay, AflMeshPart.Layer.TRANSLUCENT);
                if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(translucent);
            }
        } finally { pose.popPose(); }
    }

    private static void draw(AflBlockMeshProfile profile, AflMeshModel mesh, AflBlockMeshAnimationState animation,
                             double time, PoseStack pose, VertexConsumer vertices, int light, int overlay,
                             AflMeshPart.Layer layer) {
        for (var part : profile.roots()) drawPart(part, Vec3.ZERO, mesh, animation, time, pose, vertices, light, overlay, layer);
    }

    private static void drawPart(Part part, Vec3 parentPivot, AflMeshModel mesh, AflBlockMeshAnimationState animation,
                                 double time, PoseStack pose, VertexConsumer vertices, int light, int overlay,
                                 AflMeshPart.Layer layer) {
        pose.pushPose();
        try {
            Transform rest = part.rest();
            Transform target = part.motion() == null ? Transform.IDENTITY : part.motion().target();
            double t = part.motion() == null ? 0 : animation.sample(part.motion().channel(), time);
            pose.translate(part.pivot().x - parentPivot.x + rest.translation().x + target.translation().x * t,
                    part.pivot().y - parentPivot.y + rest.translation().y + target.translation().y * t,
                    part.pivot().z - parentPivot.z + rest.translation().z + target.translation().z * t);
            rotate(pose, rest.rotation().x + target.rotation().x * t,
                    rest.rotation().y + target.rotation().y * t, rest.rotation().z + target.rotation().z * t);
            pose.scale((float)(rest.scale().x * (1 + (target.scale().x - 1) * t)),
                    (float)(rest.scale().y * (1 + (target.scale().y - 1) * t)),
                    (float)(rest.scale().z * (1 + (target.scale().z - 1) * t)));
            AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(part.bone(), layer), pose, vertices,
                    light, overlay, 1, 1, 1, 1, null);
            for (var child : part.children()) drawPart(child, part.pivot(), mesh, animation, time, pose, vertices, light, overlay, layer);
        } finally { pose.popPose(); }
    }

    private static void rotate(PoseStack pose, double x, double y, double z) {
        if (z != 0) pose.mulPose(Axis.ZP.rotationDegrees((float)z));
        if (y != 0) pose.mulPose(Axis.YP.rotationDegrees((float)y));
        if (x != 0) pose.mulPose(Axis.XP.rotationDegrees((float)x));
    }
}
