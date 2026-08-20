package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class RadiationShielding {
    public static final TagKey<Block> SHIELDING_BLOCKS = TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "radiation_shielding"));
    public static final double RC_TRANSMISSION = 0.35;
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
                if (level.getBlockState(samplePos).is(SHIELDING_BLOCKS)) {
                    transmission *= RC_TRANSMISSION;
                    countedBlocks++;
                }
            }
            if (transmission < 1.0) hitRays++;
            totalTransmission += transmission;
        }
        return new Sample(totalTransmission / RAY_COUNT, hitRays, countedBlocks);
    }

    public record Sample(double transmission, int shieldingRaysHit, int shieldingBlocksCounted) {
        public double shielding() { return 1.0 - transmission; }
    }
}
