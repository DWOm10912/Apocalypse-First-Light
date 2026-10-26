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
 * Cinnamon fern (Osmundastrum cinnamomeum; AFL vegetation P4a batch 1, docs/gameplay/vegetation_v1_design.md 21): a block
 * is one big crown, or two smaller, of the fern of wet woods and swamp edges.
 * <ul>
 * <li>LOD0: a crown is a vase of 7-11 sterile fronds, each a strip 70-120 cm long and 16-22 cm wide whose cut-out picture is
 * the frond: a woolly stipe, then the lanceolate blade of deeply lobed pinnae. They rise steeply (6-16 degrees off
 * upright) and arch out over their upper half (4 segments). Nearly half the crowns hold 2-4 fertile fronds in the middle:
 * stiff, nearly upright, 50-80 cm, the cinnamon club of sporangia on their upper part (real colours), a crossed pair of
 * strips each.</li>
 * <li>LOD1: the sterile fronds with two segments but every third, the fertile ones a single strip. LOD2: two crossed cards a
 * crown.</li>
 * </ul>
 * Sheet columns: 75 the frond (tinted), 76 the fertile frond (real colours), 77 the card (tinted). Sways as the turf
 * (PlantShape#WIND).
 */
final class CinnamonFernShape {
    static final int FROND = 75, FERTILE = 76, CARD = 77;
    private static final float[][] FROND_AT = {{0, 0.3F, 0.55F, 0.8F, 1}, {0, 0.5F, 1}};
    private static final float[] FERTILE_AT = {0, 0.5F, 1};

    private CinnamonFernShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xC1A7L * 31 + v * 0x9E3779B97F4A7C15L);
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = new ArrayList<>();
            boolean two = r.nextFloat() < 0.3F;
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int c = 0; c < (two ? 2 : 1); c++) {
                double a = turn + Math.PI * c;
                float spread = two ? 0.22F : 0.06F;
                float x = clamp(0.5F + spread * (float) Math.cos(a)), z = clamp(0.5F + spread * (float) Math.sin(a)), scale = two ? 0.75F : 1;
                int n = 7 + r.nextInt(5);
                float tallest = 0;
                for (int k = 0; k < n; k++) {
                    long seed = r.next();
                    Blade b = frond(new Rng(seed), x, z, k, n, scale, 0);
                    tallest = Math.max(tallest, BigBluestemShape.top(PlantShape.grown(b)));
                    out[v][0].add(new Clump(x, z, (k + 0.5F) / n, List.of(b), List.of(), List.of()));
                    if (k % 3 != 2) out[v][1].add(new Clump(x, z, (k + 0.5F) / n, List.of(frond(new Rng(seed), x, z, k, n, scale, 1)), List.of(), List.of()));
                }
                int fertile = r.nextFloat() < 0.45F ? 2 + r.nextInt(3) : 0;
                for (int k = 0; k < fertile; k++) {
                    float yaw = r.nextFloat() * (float) (Math.PI * 2), off = r.nextFloat() * 0.025F, phi0 = 0.02F + r.nextFloat() * 0.08F;
                    float fx = x + (float) Math.cos(yaw) * off, fz = z + (float) Math.sin(yaw) * off, len = (0.5F + r.nextFloat() * 0.3F) * scale;
                    float phi1 = phi0 + 0.05F + r.nextFloat() * 0.1F;
                    Blade first = fertile(fx, fz, yaw, yaw + (float) (Math.PI / 2), len, phi0, phi1);
                    out[v][0].add(new Clump(fx, fz, r.nextFloat(), List.of(first, fertile(fx, fz, yaw, yaw, len, phi0, phi1).secondary()), List.of(), List.of()));
                    out[v][1].add(new Clump(fx, fz, r.nextFloat(), List.of(fertile(fx, fz, yaw, yaw + (float) (Math.PI / 2), len, phi0, phi1)), List.of(), List.of()));
                }
                float yaw = r.nextFloat() * (float) Math.PI, h = tallest * 1.05F, w = h * ASPECT * 1.7F, half = (float) (Math.PI / 2);
                out[v][2].add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, h, w, 0.08F, CARD, 2, true), new Card(x, z, yaw + half, h, w, -0.08F, CARD, 2, true)), List.of()));
            }
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = List.copyOf(out[v][level]);
        }
        return out;
    }

    /** Sterile frond number k of n round a crown at (x, z), at a level (0 or 1). */
    private static Blade frond(Rng r, float x, float z, int k, int n, float scale, int level) {
        float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.02F + r.nextFloat() * 0.03F;
        float bend = a + (r.nextFloat() - 0.5F) * 0.4F, phi0 = 0.1F + r.nextFloat() * 0.18F;
        return new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                (0.7F + r.nextFloat() * 0.5F) * scale, (0.16F + r.nextFloat() * 0.06F) * scale, phi0, Math.min(1.5F, phi0 + 0.4F + r.nextFloat() * 0.3F), FROND)
                .profile(PlantShape.FLAT).at(FROND_AT[level]).inset(0.04F).cutout().twist((r.nextFloat() - 0.5F) * 0.6F);
    }

    /** A fertile frond's strip: nearly upright, the club on its upper part (real colours, cut out). */
    private static Blade fertile(float x, float z, float yaw, float across, float len, float phi0, float phi1) {
        return new Blade(x, z, yaw, across, len, 0.045F, phi0, phi1, FERTILE)
                .profile(PlantShape.FLAT).at(FERTILE_AT).inset(0.04F).cutout().untinted();
    }
}
