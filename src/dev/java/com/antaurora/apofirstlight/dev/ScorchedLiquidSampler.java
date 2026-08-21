package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** DEV-only read-only sampler for surface-accessible Scorched Lands liquids. */
public final class ScorchedLiquidSampler {
    private static final int MAX_DEPTH = 12;
    private static final int MAX_HORIZONTAL_RADIUS = 8;
    private static final int MAX_VISITED = 256;

    private ScorchedLiquidSampler() {
    }

    public static Result sample(ServerLevel level, BlockPos center, int size) {
        int half = size / 2;
        int total = size * size;
        int loaded = 0;
        int scorched = 0;
        int directSkyWater = 0;
        int directSkyLava = 0;
        int nearSurfaceAccessibleWater = 0;
        int nearSurfaceAccessibleLava = 0;
        int deepUndergroundLiquid = 0;

        for (int x = center.getX() - half; x < center.getX() - half + size; x++) {
            for (int z = center.getZ() - half; z < center.getZ() - half + size; z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                loaded++;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos surface = new BlockPos(x, surfaceY - 1, z);
                if (!level.getBiome(surface).is(AflBiomes.SCORCHED_LANDS)) continue;
                scorched++;

                boolean columnDirectWater = false;
                boolean columnDirectLava = false;
                boolean columnAccessibleWater = false;
                boolean columnAccessibleLava = false;
                for (int depth = 0; depth <= MAX_DEPTH; depth++) {
                    BlockPos liquid = surface.below(depth);
                    BlockState state = level.getBlockState(liquid);
                    boolean water = state.is(Blocks.WATER);
                    boolean lava = state.is(Blocks.LAVA);
                    if (!water && !lava) continue;

                    if (level.canSeeSky(liquid)) {
                        columnDirectWater |= water;
                        columnDirectLava |= lava;
                    } else if (isSurfaceAccessible(level, liquid, surfaceY)) {
                        columnAccessibleWater |= water;
                        columnAccessibleLava |= lava;
                    } else {
                        deepUndergroundLiquid++;
                    }
                }
                if (columnDirectWater) directSkyWater++;
                if (columnDirectLava) directSkyLava++;
                if (columnAccessibleWater) nearSurfaceAccessibleWater++;
                if (columnAccessibleLava) nearSurfaceAccessibleLava++;
            }
        }
        int accessibleWater = directSkyWater + nearSurfaceAccessibleWater;
        int accessibleLava = directSkyLava + nearSurfaceAccessibleLava;
        return new Result(size, total, loaded, scorched, directSkyWater, directSkyLava,
                nearSurfaceAccessibleWater, nearSurfaceAccessibleLava, accessibleWater,
                accessibleLava, deepUndergroundLiquid, ratio(accessibleWater, scorched));
    }

    private static boolean isSurfaceAccessible(ServerLevel level, BlockPos liquid, int surfaceY) {
        for (Direction direction : Direction.values()) {
            BlockPos start = liquid.relative(direction);
            if (!level.getBlockState(start).isAir()) continue;
            if (connectsToSurfaceEnvelope(level, start, surfaceY)) return true;
        }
        return false;
    }

    private static boolean connectsToSurfaceEnvelope(ServerLevel level, BlockPos start, int surfaceY) {
        if (start.getY() >= surfaceY + 1) return true;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(start);
        visited.add(start.asLong());
        int minY = surfaceY - MAX_DEPTH;
        while (!queue.isEmpty() && visited.size() <= MAX_VISITED) {
            BlockPos current = queue.removeFirst();
            if (current.getY() >= surfaceY + 1) return true;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getY() < minY || next.getY() > surfaceY + 3) continue;
                if (Math.abs(next.getX() - start.getX()) > MAX_HORIZONTAL_RADIUS
                        || Math.abs(next.getZ() - start.getZ()) > MAX_HORIZONTAL_RADIUS) continue;
                if (!level.getChunkSource().hasChunk(next.getX() >> 4, next.getZ() >> 4)) continue;
                if (!level.getBlockState(next).isAir() || !visited.add(next.asLong())) continue;
                queue.addLast(next);
            }
        }
        return false;
    }

    private static double ratio(int value, int total) {
        return total == 0 ? 0.0D : (double) value / total;
    }

    public record Result(int size, int total, int loaded, int scorched, int directSkyWater,
                         int directSkyLava, int nearSurfaceAccessibleWater,
                         int nearSurfaceAccessibleLava, int surfaceAccessibleWater,
                         int surfaceAccessibleLava, int deepUndergroundLiquid,
                         double surfaceAccessibleWaterRatio) {
    }
}
