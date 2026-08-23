package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Final FEATURES-stage cleanup for vegetation written by this or a neighbour
 * after that neighbour's highway construction pass.  It is deliberately not a
 * renderer: it never restores road structure or changes engineering geometry.
 */
public final class HighwayFinalHygienePass {
    private static final int MAX_TREE_VISIT_BLOCKS = 1024;
    private static final int MAX_TREE_HORIZONTAL_RADIUS = 10;
    private static final int MAX_TREE_VERTICAL_RADIUS = 24;

    private HighwayFinalHygienePass() {}

    public static Result run(WorldGenLevel level, ChunkPos sourceChunk,
                             Collection<HighwayCorridor> corridors) {
        long started = System.nanoTime();
        HighwayHygieneWriter writer = new HighwayHygieneWriter(level, sourceChunk);
        Set<AirspaceColumn> airspace = new LinkedHashSet<>();
        Set<BlockPos> tunnelInterior = new LinkedHashSet<>();
        Counters counters = new Counters();
        for (HighwayCorridor corridor : corridors) {
            for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
                if (corridor.isTunnelArea(column.x(), column.z())) continue;
                // This is pure precomputed geometry.  Clip before creating any
                // block-scan candidate; no WorldGenRegion read happens here.
                if (!writer.canAccess(new BlockPos(column.x(), column.roadY(), column.z()))) {
                    counters.candidatesClippedOutOfRegion++;
                    continue;
                }
                counters.candidatesGenerated++;
                airspace.add(new AirspaceColumn(column.x(), column.roadY(), column.z()));
            }
            // A tunnel is only permitted to clean its planned bore.  Its shell
            // and exterior terrain never enter this footprint.
            for (BlockPos pos : corridor.tunnelBorePositions()) {
                if (!writer.canAccess(pos)) {
                    counters.candidatesClippedOutOfRegion++;
                    continue;
                }
                counters.candidatesGenerated++;
                tunnelInterior.add(pos);
            }
        }

        int blocksScanned = 0;
        int blocksCleared = 0;
        int plantsCleared = 0;
        int supportCleared = 0;
        Set<BlockPos> vegetationSeeds = new LinkedHashSet<>();
        for (AirspaceColumn column : airspace) {
            for (int y = column.roadY() + 1;
                y <= column.roadY() + HighwayCorridor.VERTICAL_CLEARANCE; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = readForHygiene(level, writer, pos, counters);
                if (state == null) continue;
                blocksScanned++;
                if (isRoadStructure(state)) continue;
                if (isTree(state)) {
                    vegetationSeeds.add(pos);
                    continue;
                }
                if (isFootprintNaturalObstruction(state) && writer.clear(pos)) {
                    blocksCleared++;
                    if (isSupportBlock(state)) supportCleared++;
                    else plantsCleared++;
                }
            }
        }

        // Tunnel interior is a distinct, planned airspace.  Never scan or
        // alter the surrounding shell/exterior.
        for (BlockPos pos : tunnelInterior) {
            BlockState state = readForHygiene(level, writer, pos, counters);
            if (state == null) continue;
            blocksScanned++;
            if (isTree(state)) vegetationSeeds.add(pos);
            else if (isFootprintNaturalObstruction(state) && !isRoadStructure(state) && writer.clear(pos)) {
                blocksCleared++;
                if (isSupportBlock(state)) supportCleared++;
                else plantsCleared++;
            }
        }

        int components = 0;
        int logsCleared = 0;
        int leavesCleared = 0;
        Set<BlockPos> visitedSeeds = new LinkedHashSet<>();
        for (BlockPos seed : vegetationSeeds) {
            BlockState seedState = readForHygiene(level, writer, seed, counters);
            if (visitedSeeds.contains(seed) || seedState == null || !isTree(seedState)) continue;
            Set<BlockPos> component = collectTreeComponent(level, writer, seed, counters);
            visitedSeeds.addAll(component);
            if (component.isEmpty()) continue;
            components++;
            for (BlockPos pos : component) {
                BlockState state = readForHygiene(level, writer, pos, counters);
                if (state == null) continue;
                if (!isTree(state) || isRoadStructure(state)) continue;
                if (writer.clear(pos)) {
                    blocksCleared++;
                    if (state.is(BlockTags.LOGS)) logsCleared++;
                    if (state.is(BlockTags.LEAVES)) leavesCleared++;
                }
            }
        }

        int coreObstructions = 0;
        int rowLogs = 0;
        int rowLeaves = 0;
        int rowVegetation = 0;
        for (AirspaceColumn column : airspace) {
            for (int y = column.roadY() + 1;
                 y <= column.roadY() + HighwayCorridor.VERTICAL_CLEARANCE; y++) {
                BlockState state = readForHygiene(level, writer,
                        new BlockPos(column.x(), y, column.z()), counters);
                if (state == null) continue;
                if (isTree(state)) {
                    if (state.is(BlockTags.LOGS)) rowLogs++;
                    if (state.is(BlockTags.LEAVES)) rowLeaves++;
                    rowVegetation++;
                }
                if (!state.isAir() && !state.getFluidState().isSource() && !isRoadStructure(state)) {
                    coreObstructions++;
                }
            }
        }
        return new Result(blocksScanned, blocksCleared, components, logsCleared, leavesCleared, plantsCleared,
                supportCleared, writer.crossChunkWrites(), writer.illegalWrites(), writer.writes(),
                coreObstructions, rowLogs, rowLeaves, rowVegetation, 0, 0,
                counters.candidatesGenerated, counters.candidatesClippedOutOfRegion, counters.liveReads,
                counters.outOfRegionReadAttempts, counters.bfsNeighborsRejectedOutOfRegion,
                System.nanoTime() - started);
    }

    private static Set<BlockPos> collectTreeComponent(WorldGenLevel level, HighwayHygieneWriter writer,
                                                       BlockPos seed, Counters counters) {
        Set<BlockPos> component = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.immutable());
        while (!queue.isEmpty() && component.size() < MAX_TREE_VISIT_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (component.contains(pos) || !withinTreeBounds(seed, pos)) continue;
            if (!writer.canAccess(pos)) {
                counters.bfsNeighborsRejectedOutOfRegion++;
                continue;
            }
            BlockState state = readForHygiene(level, writer, pos, counters);
            if (state == null || !isTree(state)) continue;
            component.add(pos);
            queue.add(pos.above());
            queue.add(pos.below());
            queue.add(pos.north());
            queue.add(pos.south());
            queue.add(pos.east());
            queue.add(pos.west());
        }
        return component;
    }

    /** The only Final Hygiene live-read path.  Bounds gate always precedes getBlockState. */
    private static BlockState readForHygiene(WorldGenLevel level, HighwayHygieneWriter writer,
                                             BlockPos pos, Counters counters) {
        if (!writer.canAccess(pos)) {
            counters.outOfRegionReadAttempts++;
            return null;
        }
        counters.liveReads++;
        return level.getBlockState(pos);
    }

    private static boolean withinTreeBounds(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= MAX_TREE_HORIZONTAL_RADIUS
                && Math.abs(pos.getZ() - origin.getZ()) <= MAX_TREE_HORIZONTAL_RADIUS
                && Math.abs(pos.getY() - origin.getY()) <= MAX_TREE_VERTICAL_RADIUS;
    }

    private static boolean isTree(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    private static boolean isFootprintNaturalObstruction(BlockState state) {
        return state.canBeReplaced() || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.VINE) || isSupportBlock(state);
    }

    private static boolean isSupportBlock(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    /** Protect highway structure even if a future palette state is accidentally scanned. */
    private static boolean isRoadStructure(BlockState state) {
        return state.is(AflBlocks.ASPHALT.get())
                || state.is(AflBlocks.REINFORCED_CONCRETE.get())
                || state.is(AflBlocks.REINFORCED_CONCRETE_SLAB.get())
                || state.is(AflBlocks.EDGE_LANE_WHITE.get())
                || state.is(AflBlocks.EDGE_LANE_YELLOW.get())
                || state.is(AflBlocks.WHITE_LANE_DIVIDER.get())
                || state.is(AflBlocks.EDGE_LANE_WHITE_STEP_CONNECTOR.get())
                || state.is(AflBlocks.EDGE_LANE_YELLOW_STEP_CONNECTOR.get())
                || state.is(AflBlocks.WHITE_LANE_DIVIDER_STEP_CONNECTOR.get());
    }

    private record AirspaceColumn(int x, int roadY, int z) {}

    private static final class Counters {
        private int candidatesGenerated;
        private int candidatesClippedOutOfRegion;
        private int liveReads;
        private int outOfRegionReadAttempts;
        private int bfsNeighborsRejectedOutOfRegion;
    }

    public record Result(int blocksScanned, int blocksCleared, int vegetationComponents,
                         int logsCleared, int leavesCleared, int plantsCleared, int supportBlocksCleared,
                         int crossChunkWrites, int illegalWrites, int writerWrites,
                          int postCoreObstructions, int postRowLogs, int postRowLeaves,
                          int postRowVegetation, int tunnelExteriorViolations,
                         int legalInterchangeStructureClearanceViolations,
                         int candidatesGenerated, int candidatesClippedOutOfRegion,
                         int liveReads, int outOfRegionReadAttempts,
                         int bfsNeighborsRejectedOutOfRegion, long nanos) {}
}
