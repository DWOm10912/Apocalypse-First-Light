package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.earthwork.BulkHolder;
import com.antaurora.apofirstlight.item.ShovelItem;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The round point shovel (tools/build-round-point-shovel-v1.mjs; docs/worldgen/earthwork_a2.md 4.2): the Pure Mesh, and
 * on its blade the heap its load needs (the parts load_1 .. load_4: a quarter of the capacity each, rounded up), tinted
 * by the load's material, so the held shovel, the skeleton's prop (ItemDisplayContext.NONE) and one on the ground look
 * the same. The inventory shows the empty shovel's rendered icon; its tooltip gives the load.
 */
public final class ShovelItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/round_point_shovel.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/round_point_shovel_mesh.png");
    /** 0.5 - mesh height 24.042 / 32 (the build's verticalOffset). */
    public static final double VERTICAL_OFFSET = -0.251321;
    /**
     * The heap's tint by material (multiplies the neutral soil texel, 188 182 172): topsoil dark brown, sand pale, gravel
     * grey, clay red-brown, mud near black, backfill a mixed brown. An unknown id draws as backfill.
     */
    private static final Map<String, float[]> TINT = Map.of(
            "topsoil", new float[]{0.42F, 0.33F, 0.24F},
            "sand", new float[]{0.98F, 0.88F, 0.66F},
            "gravel", new float[]{0.62F, 0.61F, 0.58F},
            "clay", new float[]{0.72F, 0.48F, 0.36F},
            "mud", new float[]{0.30F, 0.25F, 0.19F},
            "backfill", new float[]{0.48F, 0.40F, 0.30F});

    private record Parts(MeshModel mesh, List<MeshPart> fixed, MeshPart[] heaps) {}
    private Parts parts;

    public ShovelItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        VertexConsumer out = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
            pose.translate(0.5, VERTICAL_OFFSET, 0.5);
            MeshRenderer.renderParts(p.fixed, pose, out, light, overlay, 1, 1, 1, 1, null);
            BulkHolder load = ShovelItem.load(stack);
            int level = heap(load.stored());
            MeshPart heap = level > 0 ? p.heaps[level - 1] : null;
            if (heap != null) {
                String m = load.material();
                float[] c = TINT.getOrDefault(m == null ? "backfill" : m, TINT.get("backfill"));
                MeshRenderer.renderParts(List.of(heap), pose, out, light, overlay, c[0], c[1], c[2], 1, null);
            }
        } finally {
            pose.popPose();
        }
    }

    /** The heap drawn for a load (quanta): 0 none, 1 .. 4 by quarters of the capacity, rounded up. */
    static int heap(long stored) {
        if (stored <= 0) return 0;
        return (int) Math.min(4, (stored * 4 + ShovelItem.CAPACITY - 1) / ShovelItem.CAPACITY);
    }

    private Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> fixed = new ArrayList<>();
        MeshPart[] heaps = new MeshPart[4];
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) {
                String name = part.name();
                if (name.startsWith("load_")) {
                    int k = name.charAt(5) - '1';
                    if (k >= 0 && k < 4) heaps[k] = part;
                } else fixed.add(part);
            }
        }
        parts = new Parts(mesh, List.copyOf(fixed), heaps);
        return parts;
    }
}
