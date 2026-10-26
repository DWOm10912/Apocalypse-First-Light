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
 * Black-eyed Susan (Rudbeckia hirta; AFL vegetation P2b batch 2, docs/gameplay/vegetation_v1_design.md 16): a block is a
 * square metre of turf with 2-3 plants in it (two bluegrass tufts).
 * <ul>
 * <li>LOD0: a plant is 5-8 hairy lanceolate basal leaves, 10-17 cm, spreading low (cut out of the sheet), and 1-3 bristly
 * stems 35-70 cm (round, 4 sides, tapering). A stem carries three stem leaves at 0.2-0.6 of its height and one head at
 * its top: the dark brown-purple dome (1.4-2 cm across) and 10-14 golden rays 2-3.5 cm long, spreading nearly level and
 * a little turned down at their tips. Stem leaves and head are rigid parts carried by the stem.</li>
 * <li>LOD1: two crossed foliage cards and two crossed flower cards a plant. LOD2: a turf card and one pair of Susan cards.</li>
 * </ul>
 * Sheet columns: 48 the basal leaf, 49 the stem, 50 the parts (rows 0-8: the dome in the left half, a ray in the right;
 * the stem leaves across the rows of their heights), 51 the foliage card, 52 the flower card (real colours). The stems
 * sway 1.2 times as far as the turf, the parts 1.3 (PlantShape#WIND).
 */
final class SusanShape {
    static final int LEAF = 48, STEM = 49, PARTS = 50, CARD = 51, CARD_FLOWERS = 52;
    /** Where the stem leaves sit (shares of the stem's height; the art lays them at these rows) and their lengths (m). */
    static final float[] LEAVES_AT = {0.2F, 0.4F, 0.6F}, LEAF_LENGTHS = {0.08F, 0.065F, 0.05F};
    private static final float[] LEAF_AT = {0, 0.4F, 1}, STEM_AT = {0, 0.3F, 0.6F, 1};

    private SusanShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0x5A5A11L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int plants = 2 + r.nextInt(2), tufts = 2;
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float stems = 0;
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
            float a = (k + r.nextFloat() * 0.7F) / n * (float) (Math.PI * 2), phi0 = 0.6F + r.nextFloat() * 0.4F;
            blades.add(new Blade(x + (float) Math.cos(a) * 0.006F, z + (float) Math.sin(a) * 0.006F, a, a + (float) (Math.PI / 2),
                    0.1F + r.nextFloat() * 0.07F, 0.025F + r.nextFloat() * 0.01F, phi0, Math.min(1.5F, phi0 + 0.3F + r.nextFloat() * 0.2F), LEAF)
                    .profile(PlantShape.FLAT).at(LEAF_AT).cutout().twist((r.nextFloat() - 0.5F) * 0.4F));
        }
        int stems = 1 + r.nextInt(3);
        for (int s = 0; s < stems; s++) {
            float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = r.nextFloat() * 0.12F, off = 0.008F + r.nextFloat() * 0.012F;
            Blade stem = new Blade(x + (float) Math.cos(yaw) * off, z + (float) Math.sin(yaw) * off, yaw, yaw + (float) (Math.PI / 2),
                    0.35F + r.nextFloat() * 0.35F, 0.0045F, phi0, phi0 + 0.05F + r.nextFloat() * 0.15F, STEM)
                    .profile(PlantShape.TAPER).tube(4).at(STEM_AT);
            int index = blades.size();
            blades.add(stem);
            Pose grown = PlantShape.grown(stem);
            float side = r.nextFloat() * (float) (Math.PI * 2), scale = 0.85F + r.nextFloat() * 0.3F;
            for (int k = 0; k < LEAVES_AT.length; k++)
                pieces.add(new Piece(index, LEAVES_AT[k], PlantShape.footed(FlowerParts.stemLeaf(grown, LEAVES_AT[k],
                        side + 2.4F * k + (r.nextFloat() - 0.5F) * 0.6F, LEAF_LENGTHS[k] * scale, 0.02F * scale, PARTS, r), x, z), true));
            pieces.add(new Piece(index, 1, PlantShape.footed(head(grown, r), x, z)));
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /** The head on a stem's top: the dark dome and the golden rays round its base. */
    private static List<Quad> head(Pose stem, Rng r) {
        float rd = 0.007F + r.nextFloat() * 0.003F, hd = 0.008F + r.nextFloat() * 0.003F;
        var out = new ArrayList<Quad>(FlowerParts.middle(stem, new float[]{rd, rd * 0.85F, rd * 0.5F, 0}, new float[]{0, hd * 0.5F, hd * 0.88F, hd}, 8, PARTS, r));
        out.addAll(FlowerParts.rays(stem, 10 + r.nextInt(5), rd * 0.9F, 0.001F, 0.02F + r.nextFloat() * 0.015F, 0.008F,
                (float) Math.toRadians(4), (float) Math.toRadians(24), PARTS, r));
        return out;
    }
}
