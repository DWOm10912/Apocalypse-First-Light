package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Applies a HighwayProfile to a bounded corridor in a loaded ServerLevel. */
public final class HighwayRenderer {
    private static final int HALF_WIDTH_CLEARANCE = 5;
    private static final int PIER_SPACING = 28;

    private HighwayRenderer() {}

    public static HighwayRenderStats render(ServerLevel level, HighwayProfile profile, HighwayEditSession edit) {
        HighwayRenderStats stats = new HighwayRenderStats();
        var samples = profile.samples();
        double lastPier = -PIER_SPACING;
        for (int i = 0; i < samples.size() - 1; i++) {
            HighwayProfile.Sample a = samples.get(i);
            HighwayProfile.Sample b = samples.get(i + 1);
            HighwayTerrainMode mode = a.mode() == HighwayTerrainMode.VIADUCT || b.mode() == HighwayTerrainMode.VIADUCT
                    ? HighwayTerrainMode.VIADUCT : a.mode();
            stats.addMode(mode);
            int steps = Math.max(1, (int) Math.ceil(b.distance() - a.distance()));
            for (int step = 0; step < steps; step++) {
                double fraction = step / (double) steps;
                double distance = a.distance() + (b.distance() - a.distance()) * fraction;
                HighwayPlan.Point point = profile.plan().sample(distance);
                HighwayPlan.Tangent tangent = profile.plan().tangent(distance);
                int roadY = (int) Math.round(a.roadY() + (b.roadY() - a.roadY()) * fraction);
                int terrainY = (int) Math.round(a.terrainY() + (b.terrainY() - a.terrainY()) * fraction);
                renderCrossSection(level, edit, stats, point, tangent, roadY, terrainY, mode, profile.plan().width());
                if (mode == HighwayTerrainMode.VIADUCT && distance - lastPier >= PIER_SPACING) {
                    renderPier(level, edit, stats, point, tangent, roadY, terrainY, profile.plan().width());
                    lastPier = distance;
                }
            }
        }
        HighwayProfile.Sample last = samples.get(samples.size() - 1);
        renderCrossSection(level, edit, stats, new HighwayPlan.Point(last.x(), last.z()),
                new HighwayPlan.Tangent(last.tangentX(), last.tangentZ()), last.roadY(), last.terrainY(), last.mode(), profile.plan().width());
        return stats;
    }

    private static void renderCrossSection(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                           HighwayPlan.Point point, HighwayPlan.Tangent tangent, int roadY,
                                           int terrainY, HighwayTerrainMode mode, int width) {
        int half = width / 2;
        double rightX = -tangent.z();
        double rightZ = tangent.x();
        for (int lateral = -half; lateral <= half; lateral++) {
            BlockPos column = new BlockPos((int) Math.round(point.x() + rightX * lateral), roadY,
                    (int) Math.round(point.z() + rightZ * lateral));
            BlockState surface = surfaceState(width, lateral);
            if (edit.set(column, surface)) {
                stats.blocksPlaced++;
                if (mode == HighwayTerrainMode.VIADUCT) stats.viaductBlocks++;
            }
            if (edit.set(column.below(), HighwayPalette.BASE)) {
                stats.blocksPlaced++;
                if (mode == HighwayTerrainMode.VIADUCT) stats.viaductBlocks++;
            }
            if (edit.set(column.below(2), HighwayPalette.SUB_BASE)) {
                stats.blocksPlaced++;
                if (mode == HighwayTerrainMode.VIADUCT) stats.viaductBlocks++;
            }

            if (mode == HighwayTerrainMode.FILL) {
                int lower = Math.max(level.getMinBuildHeight(), roadY - HighwayProfile.MAX_FILL_DEPTH);
                for (int y = roadY - 3; y >= Math.max(lower, terrainY); y--) {
                    int depth = roadY - 3 - y;
                    int taper = Math.max(1, half - (depth + 1) / 2);
                    if (Math.abs(lateral) <= taper && edit.set(new BlockPos(column.getX(), y, column.getZ()), HighwayPalette.FILL)) {
                        stats.blocksPlaced++;
                        stats.fillBlocks++;
                    }
                }
            } else if (mode == HighwayTerrainMode.CUT) {
                int top = Math.min(terrainY + 1, roadY + HighwayProfile.MAX_CUT_DEPTH);
                for (int y = roadY + 1; y <= top; y++) {
                    int distanceAbove = y - roadY;
                    int allowed = half + HALF_WIDTH_CLEARANCE - distanceAbove / 3;
                    if (Math.abs(lateral) <= allowed && clear(level, edit, new BlockPos(column.getX(), y, column.getZ()))) {
                        stats.blocksCleared++;
                        stats.cutBlocks++;
                    }
                }
            } else if (mode == HighwayTerrainMode.GROUND) {
                int top = Math.min(terrainY + 1, roadY + 4);
                for (int y = roadY + 1; y <= top; y++) {
                    if (clear(level, edit, new BlockPos(column.getX(), y, column.getZ()))) stats.blocksCleared++;
                }
            }
        }
    }

    private static void renderPier(ServerLevel level, HighwayEditSession edit, HighwayRenderStats stats,
                                   HighwayPlan.Point point, HighwayPlan.Tangent tangent, int roadY, int terrainY, int width) {
        int x = (int) Math.round(point.x());
        int z = (int) Math.round(point.z());
        int foundation = findFoundation(level, x, z, terrainY);
        int bottom = Math.max(level.getMinBuildHeight(), foundation + 1);
        int top = roadY - 3;
        if (bottom > top) return;
        BlockPos pier = new BlockPos(x, top, z);
        for (int y = top; y >= bottom; y--) {
            if (edit.set(new BlockPos(pier.getX(), y, pier.getZ()), HighwayPalette.PIER)) {
                stats.blocksPlaced++;
                stats.piersPlaced++;
                stats.viaductBlocks++;
            }
        }
    }

    private static int findFoundation(ServerLevel level, int x, int z, int terrainY) {
        int bottom = Math.max(level.getMinBuildHeight(), terrainY - 64);
        for (int y = terrainY - 1; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos).isEmpty()) continue;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y;
        }
        return bottom;
    }

    private static boolean clear(ServerLevel level, HighwayEditSession edit, BlockPos pos) {
        return !level.getBlockState(pos).isAir() && edit.set(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }

    private static BlockState surfaceState(int width, int lateral) {
        if (width == HighwayPlan.MAIN_WIDTH && Math.abs(lateral) <= 1) return HighwayPalette.MEDIAN;
        if (Math.abs(lateral) >= width / 2 - 1) return HighwayPalette.SHOULDER;
        return HighwayPalette.CARRIAGEWAY;
    }
}
