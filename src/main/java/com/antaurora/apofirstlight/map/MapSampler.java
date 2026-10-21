package com.antaurora.apofirstlight.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A chunk's map samples (AFL Map V1, docs/ui/afl_map_v1_plan.md 5.2): 8 x 8 cells of 2 m, each the ground under trees
 * and plants (walked down from the world surface), whether it is water, and its biome as the caller numbers biomes (the
 * client's MapStyle index for its live record, a map's own palette on the server). Either side.
 */
public final class MapSampler {
    private MapSampler() {}

    /** How a biome is numbered (0..253) for a sample at block (x, z). */
    @FunctionalInterface
    public interface BiomeIndex {
        int of(Holder<Biome> biome, int x, int z);
    }

    public static String id(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
    }

    public static int[] sample(Level level, LevelChunk chunk, BiomeIndex biomes) {
        int[] a = new int[MapGround.CELLS * MapGround.CELLS];
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bottom = level.getMinBuildHeight();
        for (int j = 0; j < MapGround.CELLS; j++)
            for (int i = 0; i < MapGround.CELLS; i++) {
                int lx = i * MapGround.CELL, lz = j * MapGround.CELL;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                boolean water = false;
                for (int steps = 0; steps < 48 && y > bottom; steps++) {
                    p.set(bx + lx, y, bz + lz);
                    BlockState st = chunk.getBlockState(p);
                    if (st.getFluidState().is(FluidTags.WATER)) { water = true; break; }
                    if (st.isAir() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.canBeReplaced() || !st.blocksMotion()) { y--; continue; }
                    break;
                }
                p.set(bx + lx, y, bz + lz);
                a[j * MapGround.CELLS + i] = MapGround.pack(y, biomes.of(level.getBiome(p), bx + lx, bz + lz), water);
            }
        return a;
    }
}
