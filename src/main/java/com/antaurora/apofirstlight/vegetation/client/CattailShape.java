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
 * Broadleaf cattail (Typha latifolia; AFL vegetation P1c, docs/gameplay/vegetation_v1_design.md 13): a block is a square
 * metre of a stand, on wet soil or in water up to a block deep (MeshPlantBlock.Wet).
 * <ul>
 * <li>LOD0: 5-8 shoots 1.5-2.3 m tall. A shoot is a fan of 4-8 flat leaves in two ranks from a common foot: the inner
 * ones tallest and nearly upright, the outer ones shorter and arching out, a few tips broken over; 12-24 mm wide, even
 * to the last fifth, then closing to a point; twisting a little in their upper half; 4 segments each, shorter towards
 * the tip where they bend. Two shoots in five keep a dead leaf of last year, straw brown and bent over. A little under half the shoots flower: a round stem (5 sides) about
 * as tall as the leaves, with the dark brown female spike (a tube, 8 sides, 2.4-3.4 cm thick, 11 % of the stem) and the
 * thin male spike right above it to the tip (latifolia has no gap between them), both rigid parts carried by the stem.</li>
 * <li>LOD1: two crossed cards a shoot (the stand as a picture). LOD2: two crossed wide cards a block; kept at every
 * distance (tall enough to see from far away).</li>
 * </ul>
 * Sheet columns: 9 the dead leaf strip, 10 the leaf strip (both mapped to the middle half of the column: the mip levels
 * do not bleed the neighbours into a leaf's edges), 11 the stem, 12 the spikes (rows of the heights they sit at: the
 * female 0.09-0.2 of v, the male above), 15 the card. All sway three times as far as the turf (PlantShape#WIND).
 */
final class CattailShape {
    static final int DEAD = 9, LEAF = 10, STEM = 11, SPIKES = 12, CARD = 15;
    /** The flowering stem's points: the female spike from 0.8 to 0.91 of its length, the male one above it to the tip. */
    static final float FEMALE = 0.8F, MALE = 0.91F;
    private static final float[] STEM_AT = {0, 0.3F, 0.55F, FEMALE, MALE, 1};
    private static final float[] LEAF_AT = {0, 0.35F, 0.6F, 0.8F, 1};

    private CattailShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xCA77A1L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int count = 5 + r.nextInt(4);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0;
            for (int k = 0; k < count; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.25F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.25F);
                float h = 1.5F + r.nextFloat() * 0.8F;
                tallest = Math.max(tallest, h);
                near.add(shoot(r, x, z, h, r.nextFloat() < 0.45F));
                float yaw = r.nextFloat() * (float) Math.PI, ch = h * 1.02F;
                mid.add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                        new Card(x, z, yaw, ch, ch * ASPECT * 0.75F, 0.05F, CARD, 3, true),
                        new Card(x, z, yaw + (float) (Math.PI / 2), ch, ch * ASPECT * 0.75F, -0.05F, CARD, 3, true)), List.of()));
            }
            float yaw = r.nextFloat() * (float) Math.PI, fh = tallest * 0.95F;
            var far = List.of(new Clump(0.5F, 0.5F, r.nextFloat(), List.of(), List.of(
                    new Card(0.5F, 0.5F, yaw, fh, fh * ASPECT * 1.6F, 0.03F, CARD, 1, true),
                    new Card(0.5F, 0.5F, yaw + (float) (Math.PI / 2), fh, fh * ASPECT * 1.6F, -0.03F, CARD, 1, true)), List.of()));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = far;
        }
        return out;
    }

    /** A shoot h tall at (x, z): a fan of leaves, maybe a dead one, and if it flowers a stem with its spikes. */
    private static Clump shoot(Rng r, float x, float z, float h, boolean flowers) {
        float fan = r.nextFloat() * (float) Math.PI;
        int n = flowers ? 4 + r.nextInt(3) : 6 + r.nextInt(3);
        var blades = new ArrayList<Blade>();
        for (int k = 0; k < n; k++) {
            float t = k / (float) (n - 1);   // 0 the outermost .. 1 the innermost
            float bend = fan + (k % 2 == 0 ? 0 : (float) Math.PI) + (r.nextFloat() - 0.5F) * 0.7F;
            float len = h * (0.62F + 0.38F * t) * (0.92F + r.nextFloat() * 0.16F);
            float phi0 = 0.02F + (1 - t) * 0.12F + r.nextFloat() * 0.04F;
            float phi1 = phi0 + 0.1F + (1 - t) * (0.2F + r.nextFloat() * 0.35F);
            if (r.nextFloat() < 0.15F) phi1 += 0.5F;   // a tip broken over
            float off = 0.003F + r.nextFloat() * 0.01F;
            blades.add(new Blade(x + (float) Math.cos(bend) * off, z + (float) Math.sin(bend) * off, bend, bend + (float) (Math.PI / 2),
                    len, 0.012F + r.nextFloat() * 0.012F, phi0, phi1, LEAF)
                    .profile(PlantShape.SWORD).at(LEAF_AT).inset(0.25F).twist((r.nextFloat() - 0.5F) * 2.4F));
        }
        // last year's leaves: straw brown, bent over half way up
        int dead = r.nextFloat() < 0.4F ? 1 : 0;
        for (int k = 0; k < dead; k++) {
            float bend = r.nextFloat() * (float) (Math.PI * 2);
            float phi0 = 0.1F + r.nextFloat() * 0.2F;
            blades.add(new Blade(x + (float) Math.cos(bend) * 0.01F, z + (float) Math.sin(bend) * 0.01F, bend, bend + (float) (Math.PI / 2),
                    h * (0.45F + r.nextFloat() * 0.25F), 0.014F + r.nextFloat() * 0.008F, phi0, 1.2F + r.nextFloat() * 0.4F, DEAD)
                    .profile(PlantShape.SWORD).at(LEAF_AT).inset(0.25F).untinted().twist((r.nextFloat() - 0.5F) * 1.5F));
        }
        var pieces = new ArrayList<Piece>();
        if (flowers) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.03F;
            Blade stem = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), h * (0.85F + r.nextFloat() * 0.2F), 0.011F + r.nextFloat() * 0.004F,
                    phi0, phi0 + 0.02F + r.nextFloat() * 0.05F, STEM).profile(PlantShape.CATTAIL_STEM).tube(5).at(STEM_AT);
            int index = blades.size();
            blades.add(stem);
            Pose grown = PlantShape.grown(stem);
            float female = 0.012F + r.nextFloat() * 0.005F;
            pieces.add(spike(grown, index, FEMALE, MALE, female, 8, PlantShape.SPIKE, new float[]{0, 0.06F, 0.94F, 1}, true, x, z));
            pieces.add(spike(grown, index, MALE, 1, stem.width * 0.45F, 6, PlantShape.MALE, new float[]{0, 1}, false, x, z));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /**
     * A spike on a stem (as grown) from the share a to b of its length: a capped tube of the given radius along the stem's
     * tangent there, its v the stem's own at those heights (so the wind moves it with the stem), on the spikes' column.
     */
    private static Piece spike(Pose stem, int index, float a, float b, float radius, int sides, int profile, float[] at, boolean caps, float footX, float footZ) {
        float[] foot = new float[6];
        PlantShape.frame(stem.pts, stem.f, (a + b) / 2, foot);
        float[] start = new float[6];
        PlantShape.frame(stem.pts, stem.f, a, start);
        float phi = (float) Math.acos(Math.max(-1, Math.min(1, foot[4]))), yaw = (float) Math.atan2(foot[5], foot[3]);
        Blade spike = new Blade(start[0], start[2], yaw, yaw + (float) (Math.PI / 2), (b - a) * stem.blade.len, radius * 2, phi, phi, SPIKES)
                .y(start[1]).tube(sides).profile(profile).at(at).untinted().v(1 - a, 1 - b);
        if (caps) spike.capped();
        return new Piece(index, (a + b) / 2, PlantShape.footed(PlantShape.quads(PlantShape.grown(spike)), footX, footZ));
    }
}
