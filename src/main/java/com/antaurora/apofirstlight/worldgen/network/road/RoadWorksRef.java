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

    /** The last column's rock (#rock) per worker thread: the carvers ask block by block down a column. */
    private static final ThreadLocal<Object[]> ROCK = ThreadLocal.withInitial(() -> new Object[3]);

    /**
     * The rock a tunnel keeps round its bores and under its approaches against caves and carvers at block column (x, z):
     * {lo, hi} (TunnelWorks#rock), null without works or away from a tunnel.
     */
    public double[] rock(int x, int z) {
        RoadWorks w = works;
        if (w == null) return null;
        Object[] c = ROCK.get();
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (c[0] != w || c[1] == null || (long) c[1] != key) {
            c[0] = w;
            c[1] = key;
            c[2] = TunnelWorks.rock(w, x, z);
        }
        return (double[]) c[2];
    }

    /** The planned height at block column (x, z) with the works applied (g: the plan's height there). */
    public double height(int x, int z, double g) {
        RoadWorks w = works;
        if (w == null) return g;
        double f = w.densitySurface(x + 0.5, z + 0.5, g);
        return Double.isNaN(f) ? g : f;
    }
}
