package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Building Power V1 (docs/gameplay/building_power_v1_plan.md "V1 定案", docs/models/building_power_v1.md): the building a
 * Distribution Panel serves. A building is the footprint of columns under one roof: starting from the panel's column, a
 * flood over horizontally adjacent columns that are covered, i.e. that hold any built block within 1..12 blocks above the
 * panel (walls and the roof count; air, fluids and natural terrain / vegetation, tag
 * {@code apocalypse_firstlight:not_building_cover}, do not). Outdoor ground, trees and hillsides stop the flood; a
 * building's own walls, roof and attached canopies are inside it. Devices count when their position is in the footprint
 * and within 6 below / 12 above the panel. More than {@link #MAX_COLUMNS} columns (an open area, a cave) is OPEN: the
 * panel then serves no hidden wiring. Panels register their footprint per level, so a Service Meter Box finds the panel
 * of the building whose wall it hangs on, and a second panel in an already served building stays inactive (DUPLICATE).
 */
public final class BuildingPowerZone {
    public static final int MAX_COLUMNS = 4096, ABOVE = 12, BELOW = 6;
    public static final TagKey<Block> NOT_COVER = TagKey.create(Registries.BLOCK,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "not_building_cover"));

    public enum Status { OK, OPEN, DUPLICATE }

    /** columns: packed (x, z) as ChunkPos.asLong; y range of the devices served. */
    public record Footprint(LongSet columns, int minY, int maxY, Status status) {
        public boolean contains(BlockPos pos) {
            return pos.getY() >= minY && pos.getY() <= maxY && columns.contains(ChunkPos.asLong(pos.getX(), pos.getZ()));
        }
        public boolean containsColumn(int x, int z) { return columns.contains(ChunkPos.asLong(x, z)); }
    }

    private static final Map<Level, Map<BlockPos, Footprint>> PANELS = new WeakHashMap<>();

    private BuildingPowerZone() {}

    public static Footprint compute(ServerLevel level, BlockPos panel) {
        int y0 = panel.getY();
        LongSet columns = new LongOpenHashSet();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        queue.add(new long[]{panel.getX(), panel.getZ()});
        columns.add(ChunkPos.asLong(panel.getX(), panel.getZ()));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Status status = Status.OK;
        while (!queue.isEmpty()) {
            long[] c = queue.remove();
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : steps) {
                int x = (int) c[0] + d[0], z = (int) c[1] + d[1];
                long key = ChunkPos.asLong(x, z);
                if (columns.contains(key) || !covered(level, cursor, x, y0, z)) continue;
                if (columns.size() >= MAX_COLUMNS) { status = Status.OPEN; queue.clear(); break; }
                columns.add(key);
                queue.add(new long[]{x, z});
            }
        }
        return new Footprint(columns, y0 - BELOW, y0 + ABOVE, status);
    }

    /** A built block within 1..ABOVE above y0 (an unloaded column counts as open). */
    private static boolean covered(ServerLevel level, BlockPos.MutableBlockPos cursor, int x, int y0, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        for (int dy = 1; dy <= ABOVE; dy++) {
            BlockState s = level.getBlockState(cursor.set(x, y0 + dy, z));
            if (s.isAir() || !s.getFluidState().isEmpty() || s.is(NOT_COVER)) continue;
            return true;
        }
        return false;
    }

    // ---- per-level registry ----

    /** Records the panel's footprint; the status becomes DUPLICATE when another active panel already serves its column. */
    public static Footprint register(Level level, BlockPos panel, Footprint footprint) {
        Map<BlockPos, Footprint> panels = PANELS.computeIfAbsent(level, k -> new HashMap<>());
        Footprint result = footprint;
        if (footprint.status() == Status.OK) {
            for (Map.Entry<BlockPos, Footprint> other : panels.entrySet()) {
                if (other.getKey().equals(panel) || other.getValue().status() != Status.OK) continue;
                if (other.getValue().contains(panel) && other.getKey().compareTo(panel) < 0) {
                    result = new Footprint(footprint.columns(), footprint.minY(), footprint.maxY(), Status.DUPLICATE);
                    break;
                }
            }
        }
        panels.put(panel.immutable(), result);
        return result;
    }

    public static void unregister(Level level, BlockPos panel) {
        Map<BlockPos, Footprint> panels = PANELS.get(level);
        if (panels != null) panels.remove(panel);
    }

    /** The active panel whose footprint holds this column at this height, or null. */
    public static BlockPos panelServing(Level level, BlockPos pos) {
        Map<BlockPos, Footprint> panels = PANELS.get(level);
        if (panels == null) return null;
        BlockPos best = null;
        for (Map.Entry<BlockPos, Footprint> e : panels.entrySet()) {
            if (e.getValue().status() == Status.OK && e.getValue().contains(pos) && (best == null || e.getKey().compareTo(best) < 0)) best = e.getKey();
        }
        return best;
    }
}
