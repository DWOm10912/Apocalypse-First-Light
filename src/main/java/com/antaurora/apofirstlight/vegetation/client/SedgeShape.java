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
 * Pennsylvania sedge (Carex pensylvanica; AFL vegetation P2b, docs/gameplay/vegetation_v1_design.md 15): a block is a
 * square metre of the soft, low mat under oaks.
 * <ul>
 * <li>LOD0: 6-8 tufts of 9-11 leaf bundles. A bundle is one strip 22-28 mm wide whose cut-out picture is three fine
 * leaves (about 2 mm each) from one foot, so a tuft reads as some thirty leaves; 16-30 cm long, arching right over and
 * lying out at the tips (lax, as the sedge is), 3 segments, twisting a little. Reddish at the foot (the old leaf bases).</li>
 * <li>LOD1: two crossed low, wide cards a tuft. LOD2: three wide cards a block.</li>
 * </ul>
 * Sheet columns: 32 the leaf bundle (cut out, tinted), 33 the mat card. Sways as the turf.
 */
final class SedgeShape {
    static final int LEAF = 32, CARD = 33;
    private static final float[] LEAF_AT = {0, 0.35F, 0.68F, 1};

    private SedgeShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xCA4E7L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int tufts = 6 + r.nextInt(3);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            for (int k = 0; k < tufts; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.24F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.24F);
                near.add(tuft(r, x, z));
                float yaw = r.nextFloat() * (float) Math.PI, h = 0.15F + r.nextFloat() * 0.06F, w = h * ASPECT * 2.4F;
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, h, w, 0.15F, CARD, 2, true), new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.15F, CARD, 2, true)), List.of()));
            }
            var far = new ArrayList<Clump>();
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int k = 0; k < 3; k++) {
                double a = turn + k * Math.PI * 2 / 3;
                float x = clamp(0.5F + 0.22F * (float) Math.cos(a)), z = clamp(0.5F + 0.22F * (float) Math.sin(a)), h = 0.16F + r.nextFloat() * 0.04F;
                far.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, (float) a + (float) (Math.PI / 2), h, h * ASPECT * 3.2F, 0.08F, CARD, 1, true)), List.of()));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** One tuft at (x, z): leaf bundles from a crown a centimetre or two across, arching over all round. */
    private static Clump tuft(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        int n = 9 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.003F + r.nextFloat() * 0.012F;
            float bend = a + (r.nextFloat() - 0.5F) * 0.7F, phi0 = 0.25F + r.nextFloat() * 0.35F;
            blades.add(new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                    0.16F + r.nextFloat() * 0.14F, 0.022F + r.nextFloat() * 0.006F, phi0, Math.min(1.65F, phi0 + 0.6F + r.nextFloat() * 0.6F), LEAF)
                    .profile(PlantShape.FLAT).at(LEAF_AT).inset(0.08F).cutout().twist((r.nextFloat() - 0.5F) * 1.0F));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), List.of());
    }
}
