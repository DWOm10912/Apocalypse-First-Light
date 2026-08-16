package com.antaurora.apofirstlight.infected.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class VisionOcclusionRules {
    private VisionOcclusionRules() {
    }

    public static boolean blocksVision(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof GlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock
                || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof LeavesBlock) {
            return false;
        }
        return state.canOcclude() && state.isSolidRender(level, pos);
    }
}
