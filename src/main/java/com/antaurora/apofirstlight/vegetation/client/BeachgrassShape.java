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
 * American beachgrass (Ammophila breviligulata; AFL vegetation P4a batch 3, docs/gameplay/vegetation_v1_design.md 24):
 * the grass that holds the Atlantic dunes, on sand as well as soil. A block is 3-5 tufts strung along the rhizomes.
 * <ul>
 * <li>LOD0: a tuft is 8-12 leaves 40-80 cm, narrow and rolled in (0.8-1.2 cm wide), stiff at the foot and arching out
 * to fine points (4 segments, drawn out to a long point). A third of the tufts send up 1-2 culms 0.7-1 m (3 sides,
 * tapering) whose upper 28 % is the dense spike-like panicle, pale straw (a capped tube carried by the culm, its v the
 * culm's own at its heights, as the cattail's spikes).</li>
 * <li>LOD1: two crossed cards a tuft. LOD2: one wide pair a block. Tall: drawn at every distance.</li>
 * </ul>
 * Sheet columns: 104 the leaf strip (middle half), 105 the culm, 106 the spike at the culm's heights (rows 0-36, real
 * colours), 107 the card. The leaves sway 1.3 times as far as the turf, the culms and their spikes 1.2 (PlantShape#WIND).
 */
final class BeachgrassShape {
    static final int LEAF = 104, CULM = 105, SPIKE = 106, CARD = 107;
    /** Where the spike starts on its culm (a share of the culm). */
    static final float SPIKE_FROM = 0.72F;
    private static final float[] LEAF_AT = {0, 0.35F, 0.65F, 0.85F, 1}, CULM_AT = {0, 0.4F, 0.75F, 1}, SPIKE_AT = {0, 0.06F, 0.94F, 1};

    private BeachgrassShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xA3B0L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int tufts = 3 + r.nextInt(3);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0;
            for (int k = 0; k < tufts; k++) {
                int slot = slots[k];
                // the tufts kept towards the middle: their leaves arch out 25-40 cm
                float x = 0.28F + 0.44F * (slot % 3) / 2 + (r.nextFloat() - 0.5F) * 0.1F, z = 0.28F + 0.44F * (slot / 3) / 2 + (r.nextFloat() - 0.5F) * 0.1F;
                Clump tuft = tuft(r, x, z);
                near.add(tuft);
                float h = 0;
                for (Pose p : tuft.grown) h = Math.max(h, BigBluestemShape.top(p));
                tallest = Math.max(tallest, h);
                float yaw = r.nextFloat() * (float) Math.PI, ch = h * 1.02F, w = ch * ASPECT * 1.5F;
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, ch, w, 0.08F, CARD, 2, true), new Card(x, z, yaw + (float) (Math.PI / 2), ch, w, -0.08F, CARD, 2, true)), List.of()));
            }
            float yaw = r.nextFloat() * (float) Math.PI, h = tallest, w = h * ASPECT * 3.2F;
            var far = List.of(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                    new Card(0.5F, 0.5F, yaw, h, w, 0.05F, CARD, 1, true), new Card(0.5F, 0.5F, yaw + (float) (Math.PI / 2), h, w, -0.05F, CARD, 1, true)), List.of()));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = far;
        }
        return out;
    }

    /** One tuft at (x, z): its leaves and, in a third of the tufts, its flowering culms. */
    private static Clump tuft(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        int n = 8 + r.nextInt(5);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.8F) / n * (float) (Math.PI * 2), off = 0.005F + r.nextFloat() * 0.02F;
            float bend = a + (r.nextFloat() - 0.5F) * 0.6F, phi0 = 0.05F + r.nextFloat() * 0.3F;
            blades.add(new Blade(x + (float) Math.cos(a) * off, z + (float) Math.sin(a) * off, bend, bend + (float) (Math.PI / 2),
                    0.4F + r.nextFloat() * 0.4F, 0.008F + r.nextFloat() * 0.004F, phi0, phi0 + 0.3F + r.nextFloat() * 0.45F, LEAF)
                    .profile(PlantShape.ACUMINATE).at(LEAF_AT).inset(0.25F).twist((r.nextFloat() - 0.5F) * 1.6F));
        }
        int culms = r.nextFloat() < 0.33F ? 1 + r.nextInt(2) : 0;
        for (int c = 0; c < culms; c++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.08F, len = 0.7F + r.nextFloat() * 0.3F;
            Blade culm = new Blade(x + (float) Math.cos(yaw) * 0.006F, z + (float) Math.sin(yaw) * 0.006F, yaw, yaw + (float) (Math.PI / 2),
                    len, 0.0045F, phi0, phi0 + 0.04F + r.nextFloat() * 0.1F, CULM).profile(PlantShape.TAPER).tube(3).at(CULM_AT);
            int index = blades.size();
            blades.add(culm);
            Pose grown = PlantShape.grown(culm);
            float[] mid = new float[6], start = new float[6];
            PlantShape.frame(grown.pts, grown.f, (SPIKE_FROM + 1) / 2, mid);
            PlantShape.frame(grown.pts, grown.f, SPIKE_FROM, start);
            float phi = (float) Math.acos(Math.max(-1, Math.min(1, mid[4]))), heading = (float) Math.atan2(mid[5], mid[3]);
            Blade spike = new Blade(start[0], start[2], heading, heading + (float) (Math.PI / 2), (1 - SPIKE_FROM) * len, 0.014F + r.nextFloat() * 0.004F,
                    phi, phi, SPIKE).y(start[1]).tube(5).profile(PlantShape.SPIKE).at(SPIKE_AT).untinted().capped().v(1 - SPIKE_FROM, 0);
            pieces.add(new Piece(index, (SPIKE_FROM + 1) / 2, PlantShape.footed(PlantShape.quads(PlantShape.grown(spike)), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }
}
