package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Quad;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.add;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.cross;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.scale;
import static com.antaurora.apofirstlight.vegetation.client.BigBluestemShape.unit;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.clamp;

/**
 * Bracken (Pteridium aquilinum; AFL vegetation P2b, docs/gameplay/vegetation_v1_design.md 15): a block is a square metre
 * of 3-5 fronds from the creeping rhizome, each on its own.
 * <ul>
 * <li>A frond: a stiff stipe 30-55 cm (round, tapering, darker at the foot), leaning a little, and on it the blade held
 * nearly level: broadly triangular, 35-65 cm long and nearly as wide, its rachis rising off the stipe and arching over
 * (4 segments), the two halves dropping a little either side of it. The blade is a rigid part carried by the stipe, both
 * faces, its two- to three-times divided outline cut out of the sheet.</li>
 * <li>LOD1: the same fronds simpler (stipe 4 sides, blade 2 segments). LOD2: stipe 3 sides, blade 2 segments as LOD1
 * (was one flat segment: 0.61 of LOD0's side silhouette, doc 17.2).</li>
 * </ul>
 * Sheet columns: 35 the stipe, 36 the blade in rows 0-47 (its foot at row 0: the stipe's top height, so the wind moves it
 * with the stipe; its tip, lower on the sheet, swaying a little less: the rachis flexing). Both sway 0.9 times as far as
 * the turf (PlantShape#WIND).
 */
final class FernShape {
    static final int STIPE = 35, FROND = 36;
    /** The blade's rows: its foot at row 0, its tip at row 47. */
    static final float ROWS = 48 / 128F;
    private static final float[][] STIPE_AT = {{0, 0.35F, 0.7F, 1}, {0, 0.5F, 1}, {0, 1}};
    private static final int[] SIDES = {5, 4, 3};
    /** The rachis' pitch per segment at each level (degrees above level; jittered). */
    private static final float[][] PITCH = {{40, 16, -4, -22}, {28, -12}, {28, -12}};

    private FernShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xF3A2L * 31 + v * 0x9E3779B97F4A7C15L);
            int[] slots = r.slots();
            int fronds = 3 + r.nextInt(3);
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = new ArrayList<>();
            for (int k = 0; k < fronds; k++) {
                int slot = slots[k];
                float x = clamp((slot % 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.25F), z = clamp((slot / 3 + 0.5F) / 3 + (r.nextFloat() - 0.5F) * 0.25F);
                // one frond's numbers for every level, so the levels blend frond by frond
                long seed = r.next();
                for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level].add(frond(new Rng(seed), x, z, level));
            }
            for (int level = 0; level < PlantShape.LEVELS; level++) out[v][level] = List.copyOf(out[v][level]);
        }
        return out;
    }

    /** One frond at (x, z) at a level of detail: the stipe and the blade on it. */
    private static Clump frond(Rng r, float x, float z, int level) {
        float yaw = r.nextFloat() * (float) (Math.PI * 2), phi0 = 0.03F + r.nextFloat() * 0.1F;
        Blade stipe = new Blade(x, z, yaw, yaw + (float) (Math.PI / 2), 0.3F + r.nextFloat() * 0.25F, 0.007F + r.nextFloat() * 0.003F,
                phi0, phi0 + 0.12F + r.nextFloat() * 0.25F, STIPE).profile(PlantShape.TAPER).tube(SIDES[level]).at(STIPE_AT[level]);
        float len = 0.35F + r.nextFloat() * 0.3F, width = len * (0.8F + r.nextFloat() * 0.1F);
        float[] pitch = PITCH[level].clone();
        float lift = (r.nextFloat() - 0.5F) * 12;
        for (int k = 0; k < pitch.length; k++) pitch[k] = (float) Math.toRadians(pitch[k] + lift);
        float droop = (float) Math.toRadians(10 + r.nextFloat() * 8), turn = (r.nextFloat() - 0.5F) * 0.5F, rank = r.nextFloat();
        List<Quad> blade = PlantShape.footed(blade(PlantShape.grown(stipe), yaw + turn, len, width, pitch, droop), x, z);
        return new Clump(x, z, rank, List.of(stipe), List.of(), List.of(new Piece(0, 1, blade, true)));
    }

    /**
     * The blade on a stipe's top (as grown), its rachis along yaw: a segment per pitch (radians above level), each two
     * quads either side of the rachis, the outer edges dropped by {@code droop}; both faces. The sheet's blade spans the
     * full width at every row (the outline is cut out of it).
     */
    private static List<Quad> blade(Pose stipe, float yaw, float len, float width, float[] pitch, float droop) {
        float[] f = new float[6];
        PlantShape.frame(stipe.pts, stipe.f, 1, f);
        int n = pitch.length;
        float[][] rachis = new float[n + 1][];
        rachis[0] = new float[]{f[0], f[1], f[2]};
        float fx = (float) Math.cos(yaw), fz = (float) Math.sin(yaw), l = len / n;
        for (int k = 0; k < n; k++)
            rachis[k + 1] = add(rachis[k], new float[]{fx * l * (float) Math.cos(pitch[k]), l * (float) Math.sin(pitch[k]), fz * l * (float) Math.cos(pitch[k])});
        float[] side = {-fz, 0, fx};
        float half = width / 2, out = half * (float) Math.cos(droop), down = half * (float) Math.sin(droop);
        float ul = (FROND + 0.02F) / COLUMNS, um = (FROND + 0.5F) / COLUMNS, ur = (FROND + 0.98F) / COLUMNS;
        var quads = new ArrayList<Quad>();
        for (int k = 0; k < n; k++) {
            float[] d = unit(add(scale(rachis[k + 1], 1), scale(rachis[k], -1)));
            float[] up = unit(cross(side, d));
            if (up[1] < 0) up = scale(up, -1);
            float va = 0.4F / 128 + (ROWS - 0.8F / 128) * k / n, vb = 0.4F / 128 + (ROWS - 0.8F / 128) * (k + 1) / n;
            float[][] left = new float[2][], right = new float[2][];
            for (int j = 0; j < 2; j++) {
                float[] p = rachis[k + j];
                left[j] = add(add(p, scale(side, -out)), scale(up, -down));
                right[j] = add(add(p, scale(side, out)), scale(up, -down));
            }
            float light = 0.96F;
            PlantShape.face(quads, new float[][]{rachis[k], rachis[k + 1], left[1], left[0]},
                    new float[][]{{um, va}, {um, vb}, {ul, vb}, {ul, va}}, up, light, true, false, true);
            PlantShape.face(quads, new float[][]{rachis[k], right[0], right[1], rachis[k + 1]},
                    new float[][]{{um, va}, {ur, va}, {ur, vb}, {um, vb}}, up, light, true, false, true);
        }
        return quads;
    }
}
