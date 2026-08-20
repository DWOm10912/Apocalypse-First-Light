package com.antaurora.apofirstlight.world.bunker;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class BunkerSurfaceIntegration {
    public static final int MAX_ENTRANCE_SUPPORT_FILL_DEPTH = 3;
    private static final int TREE_LOG_RADIUS = 10;
    private static final int TREE_LOG_MAX_BLOCKS = 160;
    private static final int LEAF_CLEAR_RADIUS_FROM_LOG = 4;
    private static final long DRESSING_SALT = 0x5D1E551A9L;
    private static final int SILENT_AIR_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
            | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;

    private static final LocalBounds ENTRANCE_APRON_LOCAL_BOUNDS = new LocalBounds(22, 30, 4, 4, 5, 7);
    private static final LocalBounds ENTRANCE_CLEARANCE_LOCAL_BOUNDS = new LocalBounds(20, 32, 4, 9, 4, 11);
    private static final LocalBounds ROOF_BURIAL_LOCAL_BOUNDS = new LocalBounds(2, 30, 11, 11, 2, 18);
    private static final LocalBounds ROOF_VEGETATION_CLEARANCE_LOCAL_BOUNDS = new LocalBounds(1, 31, 11, 22, 1, 19);
    private static final LocalBounds ENTRANCE_KEEP_CLEAR_LOCAL_BOUNDS = new LocalBounds(20, 32, 4, 12, 4, 12);

    private BunkerSurfaceIntegration() {}

    public static SupportCheck checkEntranceSupport(ServerLevel level, StructureTemplate template,
                                                     BlockPos origin, Rotation rotation) {
        int maxGap = 0;
        for (BlockPos local : apronSamples()) {
            BlockPos world = BunkerPlacementManager.localToWorld(template, origin, rotation, local);
            int naturalGroundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    world.getX(), world.getZ());
            int gap = world.getY() - naturalGroundY;
            maxGap = Math.max(maxGap, gap);
            if (gap > MAX_ENTRANCE_SUPPORT_FILL_DEPTH || hasFluidInGap(level, world, naturalGroundY)) {
                return new SupportCheck(false, maxGap);
            }
        }
        return new SupportCheck(true, maxGap);
    }

    public static IntegrationStats apply(ServerLevel level, StructureTemplate template,
                                         BlockPos origin, Rotation rotation, long seed) {
        VegetationStats vegetation = clearVegetation(level, template, origin, rotation);
        int support = fillEntranceSupport(level, template, origin, rotation);
        int burial = dressRoof(level, template, origin, rotation, seed);
        return new IntegrationStats(support, vegetation.conflictingTrees, vegetation.logsCleared, vegetation.leavesCleared,
                vegetation.otherCleared, burial);
    }

    private static VegetationStats clearVegetation(ServerLevel level, StructureTemplate template,
                                                   BlockPos origin, Rotation rotation) {
        Set<BlockPos> clearedTrees = new HashSet<>();
        int conflictingTrees = 0;
        int logs = 0;
        int leaves = 0;
        int other = 0;
        for (LocalBounds volume : new LocalBounds[]{ENTRANCE_CLEARANCE_LOCAL_BOUNDS,
                ROOF_VEGETATION_CLEARANCE_LOCAL_BOUNDS}) {
            WorldBounds bounds = worldBounds(template, volume, origin, rotation);
            for (int x = bounds.minX; x <= bounds.maxX; x++) {
                for (int y = bounds.minY; y <= bounds.maxY; y++) {
                    for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var state = level.getBlockState(pos);
                        if (state.is(BlockTags.LOGS)) {
                            if (!clearedTrees.contains(pos)) conflictingTrees++;
                            logs += clearTreeLogs(level, pos, clearedTrees);
                        } else if (state.is(BlockTags.LEAVES)) {
                            if (clearBlock(level, pos)) leaves++;
                        } else if (!state.isAir() && isVegetation(state)) {
                            if (clearBlock(level, pos)) other++;
                        }
                    }
                }
            }
        }
        for (BlockPos log : clearedTrees) {
            leaves += clearLeavesAround(level, log);
        }
        return new VegetationStats(conflictingTrees, logs, leaves, other);
    }

    private static int clearTreeLogs(ServerLevel level, BlockPos root, Set<BlockPos> cleared) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(root);
        int count = 0;
        while (!queue.isEmpty() && count < TREE_LOG_MAX_BLOCKS) {
            BlockPos pos = queue.remove();
            if (!visited.add(pos) || root.distManhattan(pos) > TREE_LOG_RADIUS) continue;
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS)) continue;
            if (cleared.add(pos) && clearBlock(level, pos)) count++;
            for (BlockPos next : BlockPos.withinManhattan(pos, 1, 1, 1)) {
                if (!next.equals(pos)) queue.add(next);
            }
        }
        return count;
    }

    private static int clearLeavesAround(ServerLevel level, BlockPos log) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                log.offset(-LEAF_CLEAR_RADIUS_FROM_LOG, -LEAF_CLEAR_RADIUS_FROM_LOG, -LEAF_CLEAR_RADIUS_FROM_LOG),
                log.offset(LEAF_CLEAR_RADIUS_FROM_LOG, LEAF_CLEAR_RADIUS_FROM_LOG, LEAF_CLEAR_RADIUS_FROM_LOG))) {
            if (log.distManhattan(pos) <= LEAF_CLEAR_RADIUS_FROM_LOG
                    && level.getBlockState(pos).is(BlockTags.LEAVES) && clearBlock(level, pos)) cleared++;
        }
        return cleared;
    }

    private static boolean clearBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT_AIR_FLAGS);
        return true;
    }

    private static int fillEntranceSupport(ServerLevel level, StructureTemplate template,
                                           BlockPos origin, Rotation rotation) {
        int filled = 0;
        for (BlockPos local : apronSamples()) {
            BlockPos floor = BunkerPlacementManager.localToWorld(template, origin, rotation, local);
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor.getX(), floor.getZ());
            for (int y = groundY; y < floor.getY(); y++) {
                BlockPos pos = new BlockPos(floor.getX(), y, floor.getZ());
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                    filled++;
                } else if (isVegetation(level.getBlockState(pos)) && clearBlock(level, pos)) {
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                    filled++;
                }
            }
        }
        return filled;
    }

    private static int dressRoof(ServerLevel level, StructureTemplate template, BlockPos origin,
                                 Rotation rotation, long seed) {
        int placed = 0;
        RandomSource random = RandomSource.create(seed ^ DRESSING_SALT);
        for (int x = ROOF_BURIAL_LOCAL_BOUNDS.minX; x <= ROOF_BURIAL_LOCAL_BOUNDS.maxX; x++) {
            for (int z = ROOF_BURIAL_LOCAL_BOUNDS.minZ; z <= ROOF_BURIAL_LOCAL_BOUNDS.maxZ; z++) {
                BlockPos local = new BlockPos(x, ROOF_BURIAL_LOCAL_BOUNDS.minY, z);
                if (ENTRANCE_KEEP_CLEAR_LOCAL_BOUNDS.contains(local)) continue;
                int thickness = (int) (hash(seed, x / 3, z / 3) & 3L);
                if (thickness == 0) continue;
                BlockPos roof = BunkerPlacementManager.localToWorld(template, origin, rotation, local);
                if (level.getBlockState(roof).isAir()) continue;
                for (int depth = 1; depth <= thickness; depth++) {
                    BlockPos cover = roof.above(depth);
                    if (!level.getBlockState(cover).canBeReplaced()) break;
                    if (isVegetation(level.getBlockState(cover))) clearBlock(level, cover);
                    boolean top = depth == thickness || !level.getBlockState(cover.above()).canBeReplaced();
                    level.setBlock(cover, top && random.nextInt(10) > 1
                            ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static boolean hasFluidInGap(ServerLevel level, BlockPos floor, int groundY) {
        for (int y = groundY; y < floor.getY(); y++) {
            if (!level.getFluidState(new BlockPos(floor.getX(), y, floor.getZ())).isEmpty()) return true;
        }
        return false;
    }

    private static boolean isVegetation(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE) || state.is(Blocks.SNOW);
    }

    private static WorldBounds worldBounds(StructureTemplate template, LocalBounds local,
                                           BlockPos origin, Rotation rotation) {
        WorldBounds result = new WorldBounds(Integer.MAX_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);
        for (int x : new int[]{local.minX, local.maxX}) for (int y : new int[]{local.minY, local.maxY})
            for (int z : new int[]{local.minZ, local.maxZ}) result.include(
                    BunkerPlacementManager.localToWorld(template, origin, rotation, new BlockPos(x, y, z)));
        return result;
    }

    private static BlockPos[] apronSamples() {
        return new BlockPos[]{new BlockPos(22, 4, 5), new BlockPos(22, 4, 7), new BlockPos(26, 4, 5),
                new BlockPos(26, 4, 7), new BlockPos(30, 4, 5), new BlockPos(30, 4, 6),
                new BlockPos(30, 4, 7)};
    }

    private static long hash(long seed, int x, int z) {
        long value = seed + x * 341873128712L + z * 132897987541L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public record SupportCheck(boolean accepted, int maxGap) {}
    public record IntegrationStats(int supportFilled, int conflictingTrees, int logsCleared, int leavesCleared,
                                   int otherVegetationCleared, int burialPlaced) {}
    private record VegetationStats(int conflictingTrees, int logsCleared, int leavesCleared, int otherCleared) {}
    private record LocalBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
    private static final class WorldBounds {
        int minX, maxX, minY, maxY, minZ, maxZ;
        WorldBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX; this.maxX = maxX; this.minY = minY; this.maxY = maxY; this.minZ = minZ; this.maxZ = maxZ;
        }
        void include(BlockPos p) { minX=Math.min(minX,p.getX()); maxX=Math.max(maxX,p.getX()); minY=Math.min(minY,p.getY()); maxY=Math.max(maxY,p.getY()); minZ=Math.min(minZ,p.getZ()); maxZ=Math.max(maxZ,p.getZ()); }
    }
}
