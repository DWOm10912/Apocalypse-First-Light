package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Quad;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Purple coneflower (Echinacea purpurea; AFL vegetation P2b batch 2, docs/gameplay/vegetation_v1_design.md 16): a block
 * is a square metre of turf with 1-2 plants in it (2-3 bluegrass tufts round them).
 * <ul>
 * <li>LOD0: a plant is 5-8 basal leaves (a long petiole, an ovate blade coarsely toothed, 18-30 cm, arching out; cut out of
 * the sheet) and 1-3 stout stems 0.6-1 m (round, 5 sides, tapering). A stem carries three stem leaves at 0.25-0.65 of its
 * height, smaller up the stem, and one head at its top: the orange-brown spiny cone (a dome 2.4-3.2 cm across, 1.5-2 cm
 * high) and 13-18 pink-purple rays 3.5-5 cm long hanging down from under it (two segments, the outer one steeper).
 * Stem leaves and head are rigid parts carried by the stem; the leaves lie down with a fallen stem.</li>
 * <li>LOD1: two crossed foliage cards and two crossed flower cards a plant. LOD2: a turf card and one pair of coneflower cards.</li>
 * </ul>
 * Sheet columns: 43 the basal leaf, 44 the stem, 45 the parts (rows 0-8: the cone in the left half, a ray in the right;
 * the stem leaves across the rows of their heights), 46 the foliage card, 47 the flower card (real colours). The stems
 * sway 1.3 times as far as the turf, the parts 1.4 (their rows sit a little below the stem's top: PlantShape#WIND).
 */
final class ConeflowerShape {
    static final int LEAF = 43, STEM = 44, PARTS = 45, CARD = 46, CARD_FLOWERS = 47;
    /** Where the stem leaves sit (shares of the stem's height; the art lays them at these rows) and their lengths (m). */
    static final float[] LEAVES_AT = {0.25F, 0.45F, 0.65F}, LEAF_LENGTHS = {0.13F, 0.11F, 0.085F};
    private static final float[] LEAF_AT = {0, 0.35F, 0.7F, 1}, STEM_AT = {0, 0.25F, 0.5F, 0.75F, 1};

    private ConeflowerShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xEC41AL * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int plants = 1 + r.nextInt(2), tufts = 2 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float stems = 0;
            for (int k = 0; k < plants + tufts; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.2F);
                if (k >= plants) {
                    near.add(BluegrassShape.tuft(r, x, z, 0.24F + r.nextFloat() * 0.12F, 8 + r.nextInt(3)));
                    mid.add(BluegrassShape.middle(r, x, z));
                    continue;
                }
                Clump plant = plant(r, x, z);
                near.add(plant);
                float tall = 0;
                for (Blade b : plant.blades) if (b.column == STEM) tall = Math.max(tall, b.len);
                stems = Math.max(stems, tall);
                mid.add(FlowerParts.cards(r, x, z, tall * 1.05F, 1.1F, 2, CARD, CARD_FLOWERS));
            }
            var far = new ArrayList<Clump>();
            far.add(BluegrassShape.far(r, 0.5F, 0.5F, r.nextFloat() * (float) Math.PI));
            far.add(FlowerParts.cards(r, 0.5F, 0.5F, stems * 1.05F, 1.1F, 1, CARD, CARD_FLOWERS));
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.copyOf(far);
        }
        return out;
    }

    /** One plant at (x, z): its basal leaves and its flowering stems. */
    private static Clump plant(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        int n = 5 + r.nextInt(4);
        for (int k = 0; k < n; k++) {
            float a = (k + r.nextFloat() * 0.7F) / n * (float) (Math.PI * 2), phi0 = 0.45F + r.nextFloat() * 0.35F;
            blades.add(new Blade(x + (float) Math.cos(a) * 0.008F, z + (float) Math.sin(a) * 0.008F, a, a + (float) (Math.PI / 2),
                    0.18F + r.nextFloat() * 0.12F, 0.05F + r.nextFloat() * 0.02F, phi0, Math.min(1.5F, phi0 + 0.3F + r.nextFloat() * 0.3F), LEAF)
                    .profile(PlantShape.FLAT).at(LEAF_AT).cutout().twist((r.nextFloat() - 0.5F) * 0.4F));
        }
        int stems = 1 + r.nextInt(3);
        for (int s = 0; s < stems; s++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.08F, off = 0.01F + r.nextFloat() * 0.015F;
            Blade stem = new Blade(x + (float) Math.cos(yaw) * off, z + (float) Math.sin(yaw) * off, yaw, yaw + (float) (Math.PI / 2),
                    0.6F + r.nextFloat() * 0.4F, 0.006F + r.nextFloat() * 0.002F, phi0, phi0 + 0.05F + r.nextFloat() * 0.1F, STEM)
                    .profile(PlantShape.TAPER).tube(5).at(STEM_AT);
            int index = blades.size();
            blades.add(stem);
            Pose grown = PlantShape.grown(stem);
            float side = r.nextFloat() * (float) (Math.PI * 2), scale = 0.85F + r.nextFloat() * 0.3F;
            for (int k = 0; k < LEAVES_AT.length; k++)
                pieces.add(new Piece(index, LEAVES_AT[k], PlantShape.footed(FlowerParts.stemLeaf(grown, LEAVES_AT[k],
                        side + 2.4F * k + (r.nextFloat() - 0.5F) * 0.6F, LEAF_LENGTHS[k] * scale, 0.032F * scale, PARTS, r), x, z), true));
            pieces.add(new Piece(index, 1, PlantShape.footed(head(grown, r), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /** The head on a stem's top: the domed cone and the hanging rays round its base. */
    private static List<Quad> head(Pose stem, Rng r) {
        float rc = 0.012F + r.nextFloat() * 0.004F, hc = 0.015F + r.nextFloat() * 0.005F;
        var out = new ArrayList<Quad>(FlowerParts.middle(stem, new float[]{rc, rc * 0.8F, rc * 0.45F, 0}, new float[]{0, hc * 0.45F, hc * 0.85F, hc}, 8, PARTS, r));
        out.addAll(FlowerParts.rays(stem, 13 + r.nextInt(6), rc * 0.9F, 0.002F, 0.035F + r.nextFloat() * 0.015F, 0.009F,
                (float) Math.toRadians(30), (float) Math.toRadians(68), PARTS, r));
        return out;
    }
}
