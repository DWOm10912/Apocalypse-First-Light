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
 * Little bluestem (Schizachyrium scoparium; AFL vegetation P2b, docs/gameplay/vegetation_v1_design.md 15): a block is a
 * square metre of dry ground with 2-3 bunches of the bunchgrass, bare soil between them.
 * <ul>
 * <li>LOD0: a bunch is a narrow vase of 16-22 leaves, 18-34 cm long and 3.5-5 mm wide, drawn out to a long point, more
 * upright than the turf (3 segments), coming from a tight crown. Four bunches in five flower: 4-8 slender culms 45-75 cm,
 * leaning out a little from the crown, each two crossed cards (the culm with its short racemes and their silvery hairs is
 * too fine for geometry; real colours).</li>
 * <li>LOD1: two crossed cards a bunch (a bunch without culms the turf's middle cards). LOD2: one pair of wide cards a block.</li>
 * </ul>
 * Sheet columns: 28 the leaf strip (the middle half), 29 the culm card, 30 the bunch card. All sway 1.1 times as far as the
 * turf (PlantShape#WIND).
 */
final class LittleBluestemShape {
    static final int LEAF = 28, CULM = 29, CARD = 30;
    private static final float[] LEAF_AT = {0, 0.35F, 0.7F, 1};

    private LittleBluestemShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x5C4120L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int bunches = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float culms = 0;
            int flowering = 0;
            for (int k = 0; k < bunches; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                Clump b = bunch(r, x, z);
                near.add(b);
                float tall = 0;
                for (Blade blade : b.blades) if (blade.column == CULM) tall = Math.max(tall, blade.len);
                if (tall == 0) {
                    mid.add(BluegrassShape.middle(r, x, z));
                    continue;
                }
                culms += tall;
                flowering++;
                float yaw = r.nextFloat() * (float) Math.PI, h = tall * 1.02F, w = h * ASPECT * 1.3F;
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, h, w, 0.05F, CARD, 2, true), new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.05F, CARD, 2, true)), List.of()));
            }
            var far = new ArrayList<Clump>();
            if (flowering > 0) {
                float yaw = r.nextFloat() * (float) Math.PI, h = culms / flowering, w = h * ASPECT * 1.8F;
                far.add(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                        new Card(0.5F, 0.5F, yaw, h, w, 0.04F, CARD, 1, true), new Card(0.5F, 0.5F, yaw + (float) (Math.PI / 2), h, w, -0.04F, CARD, 1, true)), List.of()));
            } else far.add(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** One bunch at (x, z): its vase of leaves and, if it flowers, its culms leaning out from the crown. */
    private static Clump bunch(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        int n = 16 + r.nextInt(7);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.004F + r.nextFloat() * 0.016F;
            float bend = a + (r.nextFloat() - 0.5F) * 0.6F, phi0 = 0.04F + r.nextFloat() * 0.22F;
            blades.add(new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                    0.18F + r.nextFloat() * 0.16F, 0.0035F + r.nextFloat() * 0.0015F, phi0, phi0 + 0.15F + r.nextFloat() * 0.55F, LEAF)
                    .profile(PlantShape.ACUMINATE).at(LEAF_AT).inset(0.25F).twist((r.nextFloat() - 0.5F) * 1.2F));
        }
        if (r.nextFloat() < 0.8F) {
            int culms = 4 + r.nextInt(5);
            for (int c = 0; c < culms; c++) {
                float yaw = (c + r.nextFloat() * 0.7F) / culms * (float) (Math.PI * 2), off = 0.008F + r.nextFloat() * 0.016F;
                float cx = x + (float) Math.cos(yaw) * off, cz = z + (float) Math.sin(yaw) * off;
                float h = 0.45F + r.nextFloat() * 0.3F, phi0 = 0.03F + r.nextFloat() * 0.12F, phi1 = phi0 + r.nextFloat() * 0.12F;
                float face = r.nextFloat() * (float) Math.PI;
                blades.add(new Blade(cx, cz, yaw, face, h, h * ASPECT, phi0, phi1, CULM).profile(PlantShape.FLAT).untinted().cutout());
                blades.add(new Blade(cx, cz, yaw, face + (float) (Math.PI / 2), h, h * ASPECT, phi0, phi1, CULM)
                        .profile(PlantShape.FLAT).untinted().cutout().secondary());
            }
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), List.of());
    }
}
