package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * River plan R1 (docs/gameplay/water_river_v1_design.md 2.1, 18) and river / lake V1 R2 (docs/worldgen/river_lake_v1_design.md
 * 13): HydroQuery's channel, its continuous normal surface eta and its point query, and RiverBed's section, on synthetic
 * networks. {@code ./gradlew riverFlowCheck}.
 * <ul>
 *   <li>A: one straight line east, 400 m, 8 m wide, levels 70 then 69 after 200 m and 67 after 300 m (a one-block and a
 *   two-block step); B: a tributary joining it from the north at x 100;</li>
 *   <li>C: a meander (a sine 96 m long, 9 m out, 8 m wide, 10 km2, level 70);</li>
 *   <li>D: a straight graded line 20 km long, 8 m wide, one block down every 50 m: the pool spacing, the deep holes'
 *   share and the fall gathered into the riffles;</li>
 *   <li>E: line A with a road's works at x 300-310: the channel at the planned width there.</li>
 * </ul>
 */
public final class RiverFlowCheck {
    private static int failures, checks;

    private static void check(boolean ok, String what) {
        checks++;
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    /** The R2 section (RiverBed) against 13.2. */
    private static void bed() {
        check(Math.abs(RiverBed.bankfullDepth(1e7) - 0.66) < 0.01 && Math.abs(RiverBed.bankfullDepth(1e6) - 0.40) < 0.01,
                "bankfull depth 0.66 m at 10 km2, 0.40 at 1 km2 (" + RiverBed.bankfullDepth(1e7) + ")");
        HydroQuery.Point q = new HydroQuery.Point();
        q.halfWidth = 6.4;
        q.area = 1e7;
        q.bank = 0.5;
        q.outer = 1;
        double d = RiverBed.bankfullDepth(q.area);
        q.pool = 0;
        q.offset = 0;
        double riffle = RiverBed.depth(q, d);
        q.pool = 0.5;
        double run = RiverBed.depth(q, d);
        q.pool = 1;
        double pool = RiverBed.depth(q, d);
        q.hole = 3.2;
        double hole = RiverBed.depth(q, d);
        q.hole = 0;
        check(riffle >= 0.3 && riffle <= 0.5, "a riffle is 0.3-0.5 m deep (" + riffle + ")");
        check(run >= 0.6 && run <= 1.0, "a run is 0.6-1.0 m deep (" + run + ")");
        check(pool >= 1.2 && pool <= 2.0, "a pool is 1.2-2 m deep (" + pool + ")");
        check(Math.abs(hole - 3.2) < 1e-9, "a deep hole is as deep as the query says (" + hole + ")");
        // a bend's pool: deepest toward the outer bank, the point bar's side long and flat
        q.bend = 1;
        double deepest = -1, at = 0;
        for (double off = -6.4; off <= 6.4; off += 0.05) {
            q.offset = off;
            double v = RiverBed.depth(q, d);
            if (v > deepest) { deepest = v; at = off; }
        }
        check(at > 1, "a bend's deepest line lies toward the outer bank (" + at + " m off the centre)");
        q.offset = -5.5;
        double barFoot = RiverBed.depth(q, d);
        check(barFoot < 0.6, "the inner side shoals into a point bar (" + barFoot + " m at 5.5 m in)");
        // sides never steeper than 1 in 1; the edge at EDGE over the water; nothing wet past it
        int steep = 0, wet = 0, edges = 0;
        double steepest = 0;
        // (the banks' half widths differ, left the wider, the outer bank either side)
        for (double hw : new double[]{1.25, 2, 3.2, 6.4, 12})
            for (double pl = 0; pl <= 1; pl += 0.25)
                for (double bd = 0; bd <= 1; bd += 0.5)
                    for (double hl : new double[]{0, 3.5})
                        for (double outer : new double[]{1, -1}) {
                            q.left = hw;
                            q.right = 0.8 * hw;
                            q.outer = outer;
                            q.pool = pl;
                            q.bend = bd;
                            q.hole = hl;
                            double prev = Double.NaN;
                            for (double off = -q.left - 4; off <= q.right + 4; off += 0.02) {
                                q.offset = off;
                                double v = RiverBed.depth(q, d);
                                boolean in = off <= q.right && off >= -q.left;
                                if (in && !Double.isNaN(prev)) {
                                    double g = Math.abs(v - prev) / 0.02;
                                    steepest = Math.max(steepest, g);
                                    if (g > RiverBed.MAX_SIDE + 0.02) steep++;
                                }
                                if ((off >= q.right + 1e-9 || off <= -q.left - 1e-9) && v > -RiverBed.EDGE + 1e-9) wet++;
                                prev = in ? v : Double.NaN;
                            }
                            q.offset = q.right;
                            if (Math.abs(RiverBed.depth(q, d) + RiverBed.EDGE) > 1e-6) edges++;
                            q.offset = -q.left;
                            if (Math.abs(RiverBed.depth(q, d) + RiverBed.EDGE) > 1e-6) edges++;
                        }
        q.left = q.right = 0;
        q.outer = 1;
        check(steep == 0, "channel sides never steeper than 1 in 1 (steepest " + steepest + ", " + steep + " samples over)");
        check(edges == 0, "the bed stands " + RiverBed.EDGE + " m over the water at both edges (" + edges + " off)");
        check(wet == 0, "past the edge the ground stands over the water (" + wet + ")");
        // banks: a beach, an ordinary bank, a bend's cut bank
        q.halfWidth = 6.4;
        q.pool = 0;
        q.bend = 0;
        q.hole = 0;
        q.bank = 0;
        q.offset = -6.4 - 4;
        double beach = -RiverBed.depth(q, d) - RiverBed.EDGE;
        q.bank = 0.5;
        double ordinary = -RiverBed.depth(q, d) - RiverBed.EDGE;
        q.pool = 1;
        q.bend = 1;
        q.offset = 6.4 + 0.6;
        double cut = -RiverBed.depth(q, d) - RiverBed.EDGE;
        check(Math.abs(beach - 1.0) < 1e-9, "a beach rises 1 in 4 (" + beach + " m at 4 m)");
        check(ordinary > beach + 0.5, "an ordinary bank rises faster (" + ordinary + " m at 4 m)");
        check(cut >= 1.0 && cut <= 1.5, "a bend's outer bank is a 1-1.5 m cut bank (" + cut + " m at 0.6 m)");

        // the column: dry ground between the water + 0.1 and the noise's ground; whole tops near the target; no bare strip
        RiverBed.Out o = new RiverBed.Out();
        q.eta = 70.45;
        int under = 0, raised = 0, bare = 0, off1 = 0;
        for (double pl = 0; pl <= 1; pl += 0.5)
            for (double off = -12; off <= 12; off += 0.5)
                for (int k = 0; k < 4; k++) {
                    q.pool = pl;
                    q.bank = k / 3.0;
                    q.offset = off;
                    RiverBed.plan(q, k & 1, k >> 1, 72, o);
                    double s = o.top + 1;
                    if (o.depth <= 0 && s < q.eta + 0.1 - 1e-9) under++;
                    if (o.depth <= 0 && s > 73 + 1e-9 && s > q.eta + 0.1 + 1e-9) raised++;
                    if (s < q.eta && (q.eta - s < RiverBed.MIN_WATER - 1e-9 || Math.abs(s - o.surface) > 1 + 1e-9)) off1++;
                    if (s >= q.eta && s < q.eta + 0.1 - 1e-9) off1++;
                    if (o.material == RiverBed.BARE) bare++;
                }
        check(under == 0 && raised == 0, "dry tops between the water + 0.1 and the noise's ground (" + under + ", " + raised + ")");
        check(off1 == 0, "a wet top at least " + RiverBed.MIN_WATER + " m under the water and within a block of the bed, a dry one 0.1 m over it (" + off1 + ")");
        check(bare == 0, "no bare topsoil strip (" + bare + ")");
        q.pool = 1;
        q.offset = 0;
        q.edge = -6;
        check(q.pool >= HydroQuery.FISH_POOL, "a pool is fish water (FISH_POOL " + HydroQuery.FISH_POOL + ")");

        // a meander: pools at the apexes on the outer bank, riffles at the crossings
        int m = 200;
        float[] vx = new float[m], vz = new float[m], vw = new float[m], vd = new float[m], va = new float[m];
        short[] vl = new short[m];
        for (int i = 0; i < m; i++) {
            vx[i] = 2 * i;
            vz[i] = (float) (9 * Math.sin(2 * Math.PI * vx[i] / 96));
            vw[i] = 8;
            vd[i] = 2;
            va[i] = 1e7F;
            vl[i] = 70;
        }
        RiverNetwork n = new RiverNetwork(new int[]{0, m}, new int[]{-1}, vx, vz, vw, vd, va, vl);
        HydroQuery h = HydroQuery.of(n);
        HydroQuery.Point p = new HydroQuery.Point();
        // apex at x 24 (z 9, the bulge south): the river turns left there, its outer bank south (right)
        boolean apex = h.river(24.5, 9.5 + 2, p);
        check(apex && p.pool > 0.8 && p.outer == 1 && p.offset > 0 && p.bend > 0.8 && p.fish,
                "a meander's apex is a bend pool with fish, its outer bank the bulge's outside (pool " + p.pool + ", bend " + p.bend + ", outer " + p.outer + ")");
        boolean apex2 = h.river(72.5, -9.5 - 2, p);
        check(apex2 && p.pool > 0.8 && p.outer == -1 && p.offset < 0, "the next apex (north) turns the other way (pool " + p.pool + ", outer " + p.outer + ")");
        boolean cross = h.river(48.5, 0.5, p);
        check(cross && p.pool < 0.4, "the crossing between them is no pool (pool " + p.pool + ")");
        double far = 9 + 0.8 * 8 * (1 + HydroQuery.WOBBLE) + 2;
        check(h.near(24.5, far, RiverBed.REACH, p) && p.edge > 1 && p.edge < 4, "a bank point is found within reach, past the edge (" + p.edge + ")");
    }

    /** D: pool spacing, deep holes, the fall gathered into the riffles. */
    private static void graded() {
        int m = 10001;
        float[] vx = new float[m], vz = new float[m], vw = new float[m], vd = new float[m], va = new float[m];
        short[] vl = new short[m];
        for (int i = 0; i < m; i++) {
            vx[i] = 2 * i;
            vw[i] = 8;
            vd[i] = 2;
            va[i] = 1e7F;
            vl[i] = (short) (500 - i / 25);
        }
        RiverNetwork n = new RiverNetwork(new int[]{0, m}, new int[]{-1}, vx, vz, vw, vd, va, vl);
        HydroQuery h = HydroQuery.of(n);
        int pools = 0, holes = 0, rises = 0, over = 0;
        boolean in = false;
        double holeMax = 0, poolFall = 0, riffleFall = 0, steepest = 0;
        int poolN = 0, riffleN = 0;
        for (int i = 0; i < m; i++) {
            double pl = h.poolAtVertex(i);
            if (!in && pl > 0.7) { in = true; pools++; holeMax = 0; }
            if (in) holeMax = Math.max(holeMax, h.holeAt(i));
            if (in && pl < 0.3) { in = false; if (holeMax > 2.4) holes++; }
            if (i + 1 < m) {
                double f = h.etaAt(i) - h.etaAt(i + 1);
                if (f < -1e-6) rises++;
                steepest = Math.max(steepest, f / RiverNetwork.STEP);
                if (pl > 0.8) { poolFall += f; poolN++; }
                if (pl < 0.1) { riffleFall += f; riffleN++; }
            }
            double even = vl[i] + HydroQuery.WATER;
            if (h.etaAt(i) > even + HydroQuery.ETA_SHIFT + 1e-4) over++;
        }
        double spacing = 2.0 * (m - 1) / pools, share = (double) holes / pools;
        double poolRate = poolFall / poolN, riffleRate = riffleFall / riffleN;
        System.out.printf("     graded line: %d pools, one every %.1f m (%.1f W), %d deep holes (%.0f %%); fall %.4f m/m in pools, %.4f in riffles, steepest %.3f%n",
                pools, spacing, spacing / (8 * HydroQuery.WIDEN), holes, share * 100, poolRate / 2, riffleRate / 2, steepest);
        check(spacing >= 5 * 8 * HydroQuery.WIDEN * 0.9 && spacing <= 7 * 8 * HydroQuery.WIDEN * 1.1, "a pool every 5-7 widths (" + spacing + " m)");
        check(share >= 0.15 && share <= 0.45, "about one pool in three a deep hole (" + share + ")");
        check(rises == 0, "eta never rises downstream (" + rises + ")");
        check(over == 0, "eta at most ETA_SHIFT over the evenly falling surface (" + over + ")");
        check(poolRate < 0.4 * riffleRate, "a pool stands nearly flat, the riffles take the fall (" + poolRate + " vs " + riffleRate + ")");
        check(steepest < 0.25, "the water never falls steeper than 1 in 4 (" + steepest + ")");
    }

    public static void main(String[] args) {
        int main = 201, trib = 41;
        int total = main + trib;
        float[] vx = new float[total], vz = new float[total], vw = new float[total], vd = new float[total], va = new float[total];
        short[] vl = new short[total];
        for (int i = 0; i < main; i++) {
            vx[i] = 2 * i;
            vz[i] = 0;
            vw[i] = 8;
            vd[i] = 2;
            va[i] = 1e7F;
            vl[i] = (short) (i < 100 ? 70 : i < 150 ? 69 : 67);
        }
        // the tributary from (100, -80) south to the main stem at (100, 0), its levels 72 down to the main's 70 there
        for (int k = 0; k < trib; k++) {
            int i = main + k;
            vx[i] = 100;
            vz[i] = -80 + 2 * k;
            vw[i] = 3;
            vd[i] = 1;
            va[i] = 1e6F;
            vl[i] = (short) (k < 20 ? 72 : 70);
        }
        RiverNetwork n = new RiverNetwork(new int[]{0, main, total}, new int[]{-1, 0}, vx, vz, vw, vd, va, vl);
        HydroQuery h = HydroQuery.of(n);

        // eta: never rising downstream, never far over the carved level's surface
        int rises = 0, over = 0;
        double steepest = 0;
        for (int line = 0; line < n.lines(); line++) {
            for (int i = n.lineStart(line); i < n.lineEnd(line); i++) {
                if (h.etaAt(i) > n.vertexLevel(i) + HydroQuery.SURFACE + HydroQuery.ETA_SHIFT + 1e-4) over++;
                if (i + 1 < n.lineEnd(line)) {
                    double d = h.etaAt(i) - h.etaAt(i + 1);
                    if (d < -1e-6) rises++;
                    steepest = Math.max(steepest, d / RiverNetwork.STEP);
                }
            }
        }
        check(rises == 0, "eta never rises downstream (" + rises + ")");
        check(over == 0, "eta never more than ETA_SHIFT over the carved level's surface (" + over + ")");
        check(steepest < 0.3, "a step is never a wall, steepest " + steepest + " m a metre");
        double s = h.slopeAt(100);
        check(s > 0.012 && s < 0.018, "the reach's slope over 200 m at the first step is about 3 m in 200 m (" + s + ")");
        check(h.slopeAt(0) >= HydroQuery.MIN_SLOPE * 0.999, "a reach never reads flat (" + h.slopeAt(0) + ")");

        // the channel: 1.6 x the planned width, each bank within the wander, never inside the planned water
        double lo = 1e9, hi = 0;
        for (int i = 0; i < main; i++) {
            lo = Math.min(lo, Math.min(h.leftAt(i), h.rightAt(i)));
            hi = Math.max(hi, Math.max(h.leftAt(i), h.rightAt(i)));
        }
        double hw = 4 * HydroQuery.WIDEN;
        check(lo >= hw * (1 - HydroQuery.WOBBLE) - 1e-4 && hi <= hw * (1 + HydroQuery.WOBBLE) + 1e-4 && hi - lo > 0.3,
                "the banks wander about 1.6 x the planned half width (" + lo + " .. " + hi + " m)");
        check(lo > 4, "the wider channel covers the planned water (" + lo + " > 4)");

        // the point query
        HydroQuery.Point p = new HydroQuery.Point();
        check(h.river(50.5, 2.5, p) && p.line == 0 && Math.abs(p.tx - 1) < 1e-9 && Math.abs(p.tz) < 1e-9,
                "a point in the main stem: its line, flowing east");
        check(Math.abs(p.offset - 2.5) < 1e-6 && p.halfWidth > 4 && p.level == 70, "south of an eastward river is its right (" + p.offset + ", half width " + p.halfWidth + ")");
        check(h.river(50.5, 5.0, p), "the wider channel's water reaches past the planned half width");
        check(!h.river(50.5, hw * (1 + HydroQuery.WOBBLE) + 0.5, p), "past the widest bank it is not in the river");
        check(h.river(100.5, -30.5, p) && p.line == 1 && Math.abs(p.tz - 1) < 1e-9, "in the tributary: its own line, flowing south");
        check(h.river(100.5, 0.5, p) && p.level == 70 && p.line == 0, "at the junction the lower (main) water wins (line " + p.line + ")");

        // E: a road's works at x 300-310 hold the planned width there, easing back over the ramp
        HydroQuery e = HydroQuery.of(n, (x, z, extra) -> x >= 300 - extra && x <= 310 + extra && Math.abs(z) < 30);
        double at = Math.max(e.leftAt(152), e.rightAt(152)), ramp = Math.max(HydroQuery.NARROW_RAMP_MIN, HydroQuery.NARROW_RAMP_PER_WIDTH * 8);
        int beyond = (int) Math.ceil((310 + 0.8 * 8 + HydroQuery.NARROW_CLEAR + ramp) / 2) + 1;
        check(Math.abs(at - 4) < 1e-5 && e.widenAt(152) < 1e-6, "at a road's works the channel is the planned width (" + at + ")");
        check(e.widenAt(beyond) > 0.999, "past the ramp it is the full channel again (widen " + e.widenAt(beyond) + " at x " + 2 * beyond + ")");
        check(Math.abs(e.etaAt(152) - h.etaAt(152)) < 0.6, "the works change the surface little (" + e.etaAt(152) + " vs " + h.etaAt(152) + ")");

        bed();
        graded();

        System.out.printf("River flow check: %d checks, %d failures; steepest %.3f m/m, slope at the step %.5f%n", checks, failures, steepest, s);
        if (failures > 0) System.exit(1);
    }
}
