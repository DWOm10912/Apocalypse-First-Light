package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * GroundView's geometry (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md 6) over a GroundSource, pure Java.
 * Every block cell is one of:
 * <ul>
 *   <li>an edited column's skin cell: its 32 skin triangles (EarthGrid) inside the cell;</li>
 *   <li>a carved or worn natural block: its 1/8 m sub-cells left (RockCarve, RockRelief);</li>
 *   <li>a sloped natural top: the two triangles through its corners (SmoothRules#surface), rising up to {@link #RISE}
 *   above its floor into the open cells above;</li>
 *   <li>a drawn road top (road drainage D1, GroundSource#drawn): a pavement's design surface or graded turf's triangles
 *   as 32 triangles on the 0.25 m vertices, like a skin cell, rising up to {@link #RISE};</li>
 *   <li>any other block: its boxes (air, plants and fluids have none).</li>
 * </ul>
 * Heights are integrated exactly over the triangles; slab values are rounded by the running total (6.3), so any run of
 * slabs is within one unit of the exact sum. Not thread safe: one per thread; columns are cached for one call only.
 */
public final class GroundGeometry {
    public static final int SLABS = 16, AREA = 256, WIDTH = 16;
    public static final int UNKNOWN = -1;
    /** surfaceGrid: no ground down to the bottom (EarthGrid.NONE), the column not loaded */
    public static final int NONE = EarthGrid.NONE, UNKNOWN_HEIGHT = Integer.MIN_VALUE + 1;
    /** edges (GroundView maps Direction onto these) */
    public static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;
    /** strips an edge is sampled in (1/16 m wide, at their centres) */
    static final int STRIPS = 16;
    /** how far a sloped top may rise above its floor (SmoothRules corners: up to 8 quarters) */
    static final int RISE = 2;
    /** units per m3: 1/256 m2 x 1/16 m = 0.244 L, one earthwork quantum */
    static final double UNITS = 4096;

    private static final int OPEN = 0, EARTH = 1, CARVE = 2, SMOOTH = 3, BOXES = 4, DRAWN = 5;
    private static final Cell OPEN_CELL = new Cell(OPEN, 0);

    private final GroundSource src;
    private final Map<Long, Column> columns = new HashMap<>();
    private final Intervals iv = new Intervals();
    /** GroundSource#drawn's answer, copied into the cell when it is one */
    private final double[] drawn = new double[EarthGrid.SIDE * EarthGrid.SIDE];

    public GroundGeometry(GroundSource src) {
        this.src = src;
    }

    // ------------------------------------------------------------------------------------------------- queries

    /** GroundView#openArea: open area (1/256 m2) of column (x, z) per 1/16 m slab from block y0 to y1, UNKNOWN when not loaded. */
    public void openArea(int x, int z, int y0, int y1, short[] out) {
        clear();
        int n = (y1 - y0) * SLABS;
        Column c = column(x, z);
        if (!c.loaded) {
            Arrays.fill(out, 0, n, (short) UNKNOWN);
            return;
        }
        double[] solid = solidExact(c, y0, y1);
        for (int s = 0; s < n; s++) solid[s] = AREA - Math.min(AREA, solid[s]);
        long prev = 0;
        double total = 0;
        for (int s = 0; s < n; s++) {
            total += solid[s];
            // clamped, the difference carried on (floating error can push a full slab's running total over a half)
            long v = Math.max(0, Math.min(AREA, (long) Math.floor(total + 0.5) - prev));
            out[s] = (short) v;
            prev += v;
        }
    }

    /**
     * GroundView#openEdge: open width (1/16 m) per slab of the edge column (x, z) shares with its neighbour toward dir,
     * UNKNOWN when either is not loaded. Computed from the west or north column of the pair whichever asks, so both sides
     * get the same numbers.
     */
    public void openEdge(int x, int z, int dir, int y0, int y1, byte[] out) {
        clear();
        int ax = x, az = z, bx = x, bz = z;
        boolean alongZ = dir == EAST || dir == WEST;   // an east or west edge runs along z
        switch (dir) {
            case EAST -> bx = x + 1;
            case WEST -> ax = x - 1;
            case SOUTH -> bz = z + 1;
            default -> az = z - 1;
        }
        int n = (y1 - y0) * SLABS;
        Column a = column(ax, az), b = column(bx, bz);
        if (!a.loaded || !b.loaded) {
            Arrays.fill(out, 0, n, (byte) UNKNOWN);
            return;
        }
        double[] open = new double[n], covered = new double[n];
        double slab = 1.0 / SLABS;
        for (int k = 0; k < STRIPS; k++) {
            double u = (k + 0.5) / STRIPS;
            iv.clear();
            for (int y = y0 - RISE; y < y1; y++) {
                if (alongZ) {
                    intervals(a.at(y), 1, u, iv);
                    intervals(b.at(y), 0, u, iv);
                } else {
                    intervals(a.at(y), u, 1, iv);
                    intervals(b.at(y), u, 0, iv);
                }
            }
            Arrays.fill(covered, 0);
            iv.merge();
            for (int i = 0; i < iv.n; i += 2) spread(iv.v[i], iv.v[i + 1], y0, covered);
            // a strip 1/16 m wide, open over L m of a slab: L / 16 m2, 16 L in 1/256 m2 (the slab's width in 1/16 m)
            for (int s = 0; s < n; s++) open[s] += 16 * Math.max(0, slab - covered[s]);
        }
        long prev = 0;
        double total = 0;
        for (int s = 0; s < n; s++) {
            total += Math.min(WIDTH, open[s]);
            long v = Math.max(0, Math.min(WIDTH, (long) Math.floor(total + 0.5) - prev));
            out[s] = (byte) v;
            prev += v;
        }
    }

    /**
     * GroundView#surfaceBelow: the top of the highest solid whose bottom is at or below y in the column at (px, pz), so a
     * point inside the ground gets that ground's top; NaN when there is none down to the bottom or the column is not loaded.
     */
    public double surfaceBelow(double px, double y, double pz) {
        clear();
        return below(px, y, pz);
    }

    /**
     * GroundView#surfaceGrid: the ground height in quanta (1/256 m) at the 65 x 65 vertices (0.25 m apart) of chunk
     * (cx, cz), out[vz * 65 + vx], going down from the top of block yFrom; the east and south row are the neighbours' first
     * vertices. NONE where there is no ground, UNKNOWN_HEIGHT where the column is not loaded.
     */
    public void surfaceGrid(int cx, int cz, int yFrom, int[] out) {
        clear();
        int side = 16 * EarthGrid.PER_BLOCK + 1;
        for (int vz = 0; vz < side; vz++) for (int vx = 0; vx < side; vx++) {
            double px = cx * 16 + vx / (double) EarthGrid.PER_BLOCK, pz = cz * 16 + vz / (double) EarthGrid.PER_BLOCK;
            if (!column((int) Math.floor(px), (int) Math.floor(pz)).loaded) {
                out[vz * side + vx] = UNKNOWN_HEIGHT;
                continue;
            }
            double h = below(px, yFrom + 1 - 1e-9, pz);
            out[vz * side + vx] = Double.isNaN(h) ? NONE : (int) Math.round(h * EarthGrid.Q);
        }
    }

    /** The exact solid volume (units, not capped) of column (x, z) per slab from y0 to y1 (offline checks); null when not loaded. */
    public double[] solidExact(int x, int z, int y0, int y1) {
        clear();
        Column c = column(x, z);
        return c.loaded ? solidExact(c, y0, y1) : null;
    }

    // ------------------------------------------------------------------------------------------------- columns and cells

    private static final class Cell {
        final int type, y;
        /** EARTH, DRAWN: the 25 vertex heights above the floor (blocks), EarthGrid#at */
        double[] rel;
        /** SMOOTH: corners nw, ne, sw, se above the floor (blocks) */
        float[] corners;
        long[] gone;
        double[] boxes;

        Cell(int type, int y) {
            this.type = type;
            this.y = y;
        }
    }

    private final class Column {
        final boolean loaded;
        final int x, z, top;
        final int[] earth;
        final int emin, emax, minY;
        /** cells by y - minY, made when first asked */
        final Cell[] cells;

        Column(int x, int z) {
            this.x = x;
            this.z = z;
            loaded = src.loaded(x, z);
            top = loaded ? src.top(x, z) : Integer.MIN_VALUE;
            int[] h = new int[EarthGrid.SIDE * EarthGrid.SIDE];
            earth = loaded && src.earth(x, z, h) ? h : null;
            emin = earth == null ? 0 : EarthGrid.min(earth);
            emax = earth == null ? 0 : EarthGrid.max(earth);
            minY = src.minY();
            cells = new Cell[loaded ? Math.max(0, top - minY + 1) : 0];
        }

        Cell at(int y) {
            if (y > top || y < minY) return OPEN_CELL;
            Cell c = cells[y - minY];
            if (c == null) cells[y - minY] = c = make(y);
            return c;
        }

        private Cell make(int y) {
            if (earth != null && EarthGrid.kind(emin, emax, y) == EarthGrid.SURFACE) {
                Cell c = new Cell(EARTH, y);
                c.rel = EarthGrid.relative(earth, y);
                return c;
            }
            long[] g = src.gone(x, y, z);
            if (g != null) {
                Cell c = new Cell(CARVE, y);
                c.gone = g;
                return c;
            }
            long k = src.smooth(x, y, z);
            if (k >= 0) {
                Cell c = new Cell(SMOOTH, y);
                c.corners = src.smoothTop(x, y, z, k);
                return c;
            }
            if (src.drawn(x, y, z, drawn)) {
                Cell c = new Cell(DRAWN, y);
                c.rel = drawn.clone();
                return c;
            }
            double[] b = src.boxes(x, y, z);
            if (b.length == 0) return OPEN_CELL;
            Cell c = new Cell(BOXES, y);
            c.boxes = b;
            return c;
        }
    }

    private Column last;

    private void clear() {
        columns.clear();
        last = null;
    }

    private Column column(int x, int z) {
        if (last != null && last.x == x && last.z == z) return last;
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        Column c = columns.get(key);
        if (c == null) columns.put(key, c = new Column(x, z));
        return last = c;
    }

    // ------------------------------------------------------------------------------------------------- volume

    private double[] solidExact(Column c, int y0, int y1) {
        double[] acc = new double[(y1 - y0) * SLABS];
        for (int y = y0 - RISE; y < y1; y++) solid(c.at(y), y0, acc);
        return acc;
    }

    /** Adds the cell's solid volume (units) per slab into acc (acc[0]: the first slab of block y0). */
    private static void solid(Cell c, int y0, double[] acc) {
        switch (c.type) {
            case EARTH, DRAWN -> {
                double[] r = c.rel;
                double area = 1.0 / (EarthGrid.PER_BLOCK * EarthGrid.PER_BLOCK * 2), hi = c.type == EARTH ? 1 : RISE;
                for (int j = 0; j < EarthGrid.PER_BLOCK; j++) for (int i = 0; i < EarthGrid.PER_BLOCK; i++) {
                    double nw = r[EarthGrid.at(i, j)], ne = r[EarthGrid.at(i + 1, j)], sw = r[EarthGrid.at(i, j + 1)], se = r[EarthGrid.at(i + 1, j + 1)];
                    // the skin's triangles nw-sw-se and nw-se-ne (EarthGrid#skin), clipped to the cell (a drawn top: to RISE)
                    tri(area, nw, sw, se, c.y, 0, hi, y0, acc);
                    tri(area, nw, se, ne, c.y, 0, hi, y0, acc);
                }
            }
            case SMOOTH -> {
                float[] k = c.corners;
                tri(0.5, k[0], k[2], k[3], c.y, 0, RISE, y0, acc);
                tri(0.5, k[0], k[3], k[1], c.y, 0, RISE, y0, acc);
            }
            case CARVE -> {
                for (int sy = 0; sy < 8; sy++) {
                    int left = 64 - Long.bitCount(c.gone[sy]);
                    if (left == 0) continue;
                    double v = left / 64.0 / SLABS * UNITS;   // per 1/16 m slab: left / 64 m2 of the sub-cell layer
                    for (int half = 0; half < 2; half++) {
                        int s = (c.y - y0) * SLABS + sy * 2 + half;
                        if (s >= 0 && s < acc.length) acc[s] += v;
                    }
                }
            }
            case BOXES -> {
                double[] b = c.boxes;
                for (int i = 0; i < b.length; i += 6) {
                    double area = (b[i + 3] - b[i]) * (b[i + 5] - b[i + 2]);
                    if (area > 0) spreadVolume(c.y + b[i + 1], c.y + b[i + 4], area, y0, acc);
                }
            }
            default -> {}
        }
    }

    /** area x the overlap of [lo, hi) with every slab, into acc. */
    private static void spreadVolume(double lo, double hi, double area, int y0, double[] acc) {
        int first = Math.max(0, (int) Math.floor(lo * SLABS) - y0 * SLABS), last = Math.min(acc.length - 1, (int) Math.ceil(hi * SLABS) - 1 - y0 * SLABS);
        for (int s = first; s <= last; s++) {
            double a = (y0 * SLABS + s) / (double) SLABS, b = a + 1.0 / SLABS;
            double o = Math.min(b, hi) - Math.max(a, lo);
            if (o > 0) acc[s] += o * area * UNITS;
        }
    }

    /**
     * Adds the solid under a skin triangle of the given area (m2) with heights f1..f3 above floor y, between lo and hi above
     * the floor, per slab into acc: exactly, as G(a) - G(b) over each slab.
     */
    static void tri(double area, double f1, double f2, double f3, int y, double lo, double hi, int y0, double[] acc) {
        double a1 = Math.min(f1, Math.min(f2, f3)), a3 = Math.max(f1, Math.max(f2, f3)), a2 = f1 + f2 + f3 - a1 - a3;
        double top = Math.min(hi, a3);
        if (top <= lo) return;
        int first = Math.max(0, (int) Math.floor((y + lo) * SLABS) - y0 * SLABS);
        int last = Math.min(acc.length - 1, (int) Math.ceil((y + top) * SLABS) - 1 - y0 * SLABS);
        for (int s = first; s <= last; s++) {
            double a = (y0 * SLABS + s) / (double) SLABS - y, b = a + 1.0 / SLABS;
            a = Math.max(a, lo);
            b = Math.min(b, hi);
            if (b <= a) continue;
            acc[s] += (above(area, a1, a2, a3, a) - above(area, a1, a2, a3, b)) * UNITS;
        }
    }

    /** G(c): the volume of a linear function over a triangle (area, sorted vertex values f1 <= f2 <= f3) above level c. */
    static double above(double area, double f1, double f2, double f3, double c) {
        if (c <= f1) return area * ((f1 + f2 + f3) / 3 - c);
        if (c >= f3) return 0;
        if (c <= f2) return area * ((f1 + f2 + f3) / 3 - c) + area * Math.pow(c - f1, 3) / (3 * (f2 - f1) * (f3 - f1));
        return area * Math.pow(f3 - c, 3) / (3 * (f3 - f1) * (f3 - f2));
    }

    // ------------------------------------------------------------------------------------------------- points

    private double below(double px, double y, double pz) {
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        Column c = column(bx, bz);
        if (!c.loaded) return Double.NaN;
        double fx = px - bx, fz = pz - bz, best = Double.NaN;
        int found = Integer.MIN_VALUE;
        for (int cy = Math.min((int) Math.floor(y), c.top); cy >= src.minY(); cy--) {
            if (found != Integer.MIN_VALUE && cy < found - RISE) break;   // a sloped top lower down may still rise past it
            iv.clear();
            intervals(c.at(cy), fx, fz, iv);
            for (int i = 0; i < iv.n; i += 2) {
                if (iv.v[i] > y) continue;
                if (Double.isNaN(best) || iv.v[i + 1] > best) best = iv.v[i + 1];
                if (found == Integer.MIN_VALUE) found = cy;
            }
        }
        return best;
    }

    /** The solid intervals [lo, hi) (world y) of cell c on the vertical line at (fx, fz), fx and fz 0..1 (1: the far face). */
    private static void intervals(Cell c, double fx, double fz, Intervals out) {
        switch (c.type) {
            case EARTH -> {
                double f = Math.min(1, EarthGrid.skin(c.rel, fx, fz));
                if (f > 0) out.add(c.y, c.y + f);
            }
            case DRAWN -> {
                double f = Math.min(RISE, EarthGrid.skin(c.rel, fx, fz));
                if (f > 0) out.add(c.y, c.y + f);
            }
            case SMOOTH -> {
                double f = Math.min(RISE, SmoothRules.surface(c.corners, fx, fz));
                if (f > 0) out.add(c.y, c.y + f);
            }
            case CARVE -> {
                int sx = sub(fx), sz = sub(fz), from = -1;
                for (int sy = 0; sy <= 8; sy++) {
                    boolean solid = sy < 8 && (c.gone[sy] >>> (sx | sz << 3) & 1L) == 0;
                    if (solid && from < 0) from = sy;
                    if (!solid && from >= 0) {
                        out.add(c.y + from / 8.0, c.y + sy / 8.0);
                        from = -1;
                    }
                }
            }
            case BOXES -> {
                double[] b = c.boxes;
                for (int i = 0; i < b.length; i += 6)
                    if (inside(fx, b[i], b[i + 3]) && inside(fz, b[i + 2], b[i + 5])) out.add(c.y + b[i + 1], c.y + b[i + 4]);
            }
            default -> {}
        }
    }

    private static int sub(double f) { return Math.max(0, Math.min(7, (int) Math.floor(f * 8))); }

    /** Whether p lies in [min, max), or on a box's face at the cell's far face (p 1) or near face (p 0). */
    private static boolean inside(double p, double min, double max) {
        if (p >= 1 - 1e-9) return max >= 1 - 1e-9 && min < 1;
        if (p <= 1e-9) return min <= 1e-9 && max > 0;
        return min <= p && p < max;
    }

    /** covered length of [lo, hi) in every slab, into acc. */
    private static void spread(double lo, double hi, int y0, double[] acc) {
        int first = Math.max(0, (int) Math.floor(lo * SLABS) - y0 * SLABS), last = Math.min(acc.length - 1, (int) Math.ceil(hi * SLABS) - 1 - y0 * SLABS);
        for (int s = first; s <= last; s++) {
            double a = (y0 * SLABS + s) / (double) SLABS, b = a + 1.0 / SLABS;
            double o = Math.min(b, hi) - Math.max(a, lo);
            if (o > 0) acc[s] += o;
        }
    }

    /** A growable list of [lo, hi) pairs. */
    private static final class Intervals {
        double[] v = new double[32];
        int n;

        void clear() { n = 0; }

        void add(double lo, double hi) {
            if (hi <= lo) return;
            if (n + 2 > v.length) v = Arrays.copyOf(v, v.length * 2);
            v[n++] = lo;
            v[n++] = hi;
        }

        /** Sorts by lo and joins overlapping pairs. */
        void merge() {
            int pairs = n / 2;
            for (int i = 1; i < pairs; i++) {   // insertion sort: a column has a few dozen pairs at most
                double lo = v[2 * i], hi = v[2 * i + 1];
                int j = i - 1;
                while (j >= 0 && v[2 * j] > lo) {
                    v[2 * j + 2] = v[2 * j];
                    v[2 * j + 3] = v[2 * j + 1];
                    j--;
                }
                v[2 * j + 2] = lo;
                v[2 * j + 3] = hi;
            }
            int w = 0;
            for (int i = 0; i < n; i += 2) {
                if (w > 0 && v[i] <= v[w - 1]) v[w - 1] = Math.max(v[w - 1], v[i + 1]);
                else {
                    v[w++] = v[i];
                    v[w++] = v[i + 1];
                }
            }
            n = w;
        }
    }
}
