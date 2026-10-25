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
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Big sagebrush (Artemisia tridentata; AFL vegetation P4a batch 3, docs/gameplay/vegetation_v1_design.md 24): the
 * silver-grey shrub of the dry plateaus and canyon country. A block is one shrub (two smaller in 30 % of the layouts).
 * <ul>
 * <li>LOD0: 2-4 old trunks from the base, gnarled (4 sides, tapering; grey-brown, the bark in shreds), leaning out 15-35
 * degrees and bending on; one to three side branches from each. Every trunk's and branch's end holds a mass of foliage:
 * the small wedge-shaped, three-toothed grey leaves crowded on the twigs (two crossed quads 22-38 cm across and a level
 * one over them, cut out), so the shrub reads as a rounded, ragged silver crown 0.6-1.4 m high. Real colours.</li>
 * <li>LOD1: two crossed cards a shrub. LOD2: the same, one segment. Tall: drawn at every distance.</li>
 * </ul>
 * Sheet columns: 101 the bark, 102 the foliage (rows 0-31 a mass from the side, rows 32-63 from above: square, as the
 * quads), 103 the card.
 * Woody: sways 0.4 times as far as the turf (PlantShape#WIND).
 */
final class SagebrushShape {
    static final int BARK = 101, FOLIAGE = 102, CARD = 103;
    /** The foliage column's rows: the side view to SIDE, the view from above SIDE..TOP. */
    static final float SIDE = 31.5F / 128, TOP = 63.5F / 128;
    private static final float[] TRUNK_AT = {0, 0.4F, 0.75F, 1};

    private SagebrushShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x5A6EL * 31 + v * 0x9E3779B97F4A7C15L);
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = new ArrayList<>();
            boolean two = r.nextFloat() < 0.3F;
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int s = 0; s < (two ? 2 : 1); s++) {
                double a = turn + Math.PI * s;
                float spread = two ? 0.22F : 0.04F, scale = two ? 0.7F : 0.85F + r.nextFloat() * 0.35F;
                float x = clamp(0.5F + spread * (float) Math.cos(a)), z = clamp(0.5F + spread * (float) Math.sin(a));
                int trunks = 2 + r.nextInt(3);
                float top = 0;
                for (int k = 0; k < trunks; k++) {
                    Clump trunk = trunk(r, x, z, turn + (k + r.nextFloat() * 0.6F) / trunks * (float) (Math.PI * 2), scale);
                    out[v][0].add(trunk);
                    top = Math.max(top, BigBluestemShape.top(trunk.grown[0]) + 0.15F * scale);
                }
                float yaw = r.nextFloat() * (float) Math.PI, h = top * 1.05F, w = h * ASPECT * 4.2F, half = (float) (Math.PI / 2);
                for (int level = 1; level < PlantShape.LEVELS; level++) {
                    int seg = level == 1 ? 2 : 1;
                    out[v][level].add(new Clump(x, z, r.nextFloat(), List.of(), List.of(
                            new Card(x, z, yaw, h, w, 0.12F, CARD, seg, false), new Card(x, z, yaw + half, h, w, -0.12F, CARD, seg, false)), List.of()));
                }
            }
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = List.copyOf(out[v][level]);
        }
        return out;
    }

    /** One trunk at (x, z) leaning out along yaw: its side branches and the foliage at their ends. */
    private static Clump trunk(Rng r, float x, float z, float yaw, float scale) {
        float phi0 = 0.25F + r.nextFloat() * 0.35F, len = (0.4F + r.nextFloat() * 0.45F) * scale;
        Blade trunk = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), len, (0.03F + r.nextFloat() * 0.015F) * scale, phi0,
                Math.max(0.05F, phi0 + (r.nextFloat() - 0.6F) * 0.5F), BARK).profile(PlantShape.TAPER).tube(4).at(TRUNK_AT).untinted()
                .twist((r.nextFloat() - 0.5F) * 1.5F);
        Pose grown = PlantShape.grown(trunk);
        var pieces = new ArrayList<Piece>();
        pieces.add(new Piece(0, 1, PlantShape.footed(foliage(grown, 1, r, (0.28F + r.nextFloat() * 0.1F) * scale), x, z)));
        int branches = 1 + r.nextInt(3);
        for (int k = 0; k < branches; k++) {
            float at = 0.4F + r.nextFloat() * 0.4F, side = yaw + (r.nextFloat() < 0.5F ? -1 : 1) * (0.6F + r.nextFloat() * 1.2F);
            float bp0 = 0.35F + r.nextFloat() * 0.45F;
            Blade twig = FlowerParts.twig(grown, at, side, (0.2F + r.nextFloat() * 0.25F) * scale, trunk.width * 0.45F, bp0,
                    bp0 + (r.nextFloat() - 0.4F) * 0.4F, BARK, 3);
            Pose tp = PlantShape.grown(twig);
            var quads = new ArrayList<Quad>(PlantShape.quads(tp));
            quads.addAll(foliage(tp, 1, r, (0.22F + r.nextFloat() * 0.1F) * scale));
            pieces.add(new Piece(0, at, PlantShape.footed(quads, x, z)));
        }
        return new Clump(x, z, r.nextFloat(), List.of(trunk), List.of(), pieces);
    }

    /** A mass of foliage at the share {@code at} of a branch (as grown): two crossed quads round its end and a level one over them. */
    private static List<Quad> foliage(Pose branch, float at, Rng r, float size) {
        float spin = r.nextFloat() * (float) Math.PI;
        var out = new ArrayList<Quad>(FlowerParts.crossed(branch, at, 0, 0, size * 0.18F, size, size * 0.75F, FOLIAGE, 0.02F, 0.98F, 0.3F / 128, SIDE, spin));
        out.addAll(FlowerParts.level(branch, at, 0, 0, size * 0.36F, size * 0.85F, FOLIAGE, 0.02F, 0.98F, SIDE + 1F / 128, TOP, spin + 0.7F, false));
        return out;
    }
}
