package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.blockmesh.*;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile.*;
import com.antaurora.apofirstlight.client.mesh.*;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Native, asset-independent BER. No GeoBone, weapon controller, packets or mutable shared poses. The host decides per
 * frame which profile parts are visible and which draw at full brightness ({@link AflAnimatedMeshHost#meshPartVisible},
 * {@link AflAnimatedMeshHost#meshPartEmissive}).
 */
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
            long timing = com.antaurora.apofirstlight.client.AflRenderProfiler.begin();
            draw(entity, profile, mesh, time, pose, buffers.getBuffer(cutout),
                    packedLight, packedOverlay, AflMeshPart.Layer.CUTOUT);
            com.antaurora.apofirstlight.client.AflRenderProfiler.end("mesh.cutout", timing);
            if (mesh.hasTranslucent() && !AflShaderCompat.activeShadowPass()) {
                timing = com.antaurora.apofirstlight.client.AflRenderProfiler.begin();
                if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(cutout);
                var translucent = RenderType.entityNoOutline(profile.texture());
                draw(entity, profile, mesh, time, pose, buffers.getBuffer(translucent),
                        packedLight, packedOverlay, AflMeshPart.Layer.TRANSLUCENT);
                if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(translucent);
                // Emissive parts once more, after the glass. Packs without gbuffers_block_translucent (Sundial) draw the
                // glass with gbuffers_block, which overwrites the material buffers behind it; the redraw writes the
                // emissive parts' material back (see RelitType for why it lands after the glass).
                var relit = RelitType.of(profile.texture());
                VertexConsumer relitVertices = null;
                for (var part : profile.roots()) {
                    if (!hasVisibleEmissive(entity, part)) continue;
                    if (relitVertices == null) relitVertices = buffers.getBuffer(relit);
                    drawPart(entity, part, Vec3.ZERO, mesh, time, pose, relitVertices, packedLight, packedOverlay,
                            AflMeshPart.Layer.CUTOUT, true);
                }
                if (relitVertices != null && buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(relit);
                com.antaurora.apofirstlight.client.AflRenderProfiler.end("mesh.glass_and_relit", timing);
            }
        } finally { pose.popPose(); }
    }

    private static boolean hasVisibleEmissive(AflAnimatedMeshHost host, Part part) {
        if (!host.meshPartVisible(part.bone())) return false;
        if (host.meshPartEmissive(part.bone())) return true;
        for (var child : part.children()) if (hasVisibleEmissive(host, child)) return true;
        return false;
    }

    private static void draw(AflAnimatedMeshHost host, AflBlockMeshProfile profile, AflMeshModel mesh, double time,
                             PoseStack pose, VertexConsumer vertices, int light, int overlay, AflMeshPart.Layer layer) {
        for (var part : profile.roots()) drawPart(host, part, Vec3.ZERO, mesh, time, pose, vertices, light, overlay, layer, false);
    }

    /** emissiveOnly: only the geometry of emissive parts (the after-glass redraw); the pose still walks every part. */
    private static void drawPart(AflAnimatedMeshHost host, Part part, Vec3 parentPivot, AflMeshModel mesh, double time,
                                 PoseStack pose, VertexConsumer vertices, int light, int overlay, AflMeshPart.Layer layer,
                                 boolean emissiveOnly) {
        if (!host.meshPartVisible(part.bone())) return;
        AflBlockMeshAnimationState animation = host.meshAnimation();
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
            boolean emissive = host.meshPartEmissive(part.bone());
            if (emissive || !emissiveOnly)
                AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(part.bone(), layer), pose, vertices,
                        emissive ? LightTexture.FULL_BRIGHT : light, overlay, 1, 1, 1, 1, null);
            for (var child : part.children())
                drawPart(host, child, part.pivot(), mesh, time, pose, vertices, light, overlay, layer, emissiveOnly);
        } finally { pose.popPose(); }
    }

    /**
     * The after-glass redraw of emissive parts. With shaders on, Oculus 6020952 batches every draw (its endBatch(type) is a
     * no-op) and orders render types by transparency class (opaque, opaque decal, general translucent, decal, ...); within
     * a class it only keeps call order for draws grouped per entity, and block entities are not grouped, so a second
     * plain translucent type may land before the glass. GLINT transparency puts this type in the decal class, drawn after
     * every general translucent type. Its shader is the entity translucent one, so a pack maps it to the same program as
     * the glass, and the pack's blend for that program (Sundial: replace, colour alpha-blended) applies. Without shaders
     * the explicit flushes keep the order and the additive glint blend lays the full-bright parts over the glass as light.
     */
    private static final class RelitType extends RenderType {
        private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(RelitType::build);

        private static RenderType build(ResourceLocation texture) {
            return create("afl_mesh_emissive_relit", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(GLINT_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }

        static RenderType of(ResourceLocation texture) {
            return TYPES.apply(texture);
        }

        private RelitType() {
            super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static void rotate(PoseStack pose, double x, double y, double z) {
        if (z != 0) pose.mulPose(Axis.ZP.rotationDegrees((float)z));
        if (y != 0) pose.mulPose(Axis.YP.rotationDegrees((float)y));
        if (x != 0) pose.mulPose(Axis.XP.rotationDegrees((float)x));
    }
}
