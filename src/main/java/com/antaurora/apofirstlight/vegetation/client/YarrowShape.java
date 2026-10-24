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
 * Common yarrow (Achillea millefolium; AFL vegetation P1c, docs/gameplay/vegetation_v1_design.md 13): a block is a square
 * metre of turf with 2-4 yarrow plants in it (yarrow grows scattered through grass, so the block keeps 2-3 bluegrass tufts).
 * <ul>
 * <li>LOD0: a plant is a rosette of 8-12 feathery basal leaves, 10-22 cm, spreading low (each a strip, its finely divided
 * outline cut out of the sheet), and, on four plants in five, one or two flowering stems 35-65 cm (round, 4 sides,
 * tapering). A stem carries five alternate stem leaves, smaller up the stem, at 0.2 to 0.8 of its height, and at its top
 * the flat-topped compound corymb, 6-10 cm across: 4-6 small domes of florets (6 wedges each, the sheet's flower band
 * mapped round it, the rims uneven) at slightly different heights, each with a short skirt of florets seen from the side,
 * on fine branches from the stem's top. Leaves and corymb are rigid parts carried by the stem.</li>
 * <li>LOD1: a flowering plant is two crossed foliage cards and two crossed flower cards at 45 degrees to them; a plant
 * without flowers a turf card. LOD2: one turf card and one pair of yarrow cards a block.</li>
 * </ul>
 * Sheet columns: 13 the stem, 16 the basal leaf, 17 the foliage card, 18 the flower card (real colours, not tinted), 20
 * the parts: the corymb band in rows 0-8 (the centre at row 0: the top of the stem's height) and the five stem leaves
 * laid across at the rows of their heights. All sway 1.6 times as far as the turf (PlantShape#WIND).
 */
final class YarrowShape {
    static final int STEM = 13, LEAF = 16, CARD = 17, CARD_FLOWERS = 18, PARTS = 20;
    /** The corymb's band: rows 0 (the centre) to 8 (the rim) of the parts column. */
    static final float DISC = 8 / 128F;
    /** A stem leaf's band: 9 rows centred on its height's v. */
    static final float TILE = 4.5F / 128;
    /** The stem's points. */
    static final float[] STEM_AT = {0, 0.3F, 0.55F, 0.8F, 1};
    /** Where the stem leaves sit (shares of the stem's height; the art lays them at these rows) and their lengths (m). */
    static final float[] LEAVES_AT = {0.2F, 0.35F, 0.5F, 0.65F, 0.8F}, LEAF_LENGTHS = {0.085F, 0.075F, 0.065F, 0.052F, 0.04F};

    private YarrowShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xAC41E7L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int plants = 2 + r.nextInt(3), tufts = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float stems = 0;
            int flowering = 0;
            for (int k = 0; k < plants + tufts; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.22F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.22F);
                if (k >= plants) {
                    near.add(BluegrassShape.tuft(r, x, z, 0.22F + r.nextFloat() * 0.12F, 8 + r.nextInt(3)));
                    mid.add(BluegrassShape.middle(r, x, z));
                    continue;
                }
                Clump plant = plant(r, x, z);
                near.add(plant);
                float tall = 0;
                for (Blade b : plant.blades) if (b.column == STEM) tall = Math.max(tall, b.len);
                if (tall == 0) {
                    mid.add(BluegrassShape.middle(r, x, z));
                    continue;
                }
                stems += tall;
                flowering++;
                mid.add(cards(r, x, z, tall * 1.05F, 2));
            }
            var far = new ArrayList<Clump>();
            far.add(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
            if (flowering > 0) far.add(cards(r, 0.5F, 0.5F, stems / flowering * 1.05F, 1));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** A flowering yarrow as cards: two crossed foliage cards and two crossed flower cards at 45 degrees to them. */
    private static Clump cards(Rng r, float x, float z, float h, int segments) {
        float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * 1.1F, q = (float) (Math.PI / 4), half = (float) (Math.PI / 2);
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(
                new Card(x, z, yaw, h, w, 0.04F, CARD, segments, true),
                new Card(x, z, yaw + half, h, w, -0.04F, CARD, segments, true),
                new Card(x, z, yaw + q, h, w, 0.03F, CARD_FLOWERS, segments, false),
                new Card(x, z, yaw + q + half, h, w, -0.03F, CARD_FLOWERS, segments, false)), List.of());
    }

    /** One plant at (x, z): its rosette, and its flowering stems with their leaves and corymbs. */
    private static Clump plant(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        int n = 8 + r.nextInt(5);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.7F) / n * (float) (Math.PI * 2);
            float phi0 = 0.5F + r.nextFloat() * 0.4F;
            blades.add(new Blade(x + (float) Math.cos(a) * 0.006F, z + (float) Math.sin(a) * 0.006F, a, a + (float) (Math.PI / 2),
                    0.1F + r.nextFloat() * 0.12F, 0.028F + r.nextFloat() * 0.012F, phi0, Math.min(1.48F, phi0 + 0.3F + r.nextFloat() * 0.35F), LEAF)
                    .profile(PlantShape.FLAT).cutout().twist((r.nextFloat() - 0.5F) * 0.4F));
        }
        float roll = r.nextFloat();
        int stems = roll < 0.2F ? 0 : roll < 0.75F ? 1 : 2;
        for (int s = 0; s < stems; s++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.06F, off = 0.008F + r.nextFloat() * 0.012F;
            float sx = x + (float) Math.cos(yaw) * off, sz = z + (float) Math.sin(yaw) * off;
            Blade stem = new Blade(sx, sz, yaw, yaw + (float) (Math.PI / 2), 0.35F + r.nextFloat() * 0.3F, 0.0045F,
                    phi0, phi0 + 0.05F + r.nextFloat() * 0.15F, STEM).profile(PlantShape.TAPER).tube(4).at(STEM_AT);
            int index = blades.size();
            blades.add(stem);
            Pose grown = PlantShape.grown(stem);
            float side = r.nextFloat() < 0.5F ? 0 : (float) Math.PI, scale = 0.85F + r.nextFloat() * 0.3F;
            for (int k = 0; k < LEAVES_AT.length; k++) {
                float at = LEAVES_AT[k];
                pieces.add(new Piece(index, at, PlantShape.footed(leaf(grown, at, yaw + side + (float) Math.PI * k + (r.nextFloat() - 0.5F) * 0.6F,
                        LEAF_LENGTHS[k] * scale, 0.02F * scale, r), x, z)));
            }
            pieces.add(new Piece(index, 1, PlantShape.footed(corymb(grown, 0.03F + r.nextFloat() * 0.02F, r), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /**
     * A stem leaf at the share {@code at} of a stem (as grown), pointing along yaw: rising 40-55 degrees from the stem, then
     * drooping to 10-20 degrees, two segments, both faces; its outline the sheet's, laid across the rows of its height.
     */
    private static List<Quad> leaf(Pose stem, float at, float yaw, float len, float width, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(stem.pts, stem.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), wx = -dz, wz = dx;
        double rise0 = Math.toRadians(40 + r.nextFloat() * 15), rise1 = Math.toRadians(10 + r.nextFloat() * 10);
        float[][] mid = new float[3][];
        mid[0] = new float[]{f[0] + dx * 0.002F, f[1], f[2] + dz * 0.002F};
        float a = len * 0.45F, b = len * 0.55F;
        mid[1] = new float[]{mid[0][0] + dx * a * (float) Math.cos(rise0), mid[0][1] + a * (float) Math.sin(rise0), mid[0][2] + dz * a * (float) Math.cos(rise0)};
        mid[2] = new float[]{mid[1][0] + dx * b * (float) Math.cos(rise1), mid[1][1] + b * (float) Math.sin(rise1), mid[1][2] + dz * b * (float) Math.cos(rise1)};
        float u0 = (PARTS + 0.03F) / COLUMNS, u1 = (PARTS + 0.97F) / COLUMNS, vMid = 1 - at;
        float[] along = {0, 0.45F, 1};
        var out = new ArrayList<Quad>();
        float h = width / 2, light = PlantShape.shade(at);
        for (int k = 0; k < 2; k++) {
            float[][] c = {
                    {mid[k][0] - wx * h, mid[k][1], mid[k][2] - wz * h}, {mid[k][0] + wx * h, mid[k][1], mid[k][2] + wz * h},
                    {mid[k + 1][0] + wx * h, mid[k + 1][1], mid[k + 1][2] + wz * h}, {mid[k + 1][0] - wx * h, mid[k + 1][1], mid[k + 1][2] - wz * h}};
            float ua = u0 + (u1 - u0) * along[k], ub = u0 + (u1 - u0) * along[k + 1];
            float[][] uv = {{ua, vMid - TILE}, {ua, vMid + TILE}, {ub, vMid + TILE}, {ub, vMid - TILE}};
            PlantShape.face(out, c, uv, new float[]{0, 1, 0}, light, true, false, true);
        }
        return out;
    }

    /**
     * The compound corymb on a stem's top (as grown), about r across each way: 4-6 small domes of florets at slightly
     * different heights (6 wedges each, rims uneven: the flower band mapped round each, its centre at the band's first row),
     * each with a short skirt of florets round it seen from the side (the rim's rows again), on fine branches from the
     * stem's top. Real colours, cut out; the branches the stem's colour.
     */
    private static List<Quad> corymb(Pose stem, float r, Rng rng) {
        float[] f = new float[6];
        PlantShape.frame(stem.pts, stem.f, 1, f);
        var out = new ArrayList<Quad>();
        float[] up = {0, 1, 0};
        int heads = 4 + rng.nextInt(3);
        float turn = rng.nextFloat() * (float) (Math.PI * 2), stemU = (STEM + 0.5F) / COLUMNS, branch = 0.0012F;
        for (int h = 0; h < heads; h++) {
            // the first head over the stem, the others round it
            double a = turn + Math.PI * 2 * h / Math.max(1, heads - 1) + (rng.nextFloat() - 0.5F) * 0.6F;
            float off = h == 0 ? r * 0.12F * rng.nextFloat() : r * (0.36F + rng.nextFloat() * 0.26F);
            float hx = f[0] + (float) Math.cos(a) * off, hz = f[2] + (float) Math.sin(a) * off;
            float rd = r * (0.3F + rng.nextFloat() * 0.12F), hy = f[1] + 0.012F + rng.nextFloat() * 0.008F - off * 0.15F, dome = rd * 0.4F;
            // the branch from the stem's top up to the head's underside: a fine strip, both faces
            float bx = hx - f[0], bz = hz - f[2], bl = (float) Math.sqrt(bx * bx + bz * bz);
            float wx = bl < 1e-4F ? branch : -bz / bl * branch, wz = bl < 1e-4F ? 0 : bx / bl * branch;
            PlantShape.face(out, new float[][]{{f[0] - wx, f[1] - 0.004F, f[2] - wz}, {f[0] + wx, f[1] - 0.004F, f[2] + wz},
                            {hx + wx, hy - dome * 0.3F, hz + wz}, {hx - wx, hy - dome * 0.3F, hz - wz}},
                    new float[][]{{stemU, 0.02F}, {stemU, 0.02F}, {stemU, 0.004F}, {stemU, 0.004F}},
                    new float[]{-bx, 0.2F, -bz}, 0.9F, true, true, true);
            int n = 6;
            float[] centre = {hx, hy + dome, hz};
            float[][] ring = new float[n][], rim = new float[n][], skirt = new float[n][];
            float spin = rng.nextFloat() * (float) Math.PI;
            for (int s = 0; s < n; s++) {
                double b = spin + Math.PI * 2 * s / n;
                float cx = (float) Math.cos(b), cz = (float) Math.sin(b), edge = rd * (0.85F + rng.nextFloat() * 0.3F);
                ring[s] = new float[]{hx + cx * edge * 0.55F, hy + dome * 0.75F, hz + cz * edge * 0.55F};
                rim[s] = new float[]{hx + cx * edge, hy, hz + cz * edge};
                skirt[s] = new float[]{hx + cx * edge * 0.82F, hy - 0.009F, hz + cz * edge * 0.82F};
            }
            for (int s = 0; s < n; s++) {
                int t = (s + 1) % n;
                float ua = (PARTS + s / (float) n) / COLUMNS, ub = (PARTS + (s + 1) / (float) n) / COLUMNS, um = (ua + ub) / 2;
                PlantShape.face(out, new float[][]{centre, ring[s], ring[t], centre},
                        new float[][]{{um, 0.5F / 128}, {ua, DISC * 0.55F}, {ub, DISC * 0.55F}, {um, 0.5F / 128}}, up, 1, false, false, false);
                PlantShape.face(out, new float[][]{ring[s], rim[s], rim[t], ring[t]},
                        new float[][]{{ua, DISC * 0.55F}, {ua, DISC}, {ub, DISC}, {ub, DISC * 0.55F}}, up, 1, false, false, false);
                double b = spin + Math.PI * 2 * (s + 0.5F) / n;
                PlantShape.face(out, new float[][]{rim[s], skirt[s], skirt[t], rim[t]},
                        new float[][]{{ua, DISC * 0.5F}, {ua, DISC}, {ub, DISC}, {ub, DISC * 0.5F}},
                        new float[]{(float) Math.cos(b), -0.3F, (float) Math.sin(b)}, 0.92F, false, false, false);
            }
        }
        return out;
    }
}
