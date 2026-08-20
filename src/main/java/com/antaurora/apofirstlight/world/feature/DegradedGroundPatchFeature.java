package com.antaurora.apofirstlight.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Small irregular topsoil patches; only existing grass blocks are replaced. */
public final class DegradedGroundPatchFeature extends Feature<NoneFeatureConfiguration> {
    private final BlockState replacement;
    private final int radius;

    public DegradedGroundPatchFeature(BlockState replacement, int radius) {
        super(NoneFeatureConfiguration.CODEC);
        this.replacement = replacement;
        this.radius = radius;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos center = context.origin().below();
        boolean changed = false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius + random.nextInt(2)) continue;
                BlockPos pos = center.offset(x, 0, z);
                if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(pos, replacement, 2);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
