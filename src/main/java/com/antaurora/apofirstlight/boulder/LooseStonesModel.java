package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.client.TerrainSmoothClient;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rock R1b: the chunk model of {@code loose_stones} (docs/gameplay/rock_r1_stones_boulders_design.md 2.2, 11.3).
 * <ul>
 * <li>The layout: the field stone first, then the flint, then the pebbles, each at a point of the cell and a heading drawn
 * from the cell's position and turn (a few tries to keep them apart), its shape one of its kind's (LooseStoneData).</li>
 * <li>The ground: each stone stands on the ground under it, the lowest of its centre and four points around it, sunk a
 * little (a stone lies in the ground, not on it): on an edited column the earthwork's skin (Earthwork#surfaceAt), on a
 * smoothed slope its sloped top (TerrainSmoothClient#groundCorners), else the cell floor. Without model data (a voxel or
 * map view, other mods) the cell floor.</li>
 * <li>Drawing as the boulders (BoulderModel): solid layer, flat light from the cell, no ambient occlusion; without a shader
 * pack the vanilla direction shading baked into the vertex colour by the normal.</li>
 * </ul>
 */
final class LooseStonesModel implements IDynamicBakedModel {
    private static final ModelProperty<List<BakedQuad>> QUADS = new ModelProperty<>();
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ResourceLocation[] SPRITES = new ResourceLocation[LooseStoneData.SPRITES.length];
    static {
        for (int i = 0; i < SPRITES.length; i++) SPRITES[i] = new ResourceLocation("apocalypse_firstlight", "block/boulder/" + LooseStoneData.SPRITES[i]);
    }
    /** How deep a stone lies in the ground, of its height. */
    private static final float SINK = 0.15F;
    private static final Map<BlockState, List<BakedQuad>> FLAT = new ConcurrentHashMap<>();

    static void clearCache() { FLAT.clear(); }

    /** The ground under (fx, fz) of the cell, above the cell floor (blocks). */
    private interface Ground {
        float at(float fx, float fz);
    }

    private static final Ground FLOOR = (fx, fz) -> 0;

    private static Ground ground(BlockAndTintGetter region, BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (level != null && Earthwork.editedTop(level, x, z) != Integer.MIN_VALUE)
            return (fx, fz) -> {
                double s = Earthwork.surfaceAt(level, x + fx, z + fz);
                return Double.isNaN(s) ? 0 : (float) Mth.clamp(s - y, -1, 1);
            };
        float[] c = TerrainSmoothClient.groundCorners(region, pos.below());
        if (c == null) return FLOOR;
        return (fx, fz) -> (float) Mth.clamp(SmoothRules.surface(c, fx, fz) - 1, -1, 1);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        if (!LooseStones.is(state)) return data;
        return ModelData.builder().with(QUADS, build(state, pos.asLong(), ground(level, pos))).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return SOLID;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (side != null || renderType != null && renderType != RenderType.solid()) return List.of();
        List<BakedQuad> q = data.get(QUADS);
        if (q != null) return q;
        if (state == null || !LooseStones.is(state)) return List.of();
        return FLAT.computeIfAbsent(state, s -> build(s, 0, FLOOR));
    }

    // ------------------------------------------------------------------------------------------------ layout

    /** SplitMix64 over the cell: the same stones in the same places on every rebuild. */
    private static final class Rng {
        long s;

        Rng(long seed) { s = seed; }

        float next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return ((z ^ (z >>> 31)) >>> 40) / (float) (1 << 24);
        }
    }

    private static List<BakedQuad> build(BlockState state, long cell, Ground ground) {
        var data = LooseStoneData.get();
        boolean river = state.getValue(LooseStones.KIND) == LooseStones.Kind.RIVER;
        var kinds = new ArrayList<Integer>(6);
        if (state.getValue(LooseStones.COBBLE)) kinds.add(river ? LooseStoneData.COBBLE_RIVER : LooseStoneData.COBBLE_FIELD);
        if (state.getValue(LooseStones.FLINT)) kinds.add(river ? LooseStoneData.FLINT_RIVER : LooseStoneData.FLINT_FIELD);
        for (int i = 0; i < state.getValue(LooseStones.STONES); i++) kinds.add(river ? LooseStoneData.PEBBLE_RIVER : LooseStoneData.PEBBLE_FIELD);
        Rng rng = new Rng(cell * 0xD1B54A32D192ED03L + state.getValue(LooseStones.TURN) * 0x632BE59BD9B4E019L);
        int shade = AflShaderCompat.shaderPackInUse() ? 0 : 1;
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[SPRITES.length];
        for (int i = 0; i < sprites.length; i++) sprites[i] = atlas.apply(SPRITES[i]);
        float[] px = new float[kinds.size()], pz = new float[kinds.size()], pr = new float[kinds.size()];
        var out = new ArrayList<BakedQuad>(kinds.size() * 160);
        for (int i = 0; i < kinds.size(); i++) {
            int k = kinds.get(i), shape = Math.min(data.byKind[k].length - 1, (int) (rng.next() * data.byKind[k].length));
            var stone = data.byKind[k][shape];
            float r = data.radius[k][shape], lo = Math.min(0.5F, r + 0.05F), span = 1 - 2 * lo;
            float x = 0.5F, z = 0.5F;
            for (int t = 0; t < 10; t++) {
                x = lo + rng.next() * span;
                z = lo + rng.next() * span;
                boolean clear = true;
                for (int j = 0; j < i && clear; j++) clear = Mth.square(x - px[j]) + Mth.square(z - pz[j]) > Mth.square(r + pr[j] + 0.01F);
                if (clear) break;
            }
            px[i] = x; pz[i] = z; pr[i] = r;
            float yaw = rng.next() * Mth.TWO_PI, d = r * 0.7F;
            float floor = Math.min(ground.at(x, z), Math.min(Math.min(ground.at(clamp(x - d), z), ground.at(clamp(x + d), z)),
                    Math.min(ground.at(x, clamp(z - d)), ground.at(x, clamp(z + d)))));
            float base = floor - stone.height * SINK;
            for (int q = 0; q < stone.quads; q++) out.add(bake(stone, q, x, base, z, Mth.cos(yaw), Mth.sin(yaw), sprites[stone.sprite[q]], shade));
        }
        return List.copyOf(out);
    }

    private static float clamp(float f) { return Mth.clamp(f, 0.001F, 0.999F); }

    /** One quad of a stone turned (c, s) about its base centre at (x, y, z) in the cell. Layout: DefaultVertexFormat.BLOCK. */
    private static BakedQuad bake(LooseStoneData.Stone st, int q, float x, float y, float z, float c, float s, TextureAtlasSprite sprite, int shade) {
        float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
        int[] data = new int[32];
        float fx = 0, fy = 0, fz = 0;
        for (int i = 0; i < 4; i++) {
            int o = i * 8, p = q * 12 + i * 3;
            float sx = st.pos[p], sy = st.pos[p + 1], sz = st.pos[p + 2];
            float nx0 = st.normal[p] / 127F, ny = st.normal[p + 1] / 127F, nz0 = st.normal[p + 2] / 127F;
            float nx = nx0 * c - nz0 * s, nz = nx0 * s + nz0 * c;
            fx += nx; fy += ny; fz += nz;
            data[o] = Float.floatToRawIntBits(x + sx * c - sz * s);
            data[o + 1] = Float.floatToRawIntBits(y + sy);
            data[o + 2] = Float.floatToRawIntBits(z + sx * s + sz * c);
            float light = shade == 1 ? nx * nx * 0.6F + nz * nz * 0.8F + ny * ny * (ny >= 0 ? 1.0F : 0.5F) : 1;
            int g = Math.max(0, Math.min(255, Math.round(light * 255)));
            data[o + 3] = 0xFF000000 | g << 16 | g << 8 | g;               // ABGR
            data[o + 4] = Float.floatToRawIntBits(u0 + du * st.uv[q * 8 + i * 2]);
            data[o + 5] = Float.floatToRawIntBits(v0 + dv * st.uv[q * 8 + i * 2 + 1]);
            data[o + 6] = 0;                                                // the chunk renderer's light
            data[o + 7] = (((byte) Math.round(nx * 127)) & 0xFF) | (((byte) Math.round(ny * 127)) & 0xFF) << 8 | (((byte) Math.round(nz * 127)) & 0xFF) << 16;
        }
        return new BakedQuad(data, -1, Direction.getNearest(fx, fy, fz), sprite, shade == 0, false);
    }

    // -------------------------------------------------------------------------------------------- the rest

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SPRITES[0]);
    }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
