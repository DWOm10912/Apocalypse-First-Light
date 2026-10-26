package com.antaurora.apofirstlight.liquid;

import static com.antaurora.apofirstlight.liquid.LiquidUnits.SLAB;
import static com.antaurora.apofirstlight.liquid.LiquidUnits.SLABS;

/**
 * Where two cells of neighbouring columns meet (docs/gameplay/water_v1_design.md 5.3, 5.4): the open width of their
 * shared edge in each slab both layers span (GroundView#openEdge), so a 0.5 m trench passes half of what a 1 m one does.
 * It carries the pipe model's flow and the part of a millilitre not moved yet. Owned by the west / north cell, like C's
 * edges.
 */
public final class LiquidFace {
    /** a: the west or north cell; b: east or south of it. */
    final LiquidCell a, b;
    final boolean south;
    final int baseSlab;
    /** Open width per slab of the overlap, 1/16 m. */
    final int[] width;
    /** cross[k]: open edge area below baseSlab + k, 1/256 m2. */
    final long[] cross;
    final int sillSlab;
    /** Both layers open to the sky: past the overlap the edge is open its whole metre. */
    final boolean openAbove;
    final boolean crossChunk;
    /** Flow, m3/s, a to b positive. */
    double q;
    /** Millilitres due but not moved yet, a to b positive (not liquid: dropping it loses nothing). */
    double carry;
    boolean removed;
    /** The last tick the flow looked at this face. */
    long seen = -1;
    /** The drawn ground is one slope across this face (GroundViewV0#ramp): -1 not asked yet, 0 no, 1 yes. */
    byte ramp = -1;

    LiquidFace(LiquidCell a, LiquidCell b, boolean south, int baseSlab, int[] width) {
        this.a = a;
        this.b = b;
        this.south = south;
        this.baseSlab = baseSlab;
        this.width = width;
        cross = new long[width.length + 1];
        int sill = -1;
        for (int k = 0; k < width.length; k++) {
            cross[k + 1] = cross[k] + width[k];
            if (sill < 0 && width[k] > 0) sill = k;
        }
        sillSlab = baseSlab + sill;
        openAbove = a.openTop && b.openTop;
        crossChunk = a.column.chunk() != b.column.chunk();
    }

    /** Open edge area below height m, m2: what the flow can pass through when the higher side stands at m. */
    public double crossSection(double m) {
        double s = m * SLABS - baseSlab;
        if (s <= 0) return 0;
        int n = width.length;
        if (s >= n) return (cross[n] + (openAbove ? SLABS * (s - n) : 0)) / 256.0;
        int k = (int) s;
        return (cross[k] + width[k] * (s - k)) / 256.0;
    }

    /** Open width at height m, metres (at least a slab, for the flow's depth). */
    double topWidth(double m) {
        int k = (int) Math.floor(m * SLABS - baseSlab);
        if (openAbove && k >= width.length) return 1;
        k = Math.max(0, Math.min(width.length - 1, k));
        return Math.max(SLAB, width[k] * SLAB);
    }

    public double sill() {
        return sillSlab / (double) SLABS;
    }

    public double flow() {
        return q;
    }

    /** The cell on the other side from c. */
    public LiquidCell other(LiquidCell c) {
        return c == a ? b : a;
    }

    long key() {
        return key(south, a.floorSlab, b.floorSlab);
    }

    static long key(boolean south, int aFloor, int bFloor) {
        return ((south ? 1L : 0L) << 62) | ((long) (aFloor + (1 << 24)) << 31) | (bFloor + (1L << 24));
    }
}
