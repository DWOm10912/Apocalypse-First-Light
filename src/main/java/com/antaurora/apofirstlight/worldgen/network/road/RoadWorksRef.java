package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * The terrain works one noise router applies (C3.2): created empty with the router's plan density functions
 * (RandomStateSeedMixin) and filled by the world lock when the world's terrain_works gate is on (RoadNetworkRuntime.bind),
 * before the first chunk. Empty: the planned ground unchanged (other dimensions, r1 worlds, worlds created without roads,
 * and the lock's density probe, which runs before the binding).
 */
public final class RoadWorksRef {
    private volatile RoadWorks works;

    public RoadWorks get() { return works; }

    public void set(RoadWorks works) { this.works = works; }

    /** The planned height at block column (x, z) with the works applied (g: the plan's height there). */
    public double height(int x, int z, double g) {
        RoadWorks w = works;
        if (w == null) return g;
        double f = w.surface(x + 0.5, z + 0.5, g);
        return Double.isNaN(f) ? g : f;
    }
}
