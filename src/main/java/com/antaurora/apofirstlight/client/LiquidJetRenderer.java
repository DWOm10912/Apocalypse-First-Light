package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a LiquidJet (2026-10-05; first user: the fuel nozzle): a thin translucent tube through its parcels (interpolated
 * between ticks), newest to oldest, from the live source point while it runs. The tube breaks where the parcel chain does
 * (a gap, a parcel that landed). Its radius follows the flow: a stream that speeds up as it falls gets thinner (the same
 * flow through a smaller section, radius ~ 1 / sqrt(speed)), and the falling front tapers. The liquid's still sprite runs
 * along the tube with the parcels (V by parcel id), tinted with the liquid's colour. Vertices relative to {@code origin}
 * (a block entity renderer's pose). Also holds the tube frames (parallel transport) the dispenser's hose shares.
 */
public final class LiquidJetRenderer {
    private LiquidJetRenderer() {
    }

    /**
     * @param head        the live source point (the jet is still running and attached), or null
     * @param radius      tube radius (blocks) at {@code speed}
     * @param speed       the source's speed (blocks a second), the radius reference
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin, LiquidJet jet, @Nullable Vec3 head,
                              float partialTick, TextureAtlasSprite sprite, int rgb, int alpha, double radius, double speed, int sides) {
        render(pose, buffers, level, origin, jet, head, partialTick, sprite, rgb, alpha, radius, speed, sides, Double.MAX_VALUE);
    }

    /**
     * As above; after {@code breakup} seconds of flight the stream comes apart (2026-10-05): every third parcel the tube
     * breaks, so the falling end shows as a run of elongated drops, and it thins to about half.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin, LiquidJet jet, @Nullable Vec3 head,
                              float partialTick, TextureAtlasSprite sprite, int rgb, int alpha, double radius, double speed, int sides, double breakup) {
        if (jet.isEmpty()) return;
        List<Vec3> points = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        long previous = jet.nextId();
        if (head != null) {
            points.add(head);
            radii.add(radius);
            ids.add(previous);
        }
        for (Iterator<LiquidJet.Parcel> it = jet.parcels().descendingIterator(); it.hasNext(); ) {
            LiquidJet.Parcel p = it.next();
            boolean apart = p.age > breakup && Math.floorMod(p.id, 3) == 0;
            if ((p.id != previous - 1 || apart) && !points.isEmpty()) {   // the chain breaks here
                tube(pose, buffers, level, origin, points, radii, ids, sprite, rgb, alpha, sides);
                points.clear();
                radii.clear();
                ids.clear();
            }
            points.add(p.prev.lerp(p.pos, partialTick));
            double v = Math.max(1e-3, p.vel.length());
            radii.add(radius * Mth.clamp(Math.sqrt(speed / v), 0.45, 1.0) * Mth.clamp(1.0 - (p.age - breakup) * 0.8, 0.5, 1.0));
            ids.add(p.id);
            previous = p.id;
        }
        tube(pose, buffers, level, origin, points, radii, ids, sprite, rgb, alpha, sides);
    }

    private static void tube(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin, List<Vec3> pts, List<Double> radii,
                             List<Long> ids, TextureAtlasSprite sprite, int rgb, int alpha, int sides) {
        int n = pts.size();
        if (n < 2) return;
        Vec3[] points = pts.toArray(new Vec3[0]);
        for (int i = 1; i < n; i++) if (points[i].distanceToSqr(points[i - 1]) < 1e-8) points[i] = points[i].add(0, -1e-4, 0);
        Vec3[][] ring = rings(points, sides);
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        int[] light = new int[n];
        for (int i = 0; i < n; i++) light[i] = LevelRenderer.getLightColor(level, BlockPos.containing(points[i]));
        for (int i = 0; i + 1 < n; i++) {
            // each segment its own 2 px of the sprite by the older parcel's id: the texture travels with the liquid
            float v0 = sprite.getV(Math.floorMod(ids.get(i + 1), 8) * 2.0), v1 = sprite.getV(Math.floorMod(ids.get(i + 1), 8) * 2.0 + 2.0);
            double r0 = radii.get(i), r1 = radii.get(i + 1) * (i + 2 == n ? 0.6 : 1.0);   // the falling front tapers
            for (int k = 0; k < sides; k++) {
                int k2 = (k + 1) % sides;
                float u0 = sprite.getU(4.0 * k / sides), u1 = sprite.getU(4.0 * (k + 1) / sides);
                vertex(out, matrix, normals, origin, points[i], ring[i][k], r0, u0, v1, light[i], red, green, blue, alpha);
                vertex(out, matrix, normals, origin, points[i], ring[i][k2], r0, u1, v1, light[i], red, green, blue, alpha);
                vertex(out, matrix, normals, origin, points[i + 1], ring[i + 1][k2], r1, u1, v0, light[i + 1], red, green, blue, alpha);
                vertex(out, matrix, normals, origin, points[i + 1], ring[i + 1][k], r1, u0, v0, light[i + 1], red, green, blue, alpha);
            }
        }
    }

    /** Rotation-minimising frames (parallel transport) along a polyline: each point's ring of unit directions round the axis. */
    public static Vec3[][] rings(Vec3[] points, int sides) {
        int last = points.length - 1;
        Vec3[][] ring = new Vec3[points.length][sides];
        Vec3 previous = points[1].subtract(points[0]).normalize();
        Vec3 normal = Math.abs(previous.y) < 0.9 ? previous.cross(new Vec3(0, 1, 0)).normalize() : previous.cross(new Vec3(1, 0, 0)).normalize();
        for (int i = 0; i <= last; i++) {
            Vec3 tangent = points[Math.min(last, i + 1)].subtract(points[Math.max(0, i - 1)]).normalize();
            Vec3 axis = previous.cross(tangent);
            double sin = axis.length();
            if (sin > 1e-9) normal = rotate(normal, axis.scale(1 / sin), Math.atan2(sin, previous.dot(tangent)));
            normal = normal.subtract(tangent.scale(normal.dot(tangent))).normalize();
            Vec3 binormal = tangent.cross(normal);
            for (int k = 0; k < sides; k++) {
                double angle = 2 * Math.PI * (k + 0.5) / sides;
                ring[i][k] = normal.scale(Math.cos(angle)).add(binormal.scale(Math.sin(angle)));
            }
            previous = tangent;
        }
        return ring;
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1 - cos)));
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, BlockPos origin, Vec3 centre, Vec3 direction,
                               double radius, float u, float v, int light, int red, int green, int blue, int alpha) {
        out.vertex(matrix, (float) (centre.x - origin.getX() + direction.x * radius), (float) (centre.y - origin.getY() + direction.y * radius),
                        (float) (centre.z - origin.getZ() + direction.z * radius))
                .color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normals, (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }
}
