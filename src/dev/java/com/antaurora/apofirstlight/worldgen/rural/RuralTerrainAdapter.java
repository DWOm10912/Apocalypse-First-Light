package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Small per-lot terrain preparation. It never flattens the reservation envelope. */
public final class RuralTerrainAdapter {
    private RuralTerrainAdapter() {
    }

    public static PreparationResult prepare(ServerLevel level, RuralPlan.Lot lot) {
        int changed = 0;
        int logsCleared = 0;
        int leavesCleared = 0;
        int vegetationCleared = 0;
        BoundingBox box = lot.bounds();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                RuralTerrainSampler.Sample sample = RuralTerrainSampler.sample(level, x, z);
                if (!sample.valid() || sample.water()) continue;
                int surfaceY = sample.surfaceY();
                int fillDepth = lot.baseY() - surfaceY;
                if (fillDepth > 0 && fillDepth <= RuralGenerator.MAX_LOT_FILL_DEPTH) {
                    for (int y = surfaceY; y < lot.baseY(); y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 3);
                        changed++;
                    }
                }
            }
        }

        BoundingBox clearance = new BoundingBox(box.minX() - RuralGenerator.CLEARANCE_MARGIN,
                lot.baseY(), box.minZ() - RuralGenerator.CLEARANCE_MARGIN,
                box.maxX() + RuralGenerator.CLEARANCE_MARGIN,
                box.maxY() + RuralGenerator.CLEARANCE_TOP_MARGIN,
                box.maxZ() + RuralGenerator.CLEARANCE_MARGIN);
        for (int x = clearance.minX(); x <= clearance.maxX(); x++) {
            for (int z = clearance.minZ(); z <= clearance.maxZ(); z++) {
                for (int y = clearance.minY(); y <= clearance.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (isVegetation(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        changed++;
                        if (state.is(BlockTags.LOGS)) logsCleared++;
                        else if (state.is(BlockTags.LEAVES)) leavesCleared++;
                        else vegetationCleared++;
                    }
                }
            }
        }
        return new PreparationResult(changed, logsCleared, leavesCleared, vegetationCleared);
    }

    /**
     * Chunk-safe variant used by the native StructurePiece path. Every read and write is
     * bounded by the current generation chunk box; it never requests another chunk.
     */
    public static PreparationResult prepare(WorldGenLevel level, RuralPlan.Lot lot, BoundingBox chunkBox) {
        PreparationStats stats = new PreparationStats();
        BoundingBox box = lot.bounds();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (x < chunkBox.minX() || x > chunkBox.maxX()
                        || z < chunkBox.minZ() || z > chunkBox.maxZ()) continue;
                RuralTerrainSampler.Sample sample = RuralTerrainSampler.sample(level, x, z);
                if (!sample.valid() || sample.water()) continue;
                adjustColumn(level, x, z, sample.surfaceY(), lot.baseY(), chunkBox, false, stats);
            }
        }

        applyBlendRing(level, lot, chunkBox, stats);

        BoundingBox clearance = new BoundingBox(box.minX() - RuralGenerator.CLEARANCE_MARGIN,
                lot.baseY(), box.minZ() - RuralGenerator.CLEARANCE_MARGIN,
                box.maxX() + RuralGenerator.CLEARANCE_MARGIN,
                box.maxY() + RuralGenerator.CLEARANCE_TOP_MARGIN,
                box.maxZ() + RuralGenerator.CLEARANCE_MARGIN);
        for (int x = clearance.minX(); x <= clearance.maxX(); x++) {
            for (int z = clearance.minZ(); z <= clearance.maxZ(); z++) {
                for (int y = clearance.minY(); y <= clearance.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunkBox.isInside(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!isVegetation(state)) continue;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    stats.changed++;
                    if (state.is(BlockTags.LOGS)) stats.logsCleared++;
                    else if (state.is(BlockTags.LEAVES)) stats.leavesCleared++;
                    else stats.vegetationCleared++;
                }
            }
        }
        return stats.result();
    }

    private static void applyBlendRing(WorldGenLevel level, RuralPlan.Lot lot, BoundingBox chunkBox,
                                       PreparationStats stats) {
        final int radius = 2;
        BoundingBox box = lot.bounds();
        for (int x = box.minX() - radius; x <= box.maxX() + radius; x++) {
            for (int z = box.minZ() - radius; z <= box.maxZ() + radius; z++) {
                int distance = distanceToBox(x, z, box);
                if (distance < 1 || distance > radius
                        || x < chunkBox.minX() || x > chunkBox.maxX()
                        || z < chunkBox.minZ() || z > chunkBox.maxZ()) continue;
                RuralTerrainSampler.Sample sample = RuralTerrainSampler.sample(level, x, z);
                if (!sample.valid() || sample.water()) continue;
                int difference = sample.surfaceY() - lot.baseY();
                if (Math.abs(difference) > RuralGenerator.MAX_LOT_CORRECTION) continue;
                int targetY = lot.baseY() + Math.round(difference * (distance / 3.0F));
                adjustColumn(level, x, z, sample.surfaceY(), targetY, chunkBox, true, stats);
            }
        }
    }

    private static int distanceToBox(int x, int z, BoundingBox box) {
        int dx = x < box.minX() ? box.minX() - x : x > box.maxX() ? x - box.maxX() : 0;
        int dz = z < box.minZ() ? box.minZ() - z : z > box.maxZ() ? z - box.maxZ() : 0;
        return Math.max(dx, dz);
    }

    private static void adjustColumn(WorldGenLevel level, int x, int z, int surfaceY, int targetY,
                                     BoundingBox chunkBox, boolean blend, PreparationStats stats) {
        int difference = targetY - surfaceY;
        if (difference > RuralGenerator.MAX_LOT_CORRECTION
                || difference < -RuralGenerator.MAX_LOT_CORRECTION) return;
        if (difference > 0) {
            stats.fillBlocks += difference;
            stats.maxFillDepth = Math.max(stats.maxFillDepth, difference);
            for (int y = surfaceY; y < targetY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!chunkBox.isInside(pos)) continue;
                if (setIfDifferent(level, pos, Blocks.DIRT.defaultBlockState())) stats.changed++;
            }
            if (blend) {
                BlockPos surface = new BlockPos(x, targetY - 1, z);
                if (chunkBox.isInside(surface)
                        && setIfDifferent(level, surface, Blocks.GRASS_BLOCK.defaultBlockState())) stats.changed++;
                stats.exposedFillSurfaceBlocks++;
            }
        } else if (difference < 0) {
            int depth = -difference;
            stats.cutBlocks += depth;
            stats.maxCutDepth = Math.max(stats.maxCutDepth, depth);
            for (int y = targetY; y < surfaceY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!chunkBox.isInside(pos) || !isCuttable(level.getBlockState(pos))) continue;
                if (setIfDifferent(level, pos, Blocks.AIR.defaultBlockState())) stats.changed++;
            }
        }
        if (blend && difference != 0) stats.terrainBlendBlocks += Math.abs(difference);
    }

    private static boolean setIfDifferent(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).equals(state)) return false;
        level.setBlock(pos, state, 3);
        return true;
    }

    private static boolean isCuttable(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.CLAY) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || isVegetation(state);
    }

    /** Chunk-safe farm preparation for the native StructurePiece path. */
    public static PreparationResult prepare(WorldGenLevel level, RuralFarmPlot plot, BoundingBox chunkBox) {
        int changed = 0;
        int logsCleared = 0;
        int leavesCleared = 0;
        int vegetationCleared = 0;
        BoundingBox box = plot.bounds();
        for (int x = box.minX() - RuralFarmPlanner.FARM_CLEARANCE_MARGIN;
             x <= box.maxX() + RuralFarmPlanner.FARM_CLEARANCE_MARGIN; x++) {
            for (int z = box.minZ() - RuralFarmPlanner.FARM_CLEARANCE_MARGIN;
                 z <= box.maxZ() + RuralFarmPlanner.FARM_CLEARANCE_MARGIN; z++) {
                int surfaceY = plot.surfaceYs().getOrDefault(BlockPos.asLong(x, 0, z), plot.baseY());
                int minY = Math.min(surfaceY, plot.baseY());
                int maxY = Math.max(surfaceY, plot.baseY()) + 4;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunkBox.isInside(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!isVegetation(state)) continue;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    changed++;
                    if (state.is(BlockTags.LOGS)) logsCleared++;
                    else if (state.is(BlockTags.LEAVES)) leavesCleared++;
                    else vegetationCleared++;
                }
            }
        }
        for (RuralFarmPlot.Cell cell : plot.cells()) {
            int surfaceY = plot.surfaceYs().getOrDefault(cell.key(), plot.baseY());
            if (surfaceY < plot.baseY()) {
                for (int y = surfaceY; y < plot.baseY(); y++) {
                    BlockPos pos = new BlockPos(cell.x(), y, cell.z());
                    if (!chunkBox.isInside(pos)) continue;
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                    changed++;
                }
            } else if (surfaceY > plot.baseY()) {
                for (int y = plot.baseY(); y < surfaceY; y++) {
                    BlockPos pos = new BlockPos(cell.x(), y, cell.z());
                    if (!chunkBox.isInside(pos)) continue;
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        changed++;
                    }
                }
            }
        }
        return new PreparationResult(changed, logsCleared, leavesCleared, vegetationCleared);
    }

    /** Clears only the planned farm footprint and its one-block vegetation margin. */
    public static PreparationResult prepare(ServerLevel level, RuralFarmPlot plot) {
        int changed = 0;
        int logsCleared = 0;
        int leavesCleared = 0;
        int vegetationCleared = 0;
        BoundingBox box = plot.bounds();
        for (int x = box.minX() - RuralFarmPlanner.FARM_CLEARANCE_MARGIN;
             x <= box.maxX() + RuralFarmPlanner.FARM_CLEARANCE_MARGIN; x++) {
            for (int z = box.minZ() - RuralFarmPlanner.FARM_CLEARANCE_MARGIN;
                 z <= box.maxZ() + RuralFarmPlanner.FARM_CLEARANCE_MARGIN; z++) {
                int surfaceY = plot.surfaceYs().getOrDefault(BlockPos.asLong(x, 0, z), plot.baseY());
                int minY = Math.min(surfaceY, plot.baseY());
                int maxY = Math.max(surfaceY, plot.baseY()) + 4;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!isVegetation(state)) continue;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    changed++;
                    if (state.is(BlockTags.LOGS)) logsCleared++;
                    else if (state.is(BlockTags.LEAVES)) leavesCleared++;
                    else vegetationCleared++;
                }
            }
        }
        for (RuralFarmPlot.Cell cell : plot.cells()) {
            int surfaceY = plot.surfaceYs().getOrDefault(cell.key(), plot.baseY());
            if (surfaceY < plot.baseY()) {
                for (int y = surfaceY; y < plot.baseY(); y++) {
                    level.setBlock(new BlockPos(cell.x(), y, cell.z()), Blocks.DIRT.defaultBlockState(), 3);
                    changed++;
                }
            } else if (surfaceY > plot.baseY()) {
                for (int y = plot.baseY(); y < surfaceY; y++) {
                    BlockPos pos = new BlockPos(cell.x(), y, cell.z());
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        changed++;
                    }
                }
            }
        }
        return new PreparationResult(changed, logsCleared, leavesCleared, vegetationCleared);
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof VineBlock
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.COBWEB);
    }

    private static final class PreparationStats {
        private int changed;
        private int logsCleared;
        private int leavesCleared;
        private int vegetationCleared;
        private int cutBlocks;
        private int fillBlocks;
        private int maxCutDepth;
        private int maxFillDepth;
        private int terrainBlendBlocks;
        private int exposedFillSurfaceBlocks;

        private PreparationResult result() {
            return new PreparationResult(changed, logsCleared, leavesCleared, vegetationCleared,
                    cutBlocks, fillBlocks, maxCutDepth, maxFillDepth, terrainBlendBlocks,
                    exposedFillSurfaceBlocks);
        }
    }

    public record PreparationResult(int changed, int logsCleared, int leavesCleared, int vegetationCleared,
                                    int cutBlocks, int fillBlocks, int maxCutDepth, int maxFillDepth,
                                    int terrainBlendBlocks, int exposedFillSurfaceBlocks) {
        public PreparationResult(int changed, int logsCleared, int leavesCleared, int vegetationCleared) {
            this(changed, logsCleared, leavesCleared, vegetationCleared, 0, 0, 0, 0, 0, 0);
        }
    }
}
