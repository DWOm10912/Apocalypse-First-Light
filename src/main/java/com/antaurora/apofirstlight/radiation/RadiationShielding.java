package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RadiationShielding {
    public static final TagKey<Block> SHIELDING_BLOCKS = TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "radiation_shielding"));
    /** Per shielding block crossed by a ray: reinforced concrete and any other tagged block. */
    public static final double RC_TRANSMISSION = 0.35;
    /** Per block of lead shielding bricks: the dense shield, roughly two concrete blocks in one. */
    public static final double LEAD_TRANSMISSION = 0.15;
    public static final int RAY_COUNT = 14;
    public static final int MAX_DISTANCE = 8;

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
            {1, -1, 1}, {1, -1, -1}, {-1, -1, 1}, {-1, -1, -1}
    };

    private RadiationShielding() {}

    public static Sample sample(ServerLevel level, BlockPos playerPos) {
        double originX = playerPos.getX() + 0.5;
        double originY = playerPos.getY() + 0.9;
        double originZ = playerPos.getZ() + 0.5;
        double totalTransmission = 0.0;
        int hitRays = 0;
        int countedBlocks = 0;
        for (int[] direction : DIRECTIONS) {
            double transmission = 1.0;
            for (int step = 1; step <= MAX_DISTANCE && transmission > 0.01; step++) {
                BlockPos samplePos = BlockPos.containing(originX + direction[0] * step,
                        originY + direction[1] * step, originZ + direction[2] * step);
                BlockState state = level.getBlockState(samplePos);
                if (state.is(SHIELDING_BLOCKS)) {
                    transmission *= transmission(state);
                    countedBlocks++;
                }
            }
            if (transmission < 1.0) hitRays++;
            totalTransmission += transmission;
        }
        return new Sample(totalTransmission / RAY_COUNT, hitRays, countedBlocks);
    }

    /** Transmission of one shielding block ({@link #SHIELDING_BLOCKS}); 1.0 for everything else. */
    public static double transmission(BlockState state) {
        if (!state.is(SHIELDING_BLOCKS)) return 1.0;
        return state.is(AflBlocks.LEAD_SHIELDING_BRICKS.get()) ? LEAD_TRANSMISSION : RC_TRANSMISSION;
    }

    public record Sample(double transmission, int shieldingRaysHit, int shieldingBlocksCounted) {
        public double shielding() { return 1.0 - transmission; }
    }
}
