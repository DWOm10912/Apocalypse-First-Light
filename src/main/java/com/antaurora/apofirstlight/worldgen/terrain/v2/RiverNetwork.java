package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Terrain V2 Phase 2b river water (2026-10-10, docs/worldgen/terrain_v2_phase2b_rivers_v1.md): the rivers of the
 * frozen r1 drainage network, as smooth centrelines with a width, a centre depth and a water level per vertex.
 * <ul>
 *   <li>Network: every land or marsh cell of the r1 plan whose final D8 drainage area is at least {@link #A_MIN}
 *   (1 km2). Traced from each mouth upstream, always into the donor with the larger area (the main stem); the other
 *   donors start tributary lines that end on their parent's centreline. Breadth first, so a parent is final before its
 *   tributaries are drawn.</li>
 *   <li>Planform: the 16 m cell path Gaussian-smoothed along its length (sigma 2.2 W + 24 m, 28..56 m, ends held:
 *   no D8 staircase; a junction end held over 2 sigma, a mouth over sigma / 2 after running 1.5 cells on into the
 *   open water, the source free), resampled every 2 m, with an irregular meander offset (a sine at a wandering wavelength about
 *   12 W blended with fBm; amplitude min(1.6 W, 0.45 x floodplain half-width - W / 2), less in the foothills and the
 *   fold belt, swelling and fading into straight reaches, zero at the junction or mouth) that is held back wherever it would climb
 *   more than 1.2 m up the valley side, so the river stays on the valley floor the plan carved for it.</li>
 *   <li>Width W = max(2.5, 3.0 A_km2^0.42) m (3 m at 1 km2, 8 at 10, 14 at 40); centre depth 1 + 2 lg A_km2 blocks,
 *   1..4.</li>
 *   <li>Water level (the top water block): floor(plan surface without detail) - 1 at the vertex, never below the sea
 *   (Y62), then made non-increasing downstream along each line; a tributary's mouth is raised to its parent's level
 *   there (and its upstream levels with it), so no water stands below the river it feeds. Levels are whole blocks; a
 *   drop between two vertices is a riffle (the generator lets the upper source flow over the step).</li>
 * </ul>
 * Pure Java and immutable once built; deterministic (sequential, no hash-order iteration). The column queries
 * ({@link #column}) are what RiverCarver applies after the noise fill.
 */
public final class RiverNetwork {
    public static final double A_MIN = 1.0e6;
    public static final int SEA_WATER_TOP = 62;                 // the top sea water block (sea level Y63)
    public static final int BANK_REACH = 6;                     // blocks beyond the water edge that are graded
    static final double STEP = 2.0;
    static final int BUCKET = 32;

    final int[] start;                  // line k: vertices start[k] .. start[k + 1] - 1, upstream first
    final int[] parent;                 // parent line, -1 for a main stem
    final float[] vx, vz, vw, vd, va;   // position, width m, centre depth blocks, drainage area m2
    final short[] vl;                   // water level: the top water block Y
    private int bx0, bz0, nbx, nbz;
    private int[] bucketStart, bucketSeg;
    private float maxReach;

    RiverNetwork(int[] start, int[] parent, float[] vx, float[] vz, float[] vw, float[] vd, float[] va, short[] vl) {
        this.start = start;
        this.parent = parent;
        this.vx = vx;
        this.vz = vz;
        this.vw = vw;
        this.vd = vd;
        this.va = va;
        this.vl = vl;
        index();
    }

    public int lines() { return parent.length; }
    public int vertices() { return vx.length; }

    public static double width(double area) { return Math.max(2.5, 3.0 * Math.pow(area / 1e6, 0.42)); }

    public static double centreDepth(double area) {
        return Math.max(1, Math.min(4, Math.round(1 + 2 * Math.log10(Math.max(1e-6, area / 1e6)))));
    }

    // ------------------------------------------------------------------ build
    static RiverNetwork build(TerrainPlanV2 p, TerrainPlanSurface s) {
        final int N = TerrainPlanV2.N, n = N * N;
        boolean[] river = new boolean[n];
        for (int i = 0; i < n; i++) river[i] = (p.water[i] == 0 || p.water[i] == 3) && p.area[i] >= A_MIN;
        // donors (upstream river cells) in CSR form, ascending cell index
        int[] off = new int[n + 1];
        for (int i = 0; i < n; i++) if (river[i] && p.recv[i] >= 0 && river[p.recv[i]]) off[p.recv[i] + 1]++;
        for (int i = 0; i < n; i++) off[i + 1] += off[i];
        int[] donors = new int[off[n]], fill = Arrays.copyOf(off, n);
        for (int i = 0; i < n; i++) if (river[i] && p.recv[i] >= 0 && river[p.recv[i]]) donors[fill[p.recv[i]]++] = i;
        // mouths, largest first
        List<Integer> mouths = new ArrayList<>();
        for (int i = 0; i < n; i++) if (river[i] && (p.recv[i] < 0 || !river[p.recv[i]])) mouths.add(i);
        mouths.sort((a, b) -> p.area[a] != p.area[b] ? Float.compare(p.area[b], p.area[a]) : Integer.compare(a, b));
        ArrayDeque<int[]> queue = new ArrayDeque<>();     // {first cell, parent line, junction cell (-1: none)}
        for (int m : mouths) queue.add(new int[]{m, -1, p.recv[m]});
        List<int[]> paths = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        while (!queue.isEmpty()) {
            int[] e = queue.poll();
            int line = paths.size();
            List<Integer> path = new ArrayList<>();
            int cur = e[0];
            path.add(cur);
            while (true) {
                int best = -1;
                for (int k = off[cur]; k < off[cur + 1]; k++) {
                    int d = donors[k];
                    if (best < 0 || p.area[d] > p.area[best]) best = d;
                }
                if (best < 0) break;
                for (int k = off[cur]; k < off[cur + 1]; k++) if (donors[k] != best) queue.add(new int[]{donors[k], line, cur});
                path.add(best);
                cur = best;
            }
            int len = path.size() + (e[2] >= 0 ? 1 : 0);
            int[] cells = new int[len];
            for (int k = 0; k < path.size(); k++) cells[k] = path.get(path.size() - 1 - k);   // upstream first
            if (e[2] >= 0) cells[len - 1] = e[2];
            paths.add(cells);
            parents.add(e[1]);
        }
        // geometry, parents before tributaries (queue order)
        Builder b = new Builder();
        for (int line = 0; line < paths.size(); line++) b.line(p, s, paths.get(line), parents.get(line), line);
        return b.finish(parents);
    }

    private static final class Builder {
        final List<float[]> xs = new ArrayList<>(), zs = new ArrayList<>(), ws = new ArrayList<>(), ds = new ArrayList<>(), as = new ArrayList<>();
        final List<short[]> ls = new ArrayList<>();

        void line(TerrainPlanV2 p, TerrainPlanSurface s, int[] cells, int parent, int line) {
            int m = cells.length;
            double[] px = new double[m], pz = new double[m], pa = new double[m];
            for (int k = 0; k < m; k++) {
                int c = cells[k];
                px[k] = TerrainPlanV2.cx(c % TerrainPlanV2.N);
                pz[k] = TerrainPlanV2.cx(c / TerrainPlanV2.N);
                pa[k] = p.area[c];
            }
            if (m >= 2) pa[m - 1] = pa[m - 2];          // the junction / mouth vertex carries this line's own flow
            int parentLevel = Integer.MIN_VALUE;
            if (parent >= 0 && m >= 2) {               // end exactly on the parent's final centreline
                float[] qx = xs.get(parent), qz = zs.get(parent);
                int best = 0;
                double bd = Double.MAX_VALUE;
                for (int k = 0; k < qx.length; k++) {
                    double dx = qx[k] - px[m - 1], dz = qz[k] - pz[m - 1], d = dx * dx + dz * dz;
                    if (d < bd) { bd = d; best = k; }
                }
                px[m - 1] = qx[best];
                pz[m - 1] = qz[best];
                parentLevel = ls.get(parent)[best];
            }
            // a mouth ends in the open water as the shore-warped plan reads it (a sea or estuary cell whose ground is under
            // Y62.5): the nearest such point within 48 m of the water cell (4 m search grid), joined by a straight run
            // past the cell centre, so the smoothed line still ends in water
            if (parent < 0 && m >= 2 && p.water[cells[m - 1]] != 0) {
                double cx0 = px[m - 1], cz0 = pz[m - 1], bx = Double.NaN, bz = Double.NaN, bd = Double.MAX_VALUE;
                for (int dz = -12; dz <= 12; dz++) for (int dx = -12; dx <= 12; dx++) {
                    double qx = cx0 + dx * 4, qz = cz0 + dz * 4, d = dx * dx + dz * dz;
                    if (d > 144 || d >= bd) continue;
                    if (s.waterAt(qx, qz) && s.heightAt(qx, qz) < SEA_WATER_TOP + 0.5) { bd = d; bx = qx; bz = qz; }
                }
                if (!Double.isNaN(bx) && bd > 0) {
                    px = Arrays.copyOf(px, m + 1); pz = Arrays.copyOf(pz, m + 1); pa = Arrays.copyOf(pa, m + 1);
                    px[m] = bx;
                    pz[m] = bz;
                    pa[m] = pa[m - 1];
                }
            }
            // 1) the D8 cell path (0 / 45 degree runs) at 4 m, Gaussian-smoothed along its length (sigma 2.2 W + 24 m,
            //    28..56 m), the ends held: a valley-floor line without the grid's staircase
            double[][] lin = resample(px, pz, pa, 4.0);
            px = lin[0]; pz = lin[1]; pa = lin[2];
            int k0 = px.length;
            double[] sx = px.clone(), sz = pz.clone();
            // a tributary's junction end is held over 2 sigma (it must stay on its parent), a mouth only over sigma / 2
            // (Phase 2b fix: a long hold left a straight D8 run through the marsh to the estuary); the source is free
            boolean pinEnd = parent >= 0;
            double holdLen = pinEnd ? 2 : 0.5;
            for (int k = 1; k < k0 - 1; k++) {
                double sigma = Math.max(28, Math.min(56, 2.2 * width(pa[k]) + 24)) / 4.0;   // in 4 m samples
                int rad = (int) Math.ceil(2 * sigma);
                double wx = 0, wz = 0, ws = 0;
                for (int j = Math.max(0, k - rad); j <= Math.min(k0 - 1, k + rad); j++) {
                    double g = Math.exp(-0.5 * (j - k) * (j - k) / (sigma * sigma));
                    wx += g * px[j]; wz += g * pz[j]; ws += g;
                }
                double hold = TerrainPlanV2.smooth((k0 - 1 - k) / (holdLen * sigma));
                sx[k] = px[k] + (wx / ws - px[k]) * hold;
                sz[k] = pz[k] + (wz / ws - pz[k]) * hold;
            }
            // 2) at STEP m, then a meander train along the normal: a sine of wavelength 12 W whose amplitude
            //    (min(1.6 W, 0.45 x floodplain half-width - W / 2), less in the foothills and the belt; bend radius about
            //    2.5 W at wavelength 12 W, at least 40 m) and phase drift
            //    slowly, zero at both ends so junctions and mouths stay put
            double[][] fine = resample(sx, sz, pa, STEP);
            double[] rx = fine[0], rz = fine[1], ra = fine[2], rs = fine[3];
            int count = rx.length;
            double total = rs[count - 1];
            //    The train is irregular: a sine whose phase advances at a wandering wavelength (0.5..1.5 x) is blended
            //    with a 3-octave fBm of the same scale (no visible period), the amplitude swells and fades (straight
            //    reaches between bends). Where the offset would climb the valley side (ground more than 1.2 m above the
            //    line's own), it is held back (a running minimum, then smoothed).
            double[] off = new double[count], nxs = new double[count], nzs = new double[count], allow = new double[count];
            double theta = line * 2.399;
            for (int k = 0; k < count; k++) {
                double w = width(ra[k]);
                int a = Math.max(0, k - 2), c = Math.min(count - 1, k + 2);
                double tx = rx[c] - rx[a], tz = rz[c] - rz[a], tl = Math.hypot(tx, tz);
                double lambda = Math.max(40, 12 * w);
                double wander = 1 + 0.5 * Math.max(-1, Math.min(1, 1.6 * PlanNoise.noise(s.noiseSeed, rs[k] / (2.5 * lambda), line * 3.71 + 80, 313)));
                if (k > 0) theta += 2 * Math.PI * (rs[k] - rs[k - 1]) / (lambda * wander);
                if (tl < 1e-9 || count <= 2) continue;
                nxs[k] = -tz / tl;
                nzs[k] = tx / tl;
                int cell = TerrainPlanV2.idx(TerrainPlanV2.clampI((int) Math.floor((rx[k] - TerrainPlanV2.ORIGIN) / TerrainPlanV2.CELL)),
                        TerrainPlanV2.clampI((int) Math.floor((rz[k] - TerrainPlanV2.ORIGIN) / TerrainPlanV2.CELL)));
                double fp = ra[k] >= 1e6 ? 35 * Math.sqrt(ra[k] / 1e6) : 0;
                double amp = Math.max(0, Math.min(1.6 * w, 0.45 * fp - 0.5 * w))
                        * (1 - 0.8 * Math.min(1, p.wBelt[cell] + 0.5 * p.wFoot[cell]));
                double taper = TerrainPlanV2.smooth((total - rs[k]) / (lambda * (pinEnd ? 0.75 : 0.25)));
                double grow = TerrainPlanV2.smooth(0.45 + 1.1 * PlanNoise.noise(s.noiseSeed, rs[k] / (2 * lambda), line * 7.31, 311));
                double skew = PlanNoise.noise(s.noiseSeed, rs[k] / (4 * lambda), line * 9.13 + 20, 314);
                double wig = Math.max(-1.4, Math.min(1.4, PlanNoise.fbm(s.noiseSeed, rs[k], line * 977.0 + 13, 1.3 * lambda, 3, 0.45, 301) / 1.2));
                off[k] = amp * taper * (0.15 + 0.85 * grow) * (0.45 * (Math.sin(theta) + 0.35 * skew * Math.sin(2 * theta + 1.1)) + 0.55 * wig) / 1.1;
                // how far this bend may swing before it climbs out of the valley floor
                double base = s.heightAt(rx[k], rz[k]), sg = Math.signum(off[k]), hw = w / 2;
                allow[k] = 0;
                for (double f = 1; f > 0.1; f -= 0.25) {
                    double qx = rx[k] + nxs[k] * off[k] * f, qz = rz[k] + nzs[k] * off[k] * f;
                    double rise = Math.max(s.heightAt(qx, qz), s.heightAt(qx + nxs[k] * sg * (hw + 2), qz + nzs[k] * sg * (hw + 2))) - base;
                    if (rise <= 1.2) { allow[k] = f; break; }
                }
            }
            double[] held = new double[count];
            int win = (int) Math.ceil(10 / STEP);
            for (int k = 0; k < count; k++) {
                double lo = 1;
                for (int j = Math.max(0, k - win); j <= Math.min(count - 1, k + win); j++) lo = Math.min(lo, allow[j]);
                held[k] = lo;
            }
            double[] mx = new double[count], mz = new double[count];
            for (int k = 0; k < count; k++) {
                double g = 0, gs = 0;
                for (int j = Math.max(0, k - 2 * win); j <= Math.min(count - 1, k + 2 * win); j++) {
                    double wgt = Math.exp(-0.5 * (j - k) * (j - k) / (win * win * 1.0));
                    g += wgt * held[j];
                    gs += wgt;
                }
                double o = off[k] * g / gs;
                mx[k] = rx[k] + nxs[k] * o;
                mz[k] = rz[k] + nzs[k] * o;
            }
            double[][] fin = resample(mx, mz, ra, STEP);
            count = fin[0].length;
            float[] fx = new float[count], fz = new float[count], fw = new float[count], fd = new float[count], fa = new float[count];
            for (int k = 0; k < count; k++) {
                fx[k] = (float) fin[0][k];
                fz[k] = (float) fin[1][k];
                fa[k] = (float) fin[2][k];
                fw[k] = (float) width(fin[2][k]);
                fd[k] = (float) centreDepth(fin[2][k]);
            }
            // water levels: one block under the lowest planned ground across the water (centre and both edges + 1.5 m),
            // never below the sea, non-increasing downstream
            short[] fl = new short[count];
            for (int k = 0; k < count; k++) {
                int wc = s.waterClass(fx[k], fz[k]);
                int cand;
                if (wc == 1 || wc == 2) cand = SEA_WATER_TOP;
                else {
                    int a = Math.max(0, k - 1), c = Math.min(count - 1, k + 1);
                    double tx = fx[c] - fx[a], tz = fz[c] - fz[a], tl = Math.max(1e-9, Math.hypot(tx, tz));
                    double nx = -tz / tl, nz = tx / tl, reach = fw[k] / 2 + 1.5;
                    double low = s.heightAt(fx[k], fz[k]);
                    low = Math.min(low, s.heightAt(fx[k] + nx * reach, fz[k] + nz * reach));
                    low = Math.min(low, s.heightAt(fx[k] - nx * reach, fz[k] - nz * reach));
                    cand = (int) Math.floor(low) - 1;
                }
                cand = Math.max(SEA_WATER_TOP, cand);
                fl[k] = (short) (k == 0 ? cand : Math.min(cand, fl[k - 1]));
            }
            if (parentLevel != Integer.MIN_VALUE && fl[count - 1] < parentLevel) {
                fl[count - 1] = (short) parentLevel;
                for (int k = count - 2; k >= 0; k--) fl[k] = (short) Math.max(fl[k], fl[k + 1]);
            }
            xs.add(fx); zs.add(fz); ws.add(fw); ds.add(fd); as.add(fa); ls.add(fl);
        }

        RiverNetwork finish(List<Integer> parents) {
            int lines = xs.size(), total = 0;
            int[] start = new int[lines + 1];
            for (int k = 0; k < lines; k++) { start[k] = total; total += xs.get(k).length; }
            start[lines] = total;
            float[] vx = new float[total], vz = new float[total], vw = new float[total], vd = new float[total], va = new float[total];
            short[] vl = new short[total];
            for (int k = 0; k < lines; k++) {
                int o = start[k], len = xs.get(k).length;
                System.arraycopy(xs.get(k), 0, vx, o, len);
                System.arraycopy(zs.get(k), 0, vz, o, len);
                System.arraycopy(ws.get(k), 0, vw, o, len);
                System.arraycopy(ds.get(k), 0, vd, o, len);
                System.arraycopy(as.get(k), 0, va, o, len);
                System.arraycopy(ls.get(k), 0, vl, o, len);
            }
            int[] par = new int[lines];
            for (int k = 0; k < lines; k++) par[k] = parents.get(k);
            return new RiverNetwork(start, par, vx, vz, vw, vd, va, vl);
        }
    }

    /** Even arc-length resampling of a polyline with a per-vertex value: {x, z, value, arc length}. */
    static double[][] resample(double[] px, double[] pz, double[] pv, double step) {
        int k0 = px.length;
        double[] cum = new double[k0];
        for (int k = 1; k < k0; k++) cum[k] = cum[k - 1] + Math.hypot(px[k] - px[k - 1], pz[k] - pz[k - 1]);
        double total = cum[k0 - 1];
        int count = Math.max(2, (int) Math.ceil(total / step) + 1);
        double[] rx = new double[count], rz = new double[count], rv = new double[count], rs = new double[count];
        int seg = 0;
        for (int k = 0; k < count; k++) {
            double sk = k == count - 1 ? total : total * k / (count - 1);
            while (seg < k0 - 2 && cum[seg + 1] < sk) seg++;
            int j = Math.min(seg + 1, k0 - 1);
            double len = cum[j] - cum[seg];
            double t = len > 1e-9 ? Math.max(0, Math.min(1, (sk - cum[seg]) / len)) : 0;
            rx[k] = px[seg] + (px[j] - px[seg]) * t;
            rz[k] = pz[seg] + (pz[j] - pz[seg]) * t;
            rv[k] = pv[seg] + (pv[j] - pv[seg]) * t;
            rs[k] = sk;
        }
        return new double[][]{rx, rz, rv, rs};
    }

    // ------------------------------------------------------------------ spatial index
    private int lineOf(int vertex) {
        int k = Arrays.binarySearch(start, vertex);
        return k >= 0 ? (k < parent.length ? k : parent.length - 1) : -k - 2;
    }

    private void index() {
        int total = vx.length;
        if (total == 0) { bucketStart = new int[1]; bucketSeg = new int[0]; nbx = nbz = 0; return; }
        float mx = Float.MAX_VALUE, mz = Float.MAX_VALUE, Mx = -Float.MAX_VALUE, Mz = -Float.MAX_VALUE, mw = 0;
        for (int i = 0; i < total; i++) {
            mx = Math.min(mx, vx[i]); mz = Math.min(mz, vz[i]); Mx = Math.max(Mx, vx[i]); Mz = Math.max(Mz, vz[i]); mw = Math.max(mw, vw[i]);
        }
        maxReach = mw / 2 + BANK_REACH + 2;
        bx0 = (int) Math.floor((mx - maxReach) / BUCKET);
        bz0 = (int) Math.floor((mz - maxReach) / BUCKET);
        nbx = (int) Math.floor((Mx + maxReach) / BUCKET) - bx0 + 1;
        nbz = (int) Math.floor((Mz + maxReach) / BUCKET) - bz0 + 1;
        int[] count = new int[nbx * nbz + 1];
        for (int pass = 0; pass < 2; pass++) {
            int[] at = pass == 1 ? Arrays.copyOf(count, count.length) : null;
            for (int line = 0; line < parent.length; line++) for (int i = start[line]; i < start[line + 1] - 1; i++) {
                float r = Math.max(vw[i], vw[i + 1]) / 2 + BANK_REACH + 2;
                int b0 = (int) Math.floor((Math.min(vx[i], vx[i + 1]) - r) / BUCKET) - bx0, b1 = (int) Math.floor((Math.max(vx[i], vx[i + 1]) + r) / BUCKET) - bx0;
                int c0 = (int) Math.floor((Math.min(vz[i], vz[i + 1]) - r) / BUCKET) - bz0, c1 = (int) Math.floor((Math.max(vz[i], vz[i + 1]) + r) / BUCKET) - bz0;
                for (int bz = c0; bz <= c1; bz++) for (int bx = b0; bx <= b1; bx++) {
                    int key = bz * nbx + bx;
                    if (pass == 0) count[key + 1]++;
                    else bucketSeg[at[key]++] = i;
                }
            }
            if (pass == 0) {
                for (int k = 0; k < nbx * nbz; k++) count[k + 1] += count[k];
                bucketSeg = new int[count[nbx * nbz]];
            }
        }
        bucketStart = count;
    }

    /** What the rivers do to one block column. */
    public static final class Column {
        public static final int NONE = 0, RIVER = 1, BANK = 2;
        public int kind;
        /** RIVER: the top water block; BANK: the highest water top within reach (the ground must stand above it). */
        public int level;
        /** RIVER: water depth in blocks at this column (1 at the edge, the centre depth on the centreline). */
        public int depth;
        /** BANK: the highest the ground may stand here (graded bank); RIVER: unused. */
        public int maxTop;
        /** The lowest water top within reach (sealing caves under the banks down to below the bed). */
        public int lowLevel;
        /** Distance to the nearest water edge, m (negative inside the river). */
        public float edge;
        /** Drainage area of the nearest river vertex, m2 (diagnostics). */
        public float area;

        void clear() { kind = NONE; level = Integer.MIN_VALUE; depth = 0; maxTop = Integer.MAX_VALUE; lowLevel = Integer.MAX_VALUE; edge = Float.MAX_VALUE; area = 0; }
    }

    /** Fills {@code out} for the column (x, z) (block coordinates; evaluated at the column centre). */
    public void column(int x, int z, Column out) {
        out.clear();
        if (nbx == 0) return;
        double px = x + 0.5, pz = z + 0.5;
        int bx = (int) Math.floor(px / BUCKET) - bx0, bz = (int) Math.floor(pz / BUCKET) - bz0;
        if (bx < 0 || bz < 0 || bx >= nbx || bz >= nbz) return;
        int key = bz * nbx + bx;
        int riverLevel = Integer.MAX_VALUE, bankLevel = Integer.MIN_VALUE, cap = Integer.MAX_VALUE, depth = 0;
        double bestRel = Double.MAX_VALUE, bestEdge = Double.MAX_VALUE;
        float area = 0;
        for (int q = bucketStart[key]; q < bucketStart[key + 1]; q++) {
            int i = bucketSeg[q];
            double ax = vx[i], az = vz[i], dx = vx[i + 1] - ax, dz = vz[i + 1] - az;
            double l2 = dx * dx + dz * dz;
            double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l2)) : 0;
            double ex = ax + dx * t - px, ez = az + dz * t - pz;
            double dist = Math.sqrt(ex * ex + ez * ez);
            double hw = 0.5 * (vw[i] + (vw[i + 1] - vw[i]) * t);
            if (dist >= hw + BANK_REACH) continue;
            int lvl = t < 0.5 ? vl[i] : vl[i + 1];
            double edge = dist - hw;
            if (edge < bestEdge) { bestEdge = edge; area = t < 0.5 ? va[i] : va[i + 1]; }
            out.lowLevel = Math.min(out.lowLevel, lvl);
            if (dist < hw) {
                double rel = dist / hw;
                if (lvl < riverLevel || (lvl == riverLevel && rel < bestRel)) {
                    riverLevel = lvl;
                    bestRel = rel;
                    double dc = vd[i] + (vd[i + 1] - vd[i]) * t;
                    depth = (int) Math.max(1, Math.round(dc * (1 - rel * rel)));
                }
            } else {
                bankLevel = Math.max(bankLevel, lvl);
                cap = Math.min(cap, lvl + 1 + (int) Math.floor(edge));
            }
        }
        out.edge = (float) bestEdge;
        out.area = area;
        if (riverLevel != Integer.MAX_VALUE) {
            out.kind = Column.RIVER;
            out.level = riverLevel;
            out.depth = depth;
        } else if (bankLevel != Integer.MIN_VALUE) {
            out.kind = Column.BANK;
            out.level = bankLevel;
            out.maxTop = Math.max(cap, bankLevel + 1);
        }
    }

    /**
     * Distance from (x, z) to the nearest river water edge within maxDist (scans the index buckets in range), and that
     * river's width into width[0]; maxDist when no river is that close (ecology: the riparian corridor).
     */
    public double nearestEdge(double x, double z, double maxDist, double[] width) {
        width[0] = 0;
        if (nbx == 0) return maxDist;
        int r = (int) Math.ceil(maxDist / BUCKET);
        int bx = (int) Math.floor(x / BUCKET) - bx0, bz = (int) Math.floor(z / BUCKET) - bz0;
        double best = maxDist;
        for (int cz = Math.max(0, bz - r); cz <= Math.min(nbz - 1, bz + r); cz++)
            for (int cx = Math.max(0, bx - r); cx <= Math.min(nbx - 1, bx + r); cx++) {
                int key = cz * nbx + cx;
                for (int q = bucketStart[key]; q < bucketStart[key + 1]; q++) {
                    int i = bucketSeg[q];
                    double ax = vx[i], az = vz[i], dx = vx[i + 1] - ax, dz = vz[i + 1] - az;
                    double l2 = dx * dx + dz * dz;
                    double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2)) : 0;
                    double ex = ax + dx * t - x, ez = az + dz * t - z;
                    double w = vw[i] + (vw[i + 1] - vw[i]) * t;
                    double edge = Math.sqrt(ex * ex + ez * ez) - w / 2;
                    if (edge < best) { best = edge; width[0] = w; }
                }
            }
        return best;
    }

    /** Distance from a point to the nearest river water edge, capped at the index reach (spawn search, maps). */
    public double edgeDistance(double x, double z) {
        Column c = new Column();
        column((int) Math.floor(x), (int) Math.floor(z), c);
        return c.kind == Column.NONE ? maxReach : c.edge;
    }

    // ------------------------------------------------------------------ queries for tools and dev commands
    public float vertexX(int i) { return vx[i]; }
    public float vertexZ(int i) { return vz[i]; }
    public float vertexWidth(int i) { return vw[i]; }
    public float vertexArea(int i) { return va[i]; }
    public int vertexLevel(int i) { return vl[i]; }
    public int lineStart(int line) { return start[line]; }
    public int lineEnd(int line) { return start[line + 1]; }
    public int lineParent(int line) { return parent[line]; }
    public int lineOfVertex(int vertex) { return lineOf(vertex); }

    // ------------------------------------------------------------------ persistence (inside the plan cache)
    void write(DataOutputStream out) throws IOException {
        out.writeInt(parent.length);
        for (int k = 0; k <= parent.length; k++) out.writeInt(start[k]);
        for (int k : parent) out.writeInt(k);
        out.writeInt(vx.length);
        for (int i = 0; i < vx.length; i++) {
            out.writeFloat(vx[i]); out.writeFloat(vz[i]); out.writeFloat(vw[i]); out.writeFloat(vd[i]); out.writeFloat(va[i]);
            out.writeShort(vl[i]);
        }
    }

    static RiverNetwork read(DataInputStream in) throws IOException {
        int lines = in.readInt();
        int[] start = new int[lines + 1], parent = new int[lines];
        for (int k = 0; k <= lines; k++) start[k] = in.readInt();
        for (int k = 0; k < lines; k++) parent[k] = in.readInt();
        int total = in.readInt();
        float[] vx = new float[total], vz = new float[total], vw = new float[total], vd = new float[total], va = new float[total];
        short[] vl = new short[total];
        for (int i = 0; i < total; i++) {
            vx[i] = in.readFloat(); vz[i] = in.readFloat(); vw[i] = in.readFloat(); vd[i] = in.readFloat(); va[i] = in.readFloat();
            vl[i] = in.readShort();
        }
        return new RiverNetwork(start, parent, vx, vz, vw, vd, va, vl);
    }

    void checksum(java.util.zip.CRC32 crc) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(22);
        for (int i = 0; i < vx.length; i++) {
            b.clear();
            b.putFloat(vx[i]).putFloat(vz[i]).putFloat(vw[i]).putFloat(vd[i]).putFloat(va[i]).putShort(vl[i]);
            crc.update(b.array(), 0, 22);
        }
        b.clear();
        for (int k : start) { b.clear(); b.putInt(k); crc.update(b.array(), 0, 4); }
        for (int k : parent) { b.clear(); b.putInt(k); crc.update(b.array(), 0, 4); }
    }
}
