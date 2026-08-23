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
                TUNNEL_MISMATCH.sum(), SEGMENT_BOUNDARY_MISMATCH.sum(), DUPLICATE_ATTEMPTS.sum(), ILLEGAL_WRITES.sum());
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
            long duplicateNaturalPlacementAttempts, long illegalCrossChunkWriteAttempts) {
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
        public double getBaseColumnCallsPerAcceptedChunk() {
            return highwayAcceptedChunks == 0 ? 0.0 : getBaseColumnCalls / (double) highwayAcceptedChunks;
        }
        public double getBaseColumnCallsPerSegment() {
            return engineeringSegmentCacheMisses == 0 ? 0.0
                    : getBaseColumnCalls / (double) engineeringSegmentCacheMisses;
        }
    }
}
