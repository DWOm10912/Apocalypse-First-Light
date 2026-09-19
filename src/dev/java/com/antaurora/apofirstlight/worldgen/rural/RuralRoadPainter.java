package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Resolves junction material once per cell, then writes only the requested chunk slice. */
public final class RuralRoadPainter {
    private RuralRoadPainter() { }
    public record Result(int attempted, int written, int main, int other) { }
    private record Surface(RuralRoadType type, int distance, int priority) { }
    public static Result paint(WorldGenLevel level, RuralPlan plan, BoundingBox chunk) {
        Map<Long, Surface> cells = new TreeMap<>();
        for (var segment : plan.roadNetwork().segments()) collect(cells, segment, chunk);
        for (var lot : plan.lots()) if (lot.access() != null)
            for (var segment : lot.access().access()) collect(cells, segment, chunk);
        int written = 0, main = 0;
        for (var entry : cells.entrySet()) {
            BlockPos p = BlockPos.of(entry.getKey());
            Surface s = entry.getValue();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1;
            if (chunk != null && (y < chunk.minY() || y > chunk.maxY())) continue;
            if (level.setBlock(new BlockPos(p.getX(), y, p.getZ()),
                    s.type().material(s.distance(), plan.deterministicSeed(), p.getX(), p.getZ()), 2)) written++;
            if (s.type() == RuralRoadType.MAIN) main++;
        }
        return new Result(cells.size(), written, main, cells.size() - main);
    }
    private static void collect(Map<Long, Surface> cells, RuralRoadSegment s, BoundingBox chunk) {
        BoundingBox b = s.bounds();
        int minX = chunk == null ? b.minX() : Math.max(b.minX(), chunk.minX());
        int maxX = chunk == null ? b.maxX() : Math.min(b.maxX(), chunk.maxX());
        int minZ = chunk == null ? b.minZ() : Math.max(b.minZ(), chunk.minZ());
        int maxZ = chunk == null ? b.maxZ() : Math.min(b.maxZ(), chunk.maxZ());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            int d = s.distance(x, z);
            int priority = s.type().layer(d) * 100 + s.type().priority;
            Surface value = new Surface(s.type(), d, priority);
            cells.merge(BlockPos.asLong(x, 0, z), value, (a, c) -> a.priority() >= c.priority() ? a : c);
        }
    }
}
