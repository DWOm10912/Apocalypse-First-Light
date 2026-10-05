package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A liquid stain drawn on the face it lies on (2026-10-05; first user: fuel stains, ClientFuelStains; meant for blood
 * later): flat polygons just off the surface, on any face of any block, cut to the surface's reach round the hit (the face
 * plane rectangle u0..u1 x v0..v1 along {@code axisU} / {@code axisV}) so nothing hangs in the air past an edge.
 * <ul>
 *   <li>the blob: a soft splat (textures/block/liquid_splat_*), turned by the seed; smaller on a wall;</li>
 *   <li>on a wall, the run: a sheet of several uneven streaks (liquid_sheet_*), about as wide as the blob, from the hit down
 *   to {@code run} blocks with its ragged front at the bottom, at {@code runStrength} of the stain's alpha (a fresh flow 1,
 *   a dried film less); the caller grows it over time and the cut stops it at the lower edge.</li>
 * </ul>
 * Tinted, translucent, without depth writes (LiquidRenderTypes.DECAL; tools/build-liquid-decals-v1.mjs: pale greyscale, so
 * one set serves every liquid). Lifted off the surface a little more far away, where depth is coarser.
 */
public final class LiquidDecals {
    private static final double OFFSET = 0.003, RUN_OFFSET = 0.0045;
    private static final ResourceLocation[] SPLATS = sprites("liquid_splat_", 3), RUNS = sprites("liquid_sheet_", 2);

    private LiquidDecals() {
    }

    private static ResourceLocation[] sprites(String prefix, int count) {
        ResourceLocation[] ids = new ResourceLocation[count];
        for (int i = 0; i < count; i++) ids[i] = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/" + prefix + i);
        return ids;
    }

    /**
     * One stain at {@code at} on a block's {@code face}, {@code size} blocks across, cut to [u0, u1] x [v0, v1] along
     * {@code axisU} / {@code axisV}; on a wall with a run of {@code run} blocks. Vertices relative to {@code origin} (the
     * camera in a level stage, a block entity's position in its renderer).
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Level level, Vec3 origin, Vec3 at, Direction face, Vec3 axisU, Vec3 axisV,
                            long seed, float size, float u0, float u1, float v0, float v1, float run, float runStrength, int rgb, int alpha) {
        if (alpha <= 0) return;
        boolean wall = face.getAxis().isHorizontal();
        long h = seed * 0x9E3779B97F4A7C15L;
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.add(n.scale(0.1))));
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.DECAL);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        double lift = Math.max(OFFSET, at.distanceTo(origin) * 0.00012);   // off the surface, more far away (depth precision)
        Frame frame = new Frame(at.add(n.scale(lift)).subtract(origin), axisU, axisV, n, u0, u1, v0, v1);
        // the blob, turned
        double s = (wall ? 0.75 : 1.0) * size / 2, angle = ((h >>> 20) & 0xFFFF) / 65536.0 * 2 * Math.PI, c = Math.cos(angle) * s, sn = Math.sin(angle) * s;
        TextureAtlasSprite splat = atlas.apply(SPLATS[(int) Math.floorMod(h >>> 7, (long) SPLATS.length)]);
        frame.quad(out, matrix, normals, splat, new double[][]{{-c + sn, -sn - c}, {c + sn, sn - c}, {c - sn, sn + c}, {-c - sn, -sn + c}},
                rgb, alpha, light);
        // the run down a wall
        if (wall && run > 0.02F && runStrength > 0.0F) {
            double w = Math.max(0.06, size * 0.8) / 2;
            TextureAtlasSprite trail = atlas.apply(RUNS[(int) Math.floorMod(h >>> 31, (long) RUNS.length)]);
            Frame runFrame = new Frame(at.add(n.scale(lift + RUN_OFFSET - OFFSET)).subtract(origin), axisU, axisV, n, u0, u1, v0, v1);
            runFrame.quad(out, matrix, normals, trail, new double[][]{{-w, -0.02}, {w, -0.02}, {w, run}, {-w, run}}, rgb, Math.round(alpha * runStrength), light);
        }
    }

    /** A face plane at {@code centre} (relative to the origin), with the rectangle that cuts what is drawn on it. */
    private record Frame(Vec3 centre, Vec3 u, Vec3 v, Vec3 n, double u0, double u1, double v0, double v1) {
        /** A quad given by its corners (a, b) in the plane, texture corners U0V0, U1V0, U1V1, U0V1; cut to the rectangle. */
        void quad(VertexConsumer out, Matrix4f matrix, Matrix3f normals, TextureAtlasSprite sprite, double[][] corners, int rgb, int alpha, int light) {
            double[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            List<double[]> poly = new ArrayList<>();   // a, b, U, V
            for (int i = 0; i < 4; i++) poly.add(new double[]{corners[i][0], corners[i][1], uv[i][0], uv[i][1]});
            poly = clip(poly, 0, u0, true);
            poly = clip(poly, 0, u1, false);
            poly = clip(poly, 1, v0, true);
            poly = clip(poly, 1, v1, false);
            if (poly.size() < 3) return;
            int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
            for (int i = 1; i + 1 < poly.size(); i++) {   // a fan of quads (the last corner doubled)
                double[][] q = {poly.get(0), poly.get(i), poly.get(i + 1), poly.get(i + 1)};
                for (double[] p : q) {
                    Vec3 at = centre.add(u.scale(p[0])).add(v.scale(p[1]));
                    out.vertex(matrix, (float) at.x, (float) at.y, (float) at.z).color(red, green, blue, alpha)
                            .uv((float) (sprite.getU0() + (sprite.getU1() - sprite.getU0()) * p[2]), (float) (sprite.getV0() + (sprite.getV1() - sprite.getV0()) * p[3]))
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normals, (float) n.x, (float) n.y, (float) n.z).endVertex();
                }
            }
        }

        /** Sutherland-Hodgman against one side: keep coordinate {@code axis} >= (or <=) {@code limit}. */
        private static List<double[]> clip(List<double[]> poly, int axis, double limit, boolean keepAbove) {
            List<double[]> result = new ArrayList<>();
            for (int i = 0; i < poly.size(); i++) {
                double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
                boolean ina = keepAbove ? a[axis] >= limit : a[axis] <= limit, inb = keepAbove ? b[axis] >= limit : b[axis] <= limit;
                if (ina) result.add(a);
                if (ina != inb) {
                    double t = (limit - a[axis]) / (b[axis] - a[axis]);
                    double[] p = new double[4];
                    for (int k = 0; k < 4; k++) p[k] = a[k] + (b[k] - a[k]) * t;
                    result.add(p);
                }
            }
            return result;
        }
    }
}
