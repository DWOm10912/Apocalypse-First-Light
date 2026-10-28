package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.ground.GroundBlend;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.SmoothTop;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain rework R2b (docs/worldgen/terrain_rework_v1_design.md 13.9): the top of a natural ground block as drawn now,
 * for SmoothSurfaceModel.
 * <ul>
 *   <li>A sloped block: its smooth top (SmoothTop#top, the nine points of SmoothRules' fan layout), with a shading normal
 *   per point (SmoothTop#normals: the vertex colours shade it smoothly without a shader pack, the normals with one).</li>
 *   <li>Where loose grounds meet (sloped or flat): GroundBlend's weights at the 0.25 m points, each material drawn with its
 *   own block's top texture. A quarter one material leads at all nine of its points is one quad, a 0.25 m cell all four
 *   corners lead is one quad, and only the cells a boundary crosses are cut into pieces (GroundBlend#cell, whose diagonals
 *   follow the fan's, so every piece lies on the top).</li>
 *   <li>A flat block with one material around it stays the vanilla model (nearly every block).</li>
 * </ul>
 * A material other than the block's own carries its tint baked into the vertex colour (the renderer tints by the block
 * being meshed), its texture turned as the vanilla variant of that block at this position would be.
 */
final class SmoothTopMesh {
    private static final int SIDE = 5;

    private SmoothTopMesh() {}

    /** What one block's top needs, taken in getModelData (render thread); the quads per render type, built on first ask. */
    static final class Top {
        /** nine points above the floor, or null for a flat top at 1 */
        final float[] top;
        /** 25 shading normals {x, y, z}, or null (flat: up) */
        final float[] normals;
        final int n;
        final BlockState[] states;
        /** n materials' weights at the 25 points (GroundBlend#weights), or null with one material */
        final double[] w;
        /** where it stands, and the render region (valid while this section is built: getQuads has neither) */
        final BlockPos pos;
        final BlockAndTintGetter region;
        private RenderType builtFor;
        private List<BakedQuad> built;

        Top(BlockPos pos, BlockAndTintGetter region, float[] top, float[] normals, int n, BlockState[] states, double[] w) {
            this.pos = pos;
            this.region = region;
            this.top = top;
            this.normals = normals;
            this.n = n;
            this.states = states;
            this.w = w;
        }
    }

    /**
     * The top of the natural ground block at pos (g: its geometry key, -1 when flat or not sloped), or null when the vanilla
     * top stands (a flat block among its own material only, or not a natural surface top at all).
     */
    static @Nullable Top inputs(BlockAndTintGetter region, BlockPos pos, BlockState state, long g) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !TerrainSmooth.CLIENT.enabled() || !TerrainSmooth.applies(level)) return null;
        if (g < 0) {
            // flat: only its column's natural surface top, open above (a buried block ends at the first read)
            if (!SmoothRules.openOver(TerrainSmooth.cell(state), TerrainSmooth.cell(region.getBlockState(pos.above())))) return null;
            if (TerrainSmoothClient.surfaceAt(level, pos.getX(), pos.getZ()) != pos.getY()) return null;
        }
        int[] mats = new int[9], surf = new int[9];
        BlockState[] col = new BlockState[9];
        TerrainSmoothClient.columns(region, pos, mats, surf, col);
        mats[4] = net.minecraft.world.level.block.Block.getId(state);
        col[4] = state;
        surf[4] = pos.getY();
        boolean one = true;
        for (int k = 0; k < 9; k++) if (mats[k] >= 0 && mats[k] != mats[4] && Math.abs(surf[k] - surf[4]) < GroundBlend.STEP) one = false;
        double[] w = null;
        int[] ids = new int[GroundBlend.MAX];
        int n = 1;
        if (!one) {
            n = GroundBlend.materials(mats, ids);
            w = new double[n * SIDE * SIDE];
            GroundBlend.weights(pos.getX(), pos.getZ(), mats, surf, ids, n, w);
            int[] lead = new int[SIDE * SIDE];
            GroundBlend.leaders(w, n, lead);
            boolean mixed = false;
            for (int v : lead) if (v != 0) mixed = true;
            if (!mixed) w = null;
        }
        if (g < 0 && w == null) return null;
        BlockState[] states = new BlockState[Math.max(1, n)];
        states[0] = state;
        if (w != null)
            for (int t = 1; t < n; t++)
                for (int k = 0; k < 9; k++) if (mats[k] == ids[t]) { states[t] = col[k]; break; }
        float[] top = null, normals = null;
        if (g >= 0) {
            top = TerrainSmoothClient.renderTop(region, pos, g);
            float[] points = TerrainSmoothClient.renderPoints(region, pos);
            if (points != null) {
                normals = new float[SIDE * SIDE * 3];
                SmoothTop.normals(g, pos.getY(), points, top, normals);
            }
        }
        return new Top(pos.immutable(), region, top, normals, w == null ? 1 : n, states, w);
    }

    /** The top's quads for one render type (built once per type). */
    static List<BakedQuad> quads(Top t, BakedModel original, BlockState state, RandomSource random, @Nullable RenderType type) {
        if (t.built != null && t.builtFor == type) return t.built;
        List<BakedQuad> own = original.getQuads(state, Direction.UP, random, ModelData.EMPTY, type);
        List<BakedQuad> out = own.isEmpty() ? List.of() : build(t, own, state, t.pos, t.region, type);
        t.builtFor = type;
        t.built = out;
        return out;
    }

    /** A material's look: its top layers, the texture corners' UVs (TopTexture), and per layer the colour baked in (-1 none). */
    private record Look(List<BakedQuad> layers, float[][] uv, int[] tint, boolean own) {}

    private static List<BakedQuad> build(Top t, List<BakedQuad> own, BlockState state, BlockPos pos, BlockAndTintGetter region, @Nullable RenderType type) {
        long begin = System.nanoTime();
        Look[] looks = new Look[t.n];
        looks[0] = new Look(own, TopTexture.uv(TopTexture.orientation(own)), null, true);
        for (int m = 1; m < t.n; m++) looks[m] = look(t.states[m], pos, region, type);
        double[] h = new double[SIDE * SIDE];
        for (int j = 0; j < SIDE; j++)
            for (int i = 0; i < SIDE; i++) h[j * SIDE + i] = t.top == null ? 1 : SmoothRules.surface(t.top, i / 4.0, j / 4.0);
        int[] lead = new int[SIDE * SIDE];
        if (t.w != null) GroundBlend.leaders(t.w, t.n, lead);
        List<BakedQuad> out = new ArrayList<>();
        if (t.w == null && t.top != null && planar(t.top)) {
            // one material on an even slope: one quad
            float[] c = t.top;
            emit(out, new float[][]{{0, c[0], 0}, {0, c[2], 1}, {1, c[3], 1}, {1, c[1], 0}}, 4, looks[0], t);
            return List.copyOf(out);
        }
        // the quarters as SmoothRules#topQuads lists them (nw, sw, se, ne), each from the centre
        int[][] quarters = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        float[][][] whole = t.top == null ? SmoothRules.topQuads(new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1}) : SmoothRules.topQuads(t.top);
        for (int q = 0; q < 4; q++) {
            int qx = quarters[q][0], qz = quarters[q][1], first = lead[(qz * 2) * SIDE + qx * 2];
            boolean same = true;
            for (int j = qz * 2; j <= qz * 2 + 2; j++)
                for (int i = qx * 2; i <= qx * 2 + 2; i++) if (lead[j * SIDE + i] != first) same = false;
            if (same) {
                emit(out, whole[q], 4, looks[first], t);
                continue;
            }
            for (int j = qz * 2; j < qz * 2 + 2; j++)
                for (int i = qx * 2; i < qx * 2 + 2; i++) {
                    int nw = j * SIDE + i, ne = nw + 1, sw = nw + SIDE, se = sw + 1;
                    if (lead[nw] == lead[ne] && lead[nw] == lead[sw] && lead[nw] == lead[se]) {
                        float[] pnw = point(i, j, h), pne = point(i + 1, j, h), psw = point(i, j + 1, h), pse = point(i + 1, j + 1, h);
                        float[][] cell = GroundBlend.nwSe(i, j) ? new float[][]{pnw, psw, pse, pne} : new float[][]{psw, pse, pne, pnw};
                        emit(out, cell, 4, looks[lead[nw]], t);
                        continue;
                    }
                    GroundBlend.cell(h, t.w, t.n, lead, i, j, (m, pts, c) -> {
                        float[][] p = new float[c][];
                        for (int k = 0; k < c; k++) p[k] = new float[]{(float) pts[k][0], (float) pts[k][1], (float) pts[k][2]};
                        emit(out, p, c, looks[m], t);
                    });
                }
        }
        TerrainSmoothClient.quadBuilds.increment();
        TerrainSmoothClient.quadNanos.add(System.nanoTime() - begin);
        return List.copyOf(out);
    }

    /** Whether a nine-point top is one plane: its corners on a plane, the midpoints and the centre on it. */
    static boolean planar(float[] c) {
        float e = 1e-5F;
        return Math.abs(c[0] + c[3] - c[1] - c[2]) < e && Math.abs(c[4] - (c[0] + c[1] + c[2] + c[3]) / 4) < e
                && Math.abs(c[5] - (c[0] + c[1]) / 2) < e && Math.abs(c[6] - (c[2] + c[3]) / 2) < e
                && Math.abs(c[7] - (c[0] + c[2]) / 2) < e && Math.abs(c[8] - (c[1] + c[3]) / 2) < e;
    }

    private static float[] point(int i, int j, double[] h) {
        return new float[]{i / 4F, (float) h[j * SIDE + i], j / 4F};
    }

    /** Another material's look at pos: its block's vanilla top, turned as that block's variant here, its tint baked. */
    private static Look look(BlockState s, BlockPos pos, BlockAndTintGetter region, @Nullable RenderType type) {
        BakedModel m = Minecraft.getInstance().getBlockRenderer().getBlockModel(s);
        if (m instanceof SmoothSurfaceModel sm) m = sm.original();
        RandomSource r = RandomSource.create(s.getSeed(pos));
        List<BakedQuad> layers = m.getQuads(s, Direction.UP, r, ModelData.EMPTY, type);
        if (layers.isEmpty()) {
            // a thin model's top is in the unculled list (snow layers...)
            List<BakedQuad> free = new ArrayList<>();
            for (BakedQuad q : m.getQuads(s, null, r, ModelData.EMPTY, type)) if (q.getDirection() == Direction.UP) free.add(q);
            layers = free;
        }
        int[] tint = new int[layers.size()];
        for (int k = 0; k < tint.length; k++) {
            int ti = layers.get(k).getTintIndex();
            tint[k] = ti < 0 ? -1 : Minecraft.getInstance().getBlockColors().getColor(s, region, pos, ti);
        }
        return new Look(layers, TopTexture.uv(TopTexture.orientation(layers)), tint, false);
    }

    /** A polygon of the top (points {fx, height, fz}, counter-clockwise from above) as quads, one per layer of its look. */
    private static void emit(List<BakedQuad> out, float[][] p, int count, Look look, Top t) {
        // a fan of quads from the first point (the pieces are planar: any split lies on them); a triangle repeats its last point
        for (int a = 1; a + 1 < count; a += 2) {
            float[][] quad = {p[0], p[a], p[a + 1], p[Math.min(a + 2, count - 1)]};
            float[][] uv = TopTexture.uvAt(look.uv, quad);
            float[][] nrm = new float[4][];
            int[] shade = new int[4];
            for (int k = 0; k < 4; k++) {
                nrm[k] = normal(t, quad[k][0], quad[k][2]);
                shade[k] = t.top == null || TerrainSmoothClient.shaderPack ? -1
                        : SmoothSurfaceModel.gray(nrm[k][1] * nrm[k][1] + 0.6F * nrm[k][0] * nrm[k][0] + 0.8F * nrm[k][2] * nrm[k][2]);
            }
            for (int l = 0; l < look.layers.size(); l++) {
                BakedQuad o = look.layers.get(l);
                int[] color = shade;
                int tint = o.getTintIndex();
                if (!look.own && look.tint[l] != -1) {
                    color = new int[4];
                    for (int k = 0; k < 4; k++) color[k] = multiply(shade[k], look.tint[l]);
                    tint = -1;
                } else if (!look.own) tint = -1;
                out.add(quad(quad, uv, o.getSprite(), tint, color, nrm));
            }
        }
    }

    /** The shading normal at (fx, fz): the 25 points' normals, bilinear. */
    private static float[] normal(Top t, float fx, float fz) {
        if (t.normals == null) return new float[]{0, 1, 0};
        float px = Math.max(0, Math.min(3.9999F, fx * 4)), pz = Math.max(0, Math.min(3.9999F, fz * 4));
        int i = (int) px, j = (int) pz;
        float u = px - i, v = pz - j;
        float[] n = new float[3];
        for (int a = 0; a < 3; a++) {
            float n00 = t.normals[(j * SIDE + i) * 3 + a], n10 = t.normals[(j * SIDE + i + 1) * 3 + a];
            float n01 = t.normals[((j + 1) * SIDE + i) * 3 + a], n11 = t.normals[((j + 1) * SIDE + i + 1) * 3 + a];
            n[a] = (n00 * (1 - u) + n10 * u) * (1 - v) + (n01 * (1 - u) + n11 * u) * v;
        }
        float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        return l < 1e-6F ? new float[]{0, 1, 0} : new float[]{n[0] / l, n[1] / l, n[2] / l};
    }

    private static int multiply(int a, int rgb) {
        int r = ((a >> 16 & 255) * (rgb >> 16 & 255)) / 255, g = ((a >> 8 & 255) * (rgb >> 8 & 255)) / 255, b = ((a & 255) * (rgb & 255)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A top quad with a colour and a normal per vertex (light computed by the renderer). */
    private static BakedQuad quad(float[][] p, float[][] uv, TextureAtlasSprite sprite, int tint, int[] color, float[][] n) {
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            data[o] = Float.floatToRawIntBits(p[i][0]);
            data[o + 1] = Float.floatToRawIntBits(p[i][1]);
            data[o + 2] = Float.floatToRawIntBits(p[i][2]);
            data[o + 3] = color[i];
            data[o + 4] = Float.floatToRawIntBits(sprite.getU(uv[i][0] * 16));
            data[o + 5] = Float.floatToRawIntBits(sprite.getV(uv[i][1] * 16));
            data[o + 6] = 0;
            data[o + 7] = (((byte) (n[i][0] * 127)) & 0xFF) | ((((byte) (n[i][1] * 127)) & 0xFF) << 8) | ((((byte) (n[i][2] * 127)) & 0xFF) << 16);
        }
        return new BakedQuad(data, tint, Direction.UP, sprite, true, true);
    }
}
