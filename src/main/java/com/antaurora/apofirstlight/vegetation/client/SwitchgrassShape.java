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

import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.add;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.cross;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.scale;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.unit;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Switchgrass (Panicum virgatum; AFL vegetation P4a batch 1, docs/gameplay/vegetation_v1_design.md 21): a block is a
 * square metre of 2-3 clumps of the tall prairie bunchgrass of wet meadows and river banks.
 * <ul>
 * <li>LOD0: a clump is a vase of 10-14 basal leaves, 30-55 cm long and 8-12 mm wide, more upright than big bluestem's and
 * arching only at their points (4 segments). Most clumps flower: 2-4 stiff culms 1.0-1.6 m (round, 4 sides, tapering),
 * each with three culm leaves at 0.2, 0.35 and 0.5 of its height and from 0.7 up the panicle: open and airy, a pyramid
 * widest at its foot, hair-fine branches with tiny purple-red and tan spikelets (real colours), three crossed planes. Culm
 * leaves and panicle are rigid parts carried by the culm.</li>
 * <li>LOD1: two crossed foliage cards and two crossed panicle cards a clump. LOD2: the same a block. Tall: drawn at every
 * distance.</li>
 * </ul>
 * Sheet columns: 64 the leaf strip (the middle half), 65 the culm, 66 the parts (rows 0-38 the panicle, its foot at row
 * 38: the culm's v at 0.7, so the wind moves it with the culm; the culm leaves across the rows of their heights), 67 the
 * foliage card, 68 the panicle card (real colours). All sway 1.4 times as far as the turf, as big bluestem
 * (PlantShape#WIND).
 */
final class SwitchgrassShape {
    static final int LEAF = 64, CULM = 65, PARTS = 66, CARD = 67, CARD_PANICLES = 68;
    /** The panicle runs from this share of the culm (its rows: 0 .. (1 - PANICLE) of the parts column). */
    static final float PANICLE = 0.7F;
    /** Where the culm leaves stand out (shares of the culm's height; the art lays them at these rows). */
    static final float[] CULM_LEAVES = {0.2F, 0.35F, 0.5F};
    private static final float[] CULM_AT = {0, 0.25F, 0.5F, PANICLE, 1};
    private static final float[] LEAF_AT = {0, 0.3F, 0.6F, 0.82F, 1};

    private SwitchgrassShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x5A6A55L * 31 + v * 0x9E3779B97F4A7C15L);
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
                    else leaf = Math.max(leaf, BigBluestemShape.top(c.grown[i]));
                }
                tallest = Math.max(tallest, culm);
                leaves = Math.max(leaves, leaf);
                mid.add(cards(r, x, z, culm > 0 ? culm * 1.02F : leaf * 1.1F, culm > 0, 1.1F, 3));
            }
            boolean flowers = tallest > 0;
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.of(cards(r, 0.5F, 0.5F, flowers ? tallest : leaves * 1.1F, flowers, 1.6F, 1));
        }
        return out;
    }

    /** A clump (or a block) as cards h tall: two crossed foliage cards (tinted) and, flowering, two crossed panicle cards. */
    private static Clump cards(Rng r, float x, float z, float h, boolean flowers, float wide, int segments) {
        float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * wide, q = (float) (Math.PI / 4), half = (float) (Math.PI / 2);
        var cards = new ArrayList<Card>();
        cards.add(new Card(x, z, yaw, h, w, 0.03F, CARD, segments, true));
        cards.add(new Card(x, z, yaw + half, h, w, -0.03F, CARD, segments, true));
        if (flowers) {
            cards.add(new Card(x, z, yaw + q, h, w, 0.04F, CARD_PANICLES, segments, false));
            cards.add(new Card(x, z, yaw + q + half, h, w, -0.04F, CARD_PANICLES, segments, false));
        }
        return new Clump(x, z, r.nextFloat(), List.of(), cards, List.of());
    }

    /** One clump at (x, z): its vase of leaves and, if it flowers, its culms with their leaves and panicles. */
    private static Clump clump(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        int n = 10 + r.nextInt(5);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.005F + r.nextFloat() * 0.025F;
            float bend = a + (r.nextFloat() - 0.5F) * 0.8F, phi0 = 0.04F + r.nextFloat() * 0.2F;
            blades.add(new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                    0.3F + r.nextFloat() * 0.25F, 0.008F + r.nextFloat() * 0.004F, phi0, Math.min(1.5F, phi0 + 0.3F + r.nextFloat() * 0.6F), LEAF)
                    .profile(PlantShape.ACUMINATE).at(LEAF_AT).inset(0.25F).twist((r.nextFloat() - 0.5F) * 1.4F));
        }
        int culms = r.nextFloat() < 0.12F ? 0 : 2 + r.nextInt(3);
        for (int c = 0; c < culms; c++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.06F, off = 0.006F + r.nextFloat() * 0.014F;
            float h = 1.0F + r.nextFloat() * 0.6F;
            Blade culm = new Blade(x + (float) Math.cos(yaw) * off, z + (float) Math.sin(yaw) * off, yaw, yaw + (float) (Math.PI / 2),
                    h, 0.0045F + r.nextFloat() * 0.001F, phi0, phi0 + 0.03F + r.nextFloat() * 0.12F, CULM)
                    .profile(PlantShape.TAPER).tube(4).at(CULM_AT);
            int index = blades.size();
            blades.add(culm);
            Pose grown = PlantShape.grown(culm);
            float side = r.nextFloat() * (float) (Math.PI * 2);
            for (int k = 0; k < CULM_LEAVES.length; k++)
                pieces.add(new Piece(index, CULM_LEAVES[k], PlantShape.footed(culmLeaf(grown, CULM_LEAVES[k],
                        side + (float) Math.PI * k + (r.nextFloat() - 0.5F) * 0.8F, (0.25F + r.nextFloat() * 0.15F) * (1 - 0.25F * k), 0.01F, r), x, z), true));
            pieces.add(new Piece(index, (PANICLE + 1) / 2, PlantShape.footed(panicle(grown, h * (1 - PANICLE) * (1.02F + r.nextFloat() * 0.1F), r), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /**
     * A culm leaf at the share {@code at} of a culm (as grown), along yaw: rising 40-55 degrees off the culm, then arching
     * over 10-30 degrees down; three segments, both faces, the parts column's band at its height across it (the leaf along
     * u). Tinted, cut out.
     */
    private static List<Quad> culmLeaf(Pose culm, float at, float yaw, float len, float width, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), wx = -dz, wz = dx;
        double[] rise = {Math.toRadians(40 + r.nextFloat() * 15), Math.toRadians(10 + r.nextFloat() * 15), -Math.toRadians(10 + r.nextFloat() * 20)};
        float[] share = {0.35F, 0.35F, 0.3F}, along = {0, 0.35F, 0.7F, 1};
        float[][] mid = new float[4][];
        mid[0] = new float[]{f[0] + dx * 0.003F, f[1], f[2] + dz * 0.003F};
        for (int k = 0; k < 3; k++) {
            float l = len * share[k];
            mid[k + 1] = new float[]{mid[k][0] + dx * l * (float) Math.cos(rise[k]), mid[k][1] + l * (float) Math.sin(rise[k]), mid[k][2] + dz * l * (float) Math.cos(rise[k])};
        }
        float u0 = (PARTS + 0.03F) / COLUMNS, u1 = (PARTS + 0.97F) / COLUMNS, vMid = 1 - at, h = width / 2, tile = FlowerParts.TILE;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 3; k++) {
            float[][] c = {
                    {mid[k][0] - wx * h, mid[k][1], mid[k][2] - wz * h}, {mid[k][0] + wx * h, mid[k][1], mid[k][2] + wz * h},
                    {mid[k + 1][0] + wx * h, mid[k + 1][1], mid[k + 1][2] + wz * h}, {mid[k + 1][0] - wx * h, mid[k + 1][1], mid[k + 1][2] - wz * h}};
            float ua = u0 + (u1 - u0) * along[k], ub = u0 + (u1 - u0) * along[k + 1];
            PlantShape.face(out, c, new float[][]{{ua, vMid - tile}, {ua, vMid + tile}, {ub, vMid + tile}, {ub, vMid - tile}},
                    new float[]{0, 1, 0}, PlantShape.shade(at), true, false, true);
        }
        return out;
    }

    /**
     * The panicle on a culm (as grown): from PANICLE of the culm along its tangent there, nodding 4-12 degrees towards the
     * culm's lean, len long and 55-70 % as wide at its foot, narrowing to a sixth of that at its tip (a pyramid); three
     * crossed planes, the parts column's panicle rows (its foot at the culm's v there, full width; its tip at row 0, the
     * middle sixth). Both faces, real colours, cut out.
     */
    private static List<Quad> panicle(Pose culm, float len, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, PANICLE, f);
        float[] p0 = {f[0], f[1], f[2]}, t = unit(new float[]{f[3], f[4], f[5]});
        float[] lean = unit(new float[]{t[0], 0, t[2]});
        if (Float.isNaN(lean[0])) lean = new float[]{1, 0, 0};
        double nod = Math.toRadians(4 + r.nextFloat() * 8);
        float[] axis = unit(add(scale(t, (float) Math.cos(nod)), scale(lean, (float) Math.sin(nod))));
        float[] p1 = add(p0, scale(axis, len));
        float w = len * (0.55F + r.nextFloat() * 0.15F), spin = r.nextFloat() * (float) Math.PI;
        float[] a = unit(cross(axis, Math.abs(axis[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0})), b = cross(axis, a);
        float u0 = (PARTS + 0.02F) / COLUMNS, u1 = (PARTS + 0.98F) / COLUMNS, ua = (PARTS + 0.42F) / COLUMNS, ub = (PARTS + 0.58F) / COLUMNS;
        float v0 = 1 - PANICLE, v1 = 0.5F / 128;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 3; k++) {
            double ang = spin + Math.PI * k / 3;
            float[] across = add(scale(a, (float) Math.cos(ang)), scale(b, (float) Math.sin(ang)));
            float[] facing = cross(axis, across);
            PlantShape.face(out, new float[][]{add(p0, scale(across, -w * 0.5F)), add(p0, scale(across, w * 0.5F)),
                            add(p1, scale(across, w * 0.08F)), add(p1, scale(across, -w * 0.08F))},
                    new float[][]{{u0, v0}, {u1, v0}, {ub, v1}, {ua, v1}}, facing, 1, false, false, true);
        }
        return out;
    }
}
