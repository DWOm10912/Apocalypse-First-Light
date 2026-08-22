package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only validators for the authoritative rendered corridor. */
public final class HighwayContinuityValidator {
    private HighwayContinuityValidator() {}

    public static CoreClearanceResult validateCoreRoadClearance(ServerLevel level,
                                                                HighwayCorridor corridor) {
        int terrainIntrusions = 0;
        int directlyAboveSurface = 0;
        for (HighwayCorridor.CoreRoadColumnSnapshot column : corridor.coreRoadColumns()) {
            int top = column.clearanceTopY(level.getMaxBuildHeight());
            if (column.capped(level.getMaxBuildHeight())) {
                // A capped column has an unvalidated remainder above the safety limit and must not report success.
                terrainIntrusions++;
            }
            for (int y = column.roadY() + 1; y <= top; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                if (isClear(level, pos)) continue;
                terrainIntrusions++;
                if (y == column.roadY() + 1) directlyAboveSurface++;
            }
        }
        return new CoreClearanceResult(terrainIntrusions, directlyAboveSurface);
    }

    public static Result validate(ServerLevel level, HighwayCorridor corridor, HighwayProfile profile) {
        int actual = 0;
        int missing = 0;
        int wrongMaterial = 0;
        int bridgeInternalNonViaduct = 0;
        int viaductOutsideStructural = 0;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.roadY(), cell.z()));
            if (state.isAir()) missing++;
            else actual++;
            if (!state.is(HighwayPalette.ASPHALT.getBlock())) wrongMaterial++;
            if (profile.isWithinResolvedBridgeSpan(cell.distance())
                    && cell.mode() != HighwayTerrainMode.VIADUCT) bridgeInternalNonViaduct++;
            if (cell.mode() == HighwayTerrainMode.VIADUCT && !cell.structuralBridge()) {
                viaductOutsideStructural++;
            }
        }

        int missingViaductDeck = 0;
        int bridgeNormalBaseIntrusions = 0;
        int elevatedNormalBaseIntrusions = 0;
        int structuralMissingConcrete = 0;
        for (HighwayCorridor.Cell cell : corridor.bridgeCells()) {
            if (!cell.structuralBridge()) continue;
            boolean missingConcrete = false;
            for (int offset = 1; offset <= 3; offset++) {
                BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.roadY() - offset, cell.z()));
                if (!state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock())) {
                    missingConcrete = true;
                    if (cell.mode() == HighwayTerrainMode.VIADUCT) missingViaductDeck++;
                }
                if (isNormalRoadBase(state)) {
                    if (cell.mode() == HighwayTerrainMode.VIADUCT) bridgeNormalBaseIntrusions++;
                    else elevatedNormalBaseIntrusions++;
                }
            }
            if (missingConcrete) structuralMissingConcrete++;
        }

        int unexpectedAirspace = 0;
        int roadFurnitureIgnored = 0;
        int remainingLogs = 0;
        int remainingLeaves = 0;
        for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
            for (int y = column.roadY() + 1;
                 y <= column.roadY() + HighwayCorridor.VERTICAL_CLEARANCE; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (isExpectedRoadFurniture(corridor, pos, state)) {
                    roadFurnitureIgnored++;
                    continue;
                }
                unexpectedAirspace++;
                if (state.is(BlockTags.LOGS)) remainingLogs++;
                if (state.is(BlockTags.LEAVES)) remainingLeaves++;
            }
        }

        int terrainAirspaceViolations = 0;
        int cutColumnsWithIntrusion = 0;
        for (HighwayCorridor.CutColumn column : corridor.cutColumns()) {
            boolean intrusion = column.capped(level.getMaxBuildHeight());
            int top = column.clearanceTopY(level.getMaxBuildHeight());
            for (int y = column.roadY() + 1; y <= top; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || isExpectedRoadFurniture(corridor, pos, state)) continue;
                terrainAirspaceViolations++;
                intrusion = true;
            }
            if (intrusion) cutColumnsWithIntrusion++;
        }

        int expectedParapet = 0;
        int actualParapet = 0;
        for (HighwayCorridor.Cell cell : corridor.bridgeCells()) {
            if (!cell.structuralBridge() || cell.role() != HighwayCorridor.Role.BRIDGE_EDGE) continue;
            expectedParapet++;
            if (level.getBlockState(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()))
                    .is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock())) actualParapet++;
        }

        int medianBarrier = 0;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (cell.role() == HighwayCorridor.Role.MEDIAN && cell.lateral() == 0
                    && level.getBlockState(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()))
                    .is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock())) medianBarrier++;
        }

        int expectedWhiteEdge = 0;
        int actualWhiteEdge = 0;
        int expectedYellowEdge = 0;
        int actualYellowEdge = 0;
        int expectedWhiteLaneDivider = 0;
        int actualWhiteLaneDivider = 0;
        int wrongMarkingType = 0;
        for (HighwayCorridor.RoadMarking marking : corridor.roadMarkings()) {
            BlockPos pos = new BlockPos(marking.x(), marking.y(), marking.z());
            BlockState state = level.getBlockState(pos);
            boolean correct = isExpectedRoadMarking(corridor, pos, state);
            switch (marking.type()) {
                case WHITE_EDGE -> {
                    expectedWhiteEdge++;
                    if (correct) actualWhiteEdge++;
                }
                case YELLOW_EDGE -> {
                    expectedYellowEdge++;
                    if (correct) actualYellowEdge++;
                }
                case WHITE_LANE_DIVIDER -> {
                    expectedWhiteLaneDivider++;
                    if (correct) actualWhiteLaneDivider++;
                }
            }
            if (!correct && !isClear(level, pos)) wrongMarkingType++;
        }

        return new Result(actual, missing, unexpectedAirspace, missingViaductDeck, wrongMaterial,
                bridgeInternalNonViaduct, bridgeNormalBaseIntrusions, elevatedNormalBaseIntrusions,
                viaductOutsideStructural, structuralMissingConcrete, cutColumnsWithIntrusion,
                terrainAirspaceViolations, unexpectedAirspace, roadFurnitureIgnored,
                expectedParapet, actualParapet, expectedParapet - actualParapet, medianBarrier,
                remainingLogs, remainingLeaves, remainingLogs + remainingLeaves,
                expectedWhiteEdge, actualWhiteEdge, expectedWhiteEdge - actualWhiteEdge,
                expectedYellowEdge, actualYellowEdge, expectedYellowEdge - actualYellowEdge,
                expectedWhiteLaneDivider, actualWhiteLaneDivider,
                expectedWhiteLaneDivider - actualWhiteLaneDivider, wrongMarkingType);
    }

    private static boolean isNormalRoadBase(BlockState state) {
        return state.is(HighwayPalette.BASE.getBlock())
                || state.is(HighwayPalette.SUB_BASE.getBlock())
                || state.is(HighwayPalette.FILL.getBlock());
    }

    private static boolean isExpectedRoadFurniture(HighwayCorridor corridor, BlockPos pos, BlockState state) {
        return (corridor.isExpectedRoadFurniture(pos.getX(), pos.getY(), pos.getZ())
                && state.is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock()))
                || isExpectedRoadMarking(corridor, pos, state);
    }

    private static boolean isExpectedRoadMarking(HighwayCorridor corridor, BlockPos pos, BlockState state) {
        HighwayCorridor.RoadMarking expected = corridor.expectedRoadMarking(
                pos.getX(), pos.getY(), pos.getZ());
        if (expected == null || !state.hasProperty(RoadMarkingBlock.FACING)
                || state.getValue(RoadMarkingBlock.FACING) != expected.facing()) return false;
        return switch (expected.type()) {
            case WHITE_EDGE -> state.is(AflBlocks.EDGE_LANE_WHITE.get());
            case YELLOW_EDGE -> state.is(AflBlocks.EDGE_LANE_YELLOW.get());
            case WHITE_LANE_DIVIDER -> state.is(AflBlocks.WHITE_LANE_DIVIDER.get());
        };
    }

    private static boolean isClear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty();
    }

    public record CoreClearanceResult(int coreRoadTerrainIntrusions,
                                      int nonFurnitureBlockDirectlyAboveSurface) {}

    public record Result(int actualSurfaceCells, int missingSurfaceCells, int clearanceViolations,
                         int missingViaductDeckCells, int wrongSurfaceMaterialCells,
                         int bridgeSpanInternalNonViaductCells, int bridgeSpanNormalBaseIntrusions,
                         int elevatedNormalBaseIntrusions, int viaductOutsideStructuralBridgeCells,
                         int structuralBridgeMissingConcreteCells,
                         int cutColumnsWithRemainingTerrainIntrusion, int terrainAirspaceViolations,
                         int unexpectedAirspaceObstructions,
                         int roadFurnitureBlocksIgnoredByAirspaceValidator,
                         int expectedParapetBlocks, int actualParapetBlocks,
                         int missingParapetBlocks, int medianBarrierBlocks,
                         int remainingRowLogs, int remainingRowLeaves,
                         int vegetationClearanceViolations,
                         int expectedWhiteEdgeMarkings, int actualWhiteEdgeMarkings,
                         int missingWhiteEdgeMarkings,
                         int expectedYellowEdgeMarkings, int actualYellowEdgeMarkings,
                         int missingYellowEdgeMarkings,
                         int expectedWhiteLaneDividerMarkings,
                         int actualWhiteLaneDividerMarkings,
                         int missingWhiteLaneDividerMarkings,
                         int wrongMarkingTypeBlocks) {
        public int airspaceClearanceViolations() { return clearanceViolations; }
    }
}
