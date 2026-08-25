package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small block-state journal used to make one optional farm plot locally atomic. */
public final class FarmPlotCommitJournal {
    private final Map<BlockPos, BlockState> originalStates = new LinkedHashMap<>();

    public void captureRegion(ServerLevel level, RuralFarmPlot plot) {
        int minY = plot.surfaceYs().values().stream().min(Integer::compareTo).orElse(plot.baseY());
        int maxY = plot.baseY() + 4;
        for (int x = plot.bounds().minX() - RuralFarmPlanner.FARM_CLEARANCE_MARGIN;
             x <= plot.bounds().maxX() + RuralFarmPlanner.FARM_CLEARANCE_MARGIN; x++) {
            for (int z = plot.bounds().minZ() - RuralFarmPlanner.FARM_CLEARANCE_MARGIN;
                 z <= plot.bounds().maxZ() + RuralFarmPlanner.FARM_CLEARANCE_MARGIN; z++) {
                for (int y = minY; y <= maxY; y++) {
                    capture(level, new BlockPos(x, y, z));
                }
            }
        }
    }

    public void capture(ServerLevel level, BlockPos pos) {
        originalStates.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

    public void rollback(ServerLevel level) {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(originalStates.entrySet());
        for (int index = entries.size() - 1; index >= 0; index--) {
            Map.Entry<BlockPos, BlockState> entry = entries.get(index);
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    public void discard() {
        originalStates.clear();
    }
}
