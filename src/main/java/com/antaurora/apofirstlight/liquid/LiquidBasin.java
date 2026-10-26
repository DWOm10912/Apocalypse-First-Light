package com.antaurora.apofirstlight.liquid;

import java.util.List;

/**
 * Still liquid (docs/gameplay/water_v1_design.md 5.5): sleeping cells joined by faces open at their level. Not stepped;
 * rain into it is spread to one level over all its cells. It wakes when a perimeter face opens to a level more than
 * LiquidEngine#WAKE away (it reached its outlet, something flows in, the ground changed). Always the connected component
 * of sleeping cells, so a basin is the same after a save and load.
 */
public final class LiquidBasin {
    final List<LiquidCell> cells;
    /** Faces from a basin cell to a cell outside it. */
    final List<LiquidFace> perimeter;
    /** The height where it starts to spill, metres (readout). */
    double spill;
    boolean needsLevel;

    LiquidBasin(List<LiquidCell> cells, List<LiquidFace> perimeter) {
        this.cells = cells;
        this.perimeter = perimeter;
    }

    public List<LiquidCell> cells() {
        return cells;
    }

    public double spill() {
        return spill;
    }

    public long volume() {
        long v = 0;
        for (LiquidCell c : cells) v += c.volume;
        return v;
    }
}
