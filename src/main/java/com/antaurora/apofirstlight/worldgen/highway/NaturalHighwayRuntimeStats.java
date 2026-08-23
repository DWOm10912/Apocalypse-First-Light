package com.antaurora.apofirstlight.worldgen.highway;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Process-local aggregate profiling. No hot-path entry logs by design. */
public final class NaturalHighwayRuntimeStats {
    private static final LongAdder FEATURE_CALLS = new LongAdder();
    private static final LongAdder FAST_REJECTS = new LongAdder();
    private static final LongAdder ACCEPTED = new LongAdder();
    private static final LongAdder NS_QUERIED = new LongAdder();
    private static final LongAdder EW_QUERIED = new LongAdder();

    private static final LongAdder TOTAL_NANOS = new LongAdder();
    private static final LongAdder PLANNER_NANOS = new LongAdder();
    private static final LongAdder REJECT_NANOS = new LongAdder();
    private static final LongAdder CONTEXT_NANOS = new LongAdder();
    private static final LongAdder TERRAIN_NANOS = new LongAdder();
    private static final LongAdder PROFILE_NANOS = new LongAdder();
    private static final LongAdder BRIDGE_NANOS = new LongAdder();
    private static final LongAdder TUNNEL_NANOS = new LongAdder();
    private static final LongAdder NODE_NANOS = new LongAdder();
    private static final LongAdder SEGMENT_NANOS = new LongAdder();
    private static final LongAdder RENDER_NANOS = new LongAdder();
    private static final LongAdder WRITE_NANOS = new LongAdder();
    private static final LongAdder SNAPSHOT_NANOS = new LongAdder();
    private static final LongAdder CLEARANCE_NANOS = new LongAdder();

    private static final LongAdder BASE_HEIGHT_CALLS = new LongAdder();
    private static final LongAdder BASE_COLUMN_CALLS = new LongAdder();
    private static final LongAdder TERRAIN_SAMPLE_CALLS = new LongAdder();
    private static final LongAdder PROFILE_CALLS = new LongAdder();
    private static final LongAdder BRIDGE_CALLS = new LongAdder();
    private static final LongAdder TUNNEL_CALLS = new LongAdder();
    private static final LongAdder NODE_CALLS = new LongAdder();

    private static final LongAdder HEIGHT_HITS = new LongAdder();
    private static final LongAdder HEIGHT_MISSES = new LongAdder();
    private static final LongAdder ANCHOR_HITS = new LongAdder();
    private static final LongAdder ANCHOR_MISSES = new LongAdder();
    private static final LongAdder SEGMENT_HITS = new LongAdder();
    private static final LongAdder SEGMENT_MISSES = new LongAdder();
    private static final LongAdder NODE_HITS = new LongAdder();
    private static final LongAdder NODE_MISSES = new LongAdder();

    private static final LongAdder REJECTED_TERRAIN = new LongAdder();
    private static final LongAdder REJECTED_PROFILE = new LongAdder();
    private static final LongAdder REJECTED_BRIDGE = new LongAdder();
    private static final LongAdder REJECTED_TUNNEL = new LongAdder();
    private static final LongAdder REJECTED_VIOLATIONS = new LongAdder();
    private static final LongAccumulator MAX_ACCEPTED_NANOS = new LongAccumulator(Long::max, 0L);
    private static final LongAccumulator MAX_SEGMENT_NANOS = new LongAccumulator(Long::max, 0L);

    private static final LongAdder SURFACE_BLOCKS = new LongAdder();
    private static final LongAdder BLOCKS_CLEARED = new LongAdder();
    private static final LongAdder NODES = new LongAdder();
    private static final LongAdder UPPER_NS = new LongAdder();
    private static final LongAdder UPPER_EW = new LongAdder();
    private static final LongAdder DUPLICATE_ATTEMPTS = new LongAdder();
    private static final LongAdder ILLEGAL_WRITES = new LongAdder();
    private static final LongAdder PROFILE_MISMATCH = new LongAdder();
    private static final LongAdder STATION_MISMATCH = new LongAdder();
    private static final LongAdder MARKING_MISMATCH = new LongAdder();
    private static final LongAdder BRIDGE_MISMATCH = new LongAdder();
    private static final LongAdder TUNNEL_MISMATCH = new LongAdder();
    private static final LongAdder SEGMENT_BOUNDARY_MISMATCH = new LongAdder();
    private static final LongAdder CLEARANCE_PASS_RUNS = new LongAdder();
    private static final LongAdder SNAPSHOT_COLUMNS = new LongAdder();
    private static final LongAdder SNAPSHOT_COLUMNS_MISSING = new LongAdder();
    private static final LongAdder CORE_ROAD_COLUMNS_CHECKED = new LongAdder();
    private static final LongAdder ROW_COLUMNS_CHECKED = new LongAdder();
    private static final LongAdder AIRSPACE_COLUMNS_CHECKED = new LongAdder();
    private static final LongAdder REMAINING_ROW_LOGS = new LongAdder();
    private static final LongAdder REMAINING_ROW_LEAVES = new LongAdder();
    private static final LongAdder REMAINING_ROW_VEGETATION = new LongAdder();
    private static final LongAdder CLEARANCE_BLOCKS_REMOVED = new LongAdder();
    private static final LongAdder VEGETATION_BLOCKS_REMOVED = new LongAdder();
    private static final LongAdder FLOATING_TERRAIN_PREVENTED = new LongAdder();
    private static final LongAdder TUNNEL_STATIONS_TOUCHED = new LongAdder();
    private static final LongAdder LEGAL_INTERCHANGE_BLOCKS_IGNORED = new LongAdder();
    private static final LongAdder LEGAL_INTERCHANGE_CLEARANCE_VIOLATIONS = new LongAdder();
    private static final LongAdder SNAPSHOT_WORLD_SURFACE_COLUMNS = new LongAdder();
    private static final LongAdder SNAPSHOT_UNDERREPORTED_TOP_COLUMNS = new LongAdder();
    private static final LongAdder SNAPSHOT_UPWARD_CORRECTION_BLOCKS = new LongAdder();
    private static final LongAdder SNAPSHOT_TOP_VERIFICATION_FAILURES = new LongAdder();
    private static final LongAdder HYGIENE_INVOCATIONS = new LongAdder();
    private static final LongAdder HYGIENE_FAST_REJECTS = new LongAdder();
    private static final LongAdder HYGIENE_ACCEPTED_CHUNKS = new LongAdder();
    private static final LongAdder HYGIENE_BLOCKS_SCANNED = new LongAdder();
    private static final LongAdder HYGIENE_BLOCKS_CLEARED = new LongAdder();
    private static final LongAdder HYGIENE_VEGETATION_COMPONENTS = new LongAdder();
    private static final LongAdder HYGIENE_LOGS_CLEARED = new LongAdder();
    private static final LongAdder HYGIENE_LEAVES_CLEARED = new LongAdder();
    private static final LongAdder HYGIENE_PLANTS_CLEARED = new LongAdder();
    private static final LongAdder HYGIENE_SUPPORT_BLOCKS_CLEARED = new LongAdder();
    private static final LongAdder HYGIENE_CROSS_CHUNK_WRITES = new LongAdder();
    private static final LongAdder HYGIENE_ILLEGAL_WRITES = new LongAdder();
    private static final LongAdder HYGIENE_CANDIDATES_GENERATED = new LongAdder();
    private static final LongAdder HYGIENE_CANDIDATES_CLIPPED_OUT_OF_REGION = new LongAdder();
    private static final LongAdder HYGIENE_LIVE_READS = new LongAdder();
    private static final LongAdder HYGIENE_OUT_OF_REGION_READ_ATTEMPTS = new LongAdder();
    private static final LongAdder HYGIENE_BFS_NEIGHBORS_REJECTED_OUT_OF_REGION = new LongAdder();
    private static final LongAdder POST_HYGIENE_CORE_OBSTRUCTIONS = new LongAdder();
    private static final LongAdder POST_HYGIENE_ROW_LOGS = new LongAdder();
    private static final LongAdder POST_HYGIENE_ROW_LEAVES = new LongAdder();
    private static final LongAdder POST_HYGIENE_ROW_VEGETATION = new LongAdder();
    private static final LongAdder FINAL_HYGIENE_TUNNEL_EXTERIOR_VIOLATIONS = new LongAdder();
    private static final LongAdder LEGAL_INTERCHANGE_STRUCTURE_CLEARANCE_VIOLATIONS = new LongAdder();
    private static final LongAdder HYGIENE_NANOS = new LongAdder();
    private static final LongAccumulator MAX_HYGIENE_NANOS = new LongAccumulator(Long::max, 0L);

    private static final ThreadLocal<FeatureScope> CURRENT = new ThreadLocal<>();

    private NaturalHighwayRuntimeStats() {}

    public static FeatureScope beginFeature() {
        FEATURE_CALLS.increment();
        FeatureScope scope = new FeatureScope(System.nanoTime());
        CURRENT.set(scope);
        return scope;
    }

    public static void plannerQuery(long nanos, int ns, int ew) {
        PLANNER_NANOS.add(nanos);
        NS_QUERIED.add(ns);
        EW_QUERIED.add(ew);
    }

    public static void finishRejected(FeatureScope scope) {
        long elapsed = System.nanoTime() - scope.startedNanos;
        TOTAL_NANOS.add(elapsed);
        REJECT_NANOS.add(elapsed);
        FAST_REJECTS.increment();
        REJECTED_TERRAIN.add(scope.expensiveTerrainCalls);
        REJECTED_PROFILE.add(scope.profileCalls);
        REJECTED_BRIDGE.add(scope.bridgeCalls);
        REJECTED_TUNNEL.add(scope.tunnelCalls);
        if (scope.expensiveTerrainCalls != 0 || scope.profileCalls != 0
                || scope.bridgeCalls != 0 || scope.tunnelCalls != 0) REJECTED_VIOLATIONS.increment();
        CURRENT.remove();
    }

    public static void finishAccepted(FeatureScope scope) {
        long elapsed = System.nanoTime() - scope.startedNanos;
        TOTAL_NANOS.add(elapsed);
        ACCEPTED.increment();
        MAX_ACCEPTED_NANOS.accumulate(elapsed);
        CURRENT.remove();
    }

    public static void finishFailed(FeatureScope scope) {
        TOTAL_NANOS.add(System.nanoTime() - scope.startedNanos);
        CURRENT.remove();
    }

    public static void contextBuild(long nanos) { CONTEXT_NANOS.add(nanos); }
    public static void terrainSampling(long nanos) { TERRAIN_NANOS.add(nanos); }
    public static void profileBuild(long nanos) { PROFILE_NANOS.add(nanos); }
    public static void bridgeResolver(long nanos) { BRIDGE_NANOS.add(nanos); }
    public static void tunnelResolver(long nanos) { TUNNEL_NANOS.add(nanos); }
    public static void interchangePlanning(long nanos) { NODE_NANOS.add(nanos); }
    public static void engineeringSegmentBuild(long nanos) {
        SEGMENT_NANOS.add(nanos);
        MAX_SEGMENT_NANOS.accumulate(nanos);
    }
    public static void render(long nanos) { RENDER_NANOS.add(nanos); }
    public static void blockWrite(long nanos) { WRITE_NANOS.add(nanos); }
    public static void constructionSnapshot(HighwayPreConstructionSnapshot snapshot) {
        SNAPSHOT_NANOS.add(snapshot.captureNanos());
        SNAPSHOT_COLUMNS.add(snapshot.capturedColumns());
        SNAPSHOT_COLUMNS_MISSING.add(snapshot.missingColumns());
        SNAPSHOT_WORLD_SURFACE_COLUMNS.add(snapshot.worldSurfaceColumns());
        SNAPSHOT_UNDERREPORTED_TOP_COLUMNS.add(snapshot.underreportedTopColumns());
        SNAPSHOT_UPWARD_CORRECTION_BLOCKS.add(snapshot.upwardCorrectionBlocks());
        SNAPSHOT_TOP_VERIFICATION_FAILURES.add(snapshot.topVerificationFailures());
    }
    public static void hygieneFastReject() { HYGIENE_FAST_REJECTS.increment(); }
    public static void hygiene(HighwayFinalHygienePass.Result result) {
        HYGIENE_INVOCATIONS.increment();
        HYGIENE_ACCEPTED_CHUNKS.increment();
        HYGIENE_BLOCKS_SCANNED.add(result.blocksScanned());
        HYGIENE_BLOCKS_CLEARED.add(result.blocksCleared());
        HYGIENE_VEGETATION_COMPONENTS.add(result.vegetationComponents());
        HYGIENE_LOGS_CLEARED.add(result.logsCleared());
        HYGIENE_LEAVES_CLEARED.add(result.leavesCleared());
        HYGIENE_PLANTS_CLEARED.add(result.plantsCleared());
        HYGIENE_SUPPORT_BLOCKS_CLEARED.add(result.supportBlocksCleared());
        HYGIENE_CROSS_CHUNK_WRITES.add(result.crossChunkWrites());
        HYGIENE_ILLEGAL_WRITES.add(result.illegalWrites());
        HYGIENE_CANDIDATES_GENERATED.add(result.candidatesGenerated());
        HYGIENE_CANDIDATES_CLIPPED_OUT_OF_REGION.add(result.candidatesClippedOutOfRegion());
        HYGIENE_LIVE_READS.add(result.liveReads());
        HYGIENE_OUT_OF_REGION_READ_ATTEMPTS.add(result.outOfRegionReadAttempts());
        HYGIENE_BFS_NEIGHBORS_REJECTED_OUT_OF_REGION.add(result.bfsNeighborsRejectedOutOfRegion());
        POST_HYGIENE_CORE_OBSTRUCTIONS.add(result.postCoreObstructions());
        POST_HYGIENE_ROW_LOGS.add(result.postRowLogs());
        POST_HYGIENE_ROW_LEAVES.add(result.postRowLeaves());
        POST_HYGIENE_ROW_VEGETATION.add(result.postRowVegetation());
        FINAL_HYGIENE_TUNNEL_EXTERIOR_VIOLATIONS.add(result.tunnelExteriorViolations());
        LEGAL_INTERCHANGE_STRUCTURE_CLEARANCE_VIOLATIONS.add(
                result.legalInterchangeStructureClearanceViolations());
        HYGIENE_NANOS.add(result.nanos());
        MAX_HYGIENE_NANOS.accumulate(result.nanos());
    }
    public static void clearance(HighwayRenderStats stats) {
        CLEARANCE_NANOS.add(stats.clearancePassNanos);
        CLEARANCE_PASS_RUNS.add(stats.naturalClearancePassRuns);
        CORE_ROAD_COLUMNS_CHECKED.add(stats.naturalCoreRoadColumnsChecked);
        ROW_COLUMNS_CHECKED.add(stats.rowColumnsChecked);
        AIRSPACE_COLUMNS_CHECKED.add(stats.airspaceColumnsChecked);
        REMAINING_ROW_LOGS.add(stats.remainingRowLogs);
        REMAINING_ROW_LEAVES.add(stats.remainingRowLeaves);
        REMAINING_ROW_VEGETATION.add(stats.remainingRowVegetation);
        CLEARANCE_BLOCKS_REMOVED.add(stats.naturalClearanceBlocksRemoved);
        VEGETATION_BLOCKS_REMOVED.add(stats.naturalVegetationBlocksRemoved);
        FLOATING_TERRAIN_PREVENTED.add(stats.naturalFloatingTerrainPrevented);
        TUNNEL_STATIONS_TOUCHED.add(stats.tunnelStationsTouchedByOpenSkyClearance);
        LEGAL_INTERCHANGE_BLOCKS_IGNORED.add(stats.legalInterchangeStructureBlocksIgnored);
        LEGAL_INTERCHANGE_CLEARANCE_VIOLATIONS.add(stats.legalInterchangeStructureClearanceViolations);
    }

    public static void getBaseHeightCall() { BASE_HEIGHT_CALLS.increment(); markTerrain(); }
    public static void getBaseColumnCall() { BASE_COLUMN_CALLS.increment(); markTerrain(); }
    public static void terrainSampleCall() { TERRAIN_SAMPLE_CALLS.increment(); }
    public static void profileBuildCall() { PROFILE_CALLS.increment(); scope(s -> s.profileCalls++); }
    public static void bridgeResolverCall() { BRIDGE_CALLS.increment(); scope(s -> s.bridgeCalls++); }
    public static void tunnelResolverCall() { TUNNEL_CALLS.increment(); scope(s -> s.tunnelCalls++); }
    public static void nodePlanCall() { NODE_CALLS.increment(); }
    private static void markTerrain() { scope(s -> s.expensiveTerrainCalls++); }
    private static void scope(java.util.function.Consumer<FeatureScope> action) {
        FeatureScope scope = CURRENT.get();
        if (scope != null) action.accept(scope);
    }

    public static void heightCacheHit() { HEIGHT_HITS.increment(); }
    public static void heightCacheMiss() { HEIGHT_MISSES.increment(); }
    public static void profileAnchorCacheHit() { ANCHOR_HITS.increment(); }
    public static void profileAnchorCacheMiss() { ANCHOR_MISSES.increment(); }
    public static void engineeringSegmentCacheHit() { SEGMENT_HITS.increment(); }
    public static void engineeringSegmentCacheMiss() { SEGMENT_MISSES.increment(); }
    public static void nodePlanCacheHit() { NODE_HITS.increment(); }
    public static void nodePlanCacheMiss() { NODE_MISSES.increment(); }

    public static void placement(int surfaceBlocks, int blocksCleared, int duplicateAttempts, int illegalWrites) {
        SURFACE_BLOCKS.add(surfaceBlocks);
        BLOCKS_CLEARED.add(blocksCleared);
        DUPLICATE_ATTEMPTS.add(duplicateAttempts);
        ILLEGAL_WRITES.add(illegalWrites);
    }
    public static void node(InterstateInterchangeNode node) {
        NODES.increment();
        if (node.upper() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH) UPPER_NS.increment();
        else UPPER_EW.increment();
    }
    public static void profileMismatch() { PROFILE_MISMATCH.increment(); }
    public static void stationMismatch() { STATION_MISMATCH.increment(); }
    public static void markingMismatch() { MARKING_MISMATCH.increment(); }
    public static void bridgeMismatch() { BRIDGE_MISMATCH.increment(); }
    public static void tunnelMismatch() { TUNNEL_MISMATCH.increment(); }
    public static void engineeringSegmentBoundaryMismatch() { SEGMENT_BOUNDARY_MISMATCH.increment(); }

    public static Snapshot snapshot() {
        return new Snapshot(FEATURE_CALLS.sum(), FAST_REJECTS.sum(), ACCEPTED.sum(), NS_QUERIED.sum(), EW_QUERIED.sum(),
                TOTAL_NANOS.sum(), PLANNER_NANOS.sum(), REJECT_NANOS.sum(), CONTEXT_NANOS.sum(), TERRAIN_NANOS.sum(),
                PROFILE_NANOS.sum(), BRIDGE_NANOS.sum(), TUNNEL_NANOS.sum(), NODE_NANOS.sum(), SEGMENT_NANOS.sum(),
                RENDER_NANOS.sum(), WRITE_NANOS.sum(), BASE_HEIGHT_CALLS.sum(), BASE_COLUMN_CALLS.sum(),
                TERRAIN_SAMPLE_CALLS.sum(), PROFILE_CALLS.sum(), BRIDGE_CALLS.sum(), TUNNEL_CALLS.sum(), NODE_CALLS.sum(),
                HEIGHT_HITS.sum(), HEIGHT_MISSES.sum(), ANCHOR_HITS.sum(), ANCHOR_MISSES.sum(),
                SEGMENT_HITS.sum(), SEGMENT_MISSES.sum(), NODE_HITS.sum(), NODE_MISSES.sum(),
                REJECTED_TERRAIN.sum(), REJECTED_PROFILE.sum(), REJECTED_BRIDGE.sum(), REJECTED_TUNNEL.sum(),
                REJECTED_VIOLATIONS.sum(), MAX_ACCEPTED_NANOS.get(), MAX_SEGMENT_NANOS.get(),
                SURFACE_BLOCKS.sum(), BLOCKS_CLEARED.sum(), NODES.sum(), UPPER_NS.sum(), UPPER_EW.sum(),
                PROFILE_MISMATCH.sum(), STATION_MISMATCH.sum(), MARKING_MISMATCH.sum(), BRIDGE_MISMATCH.sum(),
                TUNNEL_MISMATCH.sum(), SEGMENT_BOUNDARY_MISMATCH.sum(), DUPLICATE_ATTEMPTS.sum(), ILLEGAL_WRITES.sum(),
                CLEARANCE_PASS_RUNS.sum(), SNAPSHOT_COLUMNS.sum(), SNAPSHOT_COLUMNS_MISSING.sum(),
                CORE_ROAD_COLUMNS_CHECKED.sum(), ROW_COLUMNS_CHECKED.sum(), AIRSPACE_COLUMNS_CHECKED.sum(),
                REMAINING_ROW_LOGS.sum(), REMAINING_ROW_LEAVES.sum(), REMAINING_ROW_VEGETATION.sum(),
                CLEARANCE_BLOCKS_REMOVED.sum(), VEGETATION_BLOCKS_REMOVED.sum(), FLOATING_TERRAIN_PREVENTED.sum(),
                TUNNEL_STATIONS_TOUCHED.sum(), LEGAL_INTERCHANGE_BLOCKS_IGNORED.sum(),
                LEGAL_INTERCHANGE_CLEARANCE_VIOLATIONS.sum(), SNAPSHOT_NANOS.sum(), CLEARANCE_NANOS.sum(),
                SNAPSHOT_WORLD_SURFACE_COLUMNS.sum(), SNAPSHOT_UNDERREPORTED_TOP_COLUMNS.sum(),
                SNAPSHOT_UPWARD_CORRECTION_BLOCKS.sum(), SNAPSHOT_TOP_VERIFICATION_FAILURES.sum(),
                HYGIENE_INVOCATIONS.sum(), HYGIENE_FAST_REJECTS.sum(), HYGIENE_ACCEPTED_CHUNKS.sum(),
                HYGIENE_BLOCKS_SCANNED.sum(), HYGIENE_BLOCKS_CLEARED.sum(), HYGIENE_VEGETATION_COMPONENTS.sum(),
                HYGIENE_LOGS_CLEARED.sum(), HYGIENE_LEAVES_CLEARED.sum(), HYGIENE_PLANTS_CLEARED.sum(),
                HYGIENE_SUPPORT_BLOCKS_CLEARED.sum(), HYGIENE_CROSS_CHUNK_WRITES.sum(), HYGIENE_ILLEGAL_WRITES.sum(),
                HYGIENE_CANDIDATES_GENERATED.sum(), HYGIENE_CANDIDATES_CLIPPED_OUT_OF_REGION.sum(),
                HYGIENE_LIVE_READS.sum(), HYGIENE_OUT_OF_REGION_READ_ATTEMPTS.sum(),
                HYGIENE_BFS_NEIGHBORS_REJECTED_OUT_OF_REGION.sum(),
                POST_HYGIENE_CORE_OBSTRUCTIONS.sum(), POST_HYGIENE_ROW_LOGS.sum(), POST_HYGIENE_ROW_LEAVES.sum(),
                POST_HYGIENE_ROW_VEGETATION.sum(), FINAL_HYGIENE_TUNNEL_EXTERIOR_VIOLATIONS.sum(),
                LEGAL_INTERCHANGE_STRUCTURE_CLEARANCE_VIOLATIONS.sum(), HYGIENE_NANOS.sum(),
                MAX_HYGIENE_NANOS.get());
    }

    public static final class FeatureScope {
        private final long startedNanos;
        private long expensiveTerrainCalls;
        private long profileCalls;
        private long bridgeCalls;
        private long tunnelCalls;
        private FeatureScope(long startedNanos) { this.startedNanos = startedNanos; }
    }

    public record Snapshot(
            long highwayFeatureInvocations, long highwayFastRejects, long highwayAcceptedChunks,
            long nsCorridorsQueried, long ewCorridorsQueried,
            long totalHighwayFeatureNanos, long plannerQueryNanos, long fastRejectNanos,
            long contextBuildNanos, long terrainSamplingNanos, long profileBuildNanos,
            long bridgeResolverNanos, long tunnelResolverNanos, long interchangePlanningNanos,
            long engineeringSegmentBuildNanos, long renderNanos, long blockWriteNanos,
            long getBaseHeightCalls, long getBaseColumnCalls, long terrainSampleCalls,
            long profileBuildCalls, long bridgeResolverCalls, long tunnelResolverCalls, long nodePlanCalls,
            long heightCacheHits, long heightCacheMisses, long profileAnchorCacheHits, long profileAnchorCacheMisses,
            long engineeringSegmentCacheHits, long engineeringSegmentCacheMisses,
            long nodePlanCacheHits, long nodePlanCacheMisses,
            long rejectedChunkExpensiveTerrainCalls, long rejectedChunkProfileBuildCalls,
            long rejectedChunkBridgeResolverCalls, long rejectedChunkTunnelResolverCalls,
            long rejectedChunkExpensiveWorkViolations,
            long maxAcceptedFeatureNanos, long maxEngineeringSegmentBuildNanos,
            long naturalHighwaySurfaceBlocksPlaced, long naturalHighwayBlocksCleared,
            long interchangeNodesEncountered, long interchangeUpperNS, long interchangeUpperEW,
            long crossChunkProfileMismatch, long crossChunkStationMismatch, long crossChunkMarkingPhaseMismatch,
            long crossChunkBridgeSpanMismatch, long crossChunkTunnelSpanMismatch,
            long engineeringSegmentBoundaryMismatch,
            long duplicateNaturalPlacementAttempts, long illegalCrossChunkWriteAttempts,
            long naturalClearancePassRuns, long preConstructionSnapshotColumns,
            long snapshotColumnsMissing, long naturalCoreRoadColumnsChecked,
            long rowColumnsChecked, long airspaceColumnsChecked,
            long remainingRowLogs, long remainingRowLeaves, long remainingRowVegetation,
            long naturalClearanceBlocksRemoved, long naturalVegetationBlocksRemoved,
            long naturalFloatingTerrainPrevented, long tunnelStationsTouchedByOpenSkyClearance,
            long legalInterchangeStructureBlocksIgnored,
            long legalInterchangeStructureClearanceViolations,
            long preConstructionSnapshotNanos, long clearancePassNanos,
            long snapshotWorldSurfaceColumns, long snapshotUnderreportedTopColumns,
            long snapshotUpwardCorrectionBlocks, long snapshotTopVerificationFailures,
            long hygieneInvocations, long hygieneFastRejects, long hygieneAcceptedChunks,
            long hygieneBlocksScanned, long hygieneBlocksCleared, long hygieneVegetationComponents,
            long hygieneLogsCleared, long hygieneLeavesCleared, long hygienePlantsCleared,
            long hygieneSupportBlocksCleared, long hygieneCrossChunkWrites, long hygieneIllegalWrites,
            long hygieneCandidatesGenerated, long hygieneCandidatesClippedOutOfRegion,
            long hygieneLiveReads, long hygieneOutOfRegionReadAttempts,
            long hygieneBfsNeighborsRejectedOutOfRegion,
            long postHygieneCoreObstructions, long postHygieneRowLogs, long postHygieneRowLeaves,
            long postHygieneRowVegetation, long finalHygieneTunnelExteriorViolations,
            long finalHygieneLegalInterchangeStructureClearanceViolations,
            long hygieneNanos, long maxHygieneNanos) {
        public double avgRejectedFeatureMicros() {
            return highwayFastRejects == 0 ? 0.0 : fastRejectNanos / 1_000.0 / highwayFastRejects;
        }
        public double avgAcceptedFeatureMillis() {
            long acceptedNanos = Math.max(0L, totalHighwayFeatureNanos - fastRejectNanos);
            return highwayAcceptedChunks == 0 ? 0.0 : acceptedNanos / 1_000_000.0 / highwayAcceptedChunks;
        }
        public double maxAcceptedFeatureMillis() { return maxAcceptedFeatureNanos / 1_000_000.0; }
        public double avgEngineeringSegmentBuildMillis() {
            return engineeringSegmentCacheMisses == 0 ? 0.0
                    : engineeringSegmentBuildNanos / 1_000_000.0 / engineeringSegmentCacheMisses;
        }
        public double maxEngineeringSegmentBuildMillis() { return maxEngineeringSegmentBuildNanos / 1_000_000.0; }
        public double avgHygieneMillis() {
            return hygieneInvocations == 0 ? 0.0 : hygieneNanos / 1_000_000.0 / hygieneInvocations;
        }
        public double maxHygieneMillis() { return maxHygieneNanos / 1_000_000.0; }
        public double getBaseColumnCallsPerAcceptedChunk() {
            return highwayAcceptedChunks == 0 ? 0.0 : getBaseColumnCalls / (double) highwayAcceptedChunks;
        }
        public double getBaseColumnCallsPerSegment() {
            return engineeringSegmentCacheMisses == 0 ? 0.0
                    : getBaseColumnCalls / (double) engineeringSegmentCacheMisses;
        }
    }
}
