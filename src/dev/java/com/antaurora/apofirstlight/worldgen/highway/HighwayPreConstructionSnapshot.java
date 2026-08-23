package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Actual target-world column tops captured after planning, but before any highway mutation.
 * Cached generator/profile terrain must never be substituted for this construction snapshot.
 */
public final class HighwayPreConstructionSnapshot {
    private static final int UPWARD_VERIFICATION_RANGE = 8;

    private final Map<ColumnKey, Integer> topByColumn;
    private final int requestedColumns;
    private final int missingColumns;
    private final int blockStatesScanned;
    private final int worldSurfaceColumns;
    private final int underreportedTopColumns;
    private final int upwardCorrectionBlocks;
    private final int topVerificationFailures;
    private final long captureNanos;

    private HighwayPreConstructionSnapshot(Map<ColumnKey, Integer> topByColumn,
                                           int requestedColumns, int missingColumns,
                                           int blockStatesScanned, int worldSurfaceColumns,
                                           int underreportedTopColumns, int upwardCorrectionBlocks,
                                           int topVerificationFailures, long captureNanos) {
        this.topByColumn = Map.copyOf(topByColumn);
        this.requestedColumns = requestedColumns;
        this.missingColumns = missingColumns;
        this.blockStatesScanned = blockStatesScanned;
        this.worldSurfaceColumns = worldSurfaceColumns;
        this.underreportedTopColumns = underreportedTopColumns;
        this.upwardCorrectionBlocks = upwardCorrectionBlocks;
        this.topVerificationFailures = topVerificationFailures;
        this.captureNanos = captureNanos;
    }

    public static HighwayPreConstructionSnapshot capture(WorldGenLevel level,
                                                          HighwayCorridor corridor,
                                                          HighwayBlockWriter writer) {
        return capture(level, java.util.List.of(corridor), writer);
    }

    /** Captures all accepted corridors together so interchange rendering order cannot alter the source data. */
    public static HighwayPreConstructionSnapshot capture(WorldGenLevel level,
                                                          Collection<HighwayCorridor> corridors,
                                                          HighwayBlockWriter writer) {
        long started = System.nanoTime();
        Set<ColumnKey> requested = new LinkedHashSet<>();
        for (HighwayCorridor corridor : corridors) {
            for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
                addOwned(requested, writer, column.x(), column.roadY(), column.z());
            }
            for (HighwayCorridor.CoreRoadColumn column : corridor.coreRoadColumns()) {
                addOwned(requested, writer, column.x(), column.roadY(), column.z());
            }
        }

        Map<ColumnKey, Integer> tops = new LinkedHashMap<>(requested.size());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;
        int underreported = 0;
        int corrections = 0;
        int verificationFailures = 0;
        for (ColumnKey key : requested) {
            // FEATURES maintains WORLD_SURFACE as blocks are placed. WORLD_SURFACE_WG is
            // intentionally not maintained after PRE_FEATURES and therefore misses trees,
            // leaves, and terrain-support blocks written by decoration.
            int top = Math.min(level.getMaxBuildHeight() - 1,
                    level.getHeight(Heightmap.Types.WORLD_SURFACE, key.x(), key.z()) - 1);
            int reportedTop = top;
            while (top >= level.getMinBuildHeight()) {
                cursor.set(key.x(), top, key.z());
                scanned++;
                if (!level.getBlockState(cursor).isAir() || !level.getFluidState(cursor).isEmpty()) break;
                top--;
            }
            if (top < level.getMinBuildHeight()) verificationFailures++;

            // The heightmap is authoritative at FEATURES, but keep a small diagnostic and
            // correction window so an unexpected under-report does not reintroduce floating
            // terrain without turning the capture into a full-height scan.
            int verifiedTop = top;
            int verificationLimit = Math.min(level.getMaxBuildHeight() - 1,
                    reportedTop + UPWARD_VERIFICATION_RANGE);
            for (int y = reportedTop + 1; y <= verificationLimit; y++) {
                cursor.set(key.x(), y, key.z());
                scanned++;
                if (!level.getBlockState(cursor).isAir() || !level.getFluidState(cursor).isEmpty()) {
                    verifiedTop = y;
                }
            }
            if (verifiedTop > top) {
                underreported++;
                corrections += verifiedTop - top;
                top = verifiedTop;
            }
            tops.put(key, top);
        }
        return new HighwayPreConstructionSnapshot(tops, requested.size(), requested.size() - tops.size(), scanned,
                requested.size(), underreported, corrections, verificationFailures, System.nanoTime() - started);
    }

    private static void addOwned(Set<ColumnKey> requested, HighwayBlockWriter writer,
                                 int x, int y, int z) {
        if (writer.owns(new BlockPos(x, y, z))) requested.add(new ColumnKey(x, z));
    }

    public Integer topY(int x, int z) { return topByColumn.get(new ColumnKey(x, z)); }
    public int requestedColumns() { return requestedColumns; }
    public int capturedColumns() { return topByColumn.size(); }
    public int missingColumns() { return missingColumns; }
    public int blockStatesScanned() { return blockStatesScanned; }
    public int worldSurfaceColumns() { return worldSurfaceColumns; }
    public int underreportedTopColumns() { return underreportedTopColumns; }
    public int upwardCorrectionBlocks() { return upwardCorrectionBlocks; }
    public int topVerificationFailures() { return topVerificationFailures; }
    public long captureNanos() { return captureNanos; }

    private record ColumnKey(int x, int z) {}
}
