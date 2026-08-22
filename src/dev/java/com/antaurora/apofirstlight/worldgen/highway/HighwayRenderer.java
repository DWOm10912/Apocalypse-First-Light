package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.block.RoadMarkingStepConnectorBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/** V1A.7 renderer: authoritative core clearance plus the existing V1A.6 engineering passes. */
public final class HighwayRenderer {
    private static final int PIER_SPACING = 32;
    private static final int PIER_CAP_HALF_WIDTH = 9;
    private static final int PIER_CAP_HALF_LONGITUDINAL = 1;
    private static final int PIER_SHAFT_HALF_WIDTH = 2;
    private static final int PIER_SHAFT_HALF_LONGITUDINAL = 1;
    private static final int MAX_PIER_SEARCH_DEPTH = 128;
    private static final int MAX_TREE_VISIT_BLOCKS = 1024;
    private static final int MAX_TREE_HORIZONTAL_RADIUS = 10;
    private static final int MAX_TREE_VERTICAL_RADIUS = 24;
    private static final int PIER_BOUNDARY_MARGIN = 8;

    private HighwayRenderer() {}

    public static HighwayRenderStats render(ServerLevel level, HighwayProfile profile, HighwayEditSession edit) {
        HighwayCorridor corridor = HighwayCorridor.build(level, profile.plan(), profile);
        HighwayRenderStats stats = new HighwayRenderStats();
        stats.corridorCellCount = corridor.cells().size();
        stats.expectedSurfaceCells = corridor.expectedSurfaceCells();
        stats.rawViaductSamples = profile.rawViaductSamples();
        stats.resolvedBridgeSpanCount = profile.bridgeSpans().size();
        stats.resolvedViaductStations = profile.resolvedViaductStations();
        stats.rawViaductLength = (int) Math.round(profile.rawViaductLength());
        stats.resolvedViaductLength = (int) Math.round(profile.bridgeSpans().stream()
                .mapToDouble(span -> span.endStation() - span.startStation()).sum());
        stats.bridgeGapClosures = profile.bridgeGapClosures();
        stats.shortBridgeCandidatesRejected = profile.shortBridgeCandidatesRejected();
        stats.maxCrossSlopeObserved = profile.maxCrossSlopeObserved();
        stats.maxWaterCoverageObserved = profile.maxWaterCoverageObserved();
        stats.extremeCrossSectionEncountered = profile.extremeCrossSectionEncountered();
        stats.structuralApproachStations = corridor.structuralApproachStations();
        stats.structuralApproachCells = corridor.structuralApproachCells();
        stats.structuralBridgeCells = corridor.structuralBridgeCells();
        stats.bridgeApproachStartExtensions = corridor.bridgeApproachStartExtensions();
        stats.bridgeApproachEndExtensions = corridor.bridgeApproachEndExtensions();
        stats.bridgeApproachSupportFailures = corridor.bridgeApproachSupportFailures();
        stats.authoritativeSurfaceKeys = corridor.authoritativeSurfaceKeys();
        stats.uniqueCoreRoadXZColumns = corridor.uniqueCoreRoadXZColumns();
        stats.duplicateXZDifferentRoadYSurfaceKeys = corridor.duplicateXZDifferentRoadYSurfaceKeys();
        stats.coreRoadColumnsPlanned = corridor.coreRoadColumns().size();
        stats.markingsSkippedUnsupportedDiagonal = corridor.markingsSkippedUnsupportedDiagonal();
        stats.roadStepTransitions = corridor.roadStepTransitions();
        stats.roadStepRiseTransitions = corridor.roadStepRiseTransitions();
        stats.roadStepDropTransitions = corridor.roadStepDropTransitions();
        stats.laneDividerStepConnectorSkipped = corridor.laneDividerStepConnectorSkipped();
        stats.unsupportedMarkingStepHeight = corridor.unsupportedMarkingStepHeight();
        stats.cutColumnsPlanned = corridor.cutColumns().size();
        for (HighwayCorridor.Cell cell : corridor.cells()) stats.addCellMode(cell.mode());

        clearRow(level, edit, stats, corridor);
        clearCoreRoadVerticalEnvelope(level, edit, stats, corridor);
        clearCutEnvelope(level, edit, stats, corridor);
        HighwayContinuityValidator.CoreClearanceResult coreClearance =
                HighwayContinuityValidator.validateCoreRoadClearance(level, corridor);
        stats.coreRoadTerrainIntrusions = coreClearance.coreRoadTerrainIntrusions();
        stats.nonFurnitureBlockDirectlyAboveSurface =
                coreClearance.nonFurnitureBlockDirectlyAboveSurface();
        placeRoadStructure(level, edit, stats, corridor);
        placeParapetsAndMedian(level, edit, stats, corridor);
        placeRoadMarkings(level, edit, stats, corridor);
        placeRoadMarkingStepConnectors(level, edit, stats, corridor);
        placePiers(level, edit, stats, corridor, profile);

        HighwayContinuityValidator.Result validation = HighwayContinuityValidator.validate(level, corridor, profile);
        stats.actualSurfaceCells = validation.actualSurfaceCells();
        stats.missingSurfaceCells = validation.missingSurfaceCells();
        stats.clearanceViolations = validation.clearanceViolations();
        stats.airspaceClearanceViolations = validation.airspaceClearanceViolations();
        stats.missingViaductDeckCells = validation.missingViaductDeckCells();
        stats.wrongSurfaceMaterialCells = validation.wrongSurfaceMaterialCells();
        stats.bridgeSpanInternalNonViaductCells = validation.bridgeSpanInternalNonViaductCells();
        stats.bridgeSpanNormalBaseIntrusions = validation.bridgeSpanNormalBaseIntrusions();
        stats.elevatedNormalBaseIntrusions = validation.elevatedNormalBaseIntrusions();
        stats.viaductOutsideStructuralBridgeCells = validation.viaductOutsideStructuralBridgeCells();
        stats.structuralBridgeMissingConcreteCells = validation.structuralBridgeMissingConcreteCells();
        stats.cutColumnsWithRemainingTerrainIntrusion = validation.cutColumnsWithRemainingTerrainIntrusion();
        stats.terrainAirspaceViolations = validation.terrainAirspaceViolations();
        stats.unexpectedAirspaceObstructions = validation.unexpectedAirspaceObstructions();
        stats.roadFurnitureBlocksIgnoredByAirspaceValidator =
                validation.roadFurnitureBlocksIgnoredByAirspaceValidator();
        stats.expectedParapetBlocks = validation.expectedParapetBlocks();
        stats.actualParapetBlocks = validation.actualParapetBlocks();
        stats.missingParapetBlocks = validation.missingParapetBlocks();
        stats.medianBarrierBlocks = validation.medianBarrierBlocks();
        stats.remainingRowLogs = validation.remainingRowLogs();
        stats.remainingRowLeaves = validation.remainingRowLeaves();
        stats.vegetationClearanceViolations = validation.vegetationClearanceViolations();
        stats.expectedWhiteEdgeMarkings = validation.expectedWhiteEdgeMarkings();
        stats.actualWhiteEdgeMarkings = validation.actualWhiteEdgeMarkings();
        stats.missingWhiteEdgeMarkings = validation.missingWhiteEdgeMarkings();
        stats.expectedYellowEdgeMarkings = validation.expectedYellowEdgeMarkings();
        stats.actualYellowEdgeMarkings = validation.actualYellowEdgeMarkings();
        stats.missingYellowEdgeMarkings = validation.missingYellowEdgeMarkings();
        stats.expectedWhiteLaneDividerMarkings = validation.expectedWhiteLaneDividerMarkings();
        stats.actualWhiteLaneDividerMarkings = validation.actualWhiteLaneDividerMarkings();
        stats.missingWhiteLaneDividerMarkings = validation.missingWhiteLaneDividerMarkings();
        stats.wrongMarkingTypeBlocks = validation.wrongMarkingTypeBlocks();
        stats.expectedWhiteEdgeStepConnectors = validation.expectedWhiteEdgeStepConnectors();
        stats.actualWhiteEdgeStepConnectors = validation.actualWhiteEdgeStepConnectors();
        stats.missingWhiteEdgeStepConnectors = validation.missingWhiteEdgeStepConnectors();
        stats.expectedYellowEdgeStepConnectors = validation.expectedYellowEdgeStepConnectors();
        stats.actualYellowEdgeStepConnectors = validation.actualYellowEdgeStepConnectors();
        stats.missingYellowEdgeStepConnectors = validation.missingYellowEdgeStepConnectors();
        stats.expectedLaneDividerStepConnectors = validation.expectedLaneDividerStepConnectors();
        stats.actualLaneDividerStepConnectors = validation.actualLaneDividerStepConnectors();
        stats.missingLaneDividerStepConnectors = validation.missingLaneDividerStepConnectors();
        stats.wrongStepConnectorTypeBlocks = validation.wrongStepConnectorTypeBlocks();
        return stats;
    }

    private static void clearCoreRoadVerticalEnvelope(ServerLevel level, HighwayEditSession edit,
                                                      HighwayRenderStats stats,
                                                      HighwayCorridor corridor) {
        for (HighwayCorridor.CoreRoadColumnSnapshot column : corridor.coreRoadColumns()) {
            if (column.hasOriginalObstruction()) stats.coreRoadColumnsWithOriginalObstruction++;
            if (column.capped(level.getMaxBuildHeight())) stats.coreRoadClearanceTruncatedColumns++;
            int top = column.clearanceTopY(level.getMaxBuildHeight());
            for (int y = column.roadY() + 1; y <= top; y++) {
                stats.coreRoadVerticalBlocksScanned++;
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = level.getBlockState(pos);
                if (state.isAir() && level.getFluidState(pos).isEmpty()) continue;
                if (isProtectedObstruction(level, pos, state)) {
                    stats.coreRoadProtectedObstructionFailures++;
                    continue;
                }
                if (clear(level, edit, pos)) {
                    stats.blocksCleared++;
                    stats.coreRoadVerticalBlocksCleared++;
                }
            }
        }
    }

    private static void clearRow(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                 HighwayCorridor corridor) {
        Set<BlockPos> vegetation = new LinkedHashSet<>();
        for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
            for (int y = column.roadY() + 1;
                 y <= column.roadY() + HighwayCorridor.VERTICAL_CLEARANCE; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                if (isVegetation(level.getBlockState(pos)) && !vegetation.contains(pos)) {
                    vegetation.addAll(collectVegetation(level, pos, stats));
                }
            }
        }
        for (BlockPos pos : vegetation) {
            if (clear(level, edit, pos)) {
                stats.blocksCleared++;
                stats.vegetationBlocksCleared++;
            }
        }
        for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
            if (corridor.isCutColumn(column.x(), column.z())) continue;
            for (int y = column.roadY() + 1;
                 y <= column.roadY() + HighwayCorridor.VERTICAL_CLEARANCE; y++) {
                if (clear(level, edit, new BlockPos(column.x(), y, column.z()))) stats.blocksCleared++;
            }
        }
    }

    private static void clearCutEnvelope(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                          HighwayCorridor corridor) {
        for (HighwayCorridor.CutColumn column : corridor.cutColumns()) {
            int top = column.clearanceTopY(level.getMaxBuildHeight());
            stats.maxCutClearanceHeightObserved = Math.max(stats.maxCutClearanceHeightObserved,
                    Math.max(0, top - column.roadY()));
            for (int y = column.roadY() + 1; y <= top; y++) {
                if (clear(level, edit, new BlockPos(column.x(), y, column.z()))) {
                    stats.blocksCleared++;
                    stats.cutBlocks++;
                    stats.cutTerrainBlocksCleared++;
                }
            }
            boolean clear = !column.capped(level.getMaxBuildHeight());
            for (int y = column.roadY() + 1; clear && y <= top; y++) {
                clear = level.getBlockState(new BlockPos(column.x(), y, column.z())).isAir();
            }
            if (clear) stats.cutColumnsCleared++;
        }
    }

    private static Set<BlockPos> collectVegetation(ServerLevel level, BlockPos seed,
                                                   HighwayRenderStats stats) {
        Set<BlockPos> component = new LinkedHashSet<>();
        if (!isVegetation(level.getBlockState(seed))) return component;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (component.contains(pos) || !withinTreeBounds(seed, pos)
                    || !isVegetation(level.getBlockState(pos))) continue;
            component.add(pos);
            if (component.size() >= MAX_TREE_VISIT_BLOCKS) {
                stats.vegetationCleanupTruncated++;
                break;
            }
            queue.add(pos.above());
            queue.add(pos.below());
            queue.add(pos.north());
            queue.add(pos.south());
            queue.add(pos.east());
            queue.add(pos.west());
        }
        if (!component.isEmpty()) stats.vegetationComponentsCleared++;
        return component;
    }

    private static boolean withinTreeBounds(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= MAX_TREE_HORIZONTAL_RADIUS
                && Math.abs(pos.getZ() - origin.getZ()) <= MAX_TREE_HORIZONTAL_RADIUS
                && Math.abs(pos.getY() - origin.getY()) <= MAX_TREE_VERTICAL_RADIUS;
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    private static void placeRoadStructure(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                           HighwayCorridor corridor) {
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            BlockPos surface = new BlockPos(cell.x(), cell.roadY(), cell.z());
            placeAsphaltSurface(level, edit, stats, surface, cell.mode());
            if (cell.structuralBridge()) {
                placeConcrete(level, edit, stats, surface.below());
                placeConcrete(level, edit, stats, surface.below(2));
                placeConcrete(level, edit, stats, surface.below(3));
                if (cell.mode() == HighwayTerrainMode.VIADUCT) stats.viaductDeckCells++;
                continue;
            }
            if (edit.set(surface.below(), HighwayPalette.BASE)) stats.blocksPlaced++;
            if (edit.set(surface.below(2), HighwayPalette.SUB_BASE)) stats.blocksPlaced++;
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

        for (HighwayCorridor.Cell cell : corridor.bridgeCells()) {
            if (!cell.structuralBridge() || cell.role() != HighwayCorridor.Role.BRIDGE_EDGE) continue;
            placeConcrete(level, edit, stats, new BlockPos(cell.x(), cell.roadY(), cell.z()));
            for (int offset = 1; offset <= 3; offset++) {
                placeConcrete(level, edit, stats,
                        new BlockPos(cell.x(), cell.roadY() - offset, cell.z()));
            }
        }
    }

    private static void placeParapetsAndMedian(ServerLevel level, HighwayEditSession edit,
                                               HighwayRenderStats stats, HighwayCorridor corridor) {
        for (HighwayCorridor.Cell cell : corridor.bridgeCells()) {
            if (cell.structuralBridge() && cell.role() == HighwayCorridor.Role.BRIDGE_EDGE) {
                if (edit.set(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()),
                        HighwayPalette.REINFORCED_CONCRETE_SLAB)) {
                    stats.blocksPlaced++;
                    stats.parapetSlabBlocks++;
                }
            }
        }
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (cell.role() != HighwayCorridor.Role.MEDIAN || cell.lateral() != 0) continue;
            if (edit.set(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()),
                    HighwayPalette.REINFORCED_CONCRETE_SLAB)) {
                stats.blocksPlaced++;
                stats.medianBarrierBlocks++;
            }
        }
    }

    private static void placeRoadMarkings(ServerLevel level, HighwayEditSession edit,
                                          HighwayRenderStats stats, HighwayCorridor corridor) {
        for (HighwayCorridor.RoadMarking marking : corridor.roadMarkings()) {
            BlockState state = roadMarkingState(marking);
            if (edit.set(new BlockPos(marking.x(), marking.y(), marking.z()), state)) {
                stats.blocksPlaced++;
                stats.roadMarkingBlocksPlaced++;
            }
        }
    }

    private static BlockState roadMarkingState(HighwayCorridor.RoadMarking marking) {
        BlockState state = switch (marking.type()) {
            case WHITE_EDGE -> AflBlocks.EDGE_LANE_WHITE.get().defaultBlockState();
            case YELLOW_EDGE -> AflBlocks.EDGE_LANE_YELLOW.get().defaultBlockState();
            case WHITE_LANE_DIVIDER -> AflBlocks.WHITE_LANE_DIVIDER.get().defaultBlockState();
        };
        return state.setValue(RoadMarkingBlock.FACING, marking.facing());
    }

    private static void placeRoadMarkingStepConnectors(ServerLevel level, HighwayEditSession edit,
                                                        HighwayRenderStats stats,
                                                        HighwayCorridor corridor) {
        for (HighwayCorridor.RoadMarkingStepConnector connector : corridor.roadMarkingStepConnectors()) {
            BlockState state = roadMarkingStepConnectorState(connector);
            if (edit.set(new BlockPos(connector.x(), connector.y(), connector.z()), state)) {
                stats.blocksPlaced++;
                stats.stepConnectorBlocksPlaced++;
            }
        }
    }

    private static BlockState roadMarkingStepConnectorState(
            HighwayCorridor.RoadMarkingStepConnector connector) {
        BlockState state = switch (connector.type()) {
            case WHITE_EDGE -> AflBlocks.EDGE_LANE_WHITE_STEP_CONNECTOR.get().defaultBlockState();
            case YELLOW_EDGE -> AflBlocks.EDGE_LANE_YELLOW_STEP_CONNECTOR.get().defaultBlockState();
            case WHITE_LANE_DIVIDER -> throw new IllegalArgumentException(
                    "Lane divider step connectors require a painted dash boundary");
        };
        return state
                .setValue(RoadMarkingStepConnectorBlock.FACING, connector.facing())
                .setValue(RoadMarkingStepConnectorBlock.LEFT_SIDE, connector.leftSide());
    }

    private static void placePiers(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                   HighwayCorridor corridor, HighwayProfile profile) {
        HighwayPlan.Tangent tangent = corridor.plan().tangent(0.0);
        for (double station = 0.0; station <= corridor.plan().length(); station += PIER_SPACING) {
            HighwayCorridor.CenterCell center = nearestCenterline(corridor, station);
            HighwayCorridor.Cell roadCell = center == null ? null : centerCell(corridor, center);
            HighwayBridgeSpanResolver.Span span = profile.spanAt(station);
            if (center == null || roadCell == null || roadCell.mode() != HighwayTerrainMode.VIADUCT || span == null) continue;
            if (station < span.startStation() + PIER_BOUNDARY_MARGIN
                    || station > span.endStation() - PIER_BOUNDARY_MARGIN) continue;
            stats.pierStationsPlanned++;

            int deckBottom = roadCell.roadY() - 3;
            Foundation foundation = findFoundation(level, center.x(), center.z(), deckBottom);
            if (!foundation.found()) {
                stats.piersSkipped++;
                stats.pierFoundationFailures++;
                ApocalypseFirstLight.LOGGER.warn("[AFL HIGHWAY] PIER_FOUNDATION_FAILED station={} x={} z={} depth={}",
                        (int) station, center.x(), center.z(), MAX_PIER_SEARCH_DEPTH);
                continue;
            }

            stats.piersPlaced++;
            if (foundation.crossedWater()) stats.waterPiers++;
            else stats.landPiers++;

            placeLocalRect(level, edit, stats, center.x(), center.z(), tangent,
                    -PIER_CAP_HALF_WIDTH, PIER_CAP_HALF_WIDTH,
                    -PIER_CAP_HALF_LONGITUDINAL, PIER_CAP_HALF_LONGITUDINAL,
                    roadCell.roadY() - 5, roadCell.roadY() - 4);
            placeLocalRect(level, edit, stats, center.x(), center.z(), tangent,
                    -PIER_SHAFT_HALF_WIDTH, PIER_SHAFT_HALF_WIDTH,
                    -PIER_SHAFT_HALF_LONGITUDINAL, PIER_SHAFT_HALF_LONGITUDINAL,
                    foundation.y() + 2, roadCell.roadY() - 6);
            placeLocalRect(level, edit, stats, center.x(), center.z(), tangent,
                    -4, 4, -3, 3, foundation.y(), foundation.y());
            placeLocalRect(level, edit, stats, center.x(), center.z(), tangent,
                    -3, 3, -2, 2, foundation.y() + 1, foundation.y() + 1);

            int height = Math.max(0, roadCell.roadY() - foundation.y());
            stats.minPierHeight = Math.min(stats.minPierHeight, height);
            stats.maxPierHeight = Math.max(stats.maxPierHeight, height);
            stats.pierHeightTotal += height;
            if (!isStableTerrain(level, new BlockPos(center.x(), foundation.y() - 1, center.z()))) {
                stats.floatingPierCount++;
            }
        }
    }

    private static HighwayCorridor.CenterCell nearestCenterline(HighwayCorridor corridor, double station) {
        HighwayCorridor.CenterCell nearest = null;
        double best = Double.MAX_VALUE;
        for (HighwayCorridor.CenterCell center : corridor.centerline()) {
            double distance = Math.abs(center.distance() - station);
            if (distance < best) {
                best = distance;
                nearest = center;
            }
        }
        return nearest;
    }

    private static HighwayCorridor.Cell centerCell(HighwayCorridor corridor, HighwayCorridor.CenterCell center) {
        HighwayCorridor.Cell nearest = null;
        int bestLateral = Integer.MAX_VALUE;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (cell.x() != center.x() || cell.z() != center.z()) continue;
            if (Math.abs(cell.lateral()) < bestLateral) {
                bestLateral = Math.abs(cell.lateral());
                nearest = cell;
            }
        }
        return nearest;
    }

    private static void placeLocalRect(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                       int centerX, int centerZ, HighwayPlan.Tangent tangent,
                                       int lateralMin, int lateralMax, int longitudinalMin, int longitudinalMax,
                                       int yMin, int yMax) {
        double rightX = -tangent.z();
        double rightZ = tangent.x();
        for (int longitudinal = longitudinalMin; longitudinal <= longitudinalMax; longitudinal++) {
            for (int lateral = lateralMin; lateral <= lateralMax; lateral++) {
                int x = (int) Math.round(centerX + tangent.x() * longitudinal + rightX * lateral);
                int z = (int) Math.round(centerZ + tangent.z() * longitudinal + rightZ * lateral);
                for (int y = yMin; y <= yMax; y++) {
                    placeConcrete(level, edit, stats, new BlockPos(x, y, z));
                }
            }
        }
    }

    private static void placeConcrete(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                      BlockPos pos) {
        if (edit.set(pos, HighwayPalette.REINFORCED_CONCRETE)) {
            stats.blocksPlaced++;
            stats.viaductBlocks++;
            stats.viaductStructuralBlocks++;
            stats.reinforcedConcreteBlocks++;
        }
    }

    private static void placeAsphaltSurface(ServerLevel level, HighwayEditSession edit,
                                            HighwayRenderStats stats, BlockPos pos,
                                            HighwayTerrainMode mode) {
        if (edit.set(pos, HighwayPalette.ASPHALT)) stats.blocksPlaced++;
        if (!level.getBlockState(pos).is(HighwayPalette.ASPHALT.getBlock())) return;

        stats.asphaltSurfaceBlocks++;
        switch (mode) {
            case GROUND -> stats.groundAsphaltSurfaceBlocks++;
            case CUT -> stats.cutAsphaltSurfaceBlocks++;
            case FILL -> stats.fillAsphaltSurfaceBlocks++;
            case VIADUCT -> stats.viaductAsphaltSurfaceBlocks++;
        }
    }

    private static Foundation findFoundation(ServerLevel level, int x, int z, int deckBottomY) {
        boolean crossedWater = false;
        int bottom = Math.max(level.getMinBuildHeight(), deckBottomY - MAX_PIER_SEARCH_DEPTH);
        for (int y = deckBottomY - 1; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos).isEmpty()) {
                crossedWater = true;
                continue;
            }
            if (isStableTerrain(level, pos)) return new Foundation(true, y, crossedWater);
        }
        return new Foundation(false, bottom, crossedWater);
    }

    private static boolean isStableTerrain(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && level.getFluidState(pos).isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.canBeReplaced()
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isProtectedObstruction(ServerLevel level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) != null || state.getDestroySpeed(level, pos) < 0.0F;
    }

    private static boolean clear(ServerLevel level, HighwayEditSession edit, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !isProtectedObstruction(level, pos, state)
                && edit.set(pos, Blocks.AIR.defaultBlockState());
    }

    private record Foundation(boolean found, int y, boolean crossedWater) {}
}
