package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk model of a natural ground block (Terrain Smooth V1; look approved in P0 / P1, mode B): exactly the vanilla model
 * unless the block is a sloped natural surface top. Then:
 * <ul>
 *   <li>the top: one quad per vanilla top layer (grass top, snowy top...) through the four corner heights, texture
 *   projected from above in the orientation of the vanilla variant at that position (TopTexture, 2026-10-15), in the UP
 *   list (lit from the cell above, culled by a block placed on it);</li>
 *   <li>each side: only the band between the neighbour's surface and this top, with the vanilla side layers (grass side
 *   and its tinted overlay) mapped down from the band's top edge, so the grass fringe follows the slope; below the band
 *   the neighbour cell is counted on, and where that cell hides nothing (a mesh trunk, a log, glass, a slab...;
 *   TerrainSmoothClient#underSides) the part below the band too, culled like a vanilla face (2026-10-12); a band
 *   reaching above the block's cell is unculled, lit with the light of the cell above (2026-10-15);</li>
 *   <li>the bottom: the vanilla one.</li>
 * </ul>
 * Lighting: vanilla smooth lighting (and Forge's per-quad flat path inside it) samples a quad that is not flush with its
 * face from the block's own cell, which is dark inside an opaque block; sloped blocks are therefore lit flat (no AO),
 * where the culled-face lists take their light from the neighbour cell. Forge 47 decides AO per model and state, so the
 * decision for the block being meshed is handed over by TerrainSmoothClient (#slopedNow).
 * <p>
 * Without a shader pack the top is darkened by its tilt like vanilla's directional shading (a 45-degree slope facing
 * east is between the top's 1.0 and the east side's 0.6); with one, the pack shades it from the normals.
 */
final class SmoothSurfaceModel extends BakedModelWrapper<BakedModel> {
    static final ModelProperty<Long> GEOMETRY = new ModelProperty<>();
    /** TerrainSmoothClient#underSides: sides whose part below the band this model draws itself, and that part's light */
    static final ModelProperty<Integer> UNDER = new ModelProperty<>();
    /** Earthwork A1: an edited column's skin cell, its skin quads (EarthSkin; unculled, solid layer) */
    static final ModelProperty<List<BakedQuad>> SKIN = new ModelProperty<>();
    /** U3a-3 round 2: a carved rock block, its surface net (RockCarveMesh; unculled, solid layer, no vanilla face at all) */
    static final ModelProperty<List<BakedQuad>> CARVE = new ModelProperty<>();
    static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** floor points a (left) and b (right) of each side seen from outside, and which corners stand above them (also SnowSkinModel) */
    static final float[][] SIDE_A = {{1, 0}, {0, 1}, {0, 0}, {1, 1}}, SIDE_B = {{0, 0}, {1, 1}, {0, 1}, {1, 0}};
    static final int[][] SIDE_CORNERS = {{1, 0}, {2, 3}, {0, 2}, {3, 1}};
    /** by top orientation (TopTexture), then geometry key */
    private final Map<Integer, Map<Key, List<BakedQuad>[]>> cache = new ConcurrentHashMap<>();

    private record Key(long geometry, int extra) {}

    SmoothSurfaceModel(BakedModel original) {
        super(original);
    }

    /** The vanilla model it wraps. */
    BakedModel original() { return originalModel; }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        List<BakedQuad> carve = RockCarveMesh.quads(level, pos, state);   // carved rock (U3a-3 round 2)
        if (carve != null) return base.derive().with(CARVE, carve).build();
        List<BakedQuad> skin = EarthSkin.quads(level, pos, state);   // an edited column's skin cell (Earthwork)
        if (skin != null) return base.derive().with(SKIN, skin).build();
        long g = TerrainSmoothClient.renderGeometry(level, pos, state);   // every ground block of every chunk passes here
        // R2b: the smooth top and the meeting of loose grounds (flat blocks too), null where the vanilla top stands
        SmoothTopMesh.Top top = SmoothTopMesh.inputs(level, pos, state, g);
        if (g < 0) return top == null ? base : base.derive().with(TOP, top).build();
        int under = TerrainSmoothClient.underSides(level, pos, g);
        var sloped = base.derive().with(GEOMETRY, g);
        if (top != null) sloped.with(TOP, top);
        return (under == 0 ? sloped : sloped.with(UNDER, under)).build();
    }

    /** R2b: the block's top as SmoothTopMesh draws it (a smooth top, a boundary between loose grounds). */
    static final ModelProperty<SmoothTopMesh.Top> TOP = new ModelProperty<>();

    /** A skin cell is drawn in the solid layer only (its tops are opaque, a grass top's overlay sides are not drawn). */
    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return data.get(SKIN) != null || data.get(CARVE) != null ? SOLID : super.getRenderTypes(state, rand, data);
    }

    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        if (!TerrainSmoothClient.aoOnSlopes && TerrainSmoothClient.slopedNow(state)) return false;
        return super.useAmbientOcclusion(state, renderType);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> carve = data.get(CARVE);
        if (carve != null && state != null) {
            if (renderType != null && renderType != RenderType.solid()) return List.of();
            return side == null ? carve : List.of();
        }
        List<BakedQuad> skin = data.get(SKIN);
        if (skin != null && state != null) {
            if (renderType != null && renderType != RenderType.solid()) return List.of();
            if (side == null) return skin;
            return side == Direction.DOWN ? originalModel.getQuads(state, side, random, ModelData.EMPTY, renderType) : List.of();
        }
        SmoothTopMesh.Top top = data.get(TOP);
        if (top != null && state != null && side == Direction.UP) return SmoothTopMesh.quads(top, originalModel, state, random, renderType);
        Long g = data.get(GEOMETRY);
        if (g == null || state == null) return super.getQuads(state, side, random, data, renderType);
        int layer = renderType == null ? 0 : RenderType.chunkBufferLayers().indexOf(renderType) + 1;
        Integer u = data.get(UNDER);
        int under = u == null ? 0 : u;
        // g: bits 0-52 (the fan's 48-52, dig R1); layer (five chunk layers) 0-2, shader pack 3, under (sides and light, 12
        // bits) 4-15 of the second half
        Key key = new Key(g, layer & 7 | (TerrainSmoothClient.shaderPack ? 8 : 0) | under << 4);
        // the top's texture orientation of the vanilla variant at this position (the renderer seeded random for it)
        int orientation = TopTexture.orientation(originalModel.getQuads(state, Direction.UP, random, ModelData.EMPTY, renderType));
        Map<Key, List<BakedQuad>[]> byKey = cache.computeIfAbsent(orientation, o -> new ConcurrentHashMap<>());
        if (byKey.size() > 32768) byKey.clear();   // distinct slopes are far fewer; bound it anyway
        List<BakedQuad>[] quads = byKey.computeIfAbsent(key, k -> build(state, g, under, renderType, orientation));
        return quads[side == null ? 6 : side.ordinal()];
    }

    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] build(BlockState state, long g, int under, @Nullable RenderType renderType, int orientation) {
        long begin = System.nanoTime();
        List<BakedQuad>[] out = new List[7];
        for (int i = 0; i < 7; i++) out[i] = List.of();
        RandomSource random = RandomSource.create(42);
        float[] c = SmoothRules.corners(g);   // nw, ne, sw, se above the floor (with a centre, dig R1: its fan's points)
        // top: one quad, or one per quarter around a centre (SmoothRules#topQuads)
        float[][] topUv = TopTexture.uv(orientation);
        List<BakedQuad> tops = new ArrayList<>();
        List<BakedQuad> up = originalModel.getQuads(state, Direction.UP, random, ModelData.EMPTY, renderType);
        for (float[][] top : SmoothRules.topQuads(c)) {
            int color = TopTexture.topColor(top);
            float[][] uv = TopTexture.uvAt(topUv, top);   // by position: fan quarters, a top split sw-ne starting at sw
            for (BakedQuad o : up) tops.add(quad(top, uv, o.getSprite(), o.getTintIndex(), Direction.UP, color, 0));
        }
        out[Direction.UP.ordinal()] = List.copyOf(tops);
        // side bands, and where a neighbour that hides nothing was counted on for the part below a band, that part too
        int underLight = LightTexture.pack(under >> 4 & 15, under >> 8 & 15);
        List<BakedQuad> free = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            float la = (g >>> (16 + 8 * s) & 15) / 4F, lb = (g >>> (20 + 8 * s) & 15) / 4F;
            float ua = c[SIDE_CORNERS[s][0]], ub = c[SIDE_CORNERS[s][1]];
            boolean hasBand = ua > la + 1e-4F || ub > lb + 1e-4F, hasUnder = (under >> s & 1) != 0;
            if (!hasBand && !hasUnder) continue;
            la = Math.min(la, ua);
            lb = Math.min(lb, ub);
            float[] a = SIDE_A[s], b = SIDE_B[s];
            List<BakedQuad> side = new ArrayList<>();
            List<BakedQuad> layers = originalModel.getQuads(state, SIDES[s], random, ModelData.EMPTY, renderType);
            if (hasBand) {
                float[][] p = {{a[0], la, a[1]}, {b[0], lb, b[1]}, {b[0], ub, b[1]}, {a[0], ua, a[1]}};
                // v measured down from the band's top edge: the side texture's top rows (the grass fringe) follow the slope
                float[][] uv = {{0, Math.min(1, ua - la)}, {1, Math.min(1, ub - lb)}, {1, 0}, {0, 0}};
                // a band reaching above the block's own cell is not hidden by the neighbour at the block's level (that
                // stops at the cell's top): unculled, or the part above would go with it (the C3.3a seams: a corner raised
                // to a turf edge beside a full block). Lit from the neighbour cell like a culled side, which is that full
                // block here: the light of the cell above (TerrainSmoothClient#underSides) is baked in.
                boolean above = ua > 1 + 1e-4F || ub > 1 + 1e-4F;
                for (BakedQuad o : layers)
                    (above ? free : side).add(quad(p, uv, o.getSprite(), o.getTintIndex(), SIDES[s], -1, above ? underLight : 0));
            }
            if (hasUnder) {
                // from the floor to the band (within this cell), the texture continuing the band's; in the culled list, so a
                // neighbour that does hide it (a full block placed there later) still culls it like a vanilla face
                float ta = Math.min(la, 1), tb = Math.min(lb, 1);
                float[][] p = {{a[0], 0, a[1]}, {b[0], 0, b[1]}, {b[0], tb, b[1]}, {a[0], ta, a[1]}};
                float[][] uv = {{0, Math.min(1, ua)}, {1, Math.min(1, ub)}, {1, Math.min(1, ub - tb)}, {0, Math.min(1, ua - ta)}};
                for (BakedQuad o : layers) side.add(quad(p, uv, o.getSprite(), o.getTintIndex(), SIDES[s], -1, underLight));
            }
            out[SIDES[s].ordinal()] = List.copyOf(side);
        }
        out[6] = List.copyOf(free);
        out[Direction.DOWN.ordinal()] = originalModel.getQuads(state, Direction.DOWN, random, ModelData.EMPTY, renderType);
        TerrainSmoothClient.quadBuilds.increment();
        TerrainSmoothClient.quadNanos.add(System.nanoTime() - begin);
        return out;
    }

    /** Vanilla-like directional shading of a tilted top: 1.0 up, 0.8 north / south, 0.6 east / west, blended by the normal. */
    static float tiltShade(float[][] p) {
        float[] n = normal(p, Direction.UP);
        return n[1] * n[1] + 0.6F * n[0] * n[0] + 0.8F * n[2] * n[2];
    }

    static int gray(float f) {
        int c = Math.max(0, Math.min(255, Math.round(255 * f)));
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    private static float[] normal(float[][] p, Direction dir) {
        float[] n = new float[3];
        for (int[] tri : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
            float ux = p[tri[1]][0] - p[tri[0]][0], uy = p[tri[1]][1] - p[tri[0]][1], uz = p[tri[1]][2] - p[tri[0]][2];
            float vx = p[tri[2]][0] - p[tri[0]][0], vy = p[tri[2]][1] - p[tri[0]][1], vz = p[tri[2]][2] - p[tri[0]][2];
            n[0] += uy * vz - uz * vy;
            n[1] += uz * vx - ux * vz;
            n[2] += ux * vy - uy * vx;
        }
        float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (l < 1e-9F) return new float[]{dir.getStepX(), dir.getStepY(), dir.getStepZ()};
        return new float[]{n[0] / l, n[1] / l, n[2] / l};
    }

    /** light: baked lightmap (LightTexture#pack), merged with the computed one by maximum; 0 for none */
    static BakedQuad quad(float[][] p, float[][] uv, TextureAtlasSprite sprite, int tint, Direction dir, int color, int light) {
        float[] n = normal(p, dir);
        int packed = (((byte) (n[0] * 127)) & 0xFF) | ((((byte) (n[1] * 127)) & 0xFF) << 8) | ((((byte) (n[2] * 127)) & 0xFF) << 16);
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            data[o] = Float.floatToRawIntBits(p[i][0]);
            data[o + 1] = Float.floatToRawIntBits(p[i][1]);
            data[o + 2] = Float.floatToRawIntBits(p[i][2]);
            data[o + 3] = color;
            data[o + 4] = Float.floatToRawIntBits(sprite.getU(uv[i][0] * 16));
            data[o + 5] = Float.floatToRawIntBits(sprite.getV(uv[i][1] * 16));
            data[o + 6] = light;
            data[o + 7] = packed;
        }
        return new BakedQuad(data, tint, dir, sprite, true, true);
    }
}
