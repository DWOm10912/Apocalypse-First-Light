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

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Common dandelion (Taraxacum officinale; AFL vegetation P2b batch 2, docs/gameplay/vegetation_v1_design.md 16): a block
 * is a square metre of turf with 2-3 rosettes in it (3-4 bluegrass tufts).
 * <ul>
 * <li>LOD0: a rosette is 6-10 leaves lying low, 10-22 cm long and 3.5-5 cm wide, deeply cut into backward-pointing lobes
 * (cut out of the sheet), and 0-3 hollow scapes 10-28 cm (round, 3 sides) curving up. About two scapes in three end in
 * the yellow head (a low dome of ray florets 2.8-3.8 cm across over a skirt of green bracts), the rest in the white seed
 * clock (3.6 cm across: three crossed squares and a level one). Heads are rigid parts carried by the scape.</li>
 * <li>LOD1: four leaves of one segment a rosette and two crossed cards of its scapes. LOD2: a turf card.</li>
 * </ul>
 * Sheet columns: 39 the leaf (cut out, tinted), 40 the scape, 41 the parts (real colours: the left half rows 0-8 the head
 * from above, row 0 its middle; rows 9-12 the bracts; the right half rows 0-15 the seed clock), 42 the scape card. The
 * parts sway 1.08 times as far as the turf (their rows sit a little below the scape's top: PlantShape#WIND).
 */
final class DandelionShape {
    static final int LEAF = 39, SCAPE = 40, PARTS = 41, CARD = 42;
    /** The bracts' rows and the seed clock's rows of the parts column. */
    static final float BRACTS = 12.6F / 128, CLOCK = 15.6F / 128;
    private static final float[][] LEAF_AT = {{0, 0.35F, 0.7F, 1}, {0, 1}};

    private DandelionShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xDA4DE1L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int rosettes = 2 + r.nextInt(2), tufts = 3 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            for (int k = 0; k < Math.min(9, rosettes + tufts); k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                if (k >= rosettes) {
                    near.add(BluegrassShape.tuft(r, x, z, 0.2F + r.nextFloat() * 0.12F, 8 + r.nextInt(3)));
                    mid.add(BluegrassShape.middle(r, x, z));
                    continue;
                }
                long seed = r.next();
                near.add(rosette(new Rng(seed), x, z, 0));
                mid.add(rosette(new Rng(seed), x, z, 1));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.of(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
        }
        return out;
    }

    /** One rosette at (x, z) at a level (0 or 1): leaves lying out and its scapes with their heads (or as cards). */
    private static Clump rosette(Rng r, float x, float z, int level) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        var cards = new ArrayList<Card>();
        int n = 6 + r.nextInt(5);
        float turn = r.nextFloat() * (float) (Math.PI * 2);
        for (int k = 0; k < n; k++) {
            float a = turn + (k + r.nextFloat() * 0.6F) / n * (float) (Math.PI * 2), phi0 = 0.95F + r.nextFloat() * 0.3F;
            float len = 0.1F + r.nextFloat() * 0.12F, width = 0.035F + r.nextFloat() * 0.015F, phi1 = Math.min(1.52F, phi0 + 0.1F + r.nextFloat() * 0.2F);
            float twist = (r.nextFloat() - 0.5F) * 0.4F;
            if (level == 1 && k >= 4) continue;
            blades.add(new Blade(x + (float) Math.cos(a) * 0.005F, z + (float) Math.sin(a) * 0.005F, a, a + (float) (Math.PI / 2),
                    len, width, phi0, phi1, LEAF).profile(PlantShape.FLAT).at(LEAF_AT[level]).cutout().twist(twist));
        }
        float roll = r.nextFloat();
        int scapes = roll < 0.15F ? 0 : roll < 0.55F ? 1 : roll < 0.85F ? 2 : 3;
        float tallest = 0;
        for (int s = 0; s < scapes; s++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = 0.05F + r.nextFloat() * 0.3F, len = 0.1F + r.nextFloat() * 0.18F;
            float phi1 = phi0 + 0.1F + r.nextFloat() * 0.2F, off = 0.008F + r.nextFloat() * 0.01F;
            boolean clock = r.nextFloat() < 0.35F;
            float radius = clock ? 0.017F + r.nextFloat() * 0.003F : 0.014F + r.nextFloat() * 0.005F;
            long head = r.next();
            tallest = Math.max(tallest, len);
            if (level == 1) continue;
            Blade scape = new Blade(x + (float) Math.cos(yaw) * off, z + (float) Math.sin(yaw) * off, yaw, yaw + (float) (Math.PI / 2),
                    len, 0.004F, phi0, phi1, SCAPE).profile(PlantShape.FLAT).tube(3).at(0, 0.5F, 1);
            int index = blades.size();
            blades.add(scape);
            Pose grown = PlantShape.grown(scape);
            pieces.add(new Piece(index, 1, PlantShape.footed(clock ? clock(grown, radius, new Rng(head)) : flower(grown, radius, new Rng(head)), x, z)));
        }
        // drawn on both levels alike, so the rosette has one rank (the levels blend rosette by rosette)
        float rank = r.nextFloat(), yaw = r.nextFloat() * (float) Math.PI;
        if (level == 1 && tallest > 0) {
            float h = tallest * 1.1F, w = h * ASPECT * 1.5F;
            cards.add(new Card(x, z, yaw, h, w, 0.05F, CARD, 1, false));
            cards.add(new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.05F, CARD, 1, false));
        }
        return new Clump(x, z, rank, blades, cards, pieces);
    }

    /** The yellow head on a scape's top: a low dome of ray florets (the head band) over a skirt of green bracts. */
    private static List<Quad> flower(Pose scape, float radius, Rng r) {
        var out = new ArrayList<Quad>(FlowerParts.middle(scape, new float[]{radius, radius * 0.6F, 0}, new float[]{0.004F, 0.008F, 0.01F}, 10, PARTS, r));
        // the bracts: from under the rim down round the top of the scape (rows 9-12)
        out.addAll(FlowerParts.middle(scape, new float[]{radius * 0.35F, radius * 0.75F}, new float[]{-0.012F, 0.003F}, 8, PARTS, r)
                .stream().map(q -> bracts(q)).toList());
        return out;
    }

    /** A bract quad: the head band's rows moved to the bracts' rows (9-12) of the left half. */
    private static Quad bracts(Quad q) {
        for (int i = 0; i < 4; i++) {
            float v = q.uv[i * 2 + 1];
            q.uv[i * 2 + 1] = 9.2F / 128 + (BRACTS - 9.2F / 128) * Math.min(1, v / FlowerParts.HEAD);
        }
        return q;
    }

    /**
     * The seed clock on a scape's top: a sphere of pappus about 3.6 cm across round the tip, three crossed squares and a
     * level one (both faces), the right half of the parts column's rows 0-15. Real colours, cut out.
     */
    private static List<Quad> clock(Pose scape, float radius, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(scape.pts, scape.f, 1, f);
        float cx = f[0] + f[3] * radius * 0.6F, cy = f[1] + f[4] * radius * 0.6F, cz = f[2] + f[5] * radius * 0.6F;
        float u0 = (PARTS + 0.52F) / COLUMNS, u1 = (PARTS + 0.98F) / COLUMNS, v0 = 0.3F / 128, v1 = CLOCK;
        var out = new ArrayList<Quad>();
        float spin = r.nextFloat() * (float) Math.PI;
        for (int k = 0; k < 3; k++) {
            double a = spin + Math.PI * k / 3;
            float ax = (float) Math.cos(a) * radius, az = (float) Math.sin(a) * radius;
            PlantShape.face(out, new float[][]{{cx - ax, cy - radius, cz - az}, {cx + ax, cy - radius, cz + az}, {cx + ax, cy + radius, cz + az}, {cx - ax, cy + radius, cz - az}},
                    new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, new float[]{-az, 0, ax}, 1, false, false, true);
        }
        PlantShape.face(out, new float[][]{{cx - radius, cy, cz - radius}, {cx + radius, cy, cz - radius}, {cx + radius, cy, cz + radius}, {cx - radius, cy, cz + radius}},
                new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, new float[]{0, 1, 0}, 1, false, false, true);
        return out;
    }
}
