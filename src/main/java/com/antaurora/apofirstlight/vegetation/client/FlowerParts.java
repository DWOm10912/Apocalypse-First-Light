package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Card;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Quad;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.add;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.cross;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.scale;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.unit;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;

/**
 * The rigid parts the wildflowers share (AFL vegetation P2b batch 2, docs/gameplay/vegetation_v1_design.md 16): stem
 * leaves, a flower head's middle (a cone or a dome round the stem's tangent at its top) and its ray florets, and the far
 * cards. A flower's parts column is laid out the same way for each species: rows 0-8 its head (the left half the middle,
 * row 0 its top; the right half one ray, its foot at row 0 and its tip at row 8), its stem leaves laid across at the rows
 * of their heights.
 */
final class FlowerParts {
    /** The head's rows of a parts column. */
    static final float HEAD = 8.6F / 128;
    /** A stem leaf's band: 9 rows centred on its height's v. */
    static final float TILE = 4.5F / 128;

    private FlowerParts() {}

    /** The basis at a stem's top (as grown): the point (0..2), the tangent t, and two directions square to it. */
    private static float[][] basis(Pose stem) {
        float[] f = new float[6];
        PlantShape.frame(stem.pts, stem.f, 1, f);
        float[] t = {f[3], f[4], f[5]};
        float[] p1 = unit(cross(t, Math.abs(t[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0})), p2 = cross(t, p1);
        return new float[][]{{f[0], f[1], f[2]}, t, p1, p2};
    }

    /**
     * A stem leaf at the share {@code at} of a stem (as grown), along yaw: rising 30-45 degrees off the stem, then levelling
     * or drooping; two segments, both faces, tinted, its outline the parts column's band at its height (leaf across u).
     */
    static List<Quad> stemLeaf(Pose stem, float at, float yaw, float len, float width, int column, Rng r) {
        return stemLeaf(stem, at, at, yaw, len, width, column, r);
    }

    /** The same, its outline the band at {@code band} (a share of the stem near {@code at}: two leaves to one band). */
    static List<Quad> stemLeaf(Pose stem, float at, float band, float yaw, float len, float width, int column, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(stem.pts, stem.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), wx = -dz, wz = dx;
        double rise0 = Math.toRadians(30 + r.nextFloat() * 15), rise1 = Math.toRadians(-15 + r.nextFloat() * 20);
        float[][] mid = new float[3][];
        mid[0] = new float[]{f[0] + dx * 0.002F, f[1], f[2] + dz * 0.002F};
        float a = len * 0.45F, b = len * 0.55F;
        mid[1] = new float[]{mid[0][0] + dx * a * (float) Math.cos(rise0), mid[0][1] + a * (float) Math.sin(rise0), mid[0][2] + dz * a * (float) Math.cos(rise0)};
        mid[2] = new float[]{mid[1][0] + dx * b * (float) Math.cos(rise1), mid[1][1] + b * (float) Math.sin(rise1), mid[1][2] + dz * b * (float) Math.cos(rise1)};
        float u0 = (column + 0.03F) / COLUMNS, u1 = (column + 0.97F) / COLUMNS, vMid = 1 - band, h = width / 2;
        float[] along = {0, 0.45F, 1};
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 2; k++) {
            float[][] c = {
                    {mid[k][0] - wx * h, mid[k][1], mid[k][2] - wz * h}, {mid[k][0] + wx * h, mid[k][1], mid[k][2] + wz * h},
                    {mid[k + 1][0] + wx * h, mid[k + 1][1], mid[k + 1][2] + wz * h}, {mid[k + 1][0] - wx * h, mid[k + 1][1], mid[k + 1][2] - wz * h}};
            float ua = u0 + (u1 - u0) * along[k], ub = u0 + (u1 - u0) * along[k + 1];
            PlantShape.face(out, c, new float[][]{{ua, vMid - TILE}, {ua, vMid + TILE}, {ub, vMid + TILE}, {ub, vMid - TILE}},
                    new float[]{0, 1, 0}, PlantShape.shade(at), true, false, true);
        }
        return out;
    }

    /**
     * A head's middle on a stem's top (as grown), round the stem's tangent: rings of the given radius at the given heights
     * along it (base first, the last radius 0: its top), {@code sides} faces round; the left half of the column's head rows
     * mapped round it (row 0 the top, the head's rows down to the base). Real colours, cut out.
     */
    static List<Quad> middle(Pose stem, float[] radius, float[] height, int sides, int column, Rng r) {
        float[][] b = basis(stem);
        float spin = r.nextFloat() * (float) (Math.PI * 2);
        int n = radius.length;
        float[][][] ring = new float[n][sides][];
        for (int k = 0; k < n; k++)
            for (int s = 0; s < sides; s++) {
                double a = spin + Math.PI * 2 * s / sides;
                float[] out = add(scale(b[2], (float) Math.cos(a)), scale(b[3], (float) Math.sin(a)));
                ring[k][s] = add(add(b[0], scale(b[1], height[k])), scale(out, radius[k]));
            }
        var out = new ArrayList<Quad>();
        for (int k = 0; k + 1 < n; k++) {
            // rows: the top (k = n - 1) at row 0.5, the base (k = 0) at the bottom of the head's rows
            float va = HEAD * (1 - k / (float) (n - 1)) + 0.5F / 128 * k / (n - 1), vb = HEAD * (1 - (k + 1) / (float) (n - 1)) + 0.5F / 128 * (k + 1) / (n - 1);
            for (int s = 0; s < sides; s++) {
                int t = (s + 1) % sides;
                float ua = (column + 0.5F * s / sides) / COLUMNS, ub = (column + 0.5F * (s + 1) / sides) / COLUMNS;
                double mid = spin + Math.PI * 2 * (s + 0.5) / sides;
                float[] facing = add(scale(add(scale(b[2], (float) Math.cos(mid)), scale(b[3], (float) Math.sin(mid))), 0.8F), b[1]);
                PlantShape.face(out, new float[][]{ring[k][s], ring[k][t], ring[k + 1][t], ring[k + 1][s]},
                        new float[][]{{ua, va}, {ub, va}, {ub, vb}, {ua, vb}}, facing, 1, false, false, false);
            }
        }
        return out;
    }

    /**
     * Ray florets round a head's base on a stem's top (as grown): {@code count} rays from the ring of radius r0 a little
     * above the top, each two segments out and down (droop0 then droop1 radians below square to the stem), len long and
     * width wide; the right half of the column's head rows (the ray's foot at row 0, its tip at row 8). Both faces, real
     * colours, cut out.
     */
    static List<Quad> rays(Pose stem, int count, float r0, float lift, float len, float width, float droop0, float droop1, int column, Rng r) {
        float[][] b = basis(stem);
        float spin = r.nextFloat() * (float) (Math.PI * 2), h = width / 2;
        float u0 = (column + 0.52F) / COLUMNS, u1 = (column + 0.98F) / COLUMNS;
        var out = new ArrayList<Quad>();
        for (int i = 0; i < count; i++) {
            double a = spin + Math.PI * 2 * (i + (r.nextFloat() - 0.5F) * 0.3F) / count;
            float[] radial = add(scale(b[2], (float) Math.cos(a)), scale(b[3], (float) Math.sin(a)));
            float[] across = unit(cross(b[1], radial));
            float d0 = droop0 + (r.nextFloat() - 0.5F) * 0.25F, d1 = droop1 + (r.nextFloat() - 0.5F) * 0.3F, l = len * (0.85F + r.nextFloat() * 0.3F);
            float[] p0 = add(add(b[0], scale(b[1], lift)), scale(radial, r0));
            float[] s0 = add(scale(radial, (float) Math.cos(d0)), scale(b[1], -(float) Math.sin(d0)));
            float[] s1 = add(scale(radial, (float) Math.cos(d1)), scale(b[1], -(float) Math.sin(d1)));
            float[] p1 = add(p0, scale(s0, l * 0.45F)), p2 = add(p1, scale(s1, l * 0.55F));
            float[] up = unit(cross(across, s0));
            if (up[0] * b[1][0] + up[1] * b[1][1] + up[2] * b[1][2] < 0) up = scale(up, -1);
            float vm = HEAD * 0.45F;
            PlantShape.face(out, new float[][]{add(p0, scale(across, -h * 0.6F)), add(p0, scale(across, h * 0.6F)), add(p1, scale(across, h)), add(p1, scale(across, -h))},
                    new float[][]{{u0, 0.4F / 128}, {u1, 0.4F / 128}, {u1, vm}, {u0, vm}}, up, 1, false, false, true);
            PlantShape.face(out, new float[][]{add(p1, scale(across, -h)), add(p1, scale(across, h)), add(p2, scale(across, h)), add(p2, scale(across, -h))},
                    new float[][]{{u0, vm}, {u1, vm}, {u1, HEAD}, {u0, HEAD}}, up, 1, false, false, true);
        }
        return out;
    }

    /** A flowering plant as cards h tall: two crossed foliage cards (tinted) and two crossed flower cards (real colours) at 45 degrees to them. */
    static Clump cards(Rng r, float x, float z, float h, float wide, int segments, int foliage, int flowers) {
        float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * wide, q = (float) (Math.PI / 4), half = (float) (Math.PI / 2);
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(
                new Card(x, z, yaw, h, w, 0.04F, foliage, segments, true),
                new Card(x, z, yaw + half, h, w, -0.04F, foliage, segments, true),
                new Card(x, z, yaw + q, h, w, 0.03F, flowers, segments, false),
                new Card(x, z, yaw + q + half, h, w, -0.03F, flowers, segments, false)), List.of());
    }
}
