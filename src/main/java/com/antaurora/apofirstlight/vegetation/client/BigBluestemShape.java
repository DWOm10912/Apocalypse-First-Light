package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Card;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Quad;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Big bluestem (Andropogon gerardii; AFL vegetation P2b, docs/gameplay/vegetation_v1_design.md 15): a block is a square
 * metre of 2-3 clumps of the tallgrass prairie's grass.
 * <ul>
 * <li>LOD0: a clump is a fountain of 18-24 basal leaves, 0.35-0.65 m long and 7-11 mm wide, drawn out to a long point
 * and arching over (4 segments). Most clumps flower: one or two culms 1.25-1.85 m (round, 4 sides, tapering), each with
 * two culm leaves hanging from its nodes at 0.3 and 0.55 of its height and at its top the "turkey foot": 2-4 racemes
 * 5-9 cm long, one nearly upright and the others spreading, purple-bronze with yellow anthers (real colours). Culm
 * leaves and racemes are rigid parts carried by the culm.</li>
 * <li>LOD1: two crossed cards a clump (flowering or leaves only). LOD2: two crossed wide cards a block, kept at every
 * distance (a 1.8 m grass shows from far away).</li>
 * </ul>
 * Sheet columns: 22 the leaf strip (the middle half), 23 the culm (purplish nodes), 24 the parts (rows 0-9 four raceme
 * slots, each raceme's foot at row 0: the culm's top height, so the wind moves it with the culm; the two culm leaves laid
 * across at the rows of their heights), 25 the flowering card, 26 the leaves-only card. All sway 1.4 times as far as the
 * turf (PlantShape#WIND).
 */
final class BigBluestemShape {
    static final int LEAF = 22, CULM = 23, PARTS = 24, CARD = 25, CARD_LEAVES = 26;
    /** The racemes' rows of the parts column: their feet at row 0, their tips at row 9. */
    static final float RACEMES = 9 / 128F;
    /** A culm leaf's band: 9 rows centred on its height's v. */
    static final float TILE = 4.5F / 128;
    /** Where the culm leaves hang (shares of the culm's height; the art lays them at these rows). */
    static final float[] CULM_LEAVES = {0.3F, 0.55F};
    private static final float[] CULM_AT = {0, 0.25F, 0.5F, 0.75F, 1};
    private static final float[] LEAF_AT = {0, 0.3F, 0.6F, 0.82F, 1};

    private BigBluestemShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xB16B1EL * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int clumps = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0, leaves = 0;
            for (int k = 0; k < clumps; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                Clump c = clump(r, x, z);
                near.add(c);
                float culm = 0, leaf = 0;
                for (int i = 0; i < c.blades.size(); i++) {
                    if (c.blades.get(i).column == CULM) culm = Math.max(culm, c.blades.get(i).len);
                    else leaf = Math.max(leaf, top(c.grown[i]));
                }
                tallest = Math.max(tallest, culm);
                leaves = Math.max(leaves, leaf);
                float yaw = r.nextFloat() * (float) Math.PI, half = (float) (Math.PI / 2);
                if (culm > 0) {
                    float h = culm * 1.02F, w = h * ASPECT;
                    mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                            new Card(x, z, yaw, h, w, 0.03F, CARD, 3, true), new Card(x, z, yaw + half, h, w, -0.03F, CARD, 3, true)), List.of()));
                } else {
                    float h = leaf * 1.05F, w = h * ASPECT * 1.6F;
                    mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                            new Card(x, z, yaw, h, w, 0.08F, CARD_LEAVES, 2, true), new Card(x, z, yaw + half, h, w, -0.08F, CARD_LEAVES, 2, true)), List.of()));
                }
            }
            float yaw = r.nextFloat() * (float) Math.PI, half = (float) (Math.PI / 2);
            boolean flowers = tallest > 0;
            float h = flowers ? tallest : leaves * 1.05F, w = h * ASPECT * (flowers ? 1.4F : 2.2F);
            int col = flowers ? CARD : CARD_LEAVES;
            var far = List.of(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                    new Card(0.5F, 0.5F, yaw, h, w, 0.03F, col, 1, true), new Card(0.5F, 0.5F, yaw + half, h, w, -0.03F, col, 1, true)), List.of()));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = far;
        }
        return out;
    }

    /** The highest point of a pose. */
    static float top(Pose p) {
        float t = 0;
        for (int k = 1; k < p.pts.length; k += 3) t = Math.max(t, p.pts[k]);
        return t;
    }

    /** One clump at (x, z): its fountain of leaves and, if it flowers, its culms with their leaves and turkey feet. */
    private static Clump clump(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        int n = 18 + r.nextInt(7);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.005F + r.nextFloat() * 0.03F;
            float bend = a + (r.nextFloat() - 0.5F) * 0.8F, phi0 = 0.08F + r.nextFloat() * 0.3F;
            blades.add(new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                    0.35F + r.nextFloat() * 0.3F, 0.007F + r.nextFloat() * 0.004F, phi0, Math.min(1.7F, phi0 + 0.45F + r.nextFloat() * 0.7F), LEAF)
                    .profile(PlantShape.ACUMINATE).at(LEAF_AT).inset(0.25F).twist((r.nextFloat() - 0.5F) * 1.6F));
        }
        float roll = r.nextFloat();
        int culms = roll < 0.35F ? 0 : roll < 0.85F ? 1 : 2;
        for (int c = 0; c < culms; c++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.05F, off = 0.006F + r.nextFloat() * 0.012F;
            Blade culm = new Blade(x + (float) Math.cos(yaw) * off, z + (float) Math.sin(yaw) * off, yaw, yaw + (float) (Math.PI / 2),
                    1.25F + r.nextFloat() * 0.6F, 0.0045F + r.nextFloat() * 0.001F, phi0, phi0 + 0.04F + r.nextFloat() * 0.12F, CULM)
                    .profile(PlantShape.TAPER).tube(4).at(CULM_AT);
            int index = blades.size();
            blades.add(culm);
            Pose grown = PlantShape.grown(culm);
            float side = r.nextFloat() * (float) (Math.PI * 2);
            // the culm leaves lie down with a fallen culm (flat pieces), rather than turning up on end
            for (int k = 0; k < CULM_LEAVES.length; k++)
                pieces.add(new Piece(index, CULM_LEAVES[k], PlantShape.footed(culmLeaf(grown, CULM_LEAVES[k],
                        side + (float) Math.PI * k + (r.nextFloat() - 0.5F) * 0.8F, 0.2F + r.nextFloat() * 0.1F, 0.012F, r), x, z), true));
            pieces.add(new Piece(index, 1, PlantShape.footed(racemes(grown, r), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /**
     * A culm leaf at the share {@code at} of a culm (as grown), along yaw: rising 35-50 degrees off the culm, levelling, then
     * hanging 15-35 degrees down; three segments, both faces, its outline the sheet's, laid across the rows of its height.
     */
    private static List<Quad> culmLeaf(Pose culm, float at, float yaw, float len, float width, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), wx = -dz, wz = dx;
        double[] rise = {Math.toRadians(35 + r.nextFloat() * 15), Math.toRadians(r.nextFloat() * 12), -Math.toRadians(15 + r.nextFloat() * 20)};
        float[] share = {0.3F, 0.35F, 0.35F}, along = {0, 0.3F, 0.65F, 1};
        float[][] mid = new float[4][];
        mid[0] = new float[]{f[0] + dx * 0.003F, f[1], f[2] + dz * 0.003F};
        for (int k = 0; k < 3; k++) {
            float l = len * share[k];
            mid[k + 1] = new float[]{mid[k][0] + dx * l * (float) Math.cos(rise[k]), mid[k][1] + l * (float) Math.sin(rise[k]), mid[k][2] + dz * l * (float) Math.cos(rise[k])};
        }
        float u0 = (PARTS + 0.03F) / COLUMNS, u1 = (PARTS + 0.97F) / COLUMNS, vMid = 1 - at, h = width / 2, light = PlantShape.shade(at);
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 3; k++) {
            float[][] c = {
                    {mid[k][0] - wx * h, mid[k][1], mid[k][2] - wz * h}, {mid[k][0] + wx * h, mid[k][1], mid[k][2] + wz * h},
                    {mid[k + 1][0] + wx * h, mid[k + 1][1], mid[k + 1][2] + wz * h}, {mid[k + 1][0] - wx * h, mid[k + 1][1], mid[k + 1][2] - wz * h}};
            float ua = u0 + (u1 - u0) * along[k], ub = u0 + (u1 - u0) * along[k + 1];
            float[][] uv = {{ua, vMid - TILE}, {ua, vMid + TILE}, {ub, vMid + TILE}, {ub, vMid - TILE}};
            PlantShape.face(out, c, uv, new float[]{0, 1, 0}, light, true, false, true);
        }
        return out;
    }

    /**
     * The turkey foot on a culm's top (as grown): 2-4 racemes 5-9 cm long from the tip, the first nearly along the culm,
     * the others spreading 20-35 degrees round it and curving a little further out over their outer half. Each a crossed
     * pair of strips 7 mm wide (both faces), its slot of the parts column's raceme rows (foot at row 0); real colours.
     */
    private static List<Quad> racemes(Pose culm, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, 1, f);
        float[] t = {f[3], f[4], f[5]};
        float[] p1 = unit(cross(t, Math.abs(t[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0})), p2 = cross(t, p1);
        float roll = r.nextFloat();
        int count = roll < 0.15F ? 2 : roll < 0.85F ? 3 : 4;
        float turn = r.nextFloat() * (float) (Math.PI * 2);
        var out = new ArrayList<Quad>();
        for (int i = 0; i < count; i++) {
            double spread = i == 0 ? Math.toRadians(4 + r.nextFloat() * 6) : Math.toRadians(20 + r.nextFloat() * 15);
            double az = turn + Math.PI * 2 * i / Math.max(1, count - 1) + (r.nextFloat() - 0.5F) * 0.5F;
            float[] radial = add(scale(p1, (float) Math.cos(az)), scale(p2, (float) Math.sin(az)));
            float[] d = unit(add(scale(t, (float) Math.cos(spread)), scale(radial, (float) Math.sin(spread))));
            float[] d2 = unit(add(d, scale(radial, i == 0 ? 0.08F : 0.2F)));
            float len = 0.05F + r.nextFloat() * 0.04F, half = 0.0035F;
            float[] a = {f[0], f[1], f[2]}, m = add(a, scale(d, len * 0.5F)), e = add(m, scale(d2, len * 0.5F));
            float ua = (PARTS + i * 0.25F + 0.03F) / COLUMNS, ub = (PARTS + (i + 1) * 0.25F - 0.03F) / COLUMNS;
            float va = 0.4F / 128, vm = RACEMES * 0.5F, ve = RACEMES;
            float[] w1 = unit(cross(d, radial)), w2 = unit(cross(d, w1));
            for (float[] w : new float[][]{w1, w2}) {
                float[] n = cross(w, d);
                strip(out, a, m, w, half, ua, ub, va, vm, n);
                strip(out, m, e, w, half, ua, ub, vm, ve, n);
            }
        }
        return out;
    }

    /** One segment of a raceme from a to b, half width h across w, both faces. */
    private static void strip(List<Quad> out, float[] a, float[] b, float[] w, float h, float ua, float ub, float va, float vb, float[] n) {
        float[][] c = {add(a, scale(w, -h)), add(a, scale(w, h)), add(b, scale(w, h)), add(b, scale(w, -h))};
        float[][] uv = {{ua, va}, {ub, va}, {ub, vb}, {ua, vb}};
        PlantShape.face(out, c, uv, n, 1, false, false, true);
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float[] unit(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-9F ? new float[]{0, 1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static float[] scale(float[] a, float s) {
        return new float[]{a[0] * s, a[1] * s, a[2] * s};
    }
}
