package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Card;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.FLAT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Kentucky bluegrass (Poa pratensis; AFL vegetation P1a/P1b, docs/gameplay/vegetation_v1_design.md 10, 11): a block is a
 * square metre of turf.
 * <ul>
 * <li>LOD0 (near): 7 clumps of 9-11 leaf blades, each blade its own geometry: 3 segments along a curve (nearly upright at
 * the foot, arching out to the tip), 11-16 mm wide at the foot, tapering, the tip narrowed (the boat-shaped tip of Poa);
 * coloured from a strip of the sheet and drawn solid. A flowering culm in about half the blocks, two in some: two crossed
 * cards (the stem and the open panicle are too fine for geometry).</li>
 * <li>LOD1 (middle): 4 clumps of 2 crossed cards, wider, 2 segments, the denser middle columns; the culms as one card.</li>
 * <li>LOD2 (far): 3 wide cards of the densest column, one segment.</li>
 * </ul>
 * Sheet columns 0-7: 0-3 the leaf strip (the blades take 1, the others pad it), 4-5 middle cards, 6 far card, 7 the culm.
 * Other species' blocks take tufts from here ({@link #tuft}, {@link #middle}, {@link #far}: a yarrow patch among the turf).
 */
final class BluegrassShape {
    static final int LEAF = 1, MIDDLE = 4, FAR = 6, CULM = 7;

    private BluegrassShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x5A11E0L * 31 + v * 0x9E3779B97F4A7C15L);
            // LOD0: seven feet from a jittered 3 x 3 grid (two slots left empty), so the turf has no straight rows
            int skipA = r.nextInt(9), skipB = (skipA + 1 + r.nextInt(8)) % 9;
            var near = new ArrayList<Clump>();
            for (int slot = 0; slot < 9; slot++) {
                if (slot == skipA || slot == skipB) continue;
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.22F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.22F);
                near.add(tuft(r, x, z, 0.26F + r.nextFloat() * 0.16F, 9 + r.nextInt(3)));
            }
            // the flowering culms (late spring to summer): about half the blocks one, some two
            int culms = r.nextFloat() < 0.5F ? 0 : r.nextFloat() < 0.75F ? 1 : 2;
            var culmFeet = new float[culms * 2];
            for (int c = 0; c < culms; c++) {
                float x = clamp(0.2F + r.nextFloat() * 0.6F), z = clamp(0.2F + r.nextFloat() * 0.6F), yaw = r.nextFloat() * (float) (Math.PI * 2);
                float h = 0.48F + r.nextFloat() * 0.14F, arch = r.nextFloat() * 0.18F;
                culmFeet[c * 2] = x; culmFeet[c * 2 + 1] = z;
                near.add(new Clump(x, z, r.nextFloat(), List.of(
                        new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), h, h * ASPECT, 0, arch, CULM).profile(FLAT).untinted().cutout(),
                        new Blade(x, z, yaw, yaw, h, h * ASPECT, 0, arch, CULM).profile(FLAT).untinted().cutout().secondary()), List.of(), List.of()));
            }
            // LOD1: four clumps of two crossed cards
            var mid = new ArrayList<Clump>();
            for (int k = 0; k < 4; k++)
                mid.add(middle(r, clamp((k % 2 + 0.5F) / 2 + (r.nextFloat() - 0.5F) * 0.3F), clamp((k / 2 + 0.5F) / 2 + (r.nextFloat() - 0.5F) * 0.3F)));
            for (int c = 0; c < culms; c++) {
                float x = culmFeet[c * 2], z = culmFeet[c * 2 + 1], h = 0.5F + r.nextFloat() * 0.1F;
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(new Card(x, z, r.nextFloat() * (float) Math.PI, h, h * ASPECT, 0.02F, CULM, 2, false)), List.of()));
            }
            // LOD2: three wide cards, a triangle around the middle
            var far = new ArrayList<Clump>();
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int k = 0; k < 3; k++) {
                double a = turn + k * Math.PI * 2 / 3;
                far.add(far(r, clamp(0.5F + 0.22F * (float) Math.cos(a)), clamp(0.5F + 0.22F * (float) Math.sin(a)), (float) a + (float) (Math.PI / 2)));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** A tuft of n leaf blades about h tall at (x, z): they come out of a tuft a few centimetres across and arch outwards, the tips drooping. */
    static Clump tuft(Rng r, float x, float z, float h, int n) {
        var blades = new ArrayList<Blade>(n);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.008F + r.nextFloat() * 0.03F;
            float bend = a + (r.nextFloat() - 0.5F) * 0.8F;
            float phi0 = 0.04F + r.nextFloat() * 0.25F;
            blades.add(new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                    h * (0.7F + r.nextFloat() * 0.45F), 0.011F + r.nextFloat() * 0.005F, phi0, phi0 + 0.25F + r.nextFloat() * 0.75F, LEAF));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), List.of());
    }

    /** A middle level clump: two crossed cards. */
    static Clump middle(Rng r, float x, float z) {
        float yaw = r.nextFloat() * (float) Math.PI, h = 0.28F + r.nextFloat() * 0.12F;
        int col = MIDDLE + r.nextInt(2);
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(
                new Card(x, z, yaw, h, h * ASPECT * 1.7F, 0.12F, col, 2, true),
                new Card(x, z, yaw + (float) (Math.PI / 2), h, h * ASPECT * 1.7F, -0.12F, col, 2, true)), List.of());
    }

    /** A far level clump: one wide card. */
    static Clump far(Rng r, float x, float z, float yaw) {
        float h = 0.3F + r.nextFloat() * 0.08F;
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(new Card(x, z, yaw, h, h * ASPECT * 2.6F, 0.05F, FAR, 1, true)), List.of());
    }
}
