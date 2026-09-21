package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lightweight deterministic-network inspection; it never places or removes blocks. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HighwayNetworkCommand {
    private HighwayNetworkCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("highway_network")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("nearest").executes(HighwayNetworkCommand::nearest))
                .then(Commands.literal("info").executes(HighwayNetworkCommand::info))
                .then(Commands.literal("perf").executes(HighwayNetworkCommand::perf))
                .then(Commands.literal("node").executes(HighwayNetworkCommand::node))
                .then(Commands.literal("diagnose")
                        .executes(context -> diagnose(context, null, null))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> diagnose(context,
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "z"))))));
        CommandNode<CommandSourceStack> afl = event.getDispatcher().getRoot().getChild("afl");
        if (afl != null) afl.addChild(command.build());
        else event.getDispatcher().register(Commands.literal("afl").then(command));
    }

    private static int nearest(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Vec3 pos = context.getSource().getPosition();
        HighwayRouteGraph graph = HighwayRouteGraph.forSeed(level.getSeed());
        HighwayRouteGraph.Edge ns = graph.edge(HighwayRouteGraph.Orientation.NORTH_SOUTH);
        HighwayRouteGraph.Edge ew = graph.edge(HighwayRouteGraph.Orientation.EAST_WEST);
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "[AFL HIGHWAY NETWORK] nearestNS=%s x=%d distance=%.1f nearestEW=%s z=%d distance=%.1f",
                ns.id(), ns.fixedCoordinate(), ns.distanceTo(pos.x, pos.z),
                ew.id(), ew.fixedCoordinate(), ew.distanceTo(pos.x, pos.z))), false);
        return 1;
    }

    private static int diagnose(CommandContext<CommandSourceStack> context, Integer targetX, Integer targetZ) {
        int x = targetX == null ? (int) Math.floor(context.getSource().getPosition().x) : targetX;
        int z = targetZ == null ? (int) Math.floor(context.getSource().getPosition().z) : targetZ;
        var report = HighwayLiveGenerationDiagnostic.diagnose(context.getSource().getLevel(), x, z);
        send(context, "[AFL Highway Diagnose] DIAGNOSTIC MODE = DRY REPLAY; not historical worldgen");
        send(context, "seed=" + report.seed() + " target=" + x + "," + z
                + " chunk=" + report.chunk().x + "," + report.chunk().z
                + " queryBounds=" + report.bounds());
        send(context, "dimension=" + report.dimension() + " biome=" + report.biome()
                + " featureEligible=" + (report.firstDrop() == HighwayLiveGenerationDiagnostic.DropPoint.CHUNK_NOT_LOADED
                        ? "unknown" : report.featureEligible())
                + " queriedEdges=" + report.edges().size());
        for (var edge : report.edges()) {
            var route = edge.edge();
            send(context, "[Edge] route=" + route.routeId() + " type=" + route.routeType()
                    + " edge=" + route.id() + " geometry=" + route.orientation()
                    + " bounds=" + route.bounds(HighwayRouteGraph.FOOTPRINT_HALF_WIDTH));
            send(context, "station coordinate=" + edge.coordinateStation() + " selectedFromChunkMin="
                    + edge.selectionStation() + " edgeRange=" + route.startStation() + ".." + route.endStation()
                    + " core=" + edge.coreStart() + ".." + edge.coreEnd()
                    + " halo=" + Math.round(edge.haloStart()) + ".." + Math.round(edge.haloEnd()));
            send(context, "profile SURFACE=" + edge.surfaceSamples() + " VIADUCT=" + edge.viaductSamples()
                    + " TUNNEL=" + edge.tunnelSamples() + " OTHER=" + edge.otherSamples()
                    + " firstUnsupported=" + edge.firstUnsupported());
            send(context, "corridor geometryDeferred=" + edge.geometryDeferred()
                    + " safeSkipTriggered=" + edge.safeSkipTriggered() + " (derived from profile + cells)"
                    + " cells=" + edge.corridorCells() + " ownedCells=" + edge.ownedCells()
                    + " targetRibbonCells=" + edge.targetFootprintCells()
                    + " wouldRender=" + (edge.firstDrop() == HighwayLiveGenerationDiagnostic.DropPoint.NONE));
            send(context, "FIRST DROP POINT = " + edge.firstDrop() + " | " + edge.detail());
        }
        send(context, "OVERALL FIRST DROP POINT = " + report.firstDrop() + " | " + report.detail());
        return report.firstDrop() == HighwayLiveGenerationDiagnostic.DropPoint.NONE ? 1 : 0;
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        NaturalHighwayRuntimeStats.Snapshot stats = NaturalHighwayRuntimeStats.snapshot();
        HighwayRouteGraph graph = HighwayRouteGraph.forSeed(context.getSource().getLevel().getSeed());
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL HIGHWAY NETWORK] graphVersion=" + HighwayRouteGraph.VERSION
                        + " edges=" + graph.edges().size() + " intersection=" + graph.intersection()
                        + " profileAnchorSpacing=" + HighwayTerrainSampler.PROFILE_ANCHOR_SPACING), false);
        for (HighwayRouteGraph.Edge edge : graph.edges()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[" + edge.routeType() + "] route=" + edge.routeId() + " edge=" + edge.id()
                            + " geometry=" + (edge.geometry()==null?"AXIAL":"POLYLINE")
                            + " builtStationRange="+edge.startStation()+".."+edge.endStation()
                            + " purpose=" + edge.purpose() + " parent=" + edge.parentAttachment()
                            + " start=" + edge.startNode() + " end=" + edge.endNode()), false);
        }
        for (var crossing : graph.seaCrossings()) {
            context.getSource().sendSuccess(() -> Component.literal("[SATELLITE CONNECTION] id="+crossing.id()
                    +" island="+crossing.islandId()+" source="+crossing.source()+" route="+crossing.routeId()
                    +" parent="+crossing.parent()+" mainland="+crossing.mainland()+" satellite="+crossing.satellite()
                    +" bridgeAxis="+crossing.dx()+","+crossing.dz()+" actualBankSpan="+crossing.span()
                    +" mainlandDisplacement="+crossing.mainlandDisplacement()+" satelliteDisplacement="+crossing.satelliteDisplacement()
                    +" combinedDisplacement="+crossing.combinedDisplacement()+" bridgeApproachEngineeringStatus=UNKNOWN"
                    +" mainlandLength="+crossing.mainlandGeometry().length()+" islandLength="+crossing.islandGeometry().length()
                    +" selectedParentTrunk="+crossing.parent().parentRouteId()+" turnCount="+crossing.turnCount()
                    +" mainlandRouteLength="+crossing.mainlandRouteLength()+" extraDistance="+crossing.extraDistance()+" networkCost="+crossing.networkCost()
                    +" seaBridge=RESERVATION_ONLY diagonalViaduct=SUPPORTED diagonalTunnel=UNSUPPORTED_SAFE_SKIP"),false);
        }
        for(var turn:graph.turns())send(context,"[TURN] "+turn);
        for(var zone:graph.reservedZones())send(context,"["+zone.kind()+"] "+zone);
        for (String diagnostic : graph.routingDiagnostics()) context.getSource().sendSuccess(
                () -> Component.literal("[SATELLITE ROUTING DEFERRED] "+diagnostic),false);
        context.getSource().sendSuccess(() -> Component.literal(
                "chunksProcessed=" + stats.highwayFeatureInvocations()
                        + " chunksWithCorridor=" + stats.highwayAcceptedChunks()
                        + " nsQueried=" + stats.nsCorridorsQueried()
                        + " ewQueried=" + stats.ewCorridorsQueried()
                        + " surfacePlaced=" + stats.naturalHighwaySurfaceBlocksPlaced()
                        + " blocksCleared=" + stats.naturalHighwayBlocksCleared()
                        + " nodes=" + stats.interchangeNodesEncountered()
                        + " upperNS=" + stats.interchangeUpperNS()
                        + " upperEW=" + stats.interchangeUpperEW()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "profileMismatch=" + stats.crossChunkProfileMismatch()
                        + " stationMismatch=" + stats.crossChunkStationMismatch()
                        + " markingMismatch=" + stats.crossChunkMarkingPhaseMismatch()
                        + " bridgeMismatch=" + stats.crossChunkBridgeSpanMismatch()
                        + " tunnelMismatch=" + stats.crossChunkTunnelSpanMismatch()
                        + " segmentBoundaryMismatch=" + stats.engineeringSegmentBoundaryMismatch()
                        + " duplicateAttempts=" + stats.duplicateNaturalPlacementAttempts()
                        + " illegalWrites=" + stats.illegalCrossChunkWriteAttempts()), false);
        return 1;
    }

    private static int perf(CommandContext<CommandSourceStack> context) {
        NaturalHighwayRuntimeStats.Snapshot s = NaturalHighwayRuntimeStats.snapshot();
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "[AFL HIGHWAY PERF] invocations=%d rejected=%d accepted=%d totalMs=%.2f avgRejectedUs=%.2f avgAcceptedMs=%.2f maxAcceptedMs=%.2f",
                s.highwayFeatureInvocations(), s.highwayFastRejects(), s.highwayAcceptedChunks(),
                s.totalHighwayFeatureNanos() / 1_000_000.0, s.avgRejectedFeatureMicros(),
                s.avgAcceptedFeatureMillis(), s.maxAcceptedFeatureMillis())), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "timingMs planner=%.2f context=%.2f terrain=%.2f profile=%.2f bridge=%.2f tunnel=%.2f node=%.2f segment=%.2f render=%.2f write=%.2f",
                s.plannerQueryNanos() / 1_000_000.0, s.contextBuildNanos() / 1_000_000.0,
                s.terrainSamplingNanos() / 1_000_000.0, s.profileBuildNanos() / 1_000_000.0,
                s.bridgeResolverNanos() / 1_000_000.0, s.tunnelResolverNanos() / 1_000_000.0,
                s.interchangePlanningNanos() / 1_000_000.0, s.engineeringSegmentBuildNanos() / 1_000_000.0,
                s.renderNanos() / 1_000_000.0, s.blockWriteNanos() / 1_000_000.0)), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "clearance runs=%d snapshotColumns=%d missing=%d coreChecked=%d rowChecked=%d airspaceChecked=%d removed=%d vegetationRemoved=%d remainingLogs=%d remainingLeaves=%d remainingVegetation=%d floatingPrevented=%d snapshotMs=%.2f clearanceMs=%.2f",
                s.naturalClearancePassRuns(), s.preConstructionSnapshotColumns(),
                s.snapshotColumnsMissing(), s.naturalCoreRoadColumnsChecked(), s.rowColumnsChecked(),
                s.airspaceColumnsChecked(), s.naturalClearanceBlocksRemoved(),
                s.naturalVegetationBlocksRemoved(), s.remainingRowLogs(), s.remainingRowLeaves(),
                s.remainingRowVegetation(), s.naturalFloatingTerrainPrevented(),
                s.preConstructionSnapshotNanos() / 1_000_000.0,
                s.clearancePassNanos() / 1_000_000.0)), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "snapshot heightmap=WORLD_SURFACE worldSurfaceColumns=" + s.snapshotWorldSurfaceColumns()
                        + " underreportedTopColumns=" + s.snapshotUnderreportedTopColumns()
                        + " upwardCorrectionBlocks=" + s.snapshotUpwardCorrectionBlocks()
                        + " topVerificationFailures=" + s.snapshotTopVerificationFailures()), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "hygiene invocations=%d accepted=%d fastRejects=%d scanned=%d cleared=%d components=%d logs=%d leaves=%d plants=%d support=%d crossChunkWrites=%d illegalWrites=%d avgMs=%.3f maxMs=%.3f",
                s.hygieneInvocations(), s.hygieneAcceptedChunks(), s.hygieneFastRejects(),
                s.hygieneBlocksScanned(), s.hygieneBlocksCleared(), s.hygieneVegetationComponents(),
                s.hygieneLogsCleared(), s.hygieneLeavesCleared(), s.hygienePlantsCleared(),
                s.hygieneSupportBlocksCleared(), s.hygieneCrossChunkWrites(), s.hygieneIllegalWrites(),
                s.avgHygieneMillis(), s.maxHygieneMillis())), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "hygieneBounds candidates=" + s.hygieneCandidatesGenerated()
                        + " clippedOutOfRegion=" + s.hygieneCandidatesClippedOutOfRegion()
                        + " liveReads=" + s.hygieneLiveReads()
                        + " outOfRegionReadAttempts=" + s.hygieneOutOfRegionReadAttempts()
                        + " bfsNeighborsRejectedOutOfRegion="
                        + s.hygieneBfsNeighborsRejectedOutOfRegion()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "postHygiene coreObstructions=" + s.postHygieneCoreObstructions()
                        + " rowLogs=" + s.postHygieneRowLogs()
                        + " rowLeaves=" + s.postHygieneRowLeaves()
                        + " rowVegetation=" + s.postHygieneRowVegetation()
                        + " tunnelExteriorViolations=" + s.finalHygieneTunnelExteriorViolations()
                        + " interchangeStructureViolations="
                        + s.finalHygieneLegalInterchangeStructureClearanceViolations()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "clearanceSafety tunnelTouched=" + s.tunnelStationsTouchedByOpenSkyClearance()
                        + " legalInterchangeIgnored=" + s.legalInterchangeStructureBlocksIgnored()
                        + " legalInterchangeViolations="
                        + s.legalInterchangeStructureClearanceViolations()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "tunnelPolicy normalCandidates=" + s.normalTunnelCandidateStations()
                        + " promotedCandidates=" + s.promotedTunnelCandidateStations()
                        + " promotedSpans=" + s.deepCutPromotedSpans()
                        + " finalSpans=" + s.finalTunnelSpans()
                        + " deepCutEvaluated=" + s.deepCutStationsEvaluated()
                        + " deepCutCandidates=" + s.deepCutPromotionCandidates()
                        + " gapClosures=" + s.deepCutGapClosures()
                        + " portalAdjustments=" + s.deepCutPortalAdjustments()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "tunnelPolicy rejects tooShort=" + s.deepCutRejectedTooShort()
                        + " tooOpen=" + s.deepCutRejectedTooOpen()
                        + " lowCover=" + s.deepCutRejectedLowCover()
                        + " evaluationMs=" + s.deepCutEvaluationNanos() / 1_000_000.0), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "calls baseHeight=%d baseColumn=%d terrainSamples=%d profiles=%d bridgeResolvers=%d tunnelResolvers=%d nodePlans=%d baseColumnPerAccepted=%.2f baseColumnPerSegment=%.2f",
                s.getBaseHeightCalls(), s.getBaseColumnCalls(), s.terrainSampleCalls(), s.profileBuildCalls(),
                s.bridgeResolverCalls(), s.tunnelResolverCalls(), s.nodePlanCalls(),
                s.getBaseColumnCallsPerAcceptedChunk(), s.getBaseColumnCallsPerSegment())), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "cache height=%d/%d anchor=%d/%d segment=%d/%d node=%d/%d avgSegmentBuildMs=%.2f maxSegmentBuildMs=%.2f",
                s.heightCacheHits(), s.heightCacheMisses(), s.profileAnchorCacheHits(),
                s.profileAnchorCacheMisses(), s.engineeringSegmentCacheHits(),
                s.engineeringSegmentCacheMisses(), s.nodePlanCacheHits(), s.nodePlanCacheMisses(),
                s.avgEngineeringSegmentBuildMillis(), s.maxEngineeringSegmentBuildMillis())), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "cachePolicy segmentLength=" + CorridorEngineeringSegment.ENGINEERING_SEGMENT_LENGTH
                        + " halo=" + CorridorEngineeringSegment.ENGINEERING_HALO
                        + " maxSegments=" + NaturalHighwayCacheManager.MAX_ENGINEERING_SEGMENTS
                        + " eviction=accessOrderLRU singleFlight=true"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "rejectedExpensive terrain=" + s.rejectedChunkExpensiveTerrainCalls()
                        + " profile=" + s.rejectedChunkProfileBuildCalls()
                        + " bridge=" + s.rejectedChunkBridgeResolverCalls()
                        + " tunnel=" + s.rejectedChunkTunnelResolverCalls()
                        + " violations=" + s.rejectedChunkExpensiveWorkViolations()), false);
        return 1;
    }

    private static int node(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Vec3 pos = context.getSource().getPosition();
        HighwayRouteGraph graph = HighwayRouteGraph.forSeed(level.getSeed());
        HighwayTerrainSampler terrain = new HighwayTerrainSampler(level,
                level.getChunkSource().getGenerator(), level.getChunkSource().randomState());
        InterstateInterchangeNode node = InterstateInterchangeNode.fromGraph(graph, terrain);
        double distance = Math.hypot(pos.x - node.x(), pos.z - node.z());
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "[AFL HIGHWAY NODE] nodeId=%s x=%d z=%d distance=%.1f ns=%s ew=%s upper=%s lower=%s baseNsRoadY=%d baseEwRoadY=%d upperCrossingRoadY=%d lowerCrossingRoadY=%d approachLength=%d verticalClearance=%d reservedBounds=%s",
                node.id(), node.x(), node.z(), distance, node.northSouth().id(), node.eastWest().id(),
                node.upper(), node.lower(), node.baseNorthSouthRoadY(), node.baseEastWestRoadY(),
                node.upperCrossingRoadY(), node.lowerCrossingRoadY(),
                InterstateInterchangeNode.APPROACH_LENGTH,
                InterstateInterchangeNode.INTERCHANGE_VERTICAL_CLEARANCE,
                node.reservedBounds())), false);
        return 1;
    }
}
