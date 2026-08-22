package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only post-render validator for the exact corridor used by the renderer. */
public final class HighwayContinuityValidator {
    private HighwayContinuityValidator() {}

    public static Result validate(ServerLevel level, HighwayCorridor corridor) {
        int actual = 0;
        int missing = 0;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (level.getBlockState(new BlockPos(cell.x(), cell.roadY(), cell.z())).isAir()) missing++;
            else actual++;
        }
        int violations = 0;
        for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
            for (int y = column.roadY() + 1; y <= column.roadY() + 6; y++) {
                if (!corridor.isExpectedSurface(column.x(), y, column.z())
                        && !level.getBlockState(new BlockPos(column.x(), y, column.z())).isAir()) violations++;
            }
        }
        return new Result(actual, missing, violations);
    }

    public record Result(int actualSurfaceCells, int missingSurfaceCells, int clearanceViolations) {}
}
