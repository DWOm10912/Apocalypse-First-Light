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
 * Common mullein (Verbascum thapsus; AFL vegetation P4a batch 2, docs/gameplay/vegetation_v1_design.md 23): a biennial of
 * disturbed open ground. A block is one plant with 2-3 bluegrass tufts round it; its layout decides the year (doc 20.2):
 * <ul>
 * <li>First year (45 % of the layouts): a flat rosette of 6-10 felted leaves, oblong, 18-36 cm long and 7-11 cm wide,
 * lying out on the ground, the inner ones rising.</li>
 * <li>Second year: what is left of the rosette (4-6 leaves) and the flowering stalk, 1-1.8 m, stout and felted (6 sides,
 * tapering), eight stem leaves on its lower half, smaller up the stalk, and on its upper 40-50 % the dense spike: a club
 * 4-5 cm thick of woolly buds with a band of open yellow flowers, the spent part below it green-brown capsules (a capped
 * tube carried by the stalk, its v the stalk's own at its heights, as the cattail's spikes).</li>
 * <li>LOD1: every other rosette leaf, two segments; the stalk as two crossed cards. LOD2: every third rosette leaf, the
 * stalk's cards.</li>
 * </ul>
 * All in the sheet's own colours (the grey-green felt is not the grass's colour). Sheet columns: 78 the rosette leaf, 79
 * the stalk, 80 the spike (rows 0-80 at the stalk's heights), 81 the parts (the stem leaves across the rows of their
 * heights, 0.08-0.46), 82 the stalk card. Tall: drawn at every distance. The stalk is stiff (PlantShape#WIND 0.8).
 */
final class MulleinShape {
    static final int LEAF = 78, STALK = 79, SPIKE = 80, PARTS = 81, CARD = 82;
    /** The stem leaves' bands (shares of the stalk; the art lays them at these rows), two leaves to a band. */
    static final float[] LEAVES_AT = {0.08F, 0.18F, 0.28F, 0.38F};
    private static final float[][] LEAF_AT = {{0, 0.35F, 0.7F, 1}, {0, 0.5F, 1}, {0, 1}};
    private static final float[] STALK_AT = {0, 0.25F, 0.5F, 0.75F, 1}, SPIKE_AT = {0, 0.06F, 0.94F, 1};

    private MulleinShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x3E11E1L * 31 + v * 0x9E3779B97F4A7C15L);
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = new ArrayList<>();
            int[] slots = r.slots();
            boolean flowering = r.nextFloat() >= 0.45F;
            float x = clamp(0.5F + (r.nextFloat() - 0.5F) * 0.24F), z = clamp(0.5F + (r.nextFloat() - 0.5F) * 0.24F);
            int n = flowering ? 4 + r.nextInt(3) : 6 + r.nextInt(5);
            float scale = flowering ? 0.8F : 0.85F + r.nextFloat() * 0.3F, turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int k = 0; k < n; k++) {
                long seed = r.next();
                for (int level = 0; level < PlantShape.LEVELS; level++)
                    if (k % (level + 1) == 0)
                        out[v][level].add(new Clump(x, z, (k + 0.5F) / n, List.of(leaf(new Rng(seed), x, z, turn, k, n, scale, level)), List.of(), List.of()));
            }
            if (flowering) {
                Clump stalk = stalk(r, x, z);
                out[v][0].add(stalk);
                float h = BigBluestemShape.top(stalk.grown[0]) * 1.02F, w = h * ASPECT * 0.9F, yaw = r.nextFloat() * (float) Math.PI;
                for (int level = 1; level < PlantShape.LEVELS; level++)
                    out[v][level].add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                            new Card(x, z, yaw, h, w, 0.03F, CARD, 2, false), new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.03F, CARD, 2, false)), List.of()));
            }
            int tufts = 2 + r.nextInt(2), placed = 0;
            for (int k = 0; k < 9 && placed < tufts; k++) {
                int slot = slots[k];
                if (slot == 4) continue;   // the middle is the mullein's
                float tx = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.15F), tz = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.15F);
                out[v][0].add(BluegrassShape.tuft(r, tx, tz, 0.22F + r.nextFloat() * 0.1F, 7 + r.nextInt(3)));
                out[v][1].add(BluegrassShape.middle(r, tx, tz));
                placed++;
            }
            out[v][2].add(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = List.copyOf(out[v][level]);
        }
        return out;
    }

    /** Rosette leaf k of n round (x, z): the outer ones lying out flat, the inner two rising. */
    private static Blade leaf(Rng r, float x, float z, float turn, int k, int n, float scale, int level) {
        float a = turn + (k + r.nextFloat() * 0.6F) / n * (float) (Math.PI * 2), off = 0.01F + r.nextFloat() * 0.015F;
        boolean inner = k >= n - 2;
        float phi0 = inner ? 0.45F + r.nextFloat() * 0.3F : 0.95F + r.nextFloat() * 0.3F;
        float phi1 = inner ? phi0 + 0.3F + r.nextFloat() * 0.2F : 1.4F + r.nextFloat() * 0.18F;
        float len = (inner ? 0.14F + r.nextFloat() * 0.08F : 0.18F + r.nextFloat() * 0.18F) * scale;
        return new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, a, a + (float) (Math.PI / 2),
                len, len * (0.34F + r.nextFloat() * 0.06F), phi0, phi1, LEAF)
                .profile(PlantShape.FLAT).at(LEAF_AT[level]).inset(0.03F).cutout().untinted().twist((r.nextFloat() - 0.5F) * 0.3F);
    }

    /** The flowering stalk at (x, z): its stem leaves and its spike. */
    private static Clump stalk(Rng r, float x, float z) {
        float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.05F, len = 1.0F + r.nextFloat() * 0.8F;
        Blade stalk = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), len, 0.022F + r.nextFloat() * 0.004F, phi0,
                phi0 + 0.02F + r.nextFloat() * 0.05F, STALK).profile(PlantShape.TAPER).tube(6).at(STALK_AT).untinted();
        Pose grown = PlantShape.grown(stalk);
        var pieces = new ArrayList<Piece>();
        float side = r.nextFloat() * (float) (Math.PI * 2);
        for (int k = 0; k < LEAVES_AT.length * 2; k++) {
            float at = LEAVES_AT[k / 2] + (k % 2 == 0 ? -0.025F : 0.025F), t = k / (float) (LEAVES_AT.length * 2 - 1);
            pieces.add(new Piece(0, at, PlantShape.footed(FlowerParts.stemLeaf(grown, at, LEAVES_AT[k / 2],
                    side + 2.4F * k + (r.nextFloat() - 0.5F) * 0.5F, 0.22F - 0.15F * t, 0.065F - 0.035F * t, PARTS, r, false), x, z), true));
        }
        float a = 0.5F + r.nextFloat() * 0.12F, radius = 0.019F + r.nextFloat() * 0.005F;
        float[] mid = new float[6], start = new float[6];
        PlantShape.frame(grown.pts, grown.f, (a + 1) / 2, mid);
        PlantShape.frame(grown.pts, grown.f, a, start);
        float phi = (float) Math.acos(Math.max(-1, Math.min(1, mid[4]))), heading = (float) Math.atan2(mid[5], mid[3]);
        Blade spike = new Blade(start[0], start[2], heading, heading + (float) (Math.PI / 2), (1 - a) * len, radius * 2, phi, phi, SPIKE)
                .y(start[1]).tube(7).profile(PlantShape.SPIKE).at(SPIKE_AT).untinted().capped().v(1 - a, 0);
        pieces.add(new Piece(0, (a + 1) / 2, PlantShape.footed(PlantShape.quads(PlantShape.grown(spike)), x, z)));
        return new Clump(x, z, r.nextFloat(), List.of(stalk), List.of(), pieces);
    }
}
