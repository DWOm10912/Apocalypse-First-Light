package com.antaurora.apofirstlight.wiring;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Building Wiring V1 route finding (docs/gameplay/building_wiring_v1_design.md 7.3), shared by the wiring wrench (filling
 * the cells a quick sweep skipped) and the development commands: A* over wireable cells of loaded chunks, six neighbours,
 * inside the two ends' bounding box grown by a margin; also one step along a slope (one across and one up or down between
 * two roof cells or stairs, BuildingWiring#slope).
 * <ul>
 *   <li>a step costs 1, or 0.2 into a cell that already holds this circuit (joining existing wire, no new wire);</li>
 *   <li>+0.1 into a cell that holds other circuits (circuits keep apart where they can);</li>
 *   <li>+0.4 for each change of direction (straight runs, few bends);</li>
 *   <li>the estimate is the Manhattan distance (it may overrate a path along existing wire: routes stay quick and straight
 *   rather than strictly cheapest).</li>
 * </ul>
 */
public final class WiringRouter {
    public static final double STEP = 1, JOIN = 0.2, CROWDED = 0.1, TURN = 0.4;
    /** Moves: 0-5 the six neighbours (Direction 3D order), 6-13 along a slope (6 + WiringNode.slopeBit); 14 the start. */
    private static final int MOVES = 14, START = 14;
    private static final Direction[] HORIZONTAL = new Direction[8];
    private static final boolean[] UP = new boolean[8];

    static {
        for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : new boolean[]{true, false}) {
            int b = WiringNode.slopeBit(h, up);
            HORIZONTAL[b] = h;
            UP[b] = up;
        }
    }

    private WiringRouter() {}

    private record Open(double f, double g, long pos, int dir, int index) {}

    /**
     * Cells from {@code from} to {@code to}, both included, or null: no route inside the box, more than maxVisited cells
     * looked at, or longer than maxCells. Both ends must be wireable.
     */
    @Nullable
    public static List<BlockPos> route(ServerLevel level, BlockPos from, BlockPos to, int circuit, int margin, int maxVisited, int maxCells) {
        if (!usable(level, from) || !usable(level, to)) return null;
        if (from.equals(to)) return List.of(from.immutable());
        int x0 = Math.min(from.getX(), to.getX()) - margin, x1 = Math.max(from.getX(), to.getX()) + margin;
        int y0 = Math.min(from.getY(), to.getY()) - margin, y1 = Math.max(from.getY(), to.getY()) + margin;
        int z0 = Math.min(from.getZ(), to.getZ()) - margin, z1 = Math.max(from.getZ(), to.getZ()) + margin;
        long goal = to.asLong();
        List<long[]> visited = new ArrayList<>();   // {pos, parent index, length}
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
        best.defaultReturnValue(Double.MAX_VALUE);
        PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        visited.add(new long[]{from.asLong(), -1, 1});
        open.add(new Open(manhattan(from, to), 0, from.asLong(), START, 0));
        best.put(from.asLong() * 16 + START, 0);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!open.isEmpty()) {
            Open o = open.poll();
            if (o.g > best.get(o.pos * 16 + o.dir)) continue;
            if (o.pos == goal) return path(visited, o.index);
            if (visited.size() > maxVisited) return null;
            long length = visited.get(o.index)[2];
            if (length >= maxCells) continue;
            BlockPos at = BlockPos.of(o.pos);
            for (int di = 0; di < MOVES; di++) {
                if (di < 6) p.set(at).move(Direction.from3DDataValue(di));
                else p.set(BuildingWiring.slopeCell(at, HORIZONTAL[di - 6], UP[di - 6]));
                if (p.getX() < x0 || p.getX() > x1 || p.getY() < y0 || p.getY() > y1 || p.getZ() < z0 || p.getZ() > z1) continue;
                if (di >= 6 && (BuildingWiring.chunk(level, p) == null || !BuildingWiring.slope(level, at, HORIZONTAL[di - 6], UP[di - 6]))) continue;
                long q = p.asLong();
                double g = o.g + (BuildingWiring.node(level, p, circuit) != 0 ? JOIN : STEP + (BuildingWiring.any(level, p) ? CROWDED : 0))
                        + (o.dir != START && heading(o.dir) != heading(di) ? TURN : 0);
                if (g >= best.get(q * 16 + di)) continue;
                if (q != goal && !usable(level, p)) continue;
                best.put(q * 16 + di, g);
                visited.add(new long[]{q, o.index, length + 1});
                open.add(new Open(g + manhattan(p, to), g, q, di, visited.size() - 1));
            }
        }
        return null;
    }

    /** A move's heading for the turn cost: a step along a slope keeps the heading of the straight step across (a roof's wire alternates the two). */
    private static int heading(int move) {
        return move < 6 ? move : HORIZONTAL[move - 6].get3DDataValue();
    }

    private static boolean usable(ServerLevel level, BlockPos p) {
        return BuildingWiring.chunk(level, p) != null && BuildingWiring.wireable(level, p);
    }

    private static double manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> path(List<long[]> visited, int index) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = index; i >= 0; i = (int) visited.get(i)[1]) out.add(BlockPos.of(visited.get(i)[0]));
        Collections.reverse(out);
        return out;
    }
}
