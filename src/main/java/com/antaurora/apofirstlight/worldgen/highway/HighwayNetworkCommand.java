package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
                .then(Commands.literal("node").executes(HighwayNetworkCommand::node));
        CommandNode<CommandSourceStack> afl = event.getDispatcher().getRoot().getChild("afl");
        if (afl != null) afl.addChild(command.build());
        else event.getDispatcher().register(Commands.literal("afl").then(command));
    }

    private static int nearest(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Vec3 pos = context.getSource().getPosition();
        PrimaryHighwayNetwork network = new PrimaryHighwayNetwork(level.getSeed());
        PrimaryHighwayNetwork.Corridor ns = network.nearest(
                PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH, (int) Math.floor(pos.x));
        PrimaryHighwayNetwork.Corridor ew = network.nearest(
                PrimaryHighwayNetwork.Orientation.PRIMARY_EAST_WEST, (int) Math.floor(pos.z));
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "[AFL HIGHWAY NETWORK] nearestNS=%s x=%d distance=%.1f nearestEW=%s z=%d distance=%.1f",
                ns.id(), ns.fixedCoordinate(), Math.abs(pos.x - ns.fixedCoordinate()),
                ew.id(), ew.fixedCoordinate(), Math.abs(pos.z - ew.fixedCoordinate()))), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        NaturalHighwayRuntimeStats.Snapshot stats = NaturalHighwayRuntimeStats.snapshot();
        context.getSource().sendSuccess(() -> Component.literal(
                "[AFL HIGHWAY NETWORK] baseSpacing=" + PrimaryHighwayNetwork.BASE_SPACING
                        + " jitter=±" + PrimaryHighwayNetwork.POSITION_JITTER
                        + " guaranteedMinimumSpacing=" + PrimaryHighwayNetwork.MINIMUM_SPACING
                        + " profileAnchorSpacing=" + HighwayTerrainSampler.PROFILE_ANCHOR_SPACING), false);
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
        PrimaryHighwayNetwork network = new PrimaryHighwayNetwork(level.getSeed());
        PrimaryHighwayNetwork.Corridor ns = network.nearest(
                PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH, (int) Math.floor(pos.x));
        PrimaryHighwayNetwork.Corridor ew = network.nearest(
                PrimaryHighwayNetwork.Orientation.PRIMARY_EAST_WEST, (int) Math.floor(pos.z));
        HighwayTerrainSampler terrain = new HighwayTerrainSampler(level,
                level.getChunkSource().getGenerator(), level.getChunkSource().randomState());
        InterstateInterchangeNode node = network.node(ns, ew, terrain);
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
