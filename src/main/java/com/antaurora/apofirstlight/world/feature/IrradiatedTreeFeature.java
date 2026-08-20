package com.antaurora.apofirstlight.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Compact, worldgen-only damaged/dead oak and birch forms. */
public final class IrradiatedTreeFeature extends Feature<NoneFeatureConfiguration> {
    private final BlockState log;
    private final BlockState leaves;
    private final boolean dead;
    private final boolean birch;

    public IrradiatedTreeFeature(BlockState log, BlockState leaves, boolean dead, boolean birch) {
        super(NoneFeatureConfiguration.CODEC);
        this.log = log;
        this.leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
        this.dead = dead;
        this.birch = birch;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos base = context.origin();
        if (!level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK) || !level.getBlockState(base).isAir()) return false;
        int height = dead ? 4 + random.nextInt(4) : 4 + random.nextInt(3);
        for (int y = 0; y < height; y++) set(level, base.above(y), log);
        int branches = dead ? 1 + random.nextInt(3) : 1 + random.nextInt(2);
        for (int branch = 0; branch < branches; branch++) {
            int direction = random.nextInt(4);
            BlockPos start = base.above(2 + random.nextInt(Math.max(1, height - 2)));
            BlockPos end = start.relative(net.minecraft.core.Direction.from2DDataValue(direction));
            set(level, end, log);
            if (!dead && random.nextBoolean()) set(level, end.above(), leaves);
        }
        if (dead) return true;
        int radius = birch ? 1 : 2;
        BlockPos crown = base.above(height - 2);
        for (int y = 0; y < 3; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(z) > radius + 1 || random.nextFloat() > 0.55F) continue;
                    BlockPos leafPos = crown.offset(x, y, z);
                    if (level.getBlockState(leafPos).isAir()) set(level, leafPos, leaves);
                }
            }
        }
        return true;
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, 2);
    }
}
