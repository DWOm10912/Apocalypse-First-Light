package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
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
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Canada goldenrod (Solidago canadensis; AFL vegetation P2b batch 3, docs/gameplay/vegetation_v1_design.md 18): a block is
 * a square metre of a clonal colony, 4-7 stems from one spreading rootstock, one or two bluegrass tufts between them.
 * <ul>
 * <li>LOD0: a stem is upright, 1-1.5 m (round, 4 sides, tapering), its lowest fifth bare (the lower leaves wither), twelve
 * lanceolate leaves 9-14 cm long and 2-2.5 cm wide crowded up to 0.73 of its height (two to each of the parts column's
 * six leaf bands), spirally alternate, rising off the stem then levelling. Its top is the panicle: 8-12 one-sided racemes
 * from 0.7-0.97 of the height, the lower ones longer (20 cm down to 8), arching out and over, the tiny yellow heads
 * crowded along their upper side; a short raceme on the tip. Leaves and racemes are rigid parts carried by the stem; the
 * leaves lie down with a fallen stem.</li>
 * <li>LOD1: the stems in two or three groups, two crossed foliage cards and two crossed flower cards a group. LOD2: a
 * turf card and one pair of goldenrod cards. Tall: drawn at every distance.</li>
 * </ul>
 * Sheet columns: 53 the stem, 54 the parts (rows 0-32: the raceme, a pattern repeating down the rows, so a raceme takes the
 * rows of the heights it spans; real colours; the six leaves across the rows of their heights, tinted), 55 the foliage
 * card, 56 the flower card (real colours). Sways 1.2 times as far as the turf (PlantShape#WIND).
 */
final class GoldenrodShape {
    static final int STEM = 53, PARTS = 54, CARD = 55, CARD_FLOWERS = 56;
    /** Where the leaves sit (shares of the stem; the art lays them at these rows). */
    static final float[] LEAVES_AT = {0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F};
    /** The racemes' rows of the parts column. */
    static final float RACEMES = 32F / 128;
    private static final float[] STEM_AT = {0, 0.3F, 0.6F, 0.8F, 1};

    private GoldenrodShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x601D20DL * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int stems = 4 + r.nextInt(4), tufts = 1 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0;
            // a colony: the stems round one point (a rhizome sends them up in loose clusters), in two or three groups
            float cx = 0.3F + r.nextFloat() * 0.4F, cz = 0.3F + r.nextFloat() * 0.4F;
            int groups = 2 + r.nextInt(2);
            float[] gx = new float[groups], gz = new float[groups], gh = new float[groups];
            int[] gn = new int[groups];
            for (int k = 0; k < stems; k++) {
                double a = (k % groups + r.nextFloat() * 0.8F) / groups * Math.PI * 2;
                float d = 0.08F + r.nextFloat() * 0.38F;
                float x = clamp(cx + (float) Math.cos(a) * d), z = clamp(cz + (float) Math.sin(a) * d);
                long seed = r.next();
                Clump stem = stem(new Rng(seed), x, z);
                near.add(stem);
                float h = stem.blades.get(0).len;
                tallest = Math.max(tallest, h);
                int g = k % groups;
                gx[g] += x; gz[g] += z; gn[g]++; gh[g] = Math.max(gh[g], h);
            }
            for (int g = 0; g < groups; g++)
                mid.add(FlowerParts.cards(r, gx[g] / gn[g], gz[g] / gn[g], gh[g] * 1.05F, 1.2F, 2, CARD, CARD_FLOWERS));
            for (int k = 0; k < tufts; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                near.add(BluegrassShape.tuft(r, x, z, 0.22F + r.nextFloat() * 0.1F, 7 + r.nextInt(3)));
                mid.add(BluegrassShape.middle(r, x, z));
            }
            var far = new ArrayList<Clump>();
            far.add(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
            far.add(FlowerParts.cards(r, cx, cz, tallest * 1.02F, 1.6F, 1, CARD, CARD_FLOWERS));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** One stem at (x, z): the stem, its leaves and its panicle. */
    private static Clump stem(Rng r, float x, float z) {
        float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.06F, len = 1.0F + r.nextFloat() * 0.5F;
        Blade stem = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), len, 0.007F + r.nextFloat() * 0.002F, phi0,
                phi0 + 0.04F + r.nextFloat() * 0.12F, STEM).profile(PlantShape.TAPER).tube(4).at(STEM_AT);
        Pose grown = PlantShape.grown(stem);
        var pieces = new ArrayList<Piece>();
        float side = r.nextFloat() * (float) (Math.PI * 2), scale = 0.85F + r.nextFloat() * 0.3F;
        for (int k = 0; k < LEAVES_AT.length * 2; k++) {
            // two leaves to a band, a little below and above its height (the band's rows still within reach)
            float at = LEAVES_AT[k / 2] + (k % 2 == 0 ? -0.03F : 0.03F);
            pieces.add(new Piece(0, at, PlantShape.footed(FlowerParts.stemLeaf(grown, at, LEAVES_AT[k / 2],
                    side + 2.4F * k + (r.nextFloat() - 0.5F) * 0.5F, (0.14F - 0.004F * k) * scale, 0.026F * scale, PARTS, r), x, z), true));
        }
        int racemes = 8 + r.nextInt(5);
        for (int k = 0; k < racemes; k++) {
            float t = k / (float) (racemes - 1);   // 0 the lowest .. 1 the highest
            float at = 0.7F + 0.27F * t, rl = (0.2F - 0.12F * t) * (0.85F + r.nextFloat() * 0.3F) * (len / 1.25F);
            pieces.add(new Piece(0, at, PlantShape.footed(raceme(grown, at, side + 2.4F * k + 1.2F, rl, 0.012F + 0.006F * (1 - t),
                    0.35F + 0.4F * (1 - t), r), x, z)));
        }
        pieces.add(new Piece(0, 1, PlantShape.footed(raceme(grown, 1, side, 0.07F * len / 1.25F, 0.012F, 0.05F, r), x, z)));
        return new Clump(x, z, r.nextFloat(), List.of(stem), List.of(), pieces);
    }

    /**
     * One raceme from the share {@code at} of a stem (as grown), along yaw: a strip len long and width wide, rising off the
     * stem at {@code lift} radians from it and arching out and over (two segments), its upper face up; the parts column's
     * raceme rows from the stem's v there upwards. Both faces, real colours, cut out.
     */
    private static List<Quad> raceme(Pose stem, float at, float yaw, float len, float width, float lift, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(stem.pts, stem.f, at, f);
        float[] p0 = {f[0], f[1], f[2]}, t = {f[3], f[4], f[5]};
        float[] out = unit(new float[]{(float) Math.cos(yaw), 0, (float) Math.sin(yaw)});
        float l0 = lift + (r.nextFloat() - 0.5F) * 0.2F, l1 = l0 + 0.7F + r.nextFloat() * 0.4F;
        float[] s0 = unit(add(scale(t, (float) Math.cos(l0)), scale(out, (float) Math.sin(l0))));
        float[] s1 = unit(add(scale(t, (float) Math.cos(l1)), scale(out, (float) Math.sin(l1))));
        float[] p1 = add(p0, scale(s0, len * 0.5F)), p2 = add(p1, scale(s1, len * 0.5F));
        float[] across = unit(cross(t, out));
        float h = width / 2;
        float u0 = (PARTS + 0.06F) / COLUMNS, u1 = (PARTS + 0.94F) / COLUMNS;
        float va = Math.min(RACEMES, 1 - at), vb = Math.max(0.5F / 128, va - len / 2), vc = Math.max(0.5F / 128, vb - len / 2);
        var quads = new ArrayList<Quad>();
        float[] up = unit(cross(across, s0));
        if (up[1] < 0) up = scale(up, -1);
        PlantShape.face(quads, new float[][]{add(p0, scale(across, -h * 0.6F)), add(p0, scale(across, h * 0.6F)), add(p1, scale(across, h)), add(p1, scale(across, -h))},
                new float[][]{{u0, va}, {u1, va}, {u1, vb}, {u0, vb}}, up, 1, false, false, true);
        PlantShape.face(quads, new float[][]{add(p1, scale(across, -h)), add(p1, scale(across, h)), add(p2, scale(across, h * 0.5F)), add(p2, scale(across, -h * 0.5F))},
                new float[][]{{u0, vb}, {u1, vb}, {u1, vc}, {u0, vc}}, up, 1, false, false, true);
        return quads;
    }
}
