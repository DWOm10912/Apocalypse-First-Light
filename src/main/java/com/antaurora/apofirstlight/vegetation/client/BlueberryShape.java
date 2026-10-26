package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Card;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Lowbush blueberry (Vaccinium angustifolium; AFL vegetation P4a batch 3, docs/gameplay/vegetation_v1_design.md 24): a
 * low shrub of dry, acid ground under open pines and oaks and on ledges, spreading by rhizomes into mats. A block is 3-5
 * crowns of the mat.
 * <ul>
 * <li>LOD0: a crown is 3-4 woody sprays, each a strip 15-37 cm long and 7-11 cm wide whose cut-out picture is the spray:
 * a red-brown and green twig, branching, with small alternate elliptic leaves; a third of them hang clusters of blue
 * berries (with their pale bloom). The sprays lean 12-37 degrees off upright at the foot, then arch out (3 segments,
 * twisting a little); each has a second strip square to it so it has body from every side.</li>
 * <li>LOD1: every other spray, its first strip only, two segments. LOD2: two crossed cards a block.</li>
 * </ul>
 * In the sheet's own colours (the leaves blue-green, the twigs red: not the grass's colour). Sheet columns: 93 the spray,
 * 94 the spray with berries, 95 the card. Woody: sways 0.6 times as far as the turf (PlantShape#WIND).
 */
final class BlueberryShape {
    static final int SPRAY = 93, BERRIES = 94, CARD = 95;
    private static final float[][] SPRAY_AT = {{0, 0.35F, 0.7F, 1}, {0, 0.5F, 1}};

    private BlueberryShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xB1BE77L * 31 + v * 0x9E3779B97F4A7C15L);
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = new ArrayList<>();
            int[] slots = r.slots();
            int crowns = 3 + r.nextInt(3), k = 0;
            float tallest = 0;
            for (int c = 0; c < crowns; c++) {
                int slot = slots[c];
                // the crowns kept towards the middle: the sprays reach out 15-35 cm
                float x = 0.27F + 0.46F * (slot % 3) / 2 + (r.nextFloat() - 0.5F) * 0.08F, z = 0.27F + 0.46F * (slot / 3) / 2 + (r.nextFloat() - 0.5F) * 0.08F;
                int n = 3 + r.nextInt(2);
                for (int s = 0; s < n; s++, k++) {
                    long seed = r.next();
                    Blade b = spray(new Rng(seed), x, z, s, n, 0, false);
                    tallest = Math.max(tallest, BigBluestemShape.top(PlantShape.grown(b)));
                    out[v][0].add(new Clump(x, z, r.nextFloat(), List.of(b, spray(new Rng(seed), x, z, s, n, 0, true)), List.of(), List.of()));
                    if (k % 2 == 0) out[v][1].add(new Clump(x, z, r.nextFloat(), List.of(spray(new Rng(seed), x, z, s, n, 1, false)), List.of(), List.of()));
                }
            }
            float yaw = r.nextFloat() * (float) Math.PI, h = tallest * 1.05F, w = h * ASPECT * 5F, half = (float) (Math.PI / 2);
            out[v][2].add(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                    new Card(0.5F, 0.5F, yaw, h, w, 0.08F, CARD, 2, false), new Card(0.5F, 0.5F, yaw + half, h, w, -0.08F, CARD, 2, false)), List.of()));
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = List.copyOf(out[v][level]);
        }
        return out;
    }

    /** Spray s of n round a crown at (x, z), at a level (0 or 1); {@code cross}: its second strip, square to the first. */
    private static Blade spray(Rng r, float x, float z, int s, int n, int level, boolean cross) {
        float a = (s + r.nextFloat() * 0.7F) / n * (float) (Math.PI * 2), off = 0.01F + r.nextFloat() * 0.02F;
        float phi0 = 0.2F + r.nextFloat() * 0.45F, phi1 = Math.min(1.35F, phi0 + 0.35F + r.nextFloat() * 0.45F);
        boolean berries = r.nextFloat() < 0.35F;
        Blade b = new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, a, a + (float) (Math.PI / 2) + (cross ? (float) (Math.PI / 2) : 0),
                0.15F + r.nextFloat() * 0.22F, 0.07F + r.nextFloat() * 0.04F, phi0, phi1, berries ? BERRIES : SPRAY)
                .profile(PlantShape.FLAT).at(SPRAY_AT[level]).inset(0.03F).cutout().untinted().twist((r.nextFloat() - 0.5F) * 0.6F);
        return cross ? b.secondary() : b;
    }
}
