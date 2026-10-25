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
 * Christmas fern (Polystichum acrostichoides; AFL vegetation P4a batch 1, docs/gameplay/vegetation_v1_design.md 21): a
 * block is one or two crowns of the evergreen fern of shaded hardwood slopes.
 * <ul>
 * <li>LOD0: a crown is a fountain of 8-13 fronds from one point, each a strip 30-55 cm long and 8.5-11 cm wide whose
 * cut-out picture is the frond: a scaly stipe, then the once-pinnate blade, its pinnae eared at the base (the
 * "stocking"). They rise at 20-35 degrees off upright and arch over to their tips (4 segments, twisting a little); one or
 * two old fronds lie out flat on the ground (the fern keeps last year's), and some crowns hold one or two fertile fronds,
 * more upright, their upper pinnae smaller and darker.</li>
 * <li>LOD1: the same fronds but every third, two segments. LOD2: two crossed cards a crown.</li>
 * </ul>
 * Sheet columns: 72 the frond, 73 the fertile frond, 74 the card; all tinted, dark (leathery). Sways 0.7 times as far as
 * the turf (PlantShape#WIND).
 */
final class ChristmasFernShape {
    static final int FROND = 72, FERTILE = 73, CARD = 74;
    private static final float[][] FROND_AT = {{0, 0.3F, 0.6F, 0.82F, 1}, {0, 0.5F, 1}};

    private ChristmasFernShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xC4F7L * 31 + v * 0x9E3779B97F4A7C15L);
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = new ArrayList<>();
            boolean two = r.nextFloat() < 0.45F;
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int c = 0; c < (two ? 2 : 1); c++) {
                double a = turn + Math.PI * c;
                float spread = two ? 0.24F : 0.08F;
                float x = clamp(0.5F + spread * (float) Math.cos(a)), z = clamp(0.5F + spread * (float) Math.sin(a)), scale = two ? 0.85F : 1;
                int n = 8 + r.nextInt(6), fertile = r.nextFloat() < 0.4F ? 1 + r.nextInt(2) : 0;
                float tallest = 0;
                for (int k = 0; k < n; k++) {
                    long seed = r.next();
                    Blade b = frond(new Rng(seed), x, z, k, n, k < fertile, scale, 0);
                    tallest = Math.max(tallest, BigBluestemShape.top(PlantShape.grown(b)));
                    out[v][0].add(new Clump(x, z, (k + 0.5F) / n, List.of(b), List.of(), List.of()));
                    if (k % 3 != 2) out[v][1].add(new Clump(x, z, (k + 0.5F) / n, List.of(frond(new Rng(seed), x, z, k, n, k < fertile, scale, 1)), List.of(), List.of()));
                }
                float yaw = r.nextFloat() * (float) Math.PI, h = tallest * 1.1F, w = h * ASPECT * 2.6F, half = (float) (Math.PI / 2);
                out[v][2].add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, h, w, 0.12F, CARD, 2, true), new Card(x, z, yaw + half, h, w, -0.12F, CARD, 2, true)), List.of()));
            }
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = List.copyOf(out[v][level]);
        }
        return out;
    }

    /**
     * Frond number k of n round a crown at (x, z), at a level (0 or 1): fertile ones more upright and longer, about one in
     * seven an old frond lying out flat.
     */
    private static Blade frond(Rng r, float x, float z, int k, int n, boolean fertile, float scale, int level) {
        float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.008F + r.nextFloat() * 0.02F;
        float bend = a + (r.nextFloat() - 0.5F) * 0.5F;
        float len, width, phi0, phi1;
        if (fertile) {
            len = 0.45F + r.nextFloat() * 0.15F;
            width = 0.08F;
            phi0 = 0.1F + r.nextFloat() * 0.15F;
            phi1 = phi0 + 0.25F + r.nextFloat() * 0.2F;
        } else if (r.nextFloat() < 0.14F) {
            len = 0.35F + r.nextFloat() * 0.15F;
            width = 0.09F;
            phi0 = 1.1F + r.nextFloat() * 0.2F;
            phi1 = 1.5F + r.nextFloat() * 0.12F;
        } else {
            len = 0.3F + r.nextFloat() * 0.25F;
            width = 0.085F + r.nextFloat() * 0.025F;
            phi0 = 0.35F + r.nextFloat() * 0.3F;
            phi1 = Math.min(1.75F, phi0 + 0.6F + r.nextFloat() * 0.35F);
        }
        float twist = (r.nextFloat() - 0.5F) * 0.8F;
        return new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                len * scale, width * scale, phi0, phi1, fertile ? FERTILE : FROND)
                .profile(PlantShape.FLAT).at(FROND_AT[level]).inset(0.04F).cutout().twist(twist);
    }
}
