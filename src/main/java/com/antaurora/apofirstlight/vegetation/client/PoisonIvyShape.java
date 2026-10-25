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
 * Poison ivy (Toxicodendron radicans; AFL vegetation P4a batch 2, docs/gameplay/vegetation_v1_design.md 23), its ground
 * form only (the vine on the trees waits for the trees, doc 20.7): a block is 2-3 low plants at a wood's edge.
 * <ul>
 * <li>LOD0: a plant is 3-7 leaves from one woody crown, each a petiole 12-35 cm (round, 3 sides) arching out, and on its
 * tip the three leaflets: the end one on a short stalk of its own, ovate, 7-11 cm, its edge with a few coarse teeth; the
 * two side ones a little smaller, lopsided (a lobe on the outer side), spreading square to the petiole. Glossy (a lighter
 * sheen in the art). The leaflets are rigid parts carried by the petiole and lie down with it.</li>
 * <li>LOD1: two crossed cards a plant. LOD2: one pair of cards.</li>
 * </ul>
 * Sheet columns: 90 the petiole, 91 the parts (the leaflets across the rows of their bands: the end leaflet at 0.25, a
 * side leaflet lobed on one side at 0.45, on the other at 0.65, a second end leaflet at 0.85; tinted), 92 the card. Sways
 * 0.9 times as far as the turf (PlantShape#WIND).
 */
final class PoisonIvyShape {
    static final int STEM = 90, PARTS = 91, CARD = 92;
    /** The leaflets' bands of the parts column: two end leaflets, the left and the right side leaflet. */
    static final float END = 0.25F, LEFT = 0.45F, RIGHT = 0.65F, END_2 = 0.85F;
    private static final float[][] STEM_AT = {{0, 0.4F, 0.75F, 1}, {0, 0.5F, 1}};

    private PoisonIvyShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x7A1C0DL * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int plants = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0;
            for (int p = 0; p < plants; p++) {
                int slot = slots[p];
                // the crowns kept towards the middle: the leaves reach out 20-30 cm
                float x = 0.25F + 0.5F * (slot % 3) / 2 + (r.nextFloat() - 0.5F) * 0.1F, z = 0.25F + 0.5F * (slot / 3) / 2 + (r.nextFloat() - 0.5F) * 0.1F;
                int n = 3 + r.nextInt(5);
                float h = 0;
                float size = 0.8F + r.nextFloat() * 0.4F;
                for (int k = 0; k < n; k++) {
                    long seed = r.next();
                    Clump leaf = leaf(new Rng(seed), x, z, k, n, size, 0);
                    near.add(leaf);
                    h = Math.max(h, BigBluestemShape.top(leaf.grown[0]) + 0.04F);
                }
                tallest = Math.max(tallest, h);
                float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * 3.2F;
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, h, w, 0.1F, CARD, 2, true), new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.1F, CARD, 2, true)), List.of()));
            }
            float yaw = r.nextFloat() * (float) Math.PI, h = tallest, w = h * ASPECT * 5.5F;
            var far = List.of(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                    new Card(0.5F, 0.5F, yaw, h, w, 0.06F, CARD, 1, true), new Card(0.5F, 0.5F, yaw + (float) (Math.PI / 2), h, w, -0.06F, CARD, 1, true)), List.of()));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = far;
        }
        return out;
    }

    /** Leaf k of n round a plant at (x, z): the petiole and its three leaflets. */
    private static Clump leaf(Rng r, float x, float z, int k, int n, float size, int level) {
        float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.01F + r.nextFloat() * 0.03F;
        float phi0 = 0.12F + r.nextFloat() * 0.35F, len = (0.12F + r.nextFloat() * 0.23F) * size;
        Blade petiole = new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, a, a + (float) (Math.PI / 2),
                len, 0.004F, phi0, Math.min(1.0F, phi0 + 0.2F + r.nextFloat() * 0.35F), STEM).tube(3).at(STEM_AT[level]);
        Pose grown = PlantShape.grown(petiole);
        float[] tip = new float[6];
        PlantShape.frame(grown.pts, grown.f, 1, tip);
        float heading = (float) Math.atan2(tip[5], tip[3]), l = (0.07F + r.nextFloat() * 0.04F) * size;
        boolean second = r.nextFloat() < 0.5F;
        var pieces = new ArrayList<Piece>();
        pieces.add(new Piece(0, 1, PlantShape.footed(FlowerParts.stemLeaf(grown, 1, second ? END_2 : END,
                heading + (r.nextFloat() - 0.5F) * 0.3F, l, l * 0.62F, PARTS, r), x, z), true));
        for (int s = -1; s <= 1; s += 2) {
            float ls = l * (0.78F + r.nextFloat() * 0.12F);
            pieces.add(new Piece(0, 0.96F, PlantShape.footed(FlowerParts.stemLeaf(grown, 0.96F, s < 0 ? LEFT : RIGHT,
                    heading + s * (1.25F + r.nextFloat() * 0.35F), ls, ls * 0.66F, PARTS, r), x, z), true));
        }
        return new Clump(x, z, r.nextFloat(), List.of(petiole), List.of(), pieces);
    }
}
