package com.antaurora.apofirstlight.client;

import com.antaurora.vextris.client.MeshGpuCache;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Item meshes drawn from the Vextris GPU cache, as the guns are ({@link MaintenanceGunRendering}): magazines (in the hand,
 * on the ground, in the gun), their top rounds, muzzle devices and the static mesh items. The CPU path submits every
 * vertex every frame, with a shader pack about 145 ns a vertex (docs/dev/vextris_gpu_cache_request.md), and a held item
 * is drawn several times a frame (the hand, the shadow pass, the hotbar): the BR-51 drum (6941 triangles) and the
 * 20-round magazine's two 7.62 top rounds (1160 each) cost up to 60 FPS in the hand (user 2026-10-21). Opaque meshes only
 * (the cache keeps no translucent layer, and glass must sort with the rest), and never into a geometry capture: the
 * inspection capture ({@link MaintenanceGunRendering.Capture}) and the chunk recordings of the gun rack, the ground items
 * and the cargo bed ({@link MaintenanceGunRendering.ClosedFaces}) take their vertices from the CPU path.
 */
public final class MeshItemCache {
    private MeshItemCache() {}

    /** Whether {@link #draw} takes this mesh into these buffers (else the caller draws it on the CPU path). */
    public static boolean usable(MeshModel mesh, MultiBufferSource buffers) {
        return mesh != null && !mesh.hasTranslucent() && MeshGpuCache.enabled()
                && !(buffers instanceof MaintenanceGunRendering.Capture) && !(buffers instanceof MaintenanceGunRendering.ClosedFaces);
    }

    /** The paired mesh of a geo where the pose stands, as GeckoMeshRenderer#render draws it; false: use the CPU path. */
    public static boolean draw(ResourceLocation geometry, ResourceLocation texture, PoseStack pose, MultiBufferSource buffers,
                               int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(geometry);
        BakedGeoModel baked = GeckoLibCache.getBakedModels().get(geometry);
        return baked != null && draw(mesh, baked, texture, pose, buffers, light, overlay);
    }

    public static boolean draw(MeshModel mesh, BakedGeoModel baked, ResourceLocation texture, PoseStack pose,
                               MultiBufferSource buffers, int light, int overlay) {
        if (!usable(mesh, buffers)) return false;
        for (GeoBone bone : baked.topLevelBones()) walk(mesh, bone, texture, pose, light, overlay);
        return true;
    }

    /** GeckoMeshRenderer's bone walk: neverRender subtrees skipped, a hidden bone's parts not drawn, its children unless it hides them. */
    private static void walk(MeshModel mesh, GeoBone bone, ResourceLocation texture, PoseStack pose, int light, int overlay) {
        if (Boolean.TRUE.equals(bone.shouldNeverRender())) return;
        pose.pushPose();
        try {
            RenderUtils.prepMatrixForBone(pose, bone);
            if (!bone.isHidden() && !mesh.parts(bone.getName(), MeshLayer.CUTOUT).isEmpty()) {
                pose.pushPose();
                RenderUtils.translateToPivotPoint(pose, bone);
                MeshGpuCache.drawBone(mesh, bone.getName(), MeshLayer.CUTOUT, MeshGpuCache.ALL_PARTS, texture, pose, light, overlay, 1, 1, 1, 1);
                pose.popPose();
            }
            if (!bone.isHidingChildren()) for (GeoBone child : bone.getChildBones()) walk(mesh, child, texture, pose, light, overlay);
        } finally {
            pose.popPose();
        }
    }
}
