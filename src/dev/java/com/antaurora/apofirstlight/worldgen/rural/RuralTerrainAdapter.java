package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
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

    public record PreparationResult(int changed, int logsCleared, int leavesCleared, int vegetationCleared) {
    }
}
