package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Common milkweed (Asclepias syriaca; AFL vegetation P4a batch 2, docs/gameplay/vegetation_v1_design.md 23): a block is a
 * square metre of a clonal colony, 2-4 stems from one spreading root, one or two bluegrass tufts between them.
 * <ul>
 * <li>LOD0: a stem is upright, 0.9-1.5 m, stout (5 sides, tapering), its lowest fifth bare. Six nodes from 0.22 to 0.72 of
 * its height, each a pair of opposite leaves square to the pair below: broad oblong, 11-19 cm long and 5-8 cm wide, thick,
 * a pale midrib and straight side veins; a smaller pair at 0.84. 70 % of the stems flower: 1-4 umbels from the upper axils
 * (0.72-0.95), each a drooping ball of pink-purple flowers 6-8 cm across on a short stalk out from the stem. The rest carry
 * 1-3 seed pods: warty, grey-green, 8-11 cm, held upright. Umbels and pods are two crossed quads each (real colours).</li>
 * <li>LOD1: two crossed foliage cards and two crossed flower cards a stem. LOD2: a turf card and one pair of milkweed
 * cards. Tall: drawn at every distance.</li>
 * </ul>
 * Sheet columns: 86 the stem, 87 the parts (rows 0-16: the umbel in the left half, the pod in the right, real colours;
 * the six leaf bands across the rows of their heights, tinted), 88 the foliage card, 89 the flower card (real colours).
 * Sways 1.1 times as far as the turf (PlantShape#WIND).
 */
final class MilkweedShape {
    static final int STEM = 86, PARTS = 87, CARD = 88, CARD_FLOWERS = 89;
    /** The nodes (shares of the stem; the art lays the leaf bands at these rows). */
    static final float[] NODES = {0.22F, 0.32F, 0.42F, 0.52F, 0.62F, 0.72F};
    /** The umbel's and the pod's rows of the parts column. */
    static final float HEAD = 16F / 128;
    private static final float[] STEM_AT = {0, 0.3F, 0.6F, 0.8F, 1};

    private MilkweedShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xA5C1EL * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int stems = 2 + r.nextInt(3), tufts = 1 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float cx = 0.35F + r.nextFloat() * 0.3F, cz = 0.35F + r.nextFloat() * 0.3F, tallest = 0;
            for (int k = 0; k < stems; k++) {
                double a = (k + r.nextFloat() * 0.7F) / stems * Math.PI * 2;
                float d = 0.12F + r.nextFloat() * 0.25F;
                float x = clamp(cx + (float) Math.cos(a) * d), z = clamp(cz + (float) Math.sin(a) * d);
                Clump stem = stem(r, x, z);
                near.add(stem);
                float h = BigBluestemShape.top(stem.grown[0]);
                tallest = Math.max(tallest, h);
                mid.add(FlowerParts.cards(r, x, z, h * 1.04F, 1.2F, 2, CARD, CARD_FLOWERS));
            }
            for (int k = 0; k < tufts; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                near.add(BluegrassShape.tuft(r, x, z, 0.22F + r.nextFloat() * 0.1F, 7 + r.nextInt(3)));
                mid.add(BluegrassShape.middle(r, x, z));
            }
            var far = new ArrayList<Clump>();
            far.add(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
            far.add(FlowerParts.cards(r, cx, cz, tallest * 1.02F, 2.0F, 1, CARD, CARD_FLOWERS));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** One stem at (x, z): its pairs of leaves and its umbels or pods. */
    private static Clump stem(Rng r, float x, float z) {
        float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.06F, len = 0.9F + r.nextFloat() * 0.6F;
        Blade stem = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), len, 0.01F + r.nextFloat() * 0.003F, phi0,
                phi0 + 0.03F + r.nextFloat() * 0.09F, STEM).profile(PlantShape.TAPER).tube(5).at(STEM_AT);
        Pose grown = PlantShape.grown(stem);
        var pieces = new ArrayList<Piece>();
        float pair = r.nextFloat() * (float) Math.PI, size = (0.85F + r.nextFloat() * 0.25F) * (0.8F + 0.2F * len / 1.5F);
        for (int k = 0; k <= NODES.length; k++) {
            boolean top = k == NODES.length;
            float at = top ? 0.84F : NODES[k], band = top ? NODES[NODES.length - 1] : NODES[k];
            float t = k / (float) NODES.length, l = (top ? 0.09F : 0.19F - 0.06F * Math.abs(t - 0.35F) / 0.65F) * size;
            for (int s = 0; s < 2; s++)
                pieces.add(new Piece(0, at, PlantShape.footed(FlowerParts.stemLeaf(grown, at, band,
                        pair + (float) (Math.PI / 2) * k + (float) Math.PI * s + (r.nextFloat() - 0.5F) * 0.25F, l, l * 0.42F, PARTS, r), x, z), true));
        }
        boolean flowers = r.nextFloat() < 0.7F;
        int n = flowers ? 1 + r.nextInt(4) : 1 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            float at = 0.72F + 0.23F * (k + r.nextFloat()) / n, side = pair + 2.4F * k + (r.nextFloat() - 0.5F) * 0.5F, spin = r.nextFloat() * (float) Math.PI;
            if (flowers) {
                float s = 0.06F + r.nextFloat() * 0.02F;
                pieces.add(new Piece(0, at, PlantShape.footed(FlowerParts.crossed(grown, at, side, 0.035F + s * 0.4F, -0.02F - s * 0.3F,
                        s, s, PARTS, 0.02F, 0.49F, 0.3F / 128, HEAD, spin), x, z)));
            } else {
                float s = 0.08F + r.nextFloat() * 0.03F;
                pieces.add(new Piece(0, at, PlantShape.footed(FlowerParts.crossed(grown, at, side, 0.03F, s * 0.45F,
                        s * 0.36F, s, PARTS, 0.51F, 0.98F, 0.3F / 128, HEAD, spin), x, z)));
            }
        }
        return new Clump(x, z, r.nextFloat(), List.of(stem), List.of(), pieces);
    }
}
