package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * American elderberry (Sambucus canadensis; AFL vegetation P4a batch 3, docs/gameplay/vegetation_v1_design.md 24): a
 * many-stemmed shrub of stream banks and wet wood edges. A block is one shrub.
 * <ul>
 * <li>LOD0: 3-6 canes from one crown, 1.4-2.8 m, round (4 sides, tapering), grey-brown with warty lenticels low down and
 * green higher, leaning out and arching over at the top. Six nodes from 0.35 to 0.92 of a cane, each a pair of opposite
 * compound leaves square to the pair below: 20-30 cm, seven leaflets on a rachis (the picture fills a column, cut out),
 * rising then drooping. Two canes in three end in a flat-topped cyme of tiny white flowers 16-24 cm across (a level quad)
 * and often a smaller one from the node below; the rest hang a cluster of purple-black berries on red stalks (two crossed
 * quads). Leaves, cymes and berries are rigid parts carried by the cane.</li>
 * <li>LOD1: two crossed foliage cards and two crossed flower cards. LOD2: the same, wider, one segment. Tall: drawn at
 * every distance (taller than the reed, the model still standing in one block and reaching over its top).</li>
 * </ul>
 * Sheet columns: 96 the cane (real colours), 97 the compound leaf (tinted), 98 the parts (the left half rows 0-15 the cyme
 * from above, the right half rows 0-31 the hanging berry cluster; real colours), 99 the foliage card, 100 the flower card (real
 * colours). The canes sway 0.7 times as far as the turf, the leaves and cards 0.9 (PlantShape#WIND).
 */
final class ElderberryShape {
    static final int CANE = 96, LEAF = 97, PARTS = 98, CARD = 99, CARD_FLOWERS = 100;
    /** The cyme's rows (left half: square) and the berry cluster's (right half) of the parts column. */
    static final float CYME = 15.7F / 128, HEAD = 31.5F / 128;
    private static final float[] CANE_AT = {0, 0.3F, 0.55F, 0.8F, 1};

    private ElderberryShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xE1DE8L * 31 + v * 0x9E3779B97F4A7C15L);
            float cx = clamp(0.5F + (r.nextFloat() - 0.5F) * 0.2F), cz = clamp(0.5F + (r.nextFloat() - 0.5F) * 0.2F);
            int canes = 3 + r.nextInt(4);
            var near = new ArrayList<Clump>();
            float tallest = 0;
            float turn = r.nextFloat() * (float) (Math.PI * 2);
            for (int k = 0; k < canes; k++) {
                float a = turn + (k + r.nextFloat() * 0.6F) / canes * (float) (Math.PI * 2);
                Clump cane = cane(r, clamp(cx + (float) Math.cos(a) * 0.05F), clamp(cz + (float) Math.sin(a) * 0.05F), a);
                near.add(cane);
                tallest = Math.max(tallest, BigBluestemShape.top(cane.grown[0]));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.of(FlowerParts.cards(r, cx, cz, tallest * 1.05F, 2.8F, 2, CARD, CARD_FLOWERS));
            out[v][2] = List.of(FlowerParts.cards(r, cx, cz, tallest * 1.02F, 3.0F, 1, CARD, CARD_FLOWERS));
        }
        return out;
    }

    /** One cane at (x, z) leaning out along yaw: its pairs of leaves and its cyme or berries. */
    private static Clump cane(Rng r, float x, float z, float yaw) {
        float phi0 = 0.03F + r.nextFloat() * 0.09F, len = 1.4F + r.nextFloat() * 1.4F;
        Blade cane = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), len, 0.018F + r.nextFloat() * 0.008F, phi0,
                phi0 + 0.12F + r.nextFloat() * 0.13F, CANE).profile(PlantShape.TAPER).tube(4).at(CANE_AT).untinted();
        Pose grown = PlantShape.grown(cane);
        var pieces = new ArrayList<Piece>();
        float pair = r.nextFloat() * (float) Math.PI;
        for (int k = 0; k < 6; k++) {
            float at = 0.35F + 0.114F * k, l = (0.2F + r.nextFloat() * 0.1F) * (0.85F + 0.15F * (1 - k / 5F));
            for (int s = 0; s < 2; s++)
                pieces.add(new Piece(0, at, PlantShape.footed(FlowerParts.columnLeaf(grown, at,
                        pair + (float) (Math.PI / 2) * k + (float) Math.PI * s + (r.nextFloat() - 0.5F) * 0.3F, l, l * 0.75F,
                        0.3F + r.nextFloat() * 0.25F, -0.25F - r.nextFloat() * 0.35F, LEAF, true), x, z), true));
        }
        float spin = r.nextFloat() * (float) Math.PI;
        if (r.nextFloat() < 0.67F) {
            float s = 0.16F + r.nextFloat() * 0.08F;
            pieces.add(new Piece(0, 1, PlantShape.footed(FlowerParts.level(grown, 1, yaw, 0, 0.02F, s, PARTS, 0.02F, 0.49F, 0.3F / 128, CYME, spin, false), x, z)));
            if (r.nextFloat() < 0.6F)
                pieces.add(new Piece(0, 0.92F, PlantShape.footed(FlowerParts.level(grown, 0.92F, pair + r.nextFloat() * 3, 0.1F, 0.06F, s * 0.7F,
                        PARTS, 0.02F, 0.49F, 0.3F / 128, CYME, spin + 1, false), x, z)));
        } else {
            float s = 0.13F + r.nextFloat() * 0.05F;
            pieces.add(new Piece(0, 1, PlantShape.footed(FlowerParts.crossed(grown, 1, yaw, 0.03F, -s * 0.45F, s, s, PARTS, 0.51F, 0.98F, 0.3F / 128, HEAD, spin), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), List.of(cane), List.of(), pieces);
    }
}
