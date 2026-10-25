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
 * Smooth cordgrass (Spartina alterniflora; AFL vegetation P4a batch 1, docs/gameplay/vegetation_v1_design.md 21): a block
 * is a square metre of the even, dense meadow of the low tidal marsh, on mud or in water a block deep (MeshPlantBlock.Wet,
 * as the reed).
 * <ul>
 * <li>LOD0: 9-13 single shoots spread over the block, mostly the tall form of the creek banks (0.9-1.5 m), some shorter
 * (0.5-0.9 m): a stout, smooth culm (round, 3 sides, tapering, nearly upright) with five leaves at 0.12-0.6 of its
 * height, alternate, 20-40 cm long and 9-14 mm wide, stiffly ascending (two segments). About half carry the narrow head
 * from 0.82 of the culm: spikes pressed to the axis, 12-25 cm, two crossed planes. Leaves and head are rigid parts carried
 * by the culm.</li>
 * <li>LOD1: three crossed pairs of stand cards a block. LOD2: two crossed wide cards a block. Tall: drawn at every
 * distance.</li>
 * </ul>
 * Sheet columns: 69 the culm (sheaths overlapping), 70 the parts (rows 0-23 the head, its foot at row 23: 0.82 of the
 * culm, real colours; the five leaves across the rows of their heights, tinted), 71 the stand card. Stiff: sways 1.1 times
 * as far as the turf (PlantShape#WIND).
 */
final class CordgrassShape {
    static final int CULM = 69, PARTS = 70, CARD = 71;
    static final float[] LEAVES_AT = {0.12F, 0.24F, 0.36F, 0.48F, 0.6F};
    /** The head runs from this share of the culm (its rows: 0 .. (1 - HEAD) of the parts column). */
    static final float HEAD = 0.82F;
    private static final float[] CULM_AT = {0, 0.3F, 0.6F, HEAD, 1};

    private CordgrassShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x5BA7L * 31 + v * 0x9E3779B97F4A7C15L);
            int shoots = 9 + r.nextInt(5);
            var near = new ArrayList<Clump>();
            float tallest = 0, sum = 0;
            for (int k = 0; k < shoots; k++) {
                // jittered over a 4 x 4 grid: even, as the meadow is
                int cell = (k * 7 + v) % 16;
                float x = clamp((cell % 4 + 0.15F + r.nextFloat() * 0.7F) / 4), z = clamp((cell / 4 + 0.15F + r.nextFloat() * 0.7F) / 4);
                float h = r.nextFloat() < 0.7F ? 0.9F + r.nextFloat() * 0.6F : 0.5F + r.nextFloat() * 0.4F;
                tallest = Math.max(tallest, h);
                sum += h;
                near.add(shoot(new Rng(r.next()), x, z, h));
            }
            var mid = new ArrayList<Clump>();
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int k = 0; k < 3; k++) {
                double a = turn + k * Math.PI * 2 / 3;
                mid.add(cards(r, clamp(0.5F + 0.24F * (float) Math.cos(a)), clamp(0.5F + 0.24F * (float) Math.sin(a)), (sum / shoots + tallest) / 2, 1.0F, 3));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.of(cards(r, 0.5F, 0.5F, tallest * 0.95F, 2.0F, 1));
        }
        return out;
    }

    /** Two crossed stand cards h tall (tinted). */
    private static Clump cards(Rng r, float x, float z, float h, float wide, int segments) {
        float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * wide, half = (float) (Math.PI / 2);
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(
                new Card(x, z, yaw, h, w, 0.03F, CARD, segments, true),
                new Card(x, z, yaw + half, h, w, -0.03F, CARD, segments, true)), List.of());
    }

    /** One shoot h tall at (x, z): the culm, its leaves and perhaps its head. */
    private static Clump shoot(Rng r, float x, float z, float h) {
        float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = 0.01F + r.nextFloat() * 0.05F;
        Blade culm = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), h, 0.009F + r.nextFloat() * 0.004F, phi0,
                phi0 + 0.03F + r.nextFloat() * 0.08F, CULM).profile(PlantShape.TAPER).tube(3).at(CULM_AT);
        Pose grown = PlantShape.grown(culm);
        var pieces = new ArrayList<Piece>();
        float side = r.nextFloat() * (float) (Math.PI * 2);
        for (int k = 0; k < LEAVES_AT.length; k++) {
            float len = (0.4F - 0.04F * k) * (0.7F + r.nextFloat() * 0.3F) * Math.min(1, h / 1.1F);
            pieces.add(new Piece(0, LEAVES_AT[k], PlantShape.footed(leaf(grown, LEAVES_AT[k],
                    side + (float) Math.PI * k + (r.nextFloat() - 0.5F) * 0.7F, len, 0.009F + r.nextFloat() * 0.005F, r), x, z), true));
        }
        if (r.nextFloat() < 0.55F) pieces.add(new Piece(0, (HEAD + 1) / 2, PlantShape.footed(head(grown, 0.12F + r.nextFloat() * 0.13F, r), x, z)));
        return new Clump(x, z, r.nextFloat(), List.of(culm), List.of(), pieces);
    }

    /**
     * A leaf from the share {@code at} of a culm (as grown) along yaw: stiffly up at 55-70 degrees above level, then 25-45
     * (two segments), the parts column's band at its height across it (the leaf along u). Both faces, tinted, cut out.
     */
    private static List<Quad> leaf(Pose culm, float at, float yaw, float len, float width, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), wx = -dz, wz = dx;
        double[] rise = {Math.toRadians(55 + r.nextFloat() * 15), Math.toRadians(25 + r.nextFloat() * 20)};
        float[] along = {0, 0.5F, 1};
        float[][] mid = new float[3][];
        mid[0] = new float[]{f[0] + dx * 0.005F, f[1], f[2] + dz * 0.005F};
        for (int k = 0; k < 2; k++) {
            float s = len * 0.5F;
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
     * The head on a culm (as grown): from HEAD of the culm along its tangent there, len long and 3-4 cm wide, nearly
     * straight; two crossed planes, the parts column's head rows (its foot at the culm's v there). Both faces, real colours,
     * cut out.
     */
    private static List<Quad> head(Pose culm, float len, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(culm.pts, culm.f, HEAD, f);
        float[] p0 = {f[0], f[1], f[2]}, axis = unit(new float[]{f[3], f[4], f[5]});
        float[] p1 = add(p0, scale(axis, len));
        float w = 0.03F + r.nextFloat() * 0.01F, spin = r.nextFloat() * (float) Math.PI;
        float[] a = unit(cross(axis, Math.abs(axis[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0})), b = cross(axis, a);
        float u0 = (PARTS + 0.03F) / COLUMNS, u1 = (PARTS + 0.97F) / COLUMNS, v0 = 1 - HEAD, v1 = 0.5F / 128;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 2; k++) {
            double ang = spin + Math.PI * k / 2;
            float[] across = add(scale(a, (float) Math.cos(ang)), scale(b, (float) Math.sin(ang)));
            float[] facing = cross(axis, across);
            PlantShape.face(out, new float[][]{add(p0, scale(across, -w * 0.5F)), add(p0, scale(across, w * 0.5F)),
                            add(p1, scale(across, w * 0.4F)), add(p1, scale(across, -w * 0.4F))},
                    new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}, facing, 1, false, false, true);
        }
        return out;
    }
}
