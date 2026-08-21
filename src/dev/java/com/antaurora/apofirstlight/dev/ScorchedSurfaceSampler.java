package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** DEV-only read-only sampler for final Scorched Lands surface material coverage. */
public final class ScorchedSurfaceSampler {
    private ScorchedSurfaceSampler() {
    }

    public static Result sample(ServerLevel level, BlockPos center, int size) {
        int half = size / 2;
        int startX = center.getX() - half;
        int startZ = center.getZ() - half;
        int totalColumns = size * size;
        int loadedColumns = 0;
        int scorchedColumns = 0;
        int otherBiomeColumns = 0;
        int skippedColumns = 0;
        int scorchedSoilColumns = 0;
        int fusedGroundColumns = 0;
        int otherSurfaceColumns = 0;
        int[][] fusedByDepth = new int[13][1];
        int[][] scorchedSoilByDepth = new int[13][1];
        int[][] otherByDepth = new int[13][1];

        for (int x = startX; x < startX + size; x++) {
            for (int z = startZ; z < startZ + size; z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    skippedColumns++;
                    continue;
                }
                loadedColumns++;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos surface = new BlockPos(x, surfaceY - 1, z);
                if (!level.getBiome(surface).is(AflBiomes.SCORCHED_LANDS)) {
                    otherBiomeColumns++;
                    continue;
                }
                scorchedColumns++;
                BlockState state = level.getBlockState(surface);
                if (state.is(AflBlocks.FUSED_GROUND.get())) {
                    fusedGroundColumns++;
                } else if (state.is(AflBlocks.SCORCHED_SOIL.get())) {
                    scorchedSoilColumns++;
                } else {
                    otherSurfaceColumns++;
                }
                for (int depth = 0; depth <= 12; depth++) {
                    BlockState depthState = level.getBlockState(surface.below(depth));
                    if (depthState.is(AflBlocks.FUSED_GROUND.get())) {
                        fusedByDepth[depth][0]++;
                    } else if (depthState.is(AflBlocks.SCORCHED_SOIL.get())) {
                        scorchedSoilByDepth[depth][0]++;
                    } else {
                        otherByDepth[depth][0]++;
                    }
                }
            }
        }

        double coverage = (double) loadedColumns / totalColumns;
        double soilRatio = ratio(scorchedSoilColumns, scorchedColumns);
        double fusedRatio = ratio(fusedGroundColumns, scorchedColumns);
        double otherRatio = ratio(otherSurfaceColumns, scorchedColumns);
        String representativeness = scorchedColumns < Math.max(64, totalColumns / 20)
                ? "SAMPLE_NOT_REPRESENTATIVE" : "OK";
        return new Result(size, totalColumns, loadedColumns, scorchedColumns, otherBiomeColumns,
                skippedColumns, coverage, scorchedSoilColumns, fusedGroundColumns,
                otherSurfaceColumns, soilRatio, fusedRatio, otherRatio, representativeness,
                flatten(fusedByDepth), flatten(scorchedSoilByDepth), flatten(otherByDepth));
    }

    private static double ratio(int value, int total) {
        return total == 0 ? 0.0D : (double) value / total;
    }

    private static int[] flatten(int[][] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index][0];
        }
        return result;
    }

    public record Result(int size, int totalColumns, int loadedColumns, int scorchedColumns,
                         int otherBiomeColumns, int skippedColumns, double coverage,
                         int scorchedSoilColumns, int fusedGroundColumns, int otherSurfaceColumns,
                         double scorchedSoilRatio, double fusedGroundRatio, double otherRatio,
                         String representativeness, int[] fusedByDepth, int[] scorchedSoilByDepth,
                         int[] otherByDepth) {
    }
}
