package com.antaurora.apofirstlight.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.HashMap;
import java.util.Map;

/** Draws an independent static Hybrid Mesh in a caller-prepared local pose. */
public final class AflHybridMeshRendering {
    private static long textureCacheGeneration = -1;
    private static final Map<ResourceLocation, Boolean> textureExists = new HashMap<>();

    /**
     * The caller supplies the target bone/anchor pose, including its pivot translation.
     * No item display transform, item centering, or gun-root transform is applied here.
     */
    public static void renderAtCurrentPose(ResourceLocation geometry, ResourceLocation texture,
                                           PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // Both caches are generation-aware; do not retain a baked bone across resource reloads.
        var snapshot = AflMeshCache.snapshot();
        AflMeshModel mesh = snapshot.get(geometry);
        var geo = GeckoLibCache.getBakedModels().get(geometry);
        if (mesh == null || geo == null) return;
        if (textureCacheGeneration != snapshot.generation()) {
            textureExists.clear();
            textureCacheGeneration = snapshot.generation();
        }
        if (!textureExists.computeIfAbsent(texture,
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent())) return;

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        try {
            for (GeoBone bone : geo.topLevelBones())
                renderBone(mesh, bone, pose, vertices, light, overlay);
        } finally {
            pose.popPose();
        }
    }

    private static void renderBone(AflMeshModel mesh, GeoBone bone, PoseStack pose,
                                   VertexConsumer vertices, int light, int overlay) {
        pose.pushPose();
        try {
            RenderUtils.prepMatrixForBone(pose, bone);
            AflMeshRenderer.render(mesh, bone, pose, vertices, light, overlay, 1, 1, 1, 1);
            if (!bone.isHidingChildren())
                for (GeoBone child : bone.getChildBones())
                    renderBone(mesh, child, pose, vertices, light, overlay);
        } finally {
            pose.popPose();
        }
    }

    private AflHybridMeshRendering() {}
}
