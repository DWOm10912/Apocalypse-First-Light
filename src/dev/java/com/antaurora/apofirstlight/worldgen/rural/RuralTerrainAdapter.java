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
