package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** Plain-Item bridge into the Phase 1 AFL Mesh cache and CPU triangle backend. */
public final class AflStaticMeshItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final ResourceLocation geometry;
    private final ResourceLocation texture;
    private final double verticalOffset;
    private final double groundVerticalOffset;

    public AflStaticMeshItemRenderer() {
        this("12_gauge_round", "12_gauge_round_mesh", 0.32, 0.32);
    }

    public AflStaticMeshItemRenderer(String model, String atlas, double verticalOffset) {
        this(model, atlas, verticalOffset, verticalOffset);
    }

    public AflStaticMeshItemRenderer(String model, String atlas, double verticalOffset, double groundVerticalOffset) {
        this(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + model + ".geo.json"),
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/" + atlas + ".png"), verticalOffset, groundVerticalOffset);
    }

    /** Explicit atlas location, for block items that share their block's Pure Mesh atlas (textures/block/...). */
    public AflStaticMeshItemRenderer(ResourceLocation geometry, ResourceLocation texture, double verticalOffset, double groundVerticalOffset) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.geometry = geometry;
        this.texture = texture;
        this.verticalOffset = verticalOffset;
        this.groundVerticalOffset = groundVerticalOffset;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        // Both caches are replaced on F3+T. Never hold an old baked bone or sidecar across reloads.
        AflMeshModel mesh = AflMeshCache.snapshot().get(geometry);
        var geo = GeckoLibCache.getBakedModels().get(geometry);
        if (mesh == null || geo == null) return;

        RenderType type = RenderType.entityCutoutNoCull(texture);
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(buffers, type, context == ItemDisplayContext.GUI, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the original display transforms and translated -0.5 on each axis.
            // Mesh vertices are centered on X/Z=0; put them at the old item model's center.
            pose.translate(0.5, context == ItemDisplayContext.GROUND ? groundVerticalOffset : verticalOffset, 0.5);
            if (mesh.hasTranslucent()) {
                com.antaurora.apofirstlight.client.mesh.AflHybridMeshRendering.renderAtCurrentPose(
                        geometry, texture, pose, buffers, light, overlay);
            } else {
                for (GeoBone bone : geo.topLevelBones()) renderBone(mesh, bone, pose, vertices, light, overlay);
            }
        } finally {
            pose.popPose();
        }
        if (context == ItemDisplayContext.GUI && buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
    }

    private static void renderBone(AflMeshModel mesh, GeoBone bone, PoseStack pose,
                                   VertexConsumer vertices, int light, int overlay) {
        pose.pushPose();
        try {
            RenderUtils.prepMatrixForBone(pose, bone);
            AflMeshRenderer.render(mesh, bone, pose, vertices, light, overlay, 1, 1, 1, 1);
            if (!bone.isHidingChildren())
                for (GeoBone child : bone.getChildBones()) renderBone(mesh, child, pose, vertices, light, overlay);
        } finally {
            pose.popPose();
        }
    }
}
