package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.item.TunnelFixtureItem;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tunnel fixture item (item/TunnelFixtureItem, V1.1) drawn as its part: the same mesh the world draws (TunnelParts),
 * turned to face north like a block item's front and scaled into the item's block (its longest side one block); the
 * item model (builtin/entity) gives the usual block item views.
 */
public final class TunnelFixtureItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final Map<String, float[]> cache = new ConcurrentHashMap<>();

    public TunnelFixtureItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof TunnelFixtureItem item)) return;
        TunnelFixtureBlock.Kind kind = item.kind();
        String bone = TunnelFixtureItem.bone(stack, kind);
        float[] eq = RoadClient.sprite(TunnelParts.SHEET), steel = RoadClient.sprite(RoadClient.STEEL);
        // the sprites in the key: a resource reload moves them
        float[] v = cache.computeIfAbsent(kind.mesh + "|" + bone + "|" + eq[0] + "|" + eq[2] + "|" + steel[0], k -> build(kind, bone, eq, steel));
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i < v.length; i += RoadClient.STRIDE)
            out.vertex(m, v[i], v[i + 1], v[i + 2]).color(255, 255, 255, 255).uv(v[i + 3], v[i + 4]).overlayCoords(overlay).uv2(light)
                    .normal(n, v[i + 5], v[i + 6], v[i + 7]).endVertex();
    }

    private static float[] build(TunnelFixtureBlock.Kind kind, String bone, float[] eq, float[] steel) {
        TunnelMesh.Placement p = new TunnelMesh.Placement(kind.mesh, bone, 0, 0, 0, new double[]{-1, 0, 0}, new double[]{0, 1, 0}, new double[]{0, 0, -1});
        float[] v = TunnelParts.bake(List.of(p), 0, 0, eq, steel);
        if (v.length == 0) return v;
        float[] lo = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY}, hi = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < v.length; i += RoadClient.STRIDE)
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], v[i + k]);
                hi[k] = Math.max(hi[k], v[i + k]);
            }
        float size = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
        float s = size > 1e-4f ? 1 / size : 1;
        for (int i = 0; i < v.length; i += RoadClient.STRIDE)
            for (int k = 0; k < 3; k++) v[i + k] = (v[i + k] - (lo[k] + hi[k]) / 2) * s + 0.5f;
        return v;
    }
}
