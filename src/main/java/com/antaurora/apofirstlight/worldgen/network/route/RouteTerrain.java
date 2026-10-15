package com.antaurora.apofirstlight.worldgen.network.route;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

/**
 * What the national road planner reads about the land (C3.1): the C2 federal plan surface and the federal map. Read
 * only, no chunks, no world; the same answers in the game and in the offline tools.
 * <p>
 * Ground heights are "surface" heights: the top of the top block plus nothing (heightAt is the planned ground; the
 * noise fill makes the column's top block {@code ceil(h) - 1}, so its top face is at about h).
 */
public final class RouteTerrain {
    /** Water at a point: none, river water, lake water, open sea / estuary, tidal marsh (land at Y63, pools). */
    public static final int DRY = 0, RIVER = 1, LAKE = 2, SEA = 3, MARSH = 4;

    public final TerrainPlanSurface plan;
    public final FederalMap map;
    private final ThreadLocal<RiverNetwork.Column> column = ThreadLocal.withInitial(RiverNetwork.Column::new);
    private final ThreadLocal<double[]> scratch = ThreadLocal.withInitial(() -> new double[6]);

    public RouteTerrain(TerrainPlanSurface plan, FederalMap map) {
        this.plan = plan;
        this.map = map;
    }

    /** The planned ground (with the sub-grid detail). */
    public double height(double x, double z) { return plan.heightAt(x, z); }

    /** The planned ground without sub-grid detail (cheap, smooth: routing). */
    public double baseHeight(double x, double z) { return plan.baseHeightAt(x, z); }

    /**
     * Water class at a point: SEA where the sea-fill rule floods the planned ground (sea, drowned valleys, the low shore
     * beside them), LAKE where the lake fills the column, RIVER from the river network's water width, MARSH on the
     * tidal marsh flat.
     */
    public int water(double x, double z) {
        int wc = plan.waterClass(x, z);
        double h = plan.heightAt(x, z);
        if (h < 63 && plan.seaFloodAt(x, z, h)) return SEA;
        double[] share = scratch.get();
        int top = plan.lakeTopAt(x, z, share);
        if (top != Integer.MIN_VALUE && share[0] >= 0.5 && h < top + 0.5) return LAKE;
        RiverNetwork.Column c = column.get();
        plan.rivers.column((int) Math.floor(x), (int) Math.floor(z), c);
        if (c.kind == RiverNetwork.Column.RIVER) return RIVER;
        return wc == 3 ? MARSH : DRY;
    }

    /** The water surface (top of the top water block) at a wet point, else NaN. */
    public double waterSurface(double x, double z) {
        int w = water(x, z);
        if (w == SEA || w == MARSH) return RiverNetwork.SEA_WATER_TOP + 1;
        if (w == LAKE) return plan.lakeTopAt(x, z, scratch.get()) + 1;
        if (w == RIVER) {
            RiverNetwork.Column c = column.get();
            plan.rivers.column((int) Math.floor(x), (int) Math.floor(z), c);
            return c.level + 1;
        }
        return Double.NaN;
    }

    /** Mountain and plateau share (0..1): where the class's mountain grade is allowed. */
    public double rugged(double x, double z) {
        double[] w = scratch.get();
        map.physiography(x, z, w);
        return w[Physiography.MOUNTAIN.ordinal()] + w[Physiography.PLATEAU.ordinal()];
    }

    public FederalStateId state(double x, double z) { return map.governingStateAt(x, z); }

    /** True on the mainland or an island (not the sea). */
    public boolean land(double x, double z) { return map.mainlandDistance(x, z) >= 0 || map.stateAt(x, z) != null; }
}
