package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.RoadChunkPass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * C3.3a group B (docs/worldgen/federal_highways_c3.md 11.11): the plants that grow on dirt grow on graded_turf (sloped
 * states included), saplings do not, and the road guard's plant rule says the same.
 */
@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
public class GradedTurfGameTests {
    @GameTest(template = "network_empty", timeoutTicks = 40)
    public static void plantsGrowOnGradedTurf(GameTestHelper h) {
        GradedTurfBlock turf = (GradedTurfBlock) AflBlocks.GRADED_TURF.get();
        BlockState[] grounds = {turf.defaultBlockState(), turf.with(2, 5, 0, 3, false), turf.with(4, 8, 6, 2, true)};
        BlockState[] plants = {Blocks.GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState()};
        for (int i = 0; i < grounds.length; i++) {
            BlockPos g = new BlockPos(1 + 2 * i, 1, 1), above = g.above();
            h.setBlock(g, grounds[i]);
            for (BlockState p : plants)
                if (!p.canSurvive(h.getLevel(), h.absolutePos(above)))
                    throw new IllegalStateException(p + " cannot stand on " + grounds[i]);
            if (Blocks.OAK_SAPLING.defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(above)))
                throw new IllegalStateException("a sapling stands on " + grounds[i]);
            // placed, a neighbour update keeps it (Forge's canSustainPlant asks the same rule)
            h.setBlock(above, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            h.setBlock(above.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            h.setBlock(above.east(), Blocks.STONE.defaultBlockState());
        }
        if (!RoadChunkPass.plantOnTurf(Blocks.POPPY.defaultBlockState()) || RoadChunkPass.plantOnTurf(Blocks.BIRCH_SAPLING.defaultBlockState())
                || RoadChunkPass.plantOnTurf(Blocks.OAK_LOG.defaultBlockState()) || RoadChunkPass.plantOnTurf(Blocks.SNOW.defaultBlockState()))
            throw new IllegalStateException("guard plant rule");
        // the block below never draws its top under the turf (a top triangle on the floor fought it); faces beside it do
        BlockPos dirt = new BlockPos(1, 1, 3), turfAbove = dirt.above(), side = dirt.east().above();
        h.setBlock(dirt, Blocks.DIRT);
        h.setBlock(turfAbove, turf.with(0, 8, 0, 0, false));
        h.setBlock(side, Blocks.DIRT);
        var lv = h.getLevel();
        if (Block.shouldRenderFace(Blocks.DIRT.defaultBlockState(), lv, h.absolutePos(dirt), Direction.UP, h.absolutePos(turfAbove)))
            throw new IllegalStateException("the block under graded_turf draws its top face");
        if (!Block.shouldRenderFace(Blocks.DIRT.defaultBlockState(), lv, h.absolutePos(side), Direction.WEST, h.absolutePos(turfAbove)))
            throw new IllegalStateException("graded_turf hides a neighbour's side face");
        h.runAfterDelay(5, () -> {
            for (int i = 0; i < grounds.length; i++) {
                BlockPos above = new BlockPos(1 + 2 * i, 2, 1);
                h.assertBlockPresent(Blocks.TALL_GRASS, above);
                h.assertBlockPresent(Blocks.TALL_GRASS, above.above());
            }
            h.succeed();
        });
    }
}
