package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.ground.GroundViewV0;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.antaurora.apofirstlight.liquid.LiquidUnits.SLABS;

/**
 * A 1 m column of the liquid framework and its layers, bottom up (docs/gameplay/water_v1_design.md 5.2): the runs of
 * slabs with open area in the ground's openArea. A slab with two disconnected pockets side by side counts as one room, so
 * pockets that only meet across a slab boundary make one layer: the accepted 1 m limit (C's 6.3 b, LiquidCheck "limit").
 */
public final class LiquidColumn {
    final int x, z;
    final long key;
    final List<LiquidCell> cells;
    /** Rain short of a whole millilitre. */
    double rainCarry;
    /** Its chunk's account, set when the engine takes the column in. */
    LiquidLedger ledger;
    /** Face states read from a save whose other side was not there yet: faceKey -> {q, carry}. */
    final Map<Long, double[]> pendingFaces = new HashMap<>();

    private LiquidColumn(int x, int z, List<LiquidCell> cells) {
        this.x = x;
        this.z = z;
        this.key = key(x, z);
        this.cells = cells;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public long chunk() {
        return chunkKey(x >> 4, z >> 4);
    }

    public List<LiquidCell> cells() {
        return cells;
    }

    /** The layer holding height m, or null. */
    public LiquidCell cellAt(double m) {
        for (LiquidCell c : cells) if (c.contains(m)) return c;
        return null;
    }

    /**
     * An open-to-sky layer is kept this many slabs (4 m) above the column's last partly solid slab: above that it is open
     * air to the top of the world, which the layer stands for by itself (1 m2 a metre past its ceiling, LiquidCell#levelOf).
     */
    static final int SKY_KEEP = 4 * SLABS;

    /** The column's layers in [y0, y1) blocks from the ground; null when the ground does not know it (not loaded). */
    static LiquidColumn build(GroundViewV0 ground, int x, int z, int y0, int y1, short[] buf) {
        int n = (y1 - y0) * SLABS;
        ground.openArea(x, z, y0, y1, buf);
        if (buf[0] == GroundViewV0.UNKNOWN) return null;
        List<LiquidCell> cells = new ArrayList<>(2);
        LiquidColumn column = new LiquidColumn(x, z, cells);
        int s = 0;
        while (s < n) {
            while (s < n && buf[s] <= 0) s++;
            if (s >= n) break;
            int start = s;
            while (s < n && buf[s] > 0) s++;
            boolean sky = s == n;
            int end = s;
            if (sky) {
                int partial = start;
                for (int k = start; k < n; k++) if (buf[k] < LiquidUnits.AREA) partial = k + 1;
                end = Math.min(n, partial + SKY_KEEP);
            }
            int[] area = new int[end - start];
            for (int k = 0; k < area.length; k++) area[k] = buf[start + k];
            LiquidCell cell = new LiquidCell(column, y0 * SLABS + start, area, sky);
            // the floor's film (LiquidSoak): the block the floor stands on, or the partly solid one it is in
            var kind = ground.materialAt(x, Math.floorDiv(cell.floorSlab - 1, SLABS), z).kind();
            double m2 = area[0] / (double) LiquidUnits.AREA;
            cell.storage = Math.round(LiquidSoak.storageMm(kind) * m2 * 1000);
            cell.soakPerTick = LiquidSoak.infiltrationMmH(kind) * m2 * LiquidUnits.ML_PER_TICK_PER_MM_H;
            cell.dryPerTick = LiquidSoak.EVAPORATION_MM_H * m2 * LiquidUnits.ML_PER_TICK_PER_MM_H;
            cells.add(cell);
        }
        return column;
    }
}
