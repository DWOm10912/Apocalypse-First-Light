package com.antaurora.apofirstlight.dev.terrainsmooth;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
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
 * Prototype chunk model of a natural ground block (SmoothPrototype): outside the test area, or where the block stays
 * flat, exactly the vanilla model. Where it slopes:
 * <ul>
 *   <li>the top: one quad per vanilla top layer (grass top, snowy top...) through the four corner heights, texture
 *   projected from above like the vanilla top, in the UP list (lit from the cell above, culled by a block placed on it);</li>
 *   <li>each side: only the band between the neighbour's surface and this top, with the vanilla side layers (grass side
 *   and its tinted overlay) mapped down from the band's top edge, so the grass fringe follows the slope;</li>
 *   <li>the bottom: the vanilla one.</li>
 * </ul>
 * Lighting: vanilla smooth lighting samples a quad that is not flush with its face from the block's own cell, which is dark
 * inside an opaque block; sloped blocks are therefore lit flat (no AO) unless {@code ao on} is chosen. Forge 47 decides AO
 * per state, so the decision for the block being meshed is handed from getModelData to useAmbientOcclusion through a
 * thread local: both run on the same chunk-builder thread, one right after the other (vanilla and Embeddium alike).
 * <p>
 * Without a shader pack the top is darkened by its tilt like vanilla's directional shading (a 45-degree slope facing
 * east is between the top's 1.0 and the east side's 0.6); with one, the pack shades it from the normals.
 */
final class SmoothSurfaceModel extends BakedModelWrapper<BakedModel> {
    static final ModelProperty<Long> GEOMETRY = new ModelProperty<>();
    private static final ThreadLocal<boolean[]> SLOPED = ThreadLocal.withInitial(() -> new boolean[1]);
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** floor points a (left) and b (right) of each side seen from outside, and which corners stand above them */
    private static final float[][] SIDE_A = {{1, 0}, {0, 1}, {0, 0}, {1, 1}}, SIDE_B = {{0, 0}, {1, 1}, {0, 1}, {1, 0}};
    private static final int[][] SIDE_CORNERS = {{1, 0}, {2, 3}, {0, 2}, {3, 1}};
    private final Map<Long, List<BakedQuad>[]> cache = new ConcurrentHashMap<>();

    SmoothSurfaceModel(BakedModel original) {
        super(original);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        SmoothWorld.Config c = SmoothWorld.CLIENT;
        if (!c.active()) return base;   // the common case: every stone block of every chunk passes here
        boolean[] flag = SLOPED.get();
        flag[0] = false;
        if (!c.contains(pos.getX(), pos.getZ())) return base;
        long begin = System.nanoTime();
        long g = SmoothWorld.geometry(level, c, pos.getX(), pos.getY(), pos.getZ());   // read from the render region
        SmoothPrototype.dataCalls.increment();
        SmoothPrototype.dataNanos.add(System.nanoTime() - begin);
        if (g < 0) return base;
        SmoothPrototype.sloped.increment();
        flag[0] = true;
        return base.derive().with(GEOMETRY, g).build();
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        if (SmoothWorld.CLIENT.active() && !SmoothPrototype.aoOnSlopes && SLOPED.get()[0]) return false;
        return super.useAmbientOcclusion(state, renderType);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        Long g = data.get(GEOMETRY);
        if (g == null || state == null) return super.getQuads(state, side, random, data, renderType);
        int layer = renderType == null ? 0 : RenderType.chunkBufferLayers().indexOf(renderType) + 1;
        long key = g | (long) layer << 48 | (SmoothPrototype.shaderPack ? 1L << 56 : 0);
        List<BakedQuad>[] quads = cache.computeIfAbsent(key, k -> build(state, g, renderType));
        return quads[side == null ? 6 : side.ordinal()];
    }

    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] build(BlockState state, long g, @Nullable RenderType renderType) {
        long begin = System.nanoTime();
        List<BakedQuad>[] out = new List[7];
        for (int i = 0; i < 7; i++) out[i] = List.of();
        RandomSource random = RandomSource.create(42);
        float[] c = SmoothRules.corners(g);   // nw, ne, sw, se above the floor
        // top
        float[][] top = {{0, c[0], 0}, {0, c[2], 1}, {1, c[3], 1}, {1, c[1], 0}};
        float[][] topUv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        int color = SmoothPrototype.shaderPack ? -1 : gray(tiltShade(top));
        List<BakedQuad> tops = new ArrayList<>();
        for (BakedQuad o : originalModel.getQuads(state, Direction.UP, random, ModelData.EMPTY, renderType))
            tops.add(quad(top, topUv, o.getSprite(), o.getTintIndex(), Direction.UP, color));
        out[Direction.UP.ordinal()] = List.copyOf(tops);
        // side bands
        for (int s = 0; s < 4; s++) {
            float la = (g >>> (16 + 8 * s) & 15) / 4F, lb = (g >>> (20 + 8 * s) & 15) / 4F;
            float ua = c[SIDE_CORNERS[s][0]], ub = c[SIDE_CORNERS[s][1]];
            if (ua <= la + 1e-4F && ub <= lb + 1e-4F) continue;
            la = Math.min(la, ua);
            lb = Math.min(lb, ub);
            float[] a = SIDE_A[s], b = SIDE_B[s];
            float[][] p = {{a[0], la, a[1]}, {b[0], lb, b[1]}, {b[0], ub, b[1]}, {a[0], ua, a[1]}};
            // v measured down from the band's top edge: the side texture's top rows (the grass fringe) follow the slope
            float[][] uv = {{0, Math.min(1, ua - la)}, {1, Math.min(1, ub - lb)}, {1, 0}, {0, 0}};
            List<BakedQuad> band = new ArrayList<>();
            for (BakedQuad o : originalModel.getQuads(state, SIDES[s], random, ModelData.EMPTY, renderType))
                band.add(quad(p, uv, o.getSprite(), o.getTintIndex(), SIDES[s], -1));
            out[SIDES[s].ordinal()] = List.copyOf(band);
        }
        out[Direction.DOWN.ordinal()] = originalModel.getQuads(state, Direction.DOWN, random, ModelData.EMPTY, renderType);
        SmoothPrototype.quadBuilds.increment();
        SmoothPrototype.quadNanos.add(System.nanoTime() - begin);
        return out;
    }

    /** Vanilla-like directional shading of a tilted top: 1.0 up, 0.8 north / south, 0.6 east / west, blended by the normal. */
    private static float tiltShade(float[][] p) {
        float[] n = normal(p, Direction.UP);
        return n[1] * n[1] + 0.6F * n[0] * n[0] + 0.8F * n[2] * n[2];
    }

    private static int gray(float f) {
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

    private static BakedQuad quad(float[][] p, float[][] uv, TextureAtlasSprite sprite, int tint, Direction dir, int color) {
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
            data[o + 6] = 0;
            data[o + 7] = packed;
        }
        return new BakedQuad(data, tint, dir, sprite, true, true);
    }
}
