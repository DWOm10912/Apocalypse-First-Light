package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
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
 * Chunk model of a snow layer (Terrain Smooth, 2026-10-14; docs/worldgen/terrain_smooth_v1.md U): lying on a sloped
 * natural surface top, it is drawn as a skin of its own thickness over that slope (a top through the slope's four corners
 * raised by the layer height, and the thin rim of each side) instead of vanilla's flat slab on the cell floor, which
 * floated over a lowered slope and sank into a raised one (the federal snow peaks). Elsewhere exactly the vanilla model.
 * <p>
 * Drawing only: collision and selection stay vanilla (the worldgen and weather snow is one layer, which has no
 * collision). The rims reach into the neighbour cells' height, so the skin is lit flat with the light of its own cell
 * baked in (merged by maximum, as SmoothSurfaceModel's parts below a band), never from an opaque neighbour.
 */
final class SnowSkinModel extends BakedModelWrapper<BakedModel> {
    /** the support's geometry (corner bits 0-15) and the light of the snow's cell (bits 16-23: sky << 4 | block) */
    static final ModelProperty<Long> SKIN = new ModelProperty<>();
    /** the state whose model data this thread took last, if it is skinned (for useAmbientOcclusion, as TerrainSmoothClient#slopedNow) */
    private static final ThreadLocal<BlockState> SKINNED = new ThreadLocal<>();
    private final Map<Long, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    SnowSkinModel(BakedModel original) {
        super(original);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        SKINNED.remove();
        long g = TerrainSmoothClient.supportGeometry(level, pos.below());
        if (g < 0) return base;
        int light = LevelRenderer.getLightColor(level, pos);
        SKINNED.set(state);
        TerrainSmoothClient.snowSkins.increment();
        return base.derive().with(SKIN, g & 0xFFFFL | (long) (LightTexture.sky(light) << 4 | LightTexture.block(light)) << 16).build();
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        if (!TerrainSmoothClient.aoOnSlopes && SKINNED.get() == state) return false;
        return super.useAmbientOcclusion(state, renderType);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        Long skin = data.get(SKIN);
        if (skin == null || state == null || !state.hasProperty(SnowLayerBlock.LAYERS)) return super.getQuads(state, side, random, data, renderType);
        if (side != null) return List.of();   // the whole skin is in the unculled list: its rims leave the cell
        int layers = state.getValue(SnowLayerBlock.LAYERS);
        int layer = renderType == null ? 0 : RenderType.chunkBufferLayers().indexOf(renderType) + 1;
        // skin: corners 0-15, light 16-23; layers 24-27; render layer 28-30; shader pack 31
        long key = skin | (long) layers << 24 | (long) (layer & 7) << 28 | (TerrainSmoothClient.shaderPack ? 1L << 31 : 0);
        if (cache.size() > 32768) cache.clear();
        return cache.computeIfAbsent(key, k -> build(state, skin, layers, renderType));
    }

    private List<BakedQuad> build(BlockState state, long skin, int layers, @Nullable RenderType renderType) {
        // the snow texture from the vanilla top: below eight layers it is not on the cell's top face, so vanilla lists it
        // with the unculled quads, not under UP (2026-10-14: looking under UP only drew nothing, and the support's top,
        // culled by the snow, left a hole to the sky)
        BakedQuad o = null;
        RandomSource random = RandomSource.create(42);
        for (Direction d : new Direction[]{Direction.UP, null}) {
            for (BakedQuad q : originalModel.getQuads(state, d, random, ModelData.EMPTY, renderType)) {
                if (q.getDirection() == Direction.UP) {
                    o = q;
                    break;
                }
            }
            if (o != null) break;
        }
        TextureAtlasSprite sprite = o != null ? o.getSprite() : originalModel.getParticleIcon(ModelData.EMPTY);
        int tint = o != null ? o.getTintIndex() : -1;
        float[] c = SmoothRules.corners(skin);   // above the support's floor, one block under this cell's floor
        float h = layers / 8F;
        float[] lo = new float[4], hi = new float[4];
        for (int k = 0; k < 4; k++) {
            lo[k] = c[k] - 1;
            hi[k] = lo[k] + h;
        }
        int light = LightTexture.pack((int) (skin >> 16) & 15, (int) (skin >> 20) & 15);
        List<BakedQuad> out = new ArrayList<>(5);
        float[][] top = {{0, hi[0], 0}, {0, hi[2], 1}, {1, hi[3], 1}, {1, hi[1], 0}};
        int color = TerrainSmoothClient.shaderPack ? -1 : SmoothSurfaceModel.gray(SmoothSurfaceModel.tiltShade(top));
        out.add(SmoothSurfaceModel.quad(top, new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}}, sprite, tint, Direction.UP, color, light));
        for (int s = 0; s < 4; s++) {
            int a = SmoothSurfaceModel.SIDE_CORNERS[s][0], b = SmoothSurfaceModel.SIDE_CORNERS[s][1];
            float[] pa = SmoothSurfaceModel.SIDE_A[s], pb = SmoothSurfaceModel.SIDE_B[s];
            float[][] p = {{pa[0], lo[a], pa[1]}, {pb[0], lo[b], pb[1]}, {pb[0], hi[b], pb[1]}, {pa[0], hi[a], pa[1]}};
            float[][] uv = {{0, 1}, {1, 1}, {1, 1 - h}, {0, 1 - h}};   // the texture's bottom rows, as vanilla's layer sides
            out.add(SmoothSurfaceModel.quad(p, uv, sprite, tint, SmoothSurfaceModel.SIDES[s], -1, light));
        }
        return List.copyOf(out);
    }
}
