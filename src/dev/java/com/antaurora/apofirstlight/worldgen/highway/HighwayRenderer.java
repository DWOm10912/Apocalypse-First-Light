package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** V1A.1 renderer: one corridor mask, one ordered construction pass. */
public final class HighwayRenderer {
    private static final int VERTICAL_CLEARANCE = 6;
    private static final int PIER_SPACING = 28;

    private HighwayRenderer() {}

    public static HighwayRenderStats render(ServerLevel level, HighwayProfile profile, HighwayEditSession edit) {
        HighwayCorridor corridor = HighwayCorridor.build(profile.plan(), profile);
        HighwayRenderStats stats = new HighwayRenderStats();
        stats.corridorCellCount = corridor.cells().size();
        stats.expectedSurfaceCells = corridor.expectedSurfaceCells();
        for (HighwayCorridor.Cell cell : corridor.cells()) stats.addCellMode(cell.mode());
        clearRow(level, edit, stats, corridor);
        clearCutEnvelope(level, edit, stats, corridor);
        placeRoadStructure(level, edit, stats, corridor);
        placePiers(level, edit, stats, corridor);
        HighwayContinuityValidator.Result validation = HighwayContinuityValidator.validate(level, corridor);
        stats.actualSurfaceCells = validation.actualSurfaceCells();
        stats.missingSurfaceCells = validation.missingSurfaceCells();
        stats.clearanceViolations = validation.clearanceViolations();
        return stats;
    }

    private static void clearRow(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                 HighwayCorridor corridor) {
        for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
            for (int y = column.roadY() + 1; y <= column.roadY() + VERTICAL_CLEARANCE; y++) {
                if (clear(level, edit, new BlockPos(column.x(), y, column.z()))) stats.blocksCleared++;
            }
        }
    }

    private static void clearCutEnvelope(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                          HighwayCorridor corridor) {
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (cell.mode() != HighwayTerrainMode.CUT) continue;
            int top = Math.min(cell.terrainY() + 1, cell.roadY() + HighwayProfile.MAX_CUT_DEPTH);
            for (int y = cell.roadY() + VERTICAL_CLEARANCE + 1; y <= top; y++) {
                for (int dx = -HighwayCorridor.ROW_MARGIN; dx <= HighwayCorridor.ROW_MARGIN; dx++) {
                    for (int dz = -HighwayCorridor.ROW_MARGIN; dz <= HighwayCorridor.ROW_MARGIN; dz++) {
                        if (clear(level, edit, new BlockPos(cell.x() + dx, y, cell.z() + dz))) {
                            stats.blocksCleared++;
                            stats.cutBlocks++;
                        }
                    }
                }
            }
        }
    }

    private static void placeRoadStructure(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                           HighwayCorridor corridor) {
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            BlockPos surface = new BlockPos(cell.x(), cell.roadY(), cell.z());
            BlockState surfaceState = surfaceState(cell.role());
            if (edit.set(surface, surfaceState)) {
                stats.blocksPlaced++;
                if (cell.mode() == HighwayTerrainMode.VIADUCT) stats.viaductBlocks++;
            }
            if (edit.set(surface.below(), HighwayPalette.BASE)) {
                stats.blocksPlaced++;
                if (cell.mode() == HighwayTerrainMode.VIADUCT) stats.viaductBlocks++;
            }
            if (edit.set(surface.below(2), HighwayPalette.SUB_BASE)) {
                stats.blocksPlaced++;
                if (cell.mode() == HighwayTerrainMode.VIADUCT) stats.viaductBlocks++;
            }
            if (cell.mode() == HighwayTerrainMode.FILL) {
                int bottom = Math.max(level.getMinBuildHeight(), cell.roadY() - HighwayProfile.MAX_FILL_DEPTH);
                for (int y = cell.roadY() - 3; y >= Math.max(bottom, cell.terrainY()); y--) {
                    if (edit.set(new BlockPos(cell.x(), y, cell.z()), HighwayPalette.FILL)) {
                        stats.blocksPlaced++;
                        stats.fillBlocks++;
                    }
                }
            }
        }
    }

    private static void placePiers(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                   HighwayCorridor corridor) {
        double lastPier = -PIER_SPACING;
        Map<Long, HighwayCorridor.Cell> centerCells = new HashMap<>();
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            centerCells.putIfAbsent(columnKey(cell.x(), cell.z()), cell);
        }
        for (HighwayCorridor.CenterCell center : corridor.centerline()) {
            HighwayCorridor.Cell cell = centerCells.get(columnKey(center.x(), center.z()));
            if (cell == null || cell.mode() != HighwayTerrainMode.VIADUCT) continue;
            if (center.distance() - lastPier >= PIER_SPACING) {
                int foundation = findFoundation(level, center.x(), center.z(), cell.terrainY());
                for (int y = cell.roadY() - 3; y > foundation; y--) {
                    if (edit.set(new BlockPos(center.x(), y, center.z()), HighwayPalette.PIER)) {
                        stats.blocksPlaced++;
                        stats.viaductBlocks++;
                        stats.piersPlaced++;
                    }
                }
                lastPier = center.distance();
            }
        }
    }

    private static long columnKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static int findFoundation(ServerLevel level, int x, int z, int terrainY) {
        int bottom = Math.max(level.getMinBuildHeight(), terrainY - 64);
        for (int y = terrainY - 1; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).isEmpty()
                    && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y;
        }
        return bottom;
    }

    private static boolean clear(ServerLevel level, HighwayEditSession edit, BlockPos pos) {
        return !level.getBlockState(pos).isAir() && edit.set(pos, Blocks.AIR.defaultBlockState());
    }

    private static BlockState surfaceState(HighwayCorridor.Role role) {
        return switch (role) {
            case OUTER_SHOULDER, INNER_SHOULDER_LEFT, INNER_SHOULDER_RIGHT -> HighwayPalette.SHOULDER;
            case MEDIAN -> HighwayPalette.MEDIAN;
            case CARRIAGEWAY_LEFT, CARRIAGEWAY_RIGHT -> HighwayPalette.CARRIAGEWAY;
        };
    }
}
