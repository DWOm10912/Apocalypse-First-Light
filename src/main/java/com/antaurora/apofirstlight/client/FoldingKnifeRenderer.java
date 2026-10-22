package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.FoldingKnifeItem;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Folding knife V1 (docs/gameplay/folding_knife_v1.md 5; tools/build-equipment-meshes-v1.mjs folding_knife): the Pure Mesh
 * with its blade turned about the pivot pin from the stack's tag (item/FoldingKnifeItem#bladeDegrees), so the held knife,
 * the skeleton's prop (ItemDisplayContext.NONE) and one on the ground look the same. The blade folds edge first into the
 * handle (+deg about z: the tip swings over the edge side, -x). Closed in the inventory, the short knife is moved up to the
 * open knife's centre and drawn larger, so the icon (the open knife's gui transform) still fills the slot.
 */
public final class FoldingKnifeRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/folding_knife.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/folding_knife_mesh.png");
    /** 0.5 - mesh height 21.107 / 32 (the build's verticalOffset). */
    public static final double VERTICAL_OFFSET = -0.1596;
    /** The pivot pin (mesh px, baked: the build's points.pivot). */
    private static final float[] PIVOT = {-0.1048F, 11.6072F};
    /** The open knife's centre and the closed one's (mesh px, y), and how much larger the closed one is drawn in a slot. */
    private static final float OPEN_CENTRE = 10.55F, CLOSED_CENTRE = 6.28F, CLOSED_GUI_SCALE = 1.45F;

    private record Parts(MeshModel mesh, List<MeshPart> handle, List<MeshPart> blade) {}
    private Parts parts;

    public FoldingKnifeRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        var level = Minecraft.getInstance().level;
        float deg = level == null ? (FoldingKnifeItem.isOpen(stack) ? 0F : 180F)
                : FoldingKnifeItem.bladeDegrees(stack, level, Minecraft.getInstance().getFrameTime());
        VertexConsumer cutout = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
            pose.translate(0.5, VERTICAL_OFFSET, 0.5);
            if (context == ItemDisplayContext.GUI && deg > 0) {
                float k = deg / 180F, s = 1 + (CLOSED_GUI_SCALE - 1) * k;
                float from = OPEN_CENTRE + (CLOSED_CENTRE - OPEN_CENTRE) * k;
                pose.translate(0, OPEN_CENTRE / 16F, 0);
                pose.scale(s, s, s);
                pose.translate(0, -from / 16F, 0);
            }
            MeshRenderer.renderParts(p.handle, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            pose.pushPose();
            pose.translate(PIVOT[0] / 16F, PIVOT[1] / 16F, 0);
            if (deg != 0) pose.mulPose(Axis.ZP.rotationDegrees(deg));
            pose.translate(-PIVOT[0] / 16F, -PIVOT[1] / 16F, 0);
            MeshRenderer.renderParts(p.blade, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            pose.popPose();
        } finally {
            pose.popPose();
        }
    }

    /** One bone with its pivot at the origin (the equipment bake): the blade and the rest. */
    private Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> handle = new ArrayList<>(), blade = new ArrayList<>();
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) (part.name().equals("blade") ? blade : handle).add(part);
        }
        parts = new Parts(mesh, List.copyOf(handle), List.copyOf(blade));
        return parts;
    }
}
