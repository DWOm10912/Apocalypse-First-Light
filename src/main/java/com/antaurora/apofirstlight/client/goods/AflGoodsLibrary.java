package com.antaurora.apofirstlight.client.goods;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshPart;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.antaurora.apofirstlight.containersearch.AflGoodsState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Draws the shared goods library (tools/build-goods-library-v1.mjs: geo / meshes / textures goods_library) for retail
 * containers: one product per shown cell (AflGoodsState), its '<product>' bone in fixed colours and its
 * '<product>_tint' bone in the cell's colour (vertex colour over a light neutral). Products are modelled in a nominal
 * cell 4 x 4.6 x 8 px, front toward -z, origin at the cell's bottom centre; the caller's pose is the container's cell
 * frame in blocks (front toward -z).
 */
public final class AflGoodsLibrary {
    public static final ResourceLocation GEO = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/goods_library.geo.json");
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/goods_library.png");
    /** Nominal cell, px. */
    public static final float CELL_W = 4.0F, CELL_H = 4.6F, CELL_D = 8.0F;
    /** Muted packaging colours (AflGoodsState.TINTS slots) multiplied over the light neutral (214). */
    private static final float[][] TINTS = {
            {0.78F, 0.32F, 0.28F}, {0.32F, 0.45F, 0.72F}, {0.36F, 0.58F, 0.36F}, {0.86F, 0.72F, 0.28F}, {0.86F, 0.50F, 0.24F},
            {0.52F, 0.36F, 0.60F}, {0.28F, 0.58F, 0.58F}, {0.86F, 0.86F, 0.84F}, {0.56F, 0.40F, 0.28F}};

    private AflGoodsLibrary() {
    }

    /**
     * Each shown cell: placed by {@code cell} (cell index -> x, y, z in the caller's frame, blocks: the cell's front-centre
     * on its deck), turned by the spot's yaw, scaled (1 = the nominal cell in px).
     */
    public static void draw(List<AflGoodsState.Spot> spots, CellPosition cell, float scale, PoseStack pose, MultiBufferSource buffers,
                            int light, int overlay) {
        if (spots.isEmpty()) return;
        var mesh = AflMeshCache.snapshot().get(GEO);
        if (mesh == null) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float s = scale;
        for (AflGoodsState.Spot spot : spots) {
            double[] at = cell.at(spot.cell());
            pose.pushPose();
            // the product's front edge on the cell's front: the nominal cell's centre is half its depth behind it
            pose.translate(at[0], at[1], at[2] + CELL_D / 2.0F * s / 16.0F);
            pose.mulPose(Axis.YP.rotationDegrees(spot.yaw()));
            pose.scale(s, s, s);
            float[] tint = TINTS[Math.floorMod(spot.tint(), TINTS.length)];
            AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(spot.product(), AflMeshPart.Layer.CUTOUT), pose, vertices, light, overlay,
                    1.0F, 1.0F, 1.0F, 1.0F, null);
            AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(spot.product() + "_tint", AflMeshPart.Layer.CUTOUT), pose, vertices, light, overlay,
                    tint[0], tint[1], tint[2], 1.0F, null);
            pose.popPose();
        }
    }

    /**
     * Each shown cell with its own placement (checkout counter, back bar): the product's front-centre on its deck, the way
     * its front faces (yaw, degrees; 0 = toward -z, 180 = toward +z) and a scale per axis (1 = the nominal cell in px), so
     * a shallow tray can take a squat, shallow product; cells the placement returns null for are skipped.
     */
    public static void drawPlaced(List<AflGoodsState.Spot> spots, CellPlacement placement, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay) {
        if (spots.isEmpty()) return;
        var mesh = AflMeshCache.snapshot().get(GEO);
        if (mesh == null) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (AflGoodsState.Spot spot : spots) {
            float[] at = placement.at(spot.cell());
            if (at == null) continue;
            pose.pushPose();
            pose.translate(at[0], at[1], at[2]);
            pose.mulPose(Axis.YP.rotationDegrees(at[3]));
            pose.translate(0.0F, 0.0F, CELL_D / 2.0F * at[6] / 16.0F);
            pose.mulPose(Axis.YP.rotationDegrees(spot.yaw()));
            pose.scale(at[4], at[5], at[6]);
            float[] tint = TINTS[Math.floorMod(spot.tint(), TINTS.length)];
            AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(spot.product(), AflMeshPart.Layer.CUTOUT), pose, vertices, light, overlay,
                    1.0F, 1.0F, 1.0F, 1.0F, null);
            AflMeshRenderer.renderPartsAtCurrentPose(mesh.parts(spot.product() + "_tint", AflMeshPart.Layer.CUTOUT), pose, vertices, light, overlay,
                    tint[0], tint[1], tint[2], 1.0F, null);
            pose.popPose();
        }
    }

    /** A container's cell positions in its renderer's frame. */
    @FunctionalInterface
    public interface CellPosition {
        /** x, y (deck top), z (front edge of the usable deck) of a cell, blocks. */
        double[] at(int cell);
    }

    /** Per-cell placement for {@link #drawPlaced}: {x, y, z (blocks), yaw (degrees), scale x, y, z}, or null to skip. */
    @FunctionalInterface
    public interface CellPlacement {
        float[] at(int cell);
    }
}
