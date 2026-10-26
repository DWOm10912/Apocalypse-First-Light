package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Card;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Wild mint (Mentha arvensis; AFL vegetation P4a batch 2, docs/gameplay/vegetation_v1_design.md 23): a block is a square
 * metre of a clonal patch on moist ground, 6-10 stems from creeping rhizomes.
 * <ul>
 * <li>LOD0: a stem is 30-60 cm, square (4 sides, tapering), leaning a little; five nodes from 0.2 to 0.84 of its height,
 * each a pair of opposite leaves (ovate, toothed, 3.5-6 cm), each pair square to the one below. On 60 % of the stems the
 * third and fourth nodes hold a whorl of pale lilac flowers round the stem in the leaves' axils (two crossed quads,
 * real colours).</li>
 * <li>LOD1: two or three groups, two crossed cards a group. LOD2: one pair of cards.</li>
 * </ul>
 * Sheet columns: 83 the stem, 84 the parts (rows 0-9 the flower whorl, real colours; the five leaf bands across the rows
 * of their heights, tinted), 85 the card. Sways as the turf.
 */
final class MintShape {
    static final int STEM = 83, PARTS = 84, CARD = 85;
    /** The nodes (shares of the stem; the art lays the leaf bands at these rows). */
    static final float[] NODES = {0.2F, 0.36F, 0.52F, 0.68F, 0.84F};
    /** The flower whorl's rows of the parts column. */
    static final float WHORL = 9.5F / 128;
    private static final float[] STEM_AT = {0, 0.35F, 0.7F, 1};

    private MintShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x3147L * 31 + v * 0x9E3779B97F4A7C15L);
            int stems = 6 + r.nextInt(5), groups = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float[] gx = new float[groups], gz = new float[groups], gh = new float[groups];
            int[] gn = new int[groups];
            float tallest = 0;
            // the rhizomes send the stems up in loose drifts round two or three points
            float[] px = new float[groups], pz = new float[groups];
            for (int g = 0; g < groups; g++) { px[g] = 0.2F + r.nextFloat() * 0.6F; pz[g] = 0.2F + r.nextFloat() * 0.6F; }
            for (int k = 0; k < stems; k++) {
                int g = k % groups;
                double a = r.nextFloat() * Math.PI * 2;
                float d = r.nextFloat() * 0.22F;
                float x = clamp(px[g] + (float) Math.cos(a) * d), z = clamp(pz[g] + (float) Math.sin(a) * d);
                Clump stem = stem(r, x, z);
                near.add(stem);
                float h = BigBluestemShape.top(stem.grown[0]);
                tallest = Math.max(tallest, h);
                gx[g] += x; gz[g] += z; gn[g]++; gh[g] = Math.max(gh[g], h);
            }
            for (int g = 0; g < groups; g++) {
                float yaw = r.nextFloat() * (float) Math.PI, h = gh[g] * 1.05F, w = h * ASPECT * 2.2F, x = gx[g] / gn[g], z = gz[g] / gn[g];
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, h, w, 0.06F, CARD, 2, true), new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.06F, CARD, 2, true)), List.of()));
            }
            float yaw = r.nextFloat() * (float) Math.PI, h = tallest, w = h * ASPECT * 4.4F;
            var far = List.of(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                    new Card(0.5F, 0.5F, yaw, h, w, 0.04F, CARD, 1, true), new Card(0.5F, 0.5F, yaw + (float) (Math.PI / 2), h, w, -0.04F, CARD, 1, true)), List.of()));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = far;
        }
        return out;
    }

    /** One stem at (x, z): its pairs of leaves and, if it flowers, the whorls in the upper axils. */
    private static Clump stem(Rng r, float x, float z) {
        float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = 0.04F + r.nextFloat() * 0.25F, len = 0.3F + r.nextFloat() * 0.3F;
        Blade stem = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), len, 0.0045F + r.nextFloat() * 0.001F, phi0,
                phi0 + 0.05F + r.nextFloat() * 0.2F, STEM).profile(PlantShape.TAPER).tube(4).at(STEM_AT);
        Pose grown = PlantShape.grown(stem);
        var pieces = new ArrayList<Piece>();
        float pair = r.nextFloat() * (float) Math.PI, size = (0.85F + r.nextFloat() * 0.3F) * (0.7F + 0.3F * len / 0.6F);
        boolean flowers = r.nextFloat() < 0.6F;
        for (int k = 0; k < NODES.length; k++) {
            float t = k / (float) (NODES.length - 1), l = (0.045F + 0.015F * (float) Math.sin(Math.PI * (0.2F + 0.6F * t))) * size;
            for (int s = 0; s < 2; s++)
                pieces.add(new Piece(0, NODES[k], PlantShape.footed(FlowerParts.stemLeaf(grown, NODES[k], NODES[k],
                        pair + (float) (Math.PI / 2) * k + (float) Math.PI * s + (r.nextFloat() - 0.5F) * 0.3F, l, l * 0.55F, PARTS, r), x, z), true));
            if (flowers && (k == 2 || k == 3))
                pieces.add(new Piece(0, NODES[k], PlantShape.footed(FlowerParts.crossed(grown, NODES[k], 0, 0, 0.003F,
                        0.022F + r.nextFloat() * 0.006F, 0.016F, PARTS, 0.04F, 0.96F, 0.3F / 128, WHORL, r.nextFloat() * (float) Math.PI), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), List.of(stem), List.of(), pieces);
    }
}
