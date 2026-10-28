package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.util.Arrays;

/**
 * C1 of the river plan (docs/gameplay/water_river_v1_design.md 8, docs/worldgen/river_lake_v1_design.md 5, 13): the
 * read-only river query AFL's water asks, only in worlds with the {@code river_flow} gate. For a point in a river's water,
 * the river line it is in, where along it (the arc s and the downstream tangent), how far off its centreline, and the
 * river's continuous normal water surface eta there.
 * <ul>
 *   <li>The channel (R2, 13.2): {@link #WIDEN} times the network's planned width, each bank wandering in and out by
 *   {@link #WOBBLE} of the half width over about {@link #WOBBLE_WAVES} widths; back to the planned width, without the
 *   wander, near a road's works (bridges, culverts, the driveway: {@link Narrow}) over a ramp along the line. The plan,
 *   the network's own query (RiverNetwork#column) and everything else that reads the planned width are unchanged.</li>
 *   <li>The line a point belongs to: among the lines whose water covers it, the lowest level, then the nearest
 *   centreline (as RiverNetwork#column carves it), so a tributary's water gives way to the main river's.</li>
 *   <li>eta: the top water block's level + {@link #WATER} (R2; R1 stood at vanilla's {@link #SURFACE}, so a flat bed of
 *   whole blocks held 0.9 m or nothing: a riffle could not be shallow), each whole-block step taken off the top of the reach above it
 *   over min(3 W, 16 m) (the river plan 2.1); then (R2) the fall gathered into the riffles ({@link #RIFFLE_FALL}), so a
 *   pool stands nearly flat and the water drops over the riffle below it; never rising downstream, at most
 *   {@link #ETA_SHIFT} off the evenly falling surface (none near a road's works).</li>
 *   <li>slope: the fall of eta over 200 m of the line (100 m each way where the line is that long), at least
 *   {@link #MIN_SLOPE}: the Manning slope of the reach (2.1).</li>
 *   <li>Pools (river / lake V1 4.3, R2): at a bend's apex (the centreline's curvature times the width, smoothed over a
 *   width), and on a straight reach one every 5-7 widths ({@link #POOL_SPACING_MIN} .. {@link #POOL_SPACING_MAX}, by
 *   cycle); a riffle between. About one cycle in three ({@link #DEEP_SHARE}) holds a deep hole (2.5-3.5 m).</li>
 * </ul>
 * Pure Java, derived from the network and the road works (not stored): deterministic, the same in every chunk.
 */
public final class HydroQuery {
    /** Vanilla water's drawn top over its block (m): R1's river surface. */
    public static final double SURFACE = 0.9;
    /**
     * R2: the river's surface over its top water block (m). AFL's water stands at any height; half a block puts a flat
     * bed of whole blocks 0.45 m under a riffle's water, 1.45 m under a pool's, 2.45-3.45 m under a deep hole's.
     */
    public static final double WATER = 0.45;
    /** Longest a step's slope is drawn out over (m), and per metre of width. */
    public static final double RAMP_MAX = 16, RAMP_PER_WIDTH = 3;
    /** Half the reach a slope is read over (m). */
    public static final double SLOPE_REACH = 100;
    /** A reach falls at least this (m a metre): a dead-flat one still flows. */
    public static final double MIN_SLOPE = 1e-4;
    /** R2: the channel's width over the planned width, away from a road's works. */
    public static final double WIDEN = 1.6;
    /** R2: each bank moves in and out by this share of the half width, its wavelength about WOBBLE_WAVES widths. */
    public static final double WOBBLE = 0.15, WOBBLE_WAVES = 5;
    /** R2: one pool every 5-7 widths of a straight reach (the cycle's own length), a deep hole in about this share. */
    public static final double POOL_SPACING_MIN = 5, POOL_SPACING_MAX = 7, DEEP_SHARE = 0.3;
    /** A deep hole's depth under the water (m). */
    public static final double HOLE_MIN = 2.5, HOLE_MAX = 3.5;
    /** R2: the share of the fall taken by the riffles (0 even, 1 all of it), and how far eta may stray (m). */
    public static final double RIFFLE_FALL = 0.85, ETA_SHIFT = 0.5;
    /** Road works this close (m, plus 0.8 of the planned width) hold the planned width; the ramp back to WIDEN (m, per width). */
    public static final double NARROW_CLEAR = 8, NARROW_RAMP_MIN = 12, NARROW_RAMP_PER_WIDTH = 2;
    /** Farthest past the water's edge {@link #near} reaches (m). */
    public static final double MAX_REACH = RiverNetwork.BANK_REACH;
    /** A pool this strong holds fish (deep, slow water; AFL fauna reads {@link Point#fish}). */
    public static final double FISH_POOL = 0.6;
    private static final int BUCKET = 32;

    /** Where a road's works keep the river at its planned width: works within extra (m) of (x, z). */
    public interface Narrow {
        boolean near(double x, double z, double extra);
    }

    /** Where a point is on a river. */
    public static final class Point {
        /** The line, and its segment from vertex to vertex + 1 (downstream). */
        public int line, vertex;
        /** Along the segment (0..1), and the arc from the line's source (m). */
        public double t, s;
        /** The downstream direction (unit, east and south). */
        public double tx, tz;
        /** Off the centreline (m; positive to the right looking downstream), and the channel's half width on that side. */
        public double offset, halfWidth;
        /** The channel's half widths left and right of the flow (m). */
        public double left, right;
        /** Drainage area, m2. */
        public double area;
        /** The top water block the generator carved for here (the plan's level). */
        public int level;
        /** The normal water surface (m) and the reach's slope (m a metre). */
        public double eta, slope;
        /** Past the water's edge (m; negative inside it): {@link #near}. */
        public double edge;
        /** 0 a riffle .. 1 a pool; the outer bank's side (+1 right, -1 left). */
        public double pool, outer;
        /** How much of the pool is a bend's (0 a straight reach's symmetric pool .. 1 a bend's, deep on the outer bank). */
        public double bend;
        /** A deep hole's depth here (m; 0 none), already eased in and out along the line. */
        public double hole;
        /** The bank's character on this side, 0 a beach .. 1 a steep low bank, varying along the line. */
        public double bank;
        /** 0 at a road's works (the planned width) .. 1 the full R2 channel. */
        public double widen;
        /** Deep, slow pool water: where fish live. */
        public boolean fish;
    }

    final RiverNetwork rivers;
    final float[] eta, slope, bend, left, right, seq, hole, bankL, bankR, widen;
    private final int bx0, bz0, nbx, nbz;
    private final int[] bucketStart, bucketSeg;

    HydroQuery(RiverNetwork rivers, Narrow narrow) {
        this.rivers = rivers;
        int n = rivers.vertices();
        eta = new float[n];
        slope = new float[n];
        bend = new float[n];
        left = new float[n];
        right = new float[n];
        seq = new float[n];
        hole = new float[n];
        bankL = new float[n];
        bankR = new float[n];
        widen = new float[n];
        double[] eta0 = new double[n];
        for (int line = 0; line < rivers.lines(); line++) {
            int a = rivers.start[line], b = rivers.start[line + 1];
            for (int j = a; j < b; j++) eta0[j] = rivers.vl[j] + WATER;
            // each step taken off the top of the reach above it
            for (int j = a; j < b - 1; j++) {
                int drop = rivers.vl[j] - rivers.vl[j + 1];
                if (drop <= 0) continue;
                double ramp = Math.max(RiverNetwork.STEP, Math.min(RAMP_MAX, RAMP_PER_WIDTH * rivers.vw[j]));
                double low = rivers.vl[j + 1] + WATER;
                for (int k = j; k >= a; k--) {
                    double d = (j + 0.5 - k) * RiverNetwork.STEP;
                    if (d >= ramp) break;
                    eta0[k] = Math.min(eta0[k], low + drop * d / ramp);
                }
            }
            widen(rivers, narrow, a, b);
            banks(rivers, line, a, b);
            bends(rivers, a, b);
            pools(rivers, line, a, b);
            riffleFall(eta0, a, b);
            int reach = (int) Math.round(SLOPE_REACH / RiverNetwork.STEP);
            for (int j = a; j < b; j++) {
                int u = Math.max(a, j - reach), d = Math.min(b - 1, j + reach);
                slope[j] = (float) (d > u ? Math.max(MIN_SLOPE, (eta[u] - eta[d]) / ((d - u) * RiverNetwork.STEP)) : MIN_SLOPE);
            }
        }
        // the channel's own index (it reaches past the network's planned one)
        if (n == 0) { bx0 = bz0 = nbx = nbz = 0; bucketStart = new int[1]; bucketSeg = new int[0]; return; }
        float mx = Float.MAX_VALUE, mz = Float.MAX_VALUE, Mx = -Float.MAX_VALUE, Mz = -Float.MAX_VALUE, mw = 0;
        for (int i = 0; i < n; i++) {
            mx = Math.min(mx, rivers.vx[i]); mz = Math.min(mz, rivers.vz[i]);
            Mx = Math.max(Mx, rivers.vx[i]); Mz = Math.max(Mz, rivers.vz[i]); mw = Math.max(mw, Math.max(left[i], right[i]));
        }
        float pad = mw + (float) MAX_REACH + 2;
        bx0 = (int) Math.floor((mx - pad) / BUCKET);
        bz0 = (int) Math.floor((mz - pad) / BUCKET);
        nbx = (int) Math.floor((Mx + pad) / BUCKET) - bx0 + 1;
        nbz = (int) Math.floor((Mz + pad) / BUCKET) - bz0 + 1;
        int[] count = new int[nbx * nbz + 1];
        int[] seg = new int[0];
        for (int pass = 0; pass < 2; pass++) {
            int[] at = pass == 1 ? Arrays.copyOf(count, count.length) : null;
            for (int line = 0; line < rivers.lines(); line++) for (int i = rivers.start[line]; i < rivers.start[line + 1] - 1; i++) {
                float r = Math.max(Math.max(left[i], right[i]), Math.max(left[i + 1], right[i + 1])) + (float) MAX_REACH + 2;
                float x0 = Math.min(rivers.vx[i], rivers.vx[i + 1]), x1 = Math.max(rivers.vx[i], rivers.vx[i + 1]);
                float z0 = Math.min(rivers.vz[i], rivers.vz[i + 1]), z1 = Math.max(rivers.vz[i], rivers.vz[i + 1]);
                int b0 = (int) Math.floor((x0 - r) / BUCKET) - bx0, b1 = (int) Math.floor((x1 + r) / BUCKET) - bx0;
                int c0 = (int) Math.floor((z0 - r) / BUCKET) - bz0, c1 = (int) Math.floor((z1 + r) / BUCKET) - bz0;
                for (int bz = c0; bz <= c1; bz++) for (int bx = b0; bx <= b1; bx++) {
                    int key = bz * nbx + bx;
                    if (pass == 0) count[key + 1]++;
                    else seg[at[key]++] = i;
                }
            }
            if (pass == 0) {
                for (int k = 0; k < nbx * nbz; k++) count[k + 1] += count[k];
                seg = new int[count[nbx * nbz]];
            }
        }
        bucketStart = count;
        bucketSeg = seg;
    }

    /** widen over a line: 0 at a vertex near a road's works, easing to 1 over the ramp along the line. */
    private void widen(RiverNetwork r, Narrow narrow, int a, int b) {
        if (narrow == null) { Arrays.fill(widen, a, b, 1f); return; }
        double[] dist = new double[b - a];
        double far = 1e9;
        for (int j = a; j < b; j++) {
            boolean near = narrow.near(r.vx[j], r.vz[j], 0.8 * r.vw[j] + NARROW_CLEAR);
            far = near ? 0 : far + RiverNetwork.STEP;
            dist[j - a] = far;
        }
        far = 1e9;
        for (int j = b - 1; j >= a; j--) {
            far = dist[j - a] == 0 ? 0 : far + RiverNetwork.STEP;
            double ramp = Math.max(NARROW_RAMP_MIN, NARROW_RAMP_PER_WIDTH * r.vw[j]);
            widen[j] = (float) smooth(Math.min(dist[j - a], far) / ramp);
        }
    }

    /** The banks over a line: each side's half width (the wander, 1D value noise along the arc) and its character. */
    private void banks(RiverNetwork r, int line, int a, int b) {
        double phase = 0, phaseB = 0;
        for (int j = a; j < b; j++) {
            double k = widen[j], hw = 0.5 * r.vw[j] * (1 + (WIDEN - 1) * k), w = 2 * hw;
            left[j] = (float) (hw * (1 + WOBBLE * k * (2 * noise(line, 1, phase) - 1)));
            right[j] = (float) (hw * (1 + WOBBLE * k * (2 * noise(line, 2, phase) - 1)));
            bankL[j] = (float) noise(line, 3, phaseB);
            bankR[j] = (float) noise(line, 4, phaseB);
            phase += RiverNetwork.STEP / (WOBBLE_WAVES * w);
            phaseB += RiverNetwork.STEP / (4 * w);
        }
    }

    /**
     * The straight-reach pools over a line: cycles of 5-7 widths each (its length by its hash, the width where it
     * begins), a riffle at each cycle's ends, the pool in its middle (seq 0..1); a deep hole in a cycle of about
     * DEEP_SHARE (hole = its depth), eased over a width at its ends.
     */
    private void pools(RiverNetwork r, int line, int a, int b) {
        if (b <= a) return;
        int cycle = 0;
        double len = cycleLength(r, line, cycle, a), at = hash(line, 7, -1) * len;
        float[] raw = new float[b - a];
        for (int j = a; j < b; j++) {
            while (at >= len) {
                at -= len;
                cycle++;
                len = cycleLength(r, line, cycle, j);
            }
            double u = at / len;
            seq[j] = (float) (smooth((u - 0.2) / 0.25) * (1 - smooth((u - 0.7) / 0.25)));
            raw[j - a] = hash(line, 8, cycle) < DEEP_SHARE ? (float) (HOLE_MIN + (HOLE_MAX - HOLE_MIN) * hash(line, 9, cycle)) : 0;
            at += RiverNetwork.STEP;
        }
        for (int j = a; j < b; j++) {
            int k = Math.max(1, (int) Math.round(0.5 * r.vw[j] * WIDEN / RiverNetwork.STEP));
            double sum = 0;
            int c = 0;
            for (int m = Math.max(a, j - k); m <= Math.min(b - 1, j + k); m++) { sum += raw[m - a]; c++; }
            hole[j] = (float) (sum / c * widen[j]);
        }
    }

    private double cycleLength(RiverNetwork r, int line, int cycle, int vertex) {
        double w = r.vw[vertex] * (1 + (WIDEN - 1) * widen[vertex]);
        return Math.max(4 * RiverNetwork.STEP, w * (POOL_SPACING_MIN + (POOL_SPACING_MAX - POOL_SPACING_MIN) * hash(line, 6, cycle)));
    }

    /**
     * eta over a line from the evenly falling eta0: each vertex's fall weighed by 1 - RIFFLE_FALL x its pool share and
     * spread over the pool spacing round it (the window's fall kept), then held within ETA_SHIFT x widen of eta0 and
     * never rising downstream.
     */
    private void riffleFall(double[] eta0, int a, int b) {
        int n = b - a;
        if (n == 0) return;
        double[] w = new double[n], f = new double[n];
        for (int j = a; j < b; j++) {
            w[j - a] = 1 - RIFFLE_FALL * poolAt(j);
            f[j - a] = j < b - 1 ? eta0[j] - eta0[j + 1] : 0;
        }
        double[] cw = new double[n + 1], cf = new double[n + 1];
        for (int k = 0; k < n; k++) { cw[k + 1] = cw[k] + w[k]; cf[k + 1] = cf[k] + f[k]; }
        double e = eta0[a];
        for (int j = a; j < b; j++) {
            double shift = ETA_SHIFT * widen[j];
            double v = Math.max(eta0[j] - shift, Math.min(eta0[j] + shift, e));
            if (j > a) v = Math.min(v, eta[j - 1]);
            eta[j] = (float) v;
            if (j == b - 1) break;
            int h = Math.max(1, (int) Math.round(0.5 * POOL_SPACING_MIN * rivers.vw[j] * WIDEN / RiverNetwork.STEP));
            int lo = Math.max(0, j - a - h), hi = Math.min(n - 1, j - a + h);
            double sw = cw[hi + 1] - cw[lo], sf = cf[hi + 1] - cf[lo];
            e = v - (sw > 1e-9 ? w[j - a] * sf / sw : f[j - a]);
        }
    }

    /** The pool share (0..1) at a vertex: a bend's, or a straight reach's away from the bends. */
    private double poolAt(int j) {
        double bp = poolShare(bend[j]);
        return Math.max(bp, seq[j] * (1 - Math.min(1, Math.abs(bend[j]) / 0.15)));
    }

    /**
     * bend over the line's vertices a .. b - 1: the turn between the chords half a width behind and ahead of each vertex
     * over their arc (positive turning right, x east and z south), times the width, then averaged over a width.
     */
    private void bends(RiverNetwork r, int a, int b) {
        int n = b - a;
        if (n < 3) return;
        float[] raw = new float[n];
        for (int j = a; j < b; j++) {
            int k = Math.max(2, (int) Math.round(0.5 * r.vw[j] / RiverNetwork.STEP));
            int u = Math.max(a, j - k), d = Math.min(b - 1, j + k);
            if (u == j || d == j) continue;
            double ux = r.vx[j] - r.vx[u], uz = r.vz[j] - r.vz[u], dx = r.vx[d] - r.vx[j], dz = r.vz[d] - r.vz[j];
            double turn = Math.atan2(ux * dz - uz * dx, ux * dx + uz * dz);
            raw[j - a] = (float) (turn / ((d - u) * 0.5 * RiverNetwork.STEP) * r.vw[j]);
        }
        for (int j = a; j < b; j++) {
            int k = Math.max(1, (int) Math.round(0.5 * r.vw[j] / RiverNetwork.STEP));
            double sum = 0;
            int c = 0;
            for (int m = Math.max(a, j - k); m <= Math.min(b - 1, j + k); m++) { sum += raw[m - a]; c++; }
            bend[j] = (float) (sum / c);
        }
    }

    /** A pool's share (0..1) at the bend b (curvature x width): the apex of a meander bends about 0.4. */
    public static double poolShare(double b) {
        double t = Math.max(0, Math.min(1, (Math.abs(b) - 0.08) / 0.22));
        return t * t * (3 - 2 * t);
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** 0..1 from the line, a salt and an integer (SplitMix64). */
    static double hash(int line, int salt, long i) {
        long z = line * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL + i * 0x165667B19E3779F9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    /** 1D value noise 0..1 along the phase (one value a unit, eased between). */
    private static double noise(int line, int salt, double phase) {
        long i = (long) Math.floor(phase);
        double u = smooth(phase - i);
        return hash(line, salt, i) + (hash(line, salt, i + 1) - hash(line, salt, i)) * u;
    }

    /** The network's query without road works (checks, dev tools, a world without roads): the full channel everywhere. */
    public static HydroQuery of(RiverNetwork rivers) {
        return rivers.hydro();
    }

    /** A query whose channel narrows to the planned width at the given road works (a world's: RiverChannels). */
    public static HydroQuery of(RiverNetwork rivers, Narrow narrow) {
        return narrow == null ? rivers.hydro() : new HydroQuery(rivers, narrow);
    }

    public RiverNetwork rivers() {
        return rivers;
    }

    /** The point (x, z) (m) in a river's water: true and out filled; false outside every river. */
    public boolean river(double x, double z, Point out) {
        int i = segment(x, z, 0, out);
        if (i < 0) return false;
        fill(i, out);
        return true;
    }

    /**
     * The point (x, z) in a river's water or within reach (m, at most {@link #MAX_REACH}) past its edge: true and out
     * filled (out.edge > 0 on the bank); false farther from every river.
     */
    public boolean near(double x, double z, double reach, Point out) {
        int i = segment(x, z, reach, out);
        if (i < 0) return false;
        fill(i, out);
        return true;
    }

    /** Could a channel or its banks reach into the 16 x 16 chunk at (x0, z0)? (Its index buckets hold a segment.) */
    public boolean touches(int x0, int z0) {
        if (nbx == 0) return false;
        int b0 = Math.floorDiv(x0, BUCKET) - bx0, b1 = Math.floorDiv(x0 + 15, BUCKET) - bx0;
        int c0 = Math.floorDiv(z0, BUCKET) - bz0, c1 = Math.floorDiv(z0 + 15, BUCKET) - bz0;
        for (int bz = Math.max(0, c0); bz <= Math.min(nbz - 1, c1); bz++)
            for (int bx = Math.max(0, b0); bx <= Math.min(nbx - 1, b1); bx++) {
                int key = bz * nbx + bx;
                if (bucketStart[key + 1] > bucketStart[key]) return true;
            }
        return false;
    }

    /**
     * The segment for the point (x, z) (m), reaching reach past the water's edge: a point in a channel's water takes, of
     * the channels covering it, the lowest level, then the nearest centreline; a point on a bank the segment whose edge
     * is nearest. out gets t, the downstream tangent, the offset, the side's half width, the edge and the level; -1 when
     * nothing is within reach.
     */
    private int segment(double px, double pz, double reach, Point out) {
        if (nbx == 0) return -1;
        int bx = (int) Math.floor(px / BUCKET) - bx0, bz = (int) Math.floor(pz / BUCKET) - bz0;
        if (bx < 0 || bz < 0 || bx >= nbx || bz >= nbz) return -1;
        int key = bz * nbx + bx, best = -1, bestLevel = Integer.MAX_VALUE;
        double bestRel = Double.MAX_VALUE, bestEdge = Double.MAX_VALUE;
        RiverNetwork r = rivers;
        for (int q = bucketStart[key]; q < bucketStart[key + 1]; q++) {
            int i = bucketSeg[q];
            double ax = r.vx[i], az = r.vz[i], dx = r.vx[i + 1] - ax, dz = r.vz[i + 1] - az;
            double l2 = dx * dx + dz * dz;
            double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l2)) : 0;
            double ex = px - (ax + dx * t), ez = pz - (az + dz * t);
            double dist = Math.sqrt(ex * ex + ez * ez);
            double len = Math.sqrt(l2), tx = len > 1e-9 ? dx / len : 1, tz = len > 1e-9 ? dz / len : 0;
            // right of the downstream direction (tx, tz) is (-tz, tx) in x east, z south
            double offset = ex * -tz + ez * tx;
            double hl = left[i] + (left[i + 1] - left[i]) * t, hr = right[i] + (right[i + 1] - right[i]) * t;
            double hw = offset >= 0 ? hr : hl;
            double edge = dist - hw;
            if (edge >= reach) continue;
            int lvl = t < 0.5 ? r.vl[i] : r.vl[i + 1];
            double rel = dist / hw;
            boolean better = edge < 0
                    ? bestEdge >= 0 || lvl < bestLevel || (lvl == bestLevel && rel < bestRel)
                    : bestEdge >= 0 && edge < bestEdge;
            if (better) {
                bestLevel = lvl;
                bestRel = rel;
                bestEdge = edge;
                best = i;
                out.edge = edge;
                out.t = t;
                out.tx = tx;
                out.tz = tz;
                out.offset = offset;
                out.halfWidth = hw;
                out.left = hl;
                out.right = hr;
                out.level = lvl;
            }
        }
        return best;
    }

    private void fill(int i, Point out) {
        double t = out.t;
        out.vertex = i;
        out.line = rivers.lineOfVertex(i);
        out.s = (i - rivers.start[out.line] + t) * RiverNetwork.STEP;
        out.area = rivers.va[i] + (rivers.va[i + 1] - rivers.va[i]) * t;
        out.eta = eta[i] + (eta[i + 1] - eta[i]) * t;
        out.slope = slope[i] + (slope[i + 1] - slope[i]) * t;
        double b = bend[i] + (bend[i + 1] - bend[i]) * t, sq = seq[i] + (seq[i + 1] - seq[i]) * t;
        double bp = poolShare(b);
        out.pool = Math.max(bp, sq * (1 - Math.min(1, Math.abs(b) / 0.15)));
        out.bend = out.pool > 1e-9 ? bp / out.pool : 0;
        // turning right, the bend's centre is on the right: its outer bank on the left
        out.outer = b > 0 ? -1 : 1;
        out.hole = hole[i] + (hole[i + 1] - hole[i]) * t;
        out.bank = out.offset >= 0 ? bankR[i] + (bankR[i + 1] - bankR[i]) * t : bankL[i] + (bankL[i + 1] - bankL[i]) * t;
        out.widen = widen[i] + (widen[i + 1] - widen[i]) * t;
        out.fish = out.edge < 0 && out.pool >= FISH_POOL;
    }

    public double bendAt(int vertex) {
        return bend[vertex];
    }

    /** eta at a vertex (checks). */
    public double etaAt(int vertex) {
        return eta[vertex];
    }

    public double slopeAt(int vertex) {
        return slope[vertex];
    }

    /** The channel's half widths at a vertex, left and right of the flow (checks). */
    public double leftAt(int vertex) {
        return left[vertex];
    }

    public double rightAt(int vertex) {
        return right[vertex];
    }

    /** The pool share at a vertex (checks). */
    public double poolAtVertex(int vertex) {
        return poolAt(vertex);
    }

    /** The deep hole's depth at a vertex, m (checks). */
    public double holeAt(int vertex) {
        return hole[vertex];
    }

    public double widenAt(int vertex) {
        return widen[vertex];
    }
}
