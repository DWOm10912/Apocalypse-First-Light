package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.earthwork.EarthGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

/**
 * Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 3.3): what the earthwork asks of the boulders, server side. A
 * rock's buried part stands in loose ground as soil blocks; the ground skin may not cut into it (its top and bottom over
 * each earthwork vertex, BoulderData.Node#top / #bottom), and a cell of it the skin bares becomes a rock cell (its
 * collision). Overworld only, as the index.
 */
public final class BoulderEarth {
    private static final ThreadLocal<ArrayList<BoulderIndex.Root>> FOUND = ThreadLocal.withInitial(ArrayList::new);

    private BoulderEarth() {}

    private static boolean applies(Level level) {
        return BoulderBlocks.ready() && !level.isClientSide() && level.dimension() == Level.OVERWORLD && !BoulderIndex.SERVER.isEmpty();
    }

    /** A root's form vertex (i, j) of world vertex (vx, vz): turned back about the origin cell's centre (form i = x * 4 + 2). */
    static int formI(BoulderIndex.Root r, int vx, int vz) {
        return BoulderData.turnX(-r.turn & 3, vx - r.ox * EarthGrid.PER_BLOCK - 2, vz - r.oz * EarthGrid.PER_BLOCK - 2) + 2;
    }

    static int formJ(BoulderIndex.Root r, int vx, int vz) {
        return BoulderData.turnZ(-r.turn & 3, vx - r.ox * EarthGrid.PER_BLOCK - 2, vz - r.oz * EarthGrid.PER_BLOCK - 2) + 2;
    }

    /** The rock's span over world vertex (vx, vz) in quanta {bottom, top}, or null where its vertical misses it. */
    static int[] span(BoulderIndex.Root r, int vx, int vz) {
        int i = formI(r, vx, vz), j = formJ(r, vx, vz), top = r.node.top(i, j);
        if (top == BoulderData.NONE) return null;
        int base = r.oy * EarthGrid.Q;   // form y 0 (the ground it was sunk into); heights in 1/256 block = quanta
        return new int[]{base + r.node.bottom(i, j), base + top};
    }

    /**
     * Narrows the range {lo, hi} (quanta) of a ground vertex now at h by every rock on its vertical: rock under the skin, no
     * lower than its top; rock over it (an overhang), no higher than its bottom; the skin inside a rock stays where it is.
     */
    public static void limit(Level level, int vx, int vz, int h, int[] range) {
        if (!applies(level)) return;
        var found = FOUND.get();
        found.clear();
        BoulderIndex.SERVER.column(Math.floorDiv(vx, EarthGrid.PER_BLOCK), Math.floorDiv(vz, EarthGrid.PER_BLOCK), found);
        for (var r : found) {
            int[] s = span(r, vx, vz);
            if (s == null) continue;
            if (h >= s[1]) range[0] = Math.max(range[0], s[1]);
            else if (h <= s[0]) range[1] = Math.min(range[1], s[0]);
            else { range[0] = Math.max(range[0], h); range[1] = Math.min(range[1], h); }
        }
        found.clear();
        if (range[0] > range[1]) range[0] = range[1];
    }

    /** Whether a rock fills part of this cell (a dig that bares it puts a rock cell there). */
    public static boolean rock(Level level, BlockPos pos) {
        if (!applies(level)) return false;
        var found = FOUND.get();
        found.clear();
        BoulderIndex.SERVER.roots(pos.getX(), pos.getY(), pos.getZ(), found);
        boolean any = false;
        for (var r : found) if (r.cellAt(pos.getX(), pos.getY(), pos.getZ()) != null) { any = true; break; }
        found.clear();
        return any;
    }

    /** Whether a rock's top lies at the skin (h, quanta) at the middle of column (x, z): the shovel's "rock here" message. */
    public static boolean under(Level level, int x, int z, int h) {
        if (!applies(level)) return false;
        int vx = x * EarthGrid.PER_BLOCK + 2, vz = z * EarthGrid.PER_BLOCK + 2;
        var found = FOUND.get();
        found.clear();
        BoulderIndex.SERVER.column(x, z, found);
        boolean any = false;
        for (var r : found) {
            int[] s = span(r, vx, vz);
            if (s != null && Math.abs(s[1] - h) <= EarthGrid.Q / 8) { any = true; break; }
        }
        found.clear();
        return any;
    }
}
