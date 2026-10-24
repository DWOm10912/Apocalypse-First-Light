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
 * Common reed (Phragmites australis; AFL vegetation P2b batch 3, docs/gameplay/vegetation_v1_design.md 18): a block is a
 * square metre of a reed bed, on wet ground or in water a block deep (MeshPlantBlock.Wet, as the cattail).
 * <ul>
 * <li>LOD0: 3-4 groups of 2-3 culms, 2-3 m tall (hollow, round, 4 sides, tapering a little), nearly upright and leaning
 * together a little. Seven leaves a culm at 0.2-0.8 of its height, alternate (half a turn apart, wandering), 30-50 cm
 * long and 2.5-3.5 cm wide, standing out from the culm and drooping at the tip (two segments). On top the plume: the
 * panicle from 0.86 of the culm to a little past its tip, 20-35 cm, purple-brown and feathery, three crossed planes
 * nodding with the culm. Leaves and plume are rigid parts carried by the culm; the leaves lie down with a fallen culm.</li>
 * <li>LOD1: two crossed foliage cards and two crossed plume cards a group. LOD2: two crossed wide cards and their plumes a
 * block. Tall: drawn at every distance.</li>
 * </ul>
 * Sheet columns: 57 the culm (pale green, darker nodes), 58 the parts (rows 0-18: the plume, its foot at row 18 (0.86 of the
 * culm), real colours; the seven leaves across the rows of their heights, tinted), 59 the foliage card, 60 the plume card
 * (real colours). Sways as far as the cattail (1.6 times the turf; PlantShape#WIND).
 */
final class ReedShape {
    static final int CULM = 57, PARTS = 58, CARD = 59, CARD_PLUMES = 60;
    static final float[] LEAVES_AT = {0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F};
    /** The plume runs from this share of the culm (its rows: 0 .. (1 - PLUME) of the parts column). */
    static final float PLUME = 0.86F;
    private static final float[] CULM_AT = {0, 0.3F, 0.55F, 0.8F, PLUME, 1};

    private ReedShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x9EED5L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int groups = 3 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0;
            for (int g = 0; g < groups; g++) {
                int slot = slots[g];
                float gx = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), gz = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                int culms = 2 + r.nextInt(2);
                float lean = r.nextFloat() * (float) (Math.PI * 2), top = 0;
                for (int c = 0; c < culms; c++) {
                    double a = r.nextFloat() * Math.PI * 2;
                    float d = 0.02F + r.nextFloat() * 0.1F;
                    float x = clamp(gx + (float) Math.cos(a) * d), z = clamp(gz + (float) Math.sin(a) * d);
                    float h = 2.0F + r.nextFloat() * 1.0F;
                    top = Math.max(top, h);
                    near.add(culm(new Rng(r.next()), x, z, h, lean + (r.nextFloat() - 0.5F) * 0.8F));
                }
                tallest = Math.max(tallest, top);
                mid.add(cards(r, gx, gz, top * 1.03F, 0.9F, 3));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.of(cards(r, 0.5F, 0.5F, tallest, 1.7F, 1));
        }
        return out;
    }

    /** A group (or a block) as cards h tall: two crossed foliage cards (tinted) and two crossed plume cards (real colours). */
    private static Clump cards(Rng r, float x, float z, float h, float wide, int segments) {
        float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * wide, q = (float) (Math.PI / 4), half = (float) (Math.PI / 2);
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(
                new Card(x, z, yaw, h, w, 0.03F, CARD, segments, true),
                new Card(x, z, yaw + half, h, w, -0.03F, CARD, segments, true),
                new Card(x, z, yaw + q, h, w, 0.04F, CARD_PLUMES, segments, false),
                new Card(x, z, yaw + q + half, h, w, -0.04F, CARD_PLUMES, segments, false)), List.of());
    }

    /** One culm h tall at (x, z) leaning towards yaw: the culm, its leaves and its plume. */
    private static Clump culm(Rng r, float x, float z, float h, float yaw) {
        float phi0 = 0.02F + r.nextFloat() * 0.05F;
        Blade culm = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), h, 0.011F + r.nextFloat() * 0.004F, phi0,
                phi0 + 0.06F + r.nextFloat() * 0.14F, CULM).profile(PlantShape.TAPER).tube(4).at(CULM_AT);
        Pose grown = PlantShape.grown(culm);
        var pieces = new ArrayList<Piece>();
        float side = r.nextFloat() * (float) (Math.PI * 2);
        for (int k = 0; k < LEAVES_AT.length; k++) {
            float len = (0.3F + r.nextFloat() * 0.2F) * (k == 0 || k == LEAVES_AT.length - 1 ? 0.8F : 1);
            pieces.add(new Piece(0, LEAVES_AT[k], PlantShape.footed(leaf(grown, LEAVES_AT[k],
                    side + (float) Math.PI * k + (r.nextFloat() - 0.5F) * 0.9F, len, 0.025F + r.nextFloat() * 0.01F, r), x, z), true));
        }
        pieces.add(new Piece(0, (PLUME + 1) / 2, PlantShape.footed(plume(grown, 0.2F + r.nextFloat() * 0.15F, r), x, z)));
        return new Clump(x, z, r.nextFloat(), List.of(culm), List.of(), pieces);
    }

    /**
     * A leaf from the share {@code at} of a culm (as grown) along yaw: out from the culm at 30-50 degrees above level, then
     * drooping (two segments; doc 18.8: were three, a reed bed was the heaviest stand), the parts column's band at its height
     * across it (the leaf along u). Both faces, tinted, cut out.
     */
    private static List<Quad> leaf(Pose culm, float at, float yaw, float len, float width, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), wx = -dz, wz = dx;
        double[] rise = {Math.toRadians(30 + r.nextFloat() * 20), Math.toRadians(-30 + r.nextFloat() * 20)};
        float[] share = {0.5F, 0.5F}, along = {0, 0.5F, 1};
        float[][] mid = new float[3][];
        mid[0] = new float[]{f[0] + dx * 0.006F, f[1], f[2] + dz * 0.006F};
        for (int k = 0; k < 2; k++) {
            float s = len * share[k];
            mid[k + 1] = new float[]{mid[k][0] + dx * s * (float) Math.cos(rise[k]), mid[k][1] + s * (float) Math.sin(rise[k]),
                    mid[k][2] + dz * s * (float) Math.cos(rise[k])};
        }
        float u0 = (PARTS + 0.03F) / COLUMNS, u1 = (PARTS + 0.97F) / COLUMNS, vMid = 1 - at, h = width / 2, tile = FlowerParts.TILE;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 2; k++) {
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
     * The plume on a culm (as grown): from PLUME of the culm along its tangent there, len long (a little past the tip) and
     * about a third as wide, three crossed planes, nodding a further 20-40 degrees towards the culm's lean; the parts
     * column's plume rows (its foot at the culm's v there). Both faces, real colours, cut out.
     */
    private static List<Quad> plume(Pose culm, float len, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, PLUME, f);
        float[] p0 = {f[0], f[1], f[2]}, t = unit(new float[]{f[3], f[4], f[5]});
        float[] lean = unit(new float[]{t[0], 0, t[2]});
        if (Float.isNaN(lean[0])) lean = new float[]{1, 0, 0};
        double nod = Math.toRadians(20 + r.nextFloat() * 20);
        float[] axis = unit(add(scale(t, (float) Math.cos(nod)), scale(lean, (float) Math.sin(nod))));
        float[] p1 = add(p0, scale(axis, len));
        float w = len * (0.28F + r.nextFloat() * 0.1F), spin = r.nextFloat() * (float) Math.PI;
        float[] a = unit(cross(axis, Math.abs(axis[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0})), b = cross(axis, a);
        float u0 = (PARTS + 0.03F) / COLUMNS, u1 = (PARTS + 0.97F) / COLUMNS, v0 = 1 - PLUME, v1 = 0.5F / 128;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 3; k++) {
            double ang = spin + Math.PI * k / 3;
            float[] across = add(scale(a, (float) Math.cos(ang)), scale(b, (float) Math.sin(ang)));
            float[] facing = cross(axis, across);
            PlantShape.face(out, new float[][]{add(p0, scale(across, -w * 0.15F)), add(p0, scale(across, w * 0.15F)),
                            add(p1, scale(across, w * 0.5F)), add(p1, scale(across, -w * 0.5F))},
                    new float[][]{{u0 + (u1 - u0) * 0.35F, v0}, {u0 + (u1 - u0) * 0.65F, v0}, {u1, v1}, {u0, v1}}, facing, 1, false, false, true);
        }
        return out;
    }
}
