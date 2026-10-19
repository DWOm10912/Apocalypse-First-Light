package com.antaurora.apofirstlight.worldgen.network.route;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCurve;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The routing cost grid of the national road planner (C3.1; docs/worldgen/federal_highways_c3.md, 3): CELL m cells over
 * the federal land, built once per network from {@link RouteTerrain}, and a 16-neighbour A* whose step cost is built
 * for drivable roads, not shortest lines:
 * <ul>
 *   <li>grade between the two cells on the cell-mean ground (16 samples of the detailed plan height per cell, so narrow
 *   ridges and gullies are not smoothed away), weighed against the class's limits at 90 % (the smoothed alignment is a
 *   little steeper than the cell path): free up to half the design grade, quadratic up to the design grade, steep up to
 *   the mountain grade, prohibitive beyond (the profile designer later decides where the mountain grade is used);</li>
 *   <li>side slope (the slope across the direction of travel): a road along a steep hillside means wide cuts and fills,
 *   paid by the class's formation width; relief inside a cell beyond what its slope explains (a hogback, a ravine)
 *   likewise;</li>
 *   <li>water: river and lake length is bridge length, paid per metre, plus a fixed price per crossing (abutments), so
 *   routes cross few, narrow and square; lakes cost more; the open sea and estuaries are closed (island links are not
 *   in this network); tidal marsh is an embankment surcharge;</li>
 *   <li>a mild physiography factor (the mountain range and the wet coast cost a little more on top of the explicit
 *   terms);</li>
 *   <li>earlier routes: running along inside an earlier route's hard corridor (its formation and earthworks) costs
 *   {@link #ALONG} per metre on top (two roads cannot share one road body; routes meet at joins instead, RoadNetworkPlan),
 *   and a state or county road entering an interstate's corridor pays a grade-separated crossing (an overpass). C1 had a
 *   reuse discount on cells an earlier corridor took; with real formations it pulled state roads onto the interstates'
 *   road bodies (2026-10-14), so C3 has none;</li>
 *   <li>avoid areas (house claims and other hard planning claims): impassable;</li>
 *   <li>a per-leg penalty field (RoadNetworkPlan's design feedback: cells where the designed road needed a viaduct, a
 *   tunnel or deep earthworks cost more on the next try).</li>
 * </ul>
 * Deterministic: fixed scan orders, a heap with an id tie-break, StrictMath for every transcendental function.
 */
public final class RouteGrid {
    public static final double CELL = 32;
    /** Extra cost per metre of running along inside an earlier route's hard corridor. */
    static final double ALONG = 3;

    final RouteTerrain terrain;
    final double ox, oz;
    final int nx, nz;
    /** cell-mean ground height, slope magnitude, relief (max - min of the cell's samples), water share (river + lake),
     *  province factor */
    final float[] hs, slope, relief, wet, factor;
    /** bit 1 river, 2 lake, 4 sea / estuary (closed), 8 marsh; riverW: widest river through the cell, m */
    final byte[] water;
    final float[] riverW;
    final float[] rugged;
    final boolean[] closed;
    /** earlier routes' hard corridors through the cell: bit 1 interstate, 2 state, 4 county */
    final byte[] taken;

    public RouteGrid(RouteTerrain terrain) { this(terrain, Double.NaN, 0, 0); }

    /**
     * The grid over the cells within reach (a square) of (wx, wz) only (G3's county road, RoadCounty: the whole country takes
     * about 3 s), on the whole grid's lattice; NaN wx: the whole grid. Cells on the window's edge see their slope from one
     * side only.
     */
    public RouteGrid(RouteTerrain terrain, double wx, double wz, double reach) {
        this.terrain = terrain;
        FederalMap.Bounds b = terrain.map.landBounds();
        double fx = Math.floor((b.minX() - 512) / CELL) * CELL, fz = Math.floor((b.minZ() - 512) / CELL) * CELL;
        int fnx = (int) Math.ceil((b.maxX() + 512 - fx) / CELL), fnz = (int) Math.ceil((b.maxZ() + 512 - fz) / CELL);
        if (Double.isNaN(wx)) {
            ox = fx;
            oz = fz;
            nx = fnx;
            nz = fnz;
        } else {
            int c0 = Math.max(0, (int) Math.floor((wx - reach - fx) / CELL)), r0 = Math.max(0, (int) Math.floor((wz - reach - fz) / CELL));
            int c1 = Math.min(fnx - 1, (int) Math.ceil((wx + reach - fx) / CELL)), r1 = Math.min(fnz - 1, (int) Math.ceil((wz + reach - fz) / CELL));
            ox = fx + c0 * CELL;
            oz = fz + r0 * CELL;
            nx = Math.max(1, c1 - c0 + 1);
            nz = Math.max(1, r1 - r0 + 1);
        }
        int n = nx * nz;
        hs = new float[n];
        slope = new float[n];
        relief = new float[n];
        wet = new float[n];
        factor = new float[n];
        rugged = new float[n];
        water = new byte[n];
        riverW = new float[n];
        taken = new byte[n];
        closed = new boolean[n];
        double[] w = new double[Physiography.values().length];
        for (int r = 0; r < nz; r++) for (int c = 0; c < nx; c++) {
            int i = r * nx + c;
            double x = cx(c), z = cz(r);
            if (terrain.map.mainlandDistance(x, z) < 0 && terrain.map.stateAt(x, z) == null) {
                closed[i] = true;
                water[i] |= 4;
                hs[i] = (float) terrain.baseHeight(x, z);
                continue;
            }
            terrain.map.physiography(x, z, w);
            factor[i] = (float) (1 + 0.5 * w[Physiography.MOUNTAIN.ordinal()] + 0.2 * w[Physiography.PLATEAU.ordinal()]
                    + 0.4 * w[Physiography.COASTAL_LOWLAND.ordinal()]);
            rugged[i] = (float) (w[Physiography.MOUNTAIN.ordinal()] + w[Physiography.PLATEAU.ordinal()]);
            // water share from a 4 x 4 lattice (8 m) inside the cell; rivers come from the network rasterised below
            int lake = 0, sea = 0, marsh = 0;
            double sum = 0, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int b2 = 0; b2 < 4; b2++) for (int a2 = 0; a2 < 4; a2++) {
                double px = x - CELL / 2 + (a2 + 0.5) * CELL / 4, pz = z - CELL / 2 + (b2 + 0.5) * CELL / 4;
                double h = terrain.height(px, pz);
                sum += h;
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                int wc = terrain.plan.waterClass(px, pz);
                if (wc == 1 || wc == 2) sea++;
                else if (wc == 3) marsh++;
                else if (wc == 4) lake++;
            }
            if (sea >= 6) { closed[i] = true; water[i] |= 4; }
            else if (sea > 0) water[i] |= 4;
            if (lake > 0) water[i] |= 2;
            if (marsh > 0) water[i] |= 8;
            wet[i] = lake / 16f;
            hs[i] = (float) (sum / 16);
            relief[i] = (float) (hi - lo);
        }
        rasteriseRivers(terrain.plan.rivers);
        for (int r = 0; r < nz; r++) for (int c = 0; c < nx; c++) {
            double gx = (hs[r * nx + clamp(c + 1, nx)] - hs[r * nx + clamp(c - 1, nx)]) / (2 * CELL);
            double gz = (hs[clamp(r + 1, nz) * nx + c] - hs[clamp(r - 1, nz) * nx + c]) / (2 * CELL);
            slope[r * nx + c] = (float) Math.sqrt(gx * gx + gz * gz);
        }
    }

    private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    /** River water into the grid: every 4 m along each river segment, the cells its water width touches. */
    private void rasteriseRivers(RiverNetwork rivers) {
        for (int line = 0; line < rivers.lines(); line++) {
            for (int i = rivers.lineStart(line); i < rivers.lineEnd(line) - 1; i++) {
                double ax = rivers.vertexX(i), az = rivers.vertexZ(i), bx = rivers.vertexX(i + 1), bz = rivers.vertexZ(i + 1);
                // a segment wholly outside the grid (a window, RoadCounty) touches none of its cells
                if (Math.max(ax, bx) < ox - CELL || Math.min(ax, bx) > ox + (nx + 1) * CELL || Math.max(az, bz) < oz - CELL
                        || Math.min(az, bz) > oz + (nz + 1) * CELL) continue;
                double len = Math.hypot(bx - ax, bz - az);
                int steps = Math.max(1, (int) Math.ceil(len / 4));
                for (int k = 0; k <= steps; k++) {
                    double t = (double) k / steps, x = ax + (bx - ax) * t, z = az + (bz - az) * t;
                    double width = rivers.vertexWidth(i) + (rivers.vertexWidth(i + 1) - rivers.vertexWidth(i)) * t;
                    int c = (int) Math.floor((x - ox) / CELL), r = (int) Math.floor((z - oz) / CELL);
                    if (c < 0 || r < 0 || c >= nx || r >= nz) continue;
                    int j = r * nx + c;
                    water[j] |= 1;
                    riverW[j] = (float) Math.max(riverW[j], width);
                    // share of the cell's length the river water covers (a river a few metres wide in a 32 m cell)
                    wet[j] = (float) Math.min(1, Math.max(wet[j], width / CELL));
                }
            }
        }
    }

    public double cx(int c) { return ox + (c + 0.5) * CELL; }

    public double cz(int r) { return oz + (r + 0.5) * CELL; }

    public int cellOf(double x, double z) {
        int c = clamp((int) Math.floor((x - ox) / CELL), nx), r = clamp((int) Math.floor((z - oz) / CELL), nz);
        return r * nx + c;
    }

    /** The cell of a point, or the nearest open cell (a city near the shore can sit on a closed cell). */
    public int openCell(double x, double z) {
        int i = cellOf(x, z);
        if (!closed[i]) return i;
        int c0 = i % nx, r0 = i / nx, best = -1;
        double bd = Double.MAX_VALUE;
        for (int r = Math.max(0, r0 - 12); r <= Math.min(nz - 1, r0 + 12); r++)
            for (int c = Math.max(0, c0 - 12); c <= Math.min(nx - 1, c0 + 12); c++) {
                int j = r * nx + c;
                double d = Math.hypot(cx(c) - x, cz(r) - z);
                if (!closed[j] && (d < bd || (d == bd && j < best))) { bd = d; best = j; }
            }
        if (best < 0) throw new IllegalStateException(String.format(Locale.ROOT, "no open cell near %.0f %.0f", x, z));
        return best;
    }

    static byte bit(RoadClassSpec k) { return (byte) (1 << k.ordinal()); }

    /** A planned route's hard corridor (half-width hard[i] at station i * ds) into the grid: every cell it touches. */
    public void markTaken(RoadCurve curve, double[] hard, double ds, RoadClassSpec k) {
        double[] f = new double[5];
        byte b = bit(k);
        for (double s = 0; s <= curve.length; s += 8) {
            curve.frame(s, f);
            double w = hard[Math.min(hard.length - 1, (int) Math.round(s / ds))];
            int c0 = cellOf(f[0], f[1]), cc = c0 % nx, cr = c0 / nx, rc = (int) Math.ceil(w / CELL) + 1;
            for (int dr = -rc; dr <= rc; dr++) for (int dc = -rc; dc <= rc; dc++) {
                int col = cc + dc, row = cr + dr;
                if (col < 0 || row < 0 || col >= nx || row >= nz) continue;
                // the cell touches the corridor when its centre is within the half-width plus half a cell diagonal
                if (Math.hypot(cx(col) - f[0], cz(row) - f[1]) > w + 0.5 * CELL) continue;
                taken[row * nx + col] |= b;
            }
        }
    }

    /** Forgets every marked corridor (the planner marks them again after re-aligning routes, C3.3b group 3). */
    public void clearTaken() { java.util.Arrays.fill(taken, (byte) 0); }

    public int cells() { return nx * nz; }

    /** Cells per row (a cell's column is c % nx, its row c / nx). */
    public int nx() { return nx; }

    static final int[] DC = {1, -1, 0, 0, 1, 1, -1, -1, 2, 2, -2, -2, 1, -1, 1, -1};
    static final int[] DR = {0, 0, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 2, 2, -2, -2};

    /** The share of the class limits the cell path is held to (the smoothed alignment runs a little steeper). */
    static final double LIMIT_SHARE = 0.9;

    /** Extra cost per metre of the grade g for a class (0 = only the length). */
    static double gradeCost(double g, RoadClassSpec k) {
        double grade = LIMIT_SHARE * k.grade, mountain = LIMIT_SHARE * k.gradeMountain, soft = 0.5 * grade;
        if (g <= soft) return 0;
        if (g <= grade) { double t = (g - soft) / (grade - soft); return 1.5 * t * t; }
        if (g <= mountain) return 1.5 + 12 * (g - grade) / (mountain - grade);
        return 13.5 + 300 * (g - mountain) / mountain;
    }

    /** Bridge price per metre of water, and per crossing (abutments), in metres of ordinary road. */
    static double bridgePerMetre(RoadClassSpec k) { return k == RoadClassSpec.INTERSTATE ? 25 : k == RoadClassSpec.STATE ? 18 : 12; }

    static double bridgeFixed(RoadClassSpec k) { return k == RoadClassSpec.INTERSTATE ? 800 : k == RoadClassSpec.STATE ? 400 : 150; }

    double stepCost(int i, int j, double len, RoadClassSpec k, boolean[] avoid, float[] penalty) {
        if (closed[j] || avoid != null && avoid[j]) return Double.POSITIVE_INFINITY;
        double f = 0.5 * (factor[i] + factor[j]);
        double g = Math.abs(hs[j] - hs[i]) / len;
        double s = 0.5 * (slope[i] + slope[j]);
        double cross = Math.sqrt(Math.max(0, s * s - g * g));
        double width = k.formation / RoadClassSpec.INTERSTATE.formation;
        // relief the slope does not explain (a narrow ridge or ravine inside the cell): cut or fill the road must take
        double rough = Math.max(0, relief[j] - 1.2 * slope[j] * CELL - 2);
        double cost = len * (f + gradeCost(g, k) + 14 * width * Math.max(0, cross - 0.04) + 0.6 * width * rough);
        if (penalty != null) cost += len * penalty[j];
        double wl = 0.5 * (wet[i] + wet[j]) * len;
        // along a river (both cells hold river water): the alignment cannot keep to one bank inside a 32 m cell, so
        // half the step is priced as bridge (a square crossing pays the fixed price and its water only)
        if ((water[i] & 1) != 0 && (water[j] & 1) != 0) wl = Math.max(wl, 0.5 * len);
        if (wl > 0) {
            boolean lake = (water[i] & 2) != 0 || (water[j] & 2) != 0;
            cost += wl * bridgePerMetre(k) * (lake ? 3 : 1);
            if (wet[i] < 0.02 || (water[j] & 3) != (water[i] & 3)) cost += bridgeFixed(k);
        }
        if ((water[j] & 8) != 0) cost += len * (k == RoadClassSpec.COUNTY ? 0.5 : 1.0);
        if ((water[j] & 4) != 0) cost += len * 2;   // sea margin: estuary shore
        if (taken[i] != 0 && taken[j] != 0) cost += len * ALONG;
        byte inter = bit(RoadClassSpec.INTERSTATE);
        if (k != RoadClassSpec.INTERSTATE && (taken[j] & inter) != 0 && (taken[i] & inter) == 0) cost += bridgeFixed(k);
        return cost;
    }

    /**
     * A* from any source cell to any goal cell (penalty: extra cost per metre per cell, or null); null when no route
     * exists. The heuristic is the straight distance to the goal disk (centre gx, gz, radius gr, holding every goal cell):
     * admissible (no step costs less than its length), so the path is the cheapest.
     */
    public int[] route(int[] from, boolean[] goal, double gx, double gz, double gr, RoadClassSpec k, boolean[] avoid, float[] penalty) {
        return route(from, null, goal, gx, gz, gr, k, avoid, penalty);
    }

    /**
     * #route with a starting cost per source cell (fromCost[i] for from[i]; null: all 0): the cheapest path over the
     * sources' costs and the steps (County road G3 side join: the driveway's cost to each mouth, RoadCounty).
     */
    public int[] route(int[] from, double[] fromCost, boolean[] goal, double gx, double gz, double gr, RoadClassSpec k, boolean[] avoid, float[] penalty) {
        int n = nx * nz;
        double[] gScore = new double[n];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        int[] prev = new int[n];
        Arrays.fill(prev, -1);
        boolean[] done = new boolean[n];
        Heap open = new Heap();
        for (int q = 0; q < from.length; q++) {
            int s = from[q];
            double c0 = fromCost == null ? 0 : fromCost[q];
            if (gScore[s] <= c0) continue;
            gScore[s] = c0;
            open.push(c0 + h(s, gx, gz, gr), s);
        }
        int to = -1;
        while (open.size > 0) {
            int i = open.pop();
            if (done[i]) continue;
            done[i] = true;
            if (goal[i]) { to = i; break; }
            int c = i % nx, r = i / nx;
            for (int d = 0; d < DC.length; d++) {
                int cc = c + DC[d], rr = r + DR[d];
                if (cc < 0 || rr < 0 || cc >= nx || rr >= nz) continue;
                int j = rr * nx + cc;
                if (done[j]) continue;
                double len = CELL * Math.hypot(DC[d], DR[d]);
                double ng = gScore[i] + stepCost(i, j, len, k, avoid, penalty);
                if (ng < gScore[j]) { gScore[j] = ng; prev[j] = i; open.push(ng + h(j, gx, gz, gr), j); }
            }
        }
        if (to < 0) return null;
        List<Integer> path = new ArrayList<>();
        for (int i = to; i != -1; i = prev[i]) path.add(i);
        int[] out = new int[path.size()];
        for (int q = 0; q < out.length; q++) out[q] = path.get(out.length - 1 - q);
        return out;
    }

    double h(int i, double gx, double gz, double gr) { return Math.max(0, Math.hypot(cx(i % nx) - gx, cz(i / nx) - gz) - gr); }

    /** Min-heap of (double key, int id); ties broken by id. */
    static final class Heap {
        double[] key = new double[4096];
        int[] id = new int[4096];
        int size;

        void push(double k, int v) {
            if (size == key.length) { key = Arrays.copyOf(key, size * 2); id = Arrays.copyOf(id, size * 2); }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (less(key[p], id[p], k, v)) break;
                key[i] = key[p]; id[i] = id[p]; i = p;
            }
            key[i] = k; id[i] = v;
        }

        int pop() {
            int top = id[0];
            double k = key[--size];
            int v = id[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int m = l + 1 < size && less(key[l + 1], id[l + 1], key[l], id[l]) ? l + 1 : l;
                if (!less(key[m], id[m], k, v)) break;
                key[i] = key[m]; id[i] = id[m]; i = m;
            }
            key[i] = k; id[i] = v;
            return top;
        }

        static boolean less(double ka, int ia, double kb, int ib) { return ka < kb || (ka == kb && ia < ib); }
    }
}
