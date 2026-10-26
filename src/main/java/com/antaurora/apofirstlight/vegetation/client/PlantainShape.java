package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Broadleaf plantain (Plantago major; AFL vegetation P2b batch 2, docs/gameplay/vegetation_v1_design.md 16): a block is
 * a square metre of trodden ground, 2-4 rosettes among 2-3 short bluegrass tufts.
 * <ul>
 * <li>LOD0: a rosette is 5-8 broad leaves lying nearly flat, 12-22 cm with the petiole and 4.5-6.5 cm wide (the ovate
 * blade with its arched parallel veins cut out of the sheet), and 0-4 flower spikes 12-28 cm: a bare scape, the slender
 * dense spike on its upper half (two crossed cards each, real colours), leaning out a little.</li>
 * <li>LOD1: the same rosettes, four leaves of one segment and the spikes. LOD2: a turf card (a rosette lies too low to see).</li>
 * </ul>
 * Sheet columns: 37 the leaf (petiole and blade, cut out, tinted), 38 the spike card. Sways as the turf.
 */
final class PlantainShape {
    static final int LEAF = 37, SPIKE = 38;
    private static final float[][] LEAF_AT = {{0, 0.35F, 0.7F, 1}, {0, 1}};

    private PlantainShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x91A7A1L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int rosettes = 2 + r.nextInt(3), tufts = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            for (int k = 0; k < Math.min(9, rosettes + tufts); k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                if (k >= rosettes) {
                    near.add(BluegrassShape.tuft(r, x, z, 0.14F + r.nextFloat() * 0.08F, 8 + r.nextInt(3)));
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

    /** One rosette at (x, z) at a level (0 or 1): its leaves lying out and its spikes. */
    private static Clump rosette(Rng r, float x, float z, int level) {
        var blades = new ArrayList<Blade>();
        int n = 5 + r.nextInt(4);
        float turn = r.nextFloat() * (float) (Math.PI * 2);
        for (int k = 0; k < n; k++) {
            float a = turn + (k + r.nextFloat() * 0.6F) / n * (float) (Math.PI * 2), phi0 = 1.0F + r.nextFloat() * 0.25F;
            float len = 0.12F + r.nextFloat() * 0.1F, width = 0.045F + r.nextFloat() * 0.02F, phi1 = Math.min(1.52F, phi0 + 0.1F + r.nextFloat() * 0.25F);
            float twist = (r.nextFloat() - 0.5F) * 0.4F;
            if (level == 1 && k >= 4) continue;
            blades.add(new Blade(x + (float) Math.cos(a) * 0.006F, z + (float) Math.sin(a) * 0.006F, a, a + (float) (Math.PI / 2),
                    len, width, phi0, phi1, LEAF).profile(PlantShape.FLAT).at(LEAF_AT[level]).cutout().twist(twist));
        }
        float roll = r.nextFloat();
        int spikes = roll < 0.2F ? 0 : roll < 0.5F ? 1 : roll < 0.8F ? 2 : roll < 0.93F ? 3 : 4;
        for (int s = 0; s < spikes; s++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), h = 0.12F + r.nextFloat() * 0.16F, phi0 = 0.05F + r.nextFloat() * 0.25F;
            float phi1 = phi0 + 0.05F + r.nextFloat() * 0.15F, face = r.nextFloat() * (float) Math.PI, off = 0.01F + r.nextFloat() * 0.01F;
            float sx = x + (float) Math.cos(yaw) * off, sz = z + (float) Math.sin(yaw) * off, w = h * ASPECT * 0.5F;
            blades.add(new Blade(sx, sz, yaw, face, h, w, phi0, phi1, SPIKE).profile(PlantShape.FLAT).untinted().cutout());
            blades.add(new Blade(sx, sz, yaw, face + (float) (Math.PI / 2), h, w, phi0, phi1, SPIKE).profile(PlantShape.FLAT).untinted().cutout().secondary());
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), List.of());
    }
}
