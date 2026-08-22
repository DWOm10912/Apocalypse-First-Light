package com.antaurora.apofirstlight.worldgen.highway;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** DEV-only entry point for the Regional Highway V1A engineering prototype. */
public final class HighwayDebugCommand {
    private static final int DEFAULT_LENGTH = 1024;
    private static final int MIN_LENGTH = 256;
    private static final int MAX_LENGTH = 2048;
    private static final Map<ServerLevel, HighwayEditSession> SESSIONS = new WeakHashMap<>();

    private HighwayDebugCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("highway_test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear").executes(HighwayDebugCommand::clear))
                .executes(context -> generate(context, DEFAULT_LENGTH, null))
                .then(Commands.argument("length", IntegerArgumentType.integer(MIN_LENGTH, MAX_LENGTH))
                        .executes(context -> generate(context, IntegerArgumentType.getInteger(context, "length"), null))
                        .then(Commands.argument("heading", StringArgumentType.word())
                                .executes(context -> generate(context,
                                        IntegerArgumentType.getInteger(context, "length"),
                                        StringArgumentType.getString(context, "heading")))));
    }

    private static int generate(CommandContext<CommandSourceStack> context, int length, String headingName) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        Heading heading = headingName == null ? Heading.fromYaw(player.getYRot()) : Heading.parse(headingName);
        if (heading == null) {
            context.getSource().sendFailure(Component.literal("Heading must be N, NE, E, SE, S, SW, W, or NW."));
            return 0;
        }
        HighwayEditSession old = SESSIONS.remove(level);
        if (old != null) old.restore();
        BlockPos start = player.blockPosition();
        HighwayPlan main = HighwayPlan.main(start, heading.x, heading.z, length, level.getSeed());
        HighwayEditSession edit = new HighwayEditSession(level);
        ensureChunks(level, main);
        long started = System.nanoTime();
        HighwayProfile profile = HighwayProfile.sample(level, main);
        HighwayRenderStats stats = HighwayRenderer.render(level, profile, edit);
        SESSIONS.put(level, edit);
        int minPierHeight = stats.piersPlaced == 0 ? 0 : stats.minPierHeight;
        int avgPierHeight = stats.piersPlaced == 0 ? 0 : stats.pierHeightTotal / stats.piersPlaced;
        StringBuilder message = new StringBuilder("[AFL HIGHWAY V1A.7 VISUAL V1.1A]");
        message.append(" start=(").append(start.getX()).append(',').append(start.getY()).append(',')
                .append(start.getZ()).append(')');
        message.append(" end=(").append((int) Math.round(main.sample(main.length()).x())).append(',')
                .append((int) Math.round(main.sample(main.length()).z())).append(')');
        stat(message, "heading", heading.name());
        stat(message, "length", length);
        stat(message, "actualWidth", main.width());
        stat(message, "bridgeWidth", HighwayCorridor.BRIDGE_WIDTH);
        stat(message, "routeMode", "STRAIGHT");
        stat(message, "interchangeDisabled", true);
        stat(message, "profileSampleCount", profile.samples().size());
        stat(message, "corridorCellCount", stats.corridorCellCount);
        stat(message, "authoritativeSurfaceKeys", stats.authoritativeSurfaceKeys);
        stat(message, "uniqueCoreRoadXZColumns", stats.uniqueCoreRoadXZColumns);
        stat(message, "duplicateXZDifferentRoadYSurfaceKeys",
                stats.duplicateXZDifferentRoadYSurfaceKeys);
        stat(message, "coreRoadColumnsPlanned", stats.coreRoadColumnsPlanned);
        stat(message, "coreRoadColumnsWithOriginalObstruction",
                stats.coreRoadColumnsWithOriginalObstruction);
        stat(message, "coreRoadVerticalBlocksScanned", stats.coreRoadVerticalBlocksScanned);
        stat(message, "coreRoadVerticalBlocksCleared", stats.coreRoadVerticalBlocksCleared);
        stat(message, "coreRoadClearanceTruncatedColumns",
                stats.coreRoadClearanceTruncatedColumns);
        stat(message, "coreRoadProtectedObstructionFailures",
                stats.coreRoadProtectedObstructionFailures);
        stat(message, "coreRoadTerrainIntrusions", stats.coreRoadTerrainIntrusions);
        stat(message, "nonFurnitureBlockDirectlyAboveSurface",
                stats.nonFurnitureBlockDirectlyAboveSurface);
        stat(message, "expectedSurfaceCells", stats.expectedSurfaceCells);
        stat(message, "actualSurfaceCells", stats.actualSurfaceCells);
        stat(message, "missingSurfaceCells", stats.missingSurfaceCells);
        stat(message, "wrongSurfaceMaterialCells", stats.wrongSurfaceMaterialCells);
        stat(message, "clearanceViolations", stats.clearanceViolations);
        stat(message, "airspaceClearanceViolations", stats.airspaceClearanceViolations);
        stat(message, "terrainAirspaceViolations", stats.terrainAirspaceViolations);
        stat(message, "unexpectedAirspaceObstructions", stats.unexpectedAirspaceObstructions);
        stat(message, "roadFurnitureBlocksIgnoredByAirspaceValidator",
                stats.roadFurnitureBlocksIgnoredByAirspaceValidator);
        stat(message, "expectedWhiteEdgeMarkings", stats.expectedWhiteEdgeMarkings);
        stat(message, "actualWhiteEdgeMarkings", stats.actualWhiteEdgeMarkings);
        stat(message, "missingWhiteEdgeMarkings", stats.missingWhiteEdgeMarkings);
        stat(message, "expectedYellowEdgeMarkings", stats.expectedYellowEdgeMarkings);
        stat(message, "actualYellowEdgeMarkings", stats.actualYellowEdgeMarkings);
        stat(message, "missingYellowEdgeMarkings", stats.missingYellowEdgeMarkings);
        stat(message, "expectedWhiteLaneDividerMarkings",
                stats.expectedWhiteLaneDividerMarkings);
        stat(message, "actualWhiteLaneDividerMarkings",
                stats.actualWhiteLaneDividerMarkings);
        stat(message, "missingWhiteLaneDividerMarkings",
                stats.missingWhiteLaneDividerMarkings);
        stat(message, "laneDividerDashOnBlocks", stats.laneDividerDashOnBlocks);
        stat(message, "laneDividerDashGapBlocks", stats.laneDividerDashGapBlocks);
        stat(message, "unexpectedWhiteLaneDividerInGap",
                stats.unexpectedWhiteLaneDividerInGap);
        stat(message, "laneDividerDashCycle", HighwayCorridor.LANE_DIVIDER_DASH_CYCLE);
        stat(message, "laneDividerDashOn", HighwayCorridor.LANE_DIVIDER_DASH_ON);
        stat(message, "laneDividerDashOff", HighwayCorridor.LANE_DIVIDER_DASH_OFF);
        stat(message, "wrongMarkingTypeBlocks", stats.wrongMarkingTypeBlocks);
        stat(message, "markingsSkippedUnsupportedDiagonal",
                stats.markingsSkippedUnsupportedDiagonal);
        stat(message, "roadMarkingBlocksPlaced", stats.roadMarkingBlocksPlaced);
        stat(message, "roadStepTransitions", stats.roadStepTransitions);
        stat(message, "roadStepRiseTransitions", stats.roadStepRiseTransitions);
        stat(message, "roadStepDropTransitions", stats.roadStepDropTransitions);
        stat(message, "medianStepTransitions", stats.medianStepTransitions);
        stat(message, "medianStepRiseTransitions", stats.medianStepRiseTransitions);
        stat(message, "medianStepDropTransitions", stats.medianStepDropTransitions);
        stat(message, "expectedMedianStepConnectors", stats.expectedMedianStepConnectors);
        stat(message, "actualMedianStepConnectors", stats.actualMedianStepConnectors);
        stat(message, "missingMedianStepConnectors", stats.missingMedianStepConnectors);
        stat(message, "wrongMedianStepConnectorTypeBlocks",
                stats.wrongMedianStepConnectorTypeBlocks);
        stat(message, "unsupportedMedianStepHeight", stats.unsupportedMedianStepHeight);
        stat(message, "medianStepConnectorBlocksPlaced", stats.medianStepConnectorBlocksPlaced);
        stat(message, "expectedWhiteEdgeStepConnectors", stats.expectedWhiteEdgeStepConnectors);
        stat(message, "actualWhiteEdgeStepConnectors", stats.actualWhiteEdgeStepConnectors);
        stat(message, "missingWhiteEdgeStepConnectors", stats.missingWhiteEdgeStepConnectors);
        stat(message, "expectedYellowEdgeStepConnectors", stats.expectedYellowEdgeStepConnectors);
        stat(message, "actualYellowEdgeStepConnectors", stats.actualYellowEdgeStepConnectors);
        stat(message, "missingYellowEdgeStepConnectors", stats.missingYellowEdgeStepConnectors);
        stat(message, "expectedLaneDividerStepConnectors", stats.expectedLaneDividerStepConnectors);
        stat(message, "actualLaneDividerStepConnectors", stats.actualLaneDividerStepConnectors);
        stat(message, "missingLaneDividerStepConnectors", stats.missingLaneDividerStepConnectors);
        stat(message, "laneDividerStepConnectorSkipped", stats.laneDividerStepConnectorSkipped);
        stat(message, "unexpectedLaneDividerStepConnectorInGap",
                stats.unexpectedLaneDividerStepConnectorInGap);
        stat(message, "wrongStepConnectorTypeBlocks", stats.wrongStepConnectorTypeBlocks);
        stat(message, "unsupportedMarkingStepHeight", stats.unsupportedMarkingStepHeight);
        stat(message, "stepConnectorBlocksPlaced", stats.stepConnectorBlocksPlaced);
        stat(message, "vegetationClearanceViolations", stats.vegetationClearanceViolations);
        stat(message, "remainingRowLogs", stats.remainingRowLogs);
        stat(message, "remainingRowLeaves", stats.remainingRowLeaves);
        stat(message, "viaductDeckCells", stats.viaductDeckCells);
        stat(message, "viaductStructuralBlocks", stats.viaductStructuralBlocks);
        stat(message, "missingViaductDeckCells", stats.missingViaductDeckCells);
        stat(message, "bridgeSpanNormalBaseIntrusions", stats.bridgeSpanNormalBaseIntrusions);
        stat(message, "bridgeSpanInternalNonViaductCells", stats.bridgeSpanInternalNonViaductCells);
        stat(message, "viaductOutsideStructuralBridgeCells", stats.viaductOutsideStructuralBridgeCells);
        stat(message, "structuralBridgeMissingConcreteCells", stats.structuralBridgeMissingConcreteCells);
        stat(message, "structuralApproachStations", stats.structuralApproachStations);
        stat(message, "structuralApproachCells", stats.structuralApproachCells);
        stat(message, "structuralBridgeCells", stats.structuralBridgeCells);
        stat(message, "bridgeApproachStartExtensions", stats.bridgeApproachStartExtensions);
        stat(message, "bridgeApproachEndExtensions", stats.bridgeApproachEndExtensions);
        stat(message, "bridgeApproachSupportFailures", stats.bridgeApproachSupportFailures);
        stat(message, "elevatedNormalBaseIntrusions", stats.elevatedNormalBaseIntrusions);
        stat(message, "cutColumnsPlanned", stats.cutColumnsPlanned);
        stat(message, "cutColumnsCleared", stats.cutColumnsCleared);
        stat(message, "cutTerrainBlocksCleared", stats.cutTerrainBlocksCleared);
        stat(message, "cutColumnsWithRemainingTerrainIntrusion",
                stats.cutColumnsWithRemainingTerrainIntrusion);
        stat(message, "maxCutClearanceHeightObserved", stats.maxCutClearanceHeightObserved);
        stat(message, "expectedParapetBlocks", stats.expectedParapetBlocks);
        stat(message, "actualParapetBlocks", stats.actualParapetBlocks);
        stat(message, "missingParapetBlocks", stats.missingParapetBlocks);
        stat(message, "parapetSlabBlocks", stats.parapetSlabBlocks);
        stat(message, "medianBarrierBlocks", stats.medianBarrierBlocks);
        stat(message, "rawViaductSamples", stats.rawViaductSamples);
        stat(message, "rawViaductLength", stats.rawViaductLength);
        stat(message, "resolvedBridgeSpanCount", stats.resolvedBridgeSpanCount);
        stat(message, "resolvedViaductLength", stats.resolvedViaductLength);
        stat(message, "resolvedViaductStations", stats.resolvedViaductStations);
        stat(message, "bridgeGapClosures", stats.bridgeGapClosures);
        stat(message, "shortBridgeCandidatesRejected", stats.shortBridgeCandidatesRejected);
        stat(message, "crossSectionTerrainMin", profile.samples().stream()
                .mapToInt(HighwayProfile.Sample::terrainMinY).min().orElse(0));
        stat(message, "crossSectionTerrainMax", profile.samples().stream()
                .mapToInt(HighwayProfile.Sample::terrainMaxY).max().orElse(0));
        stat(message, "maxCrossSlopeObserved", profile.maxCrossSlopeObserved());
        stat(message, "maxWaterCoverageObserved",
                String.format(java.util.Locale.ROOT, "%.2f", profile.maxWaterCoverageObserved()));
        stat(message, "extremeCrossSectionEncountered", profile.extremeCrossSectionEncountered());
        stat(message, "profileMinY", profile.minRoadY());
        stat(message, "profileMaxY", profile.maxRoadY());
        stat(message, "maxGradeObserved", profile.observedMaxGrade());
        stat(message, "GROUND_cells", stats.groundCells);
        stat(message, "CUT_cells", stats.cutCells);
        stat(message, "FILL_cells", stats.fillCells);
        stat(message, "VIADUCT_cells", stats.viaductCells);
        stat(message, "pierStationsPlanned", stats.pierStationsPlanned);
        stat(message, "piersPlaced", stats.piersPlaced);
        stat(message, "piersSkipped", stats.piersSkipped);
        stat(message, "pierFoundationFailures", stats.pierFoundationFailures);
        stat(message, "minPierHeight", minPierHeight);
        stat(message, "maxPierHeight", stats.maxPierHeight);
        stat(message, "avgPierHeight", avgPierHeight);
        stat(message, "floatingPierCount", stats.floatingPierCount);
        stat(message, "waterPiers", stats.waterPiers);
        stat(message, "landPiers", stats.landPiers);
        stat(message, "asphaltSurfaceBlocks", stats.asphaltSurfaceBlocks);
        stat(message, "groundAsphaltSurfaceBlocks", stats.groundAsphaltSurfaceBlocks);
        stat(message, "cutAsphaltSurfaceBlocks", stats.cutAsphaltSurfaceBlocks);
        stat(message, "fillAsphaltSurfaceBlocks", stats.fillAsphaltSurfaceBlocks);
        stat(message, "viaductAsphaltSurfaceBlocks", stats.viaductAsphaltSurfaceBlocks);
        stat(message, "reinforcedConcreteBlocks", stats.reinforcedConcreteBlocks);
        stat(message, "vegetationBlocksCleared", stats.vegetationBlocksCleared);
        stat(message, "vegetationComponentsCleared", stats.vegetationComponentsCleared);
        stat(message, "vegetationCleanupTruncated", stats.vegetationCleanupTruncated);
        stat(message, "blocksPlaced", stats.blocksPlaced);
        stat(message, "blocksCleared", stats.blocksCleared);
        stat(message, "fillBlocks", stats.fillBlocks);
        stat(message, "cutBlocks", stats.cutBlocks);
        stat(message, "viaductBlocks", stats.viaductBlocks);
        stat(message, "elapsedCommandMs", (System.nanoTime() - started) / 1_000_000L);
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        HighwayEditSession session = SESSIONS.remove(level);
        if (session == null || session.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No AFL highway test session is recorded in this dimension."));
            return 0;
        }
        int restored = session.restore();
        context.getSource().sendSuccess(() -> Component.literal("[AFL HIGHWAY V1A] clear restoredBlocks=" + restored), true);
        return 1;
    }

    private static void stat(StringBuilder message, String name, Object value) {
        message.append(' ').append(name).append('=').append(value);
    }

    private static void ensureChunks(ServerLevel level, HighwayPlan plan) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (HighwayPlan.Point point : plan.controlPoints()) {
            minX = Math.min(minX, (int) Math.floor(point.x()) - plan.width() - 8);
            maxX = Math.max(maxX, (int) Math.ceil(point.x()) + plan.width() + 8);
            minZ = Math.min(minZ, (int) Math.floor(point.z()) - plan.width() - 8);
            maxZ = Math.max(maxZ, (int) Math.ceil(point.z()) + plan.width() + 8);
        }
        // The command's per-block edits also load intermediate chunks. This bounded
        // pre-touch prevents the first few terrain samples from racing chunk IO.
        for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
            for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) level.getChunk(cx, cz);
        }
    }

    private enum Heading {
        N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0), NW(-1, -1);
        private final double x, z;
        Heading(double x, double z) { this.x = x; this.z = z; }
        static Heading parse(String value) { try { return value == null ? null : valueOf(value.toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException ignored) { return null; } }
        static Heading fromYaw(float yaw) {
            double radians = Math.toRadians(yaw);
            double x = Math.sin(radians), z = Math.cos(radians);
            Heading best = S;
            double score = -Double.MAX_VALUE;
            for (Heading candidate : values()) {
                double length = Math.hypot(candidate.x, candidate.z);
                double next = (x * candidate.x + z * candidate.z) / length;
                if (next > score) { score = next; best = candidate; }
            }
            return best;
        }
    }
}
