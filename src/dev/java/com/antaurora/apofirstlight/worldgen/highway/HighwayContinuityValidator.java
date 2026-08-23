package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.block.RoadMarkingStepConnectorBlock;
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
            if (corridor.isTunnelStation(column.distance())) continue;
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
            if (!state.is(HighwayPalette.ASPHALT.getBlock())
                    && !(corridor.isExpectedOuterEdge(cell.x(), cell.roadY(), cell.z())
                    && state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock()))) {
                wrongMaterial++;
            }
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
            if (corridor.isTunnelArea(column.x(), column.z())) continue;
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
            if (corridor.isTunnelArea(column.x(), column.z())) continue;
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
            BlockState medianState = level.getBlockState(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()));
            if (cell.role() == HighwayCorridor.Role.MEDIAN && cell.lateral() == 0
                    && isMedianBarrier(medianState)) {
                medianBarrier++;
            }
        }

        int expectedMedianStepConnector = corridor.medianStepConnectorPositions().size();
        int actualMedianStepConnector = 0;
        int wrongMedianStepConnectorType = 0;
        for (BlockPos pos : corridor.medianStepConnectorPositions()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock())) {
                actualMedianStepConnector++;
            } else if (!isClear(level, pos)) {
                wrongMedianStepConnectorType++;
            }
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

        int laneDividerDashOnBlocks = 0;
        int laneDividerDashGapBlocks = 0;
        int unexpectedWhiteLaneDividerInGap = 0;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (cell.lateral() != -6 && cell.lateral() != 6) continue;
            BlockPos pos = new BlockPos(cell.x(), cell.roadY() + 1, cell.z());
            if (HighwayCorridor.isLaneDividerPainted(profile.plan().globalStation(cell.distance()))) {
                laneDividerDashOnBlocks++;
            } else {
                laneDividerDashGapBlocks++;
                if (level.getBlockState(pos).is(AflBlocks.WHITE_LANE_DIVIDER.get())) {
                    unexpectedWhiteLaneDividerInGap++;
                }
            }
        }

        int expectedWhiteEdgeStepConnector = 0;
        int actualWhiteEdgeStepConnector = 0;
        int expectedYellowEdgeStepConnector = 0;
        int actualYellowEdgeStepConnector = 0;
        int expectedLaneDividerStepConnector = 0;
        int actualLaneDividerStepConnector = 0;
        int wrongStepConnectorType = 0;
        for (HighwayCorridor.RoadMarkingStepConnector connector : corridor.roadMarkingStepConnectors()) {
            BlockPos pos = new BlockPos(connector.x(), connector.y(), connector.z());
            BlockState state = level.getBlockState(pos);
            boolean correct = isExpectedRoadMarkingStepConnector(corridor, pos, state);
            switch (connector.type()) {
                case WHITE_EDGE -> {
                    expectedWhiteEdgeStepConnector++;
                    if (correct) actualWhiteEdgeStepConnector++;
                }
                case YELLOW_EDGE -> {
                    expectedYellowEdgeStepConnector++;
                    if (correct) actualYellowEdgeStepConnector++;
                }
                case WHITE_LANE_DIVIDER -> {
                    expectedLaneDividerStepConnector++;
                    if (correct) actualLaneDividerStepConnector++;
                }
            }
            if (!correct && !isClear(level, pos)) wrongStepConnectorType++;
        }
        int unexpectedLaneDividerStepConnectorInGap = 0;
        for (BlockPos pos : corridor.laneDividerStepConnectorGapPositions()) {
            if (level.getBlockState(pos).is(AflBlocks.WHITE_LANE_DIVIDER_STEP_CONNECTOR.get())) {
                unexpectedLaneDividerStepConnectorInGap++;
            }
        }

        int tunnelRoofOpenToSky = 0;
        int tunnelInteriorObstruction = 0;
        for (HighwayTunnelGeometry.TunnelSection section : corridor.tunnelSections()) {
            if (!corridor.isTunnelInterior(section.distance())) continue;
            Integer top = preConstructionTopY(corridor, section.x(), section.z());
            if (top == null || top - 1 - HighwayTunnelSpanResolver.tunnelOuterCrownY(section.roadY())
                    < HighwayTunnelSpanResolver.MIN_TUNNEL_ROCK_COVER) {
                tunnelRoofOpenToSky++;
            }
        }
        for (BlockPos pos : corridor.tunnelBorePositions()) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !corridor.isExpectedTunnelRoadFeature(pos.getX(), pos.getY(), pos.getZ())) {
                tunnelInteriorObstruction++;
            }
        }
        int actualTunnelLining = 0;
        for (BlockPos pos : corridor.tunnelLiningPositions()) {
            if (level.getBlockState(pos).is(HighwayPalette.REINFORCED_CONCRETE.getBlock())) {
                actualTunnelLining++;
            }
        }
        int actualPortal = 0;
        for (BlockPos pos : corridor.portalPositions()) {
            if (level.getBlockState(pos).is(HighwayPalette.REINFORCED_CONCRETE.getBlock())) actualPortal++;
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
                expectedWhiteLaneDivider - actualWhiteLaneDivider, wrongMarkingType,
                laneDividerDashOnBlocks, laneDividerDashGapBlocks,
                unexpectedWhiteLaneDividerInGap,
                expectedWhiteEdgeStepConnector, actualWhiteEdgeStepConnector,
                expectedWhiteEdgeStepConnector - actualWhiteEdgeStepConnector,
                expectedYellowEdgeStepConnector, actualYellowEdgeStepConnector,
                expectedYellowEdgeStepConnector - actualYellowEdgeStepConnector,
                expectedLaneDividerStepConnector, actualLaneDividerStepConnector,
                expectedLaneDividerStepConnector - actualLaneDividerStepConnector,
                wrongStepConnectorType, unexpectedLaneDividerStepConnectorInGap,
                expectedMedianStepConnector, actualMedianStepConnector,
                expectedMedianStepConnector - actualMedianStepConnector,
                wrongMedianStepConnectorType, tunnelRoofOpenToSky, tunnelInteriorObstruction,
                actualTunnelLining, corridor.tunnelLiningPositions().size() - actualTunnelLining,
                actualPortal, corridor.portalPositions().size() - actualPortal);
    }

    private static Integer preConstructionTopY(HighwayCorridor corridor, int x, int z) {
        for (HighwayCorridor.CoreRoadColumnSnapshot column : corridor.coreRoadColumns()) {
            if (column.x() == x && column.z() == z) return column.preConstructionTopY();
        }
        return null;
    }

    private static boolean isMedianBarrier(BlockState state) {
        return state.is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock())
                || state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock());
    }

    private static boolean isNormalRoadBase(BlockState state) {
        return state.is(HighwayPalette.BASE.getBlock())
                || state.is(HighwayPalette.SUB_BASE.getBlock())
                || state.is(HighwayPalette.FILL.getBlock());
    }

    private static boolean isExpectedRoadFurniture(HighwayCorridor corridor, BlockPos pos, BlockState state) {
        if ((corridor.isTunnelLining(pos.getX(), pos.getY(), pos.getZ())
                || corridor.isPortal(pos.getX(), pos.getY(), pos.getZ()))
                && state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock())) return true;
        return (corridor.isExpectedRoadFurniture(pos.getX(), pos.getY(), pos.getZ())
                && (state.is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock())
                || (corridor.isExpectedOuterEdge(pos.getX(), pos.getY(), pos.getZ())
                && state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock()))))
                || (corridor.isExpectedMedianStepConnector(pos.getX(), pos.getY(), pos.getZ())
                && state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock()))
                || isExpectedRoadMarking(corridor, pos, state)
                || isExpectedRoadMarkingStepConnector(corridor, pos, state);
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

    private static boolean isExpectedRoadMarkingStepConnector(HighwayCorridor corridor, BlockPos pos,
                                                               BlockState state) {
        HighwayCorridor.RoadMarkingStepConnector expected = corridor.expectedRoadMarkingStepConnector(
                pos.getX(), pos.getY(), pos.getZ());
        if (expected == null || !state.hasProperty(RoadMarkingStepConnectorBlock.FACING)
                || !state.hasProperty(RoadMarkingStepConnectorBlock.LEFT_SIDE)
                || state.getValue(RoadMarkingStepConnectorBlock.FACING) != expected.facing()
                || state.getValue(RoadMarkingStepConnectorBlock.LEFT_SIDE) != expected.leftSide()) return false;
        return switch (expected.type()) {
            case WHITE_EDGE -> state.is(AflBlocks.EDGE_LANE_WHITE_STEP_CONNECTOR.get());
            case YELLOW_EDGE -> state.is(AflBlocks.EDGE_LANE_YELLOW_STEP_CONNECTOR.get());
            case WHITE_LANE_DIVIDER -> state.is(AflBlocks.WHITE_LANE_DIVIDER_STEP_CONNECTOR.get());
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
                          int wrongMarkingTypeBlocks,
                          int laneDividerDashOnBlocks,
                          int laneDividerDashGapBlocks,
                          int unexpectedWhiteLaneDividerInGap,
                          int expectedWhiteEdgeStepConnectors,
                          int actualWhiteEdgeStepConnectors,
                          int missingWhiteEdgeStepConnectors,
                          int expectedYellowEdgeStepConnectors,
                          int actualYellowEdgeStepConnectors,
                          int missingYellowEdgeStepConnectors,
                          int expectedLaneDividerStepConnectors,
                          int actualLaneDividerStepConnectors,
                          int missingLaneDividerStepConnectors,
                           int wrongStepConnectorTypeBlocks,
                           int unexpectedLaneDividerStepConnectorInGap,
                           int expectedMedianStepConnectors,
                           int actualMedianStepConnectors,
                           int missingMedianStepConnectors,
                          int wrongMedianStepConnectorTypeBlocks,
                          int tunnelRoofOpenToSkyViolations,
                          int tunnelInteriorObstructionCells,
                          int actualTunnelLiningBlocks,
                          int missingTunnelLiningBlocks,
                          int actualPortalBlocks,
                          int missingPortalBlocks) {
        public int airspaceClearanceViolations() { return clearanceViolations; }
    }
}
