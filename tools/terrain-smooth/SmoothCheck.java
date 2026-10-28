package com.antaurora.apofirstlight.terrainsmooth;

import java.util.Locale;
import java.util.Random;

/**
 * Offline checks of the terrain smoothing rules (SmoothRules, Terrain Smooth V1): no Minecraft, a synthetic world of
 * columns. Run by tools/terrain-smooth/check.mjs. Prints the measured numbers; exits 1 on a failed check.
 */
final class SmoothCheck {
    static int failures;

    static void require(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } }

    /**
     * Columns: surface H, kind of top (natural / other / water), cover (plant), trunk or leaves on top; optionally a cave
     * inside the ground (air between caveFloor and caveTop), a roof block high above, leaves of a crown 3..4 above.
     */
    static final class World implements SmoothRules.Cells {
        final int x0, z0, w;
        final int[] h, caveFloor, caveTop, roof;
        final byte[] top, above;   // top: 0 natural, 1 other, 2 water; above: 0 air, 1 plant, 2 trunk (3 high), 3 leaves
        final boolean[] crown;

        World(int x0, int z0, int w) {
            this.x0 = x0; this.z0 = z0; this.w = w;
            h = new int[w * w]; caveFloor = new int[w * w]; caveTop = new int[w * w]; roof = new int[w * w];
            top = new byte[w * w]; above = new byte[w * w]; crown = new boolean[w * w];
            java.util.Arrays.fill(roof, Integer.MIN_VALUE);
        }

        int i(int x, int z) { int a = x - x0, b = z - z0; return a < 0 || b < 0 || a >= w || b >= w ? -1 : b * w + a; }

        @Override public int cell(int x, int y, int z) {
            int k = i(x, z);
            if (k < 0) return SmoothRules.UNKNOWN;
            int hh = h[k];
            if (y == roof[k]) return SmoothRules.OTHER;
            if (crown[k] && (y == hh + 3 || y == hh + 4)) return SmoothRules.LEAF;
            if (top[k] == 2) {   // water: a bed of natural ground two below the water surface
                if (y >= hh) return SmoothRules.AIR;
                return y >= hh - 2 ? SmoothRules.FLUID : SmoothRules.NATURAL;
            }
            if (caveTop[k] > caveFloor[k] && y > caveFloor[k] && y < caveTop[k]) return SmoothRules.AIR;
            if (y < hh - 1) return SmoothRules.NATURAL;
            if (y == hh - 1) return top[k] == 0 ? SmoothRules.NATURAL : SmoothRules.OTHER;
            if (above[k] == 1 && y == hh) return SmoothRules.COVER;
            if (above[k] == 2 && y >= hh && y < hh + 3) return SmoothRules.TREE;
            if (above[k] == 3 && y == hh) return SmoothRules.LEAF;
            return SmoothRules.AIR;
        }

        /** the game's rule: down from the highest block (a little above anything this world puts on a column) */
        @Override public int surface(int x, int z) {
            int k = i(x, z);
            return k < 0 ? SmoothRules.NO_SURFACE : SmoothRules.surface(this, x, z, Math.max(h[k] + 8, roof[k]), h[k] - 30);
        }
    }

    static World terrain(long seed, int x0, int z0, int w, boolean features) {
        World t = new World(x0, z0, w);
        Random r = new Random(seed);
        double p1 = r.nextDouble() * 6, p2 = r.nextDouble() * 6;
        for (int z = z0; z < z0 + w; z++) for (int x = x0; x < x0 + w; x++) {
            int k = t.i(x, z);
            t.h[k] = (int) Math.round(70 + 5 * Math.sin(x / 23.0 + p1) * Math.cos(z / 17.0 + p2) + 2.5 * Math.sin((x + z) / 9.0) + 1.2 * Math.sin(x / 4.1 - z / 5.3));
            if (!features) continue;
            if (t.h[k] <= 64) { t.top[k] = 2; t.h[k] = 65; }
            double f = r.nextDouble();
            if (f < 0.18) t.above[k] = 1; else if (f < 0.19) t.above[k] = 2; else if (f < 0.195) t.above[k] = 3; else if (f < 0.2) t.top[k] = 1;
        }
        return t;
    }

    /** the checked blocks: two columns in from the data's edge */
    static int[] area(World t) { return new int[]{t.x0 + 2, t.z0 + 2, t.w - 4}; }

    static long geo(World t, int mode, int x, int z) {
        int k = t.i(x, z);
        return SmoothRules.geometry(t, mode, x, t.h[k] - 1, z);
    }

    public static void main(String[] args) {
        straightStep();
        digs();
        for (int mode : new int[]{SmoothRules.MODE_A, SmoothRules.MODE_B}) {
            World t = terrain(7 + mode, -40, -40, 96, true);
            int[] a = area(t);
            corners(t, mode, a);
            watertight(t, mode, a);
            edgeSeams(t, mode, a, "mode " + (mode == SmoothRules.MODE_A ? "A" : "B"));
            overlayFit(t, mode, a, "mode " + (mode == SmoothRules.MODE_A ? "A" : "B"));
            collision(t, mode, a);
            edits(t, mode, a);
        }
        underground();
        crowns();
        roofs();
        explosions();
        seeThroughNeighbours();
        offAndUnknown();
        bench();
        System.out.println(failures == 0 ? "TERRAIN SMOOTH CHECK OK" : "TERRAIN SMOOTH CHECK FAILED: " + failures);
        if (failures != 0) System.exit(1);
    }

    /**
     * Dig R1 (user 2026-10-26: "挖下去就是一个很奇怪的坑，而不是平滑的"): holes dug into flat ground (and blocks put on it), mode
     * B. One and two deep (or high): every block around takes part (no pins, no soil walls, none under the slopes either), the hole's floor is drawn at its true
     * depth, the tops meet along every shared edge; three deep: the rim stays a wall. A one-wide trench is a trough whose
     * floor runs along its whole length at the trench's depth.
     */
    static void digs() {
        Object[][] scenes = {
                {"pit 1x1 d1", 0, 0, 0, 0, -1}, {"pit 3x3 d1", -1, -1, 1, 1, -1}, {"pit 1x1 d2", 0, 0, 0, 0, -2},
                {"pit 3x3 d2", -1, -1, 1, 1, -2}, {"pit 3x3 d3", -1, -1, 1, 1, -3}, {"trench 1x7 d1", 0, -3, 0, 3, -1},
                {"trench 1x7 d2", 0, -3, 0, 3, -2}, {"mound 1x1 +1", 0, 0, 0, 0, 1}, {"ridge 1x7 +1", -3, 0, 3, 0, 1},
                {"mound 1x1 +2", 0, 0, 0, 0, 2}, {"mound 3x3 +2", -1, -1, 1, 1, 2}, {"ridge 1x7 +2", -3, 0, 3, 0, 2}};
        int mode = SmoothRules.MODE_B;
        for (Object[] sc : scenes) {
            String name = (String) sc[0];
            int x0 = (int) sc[1], z0 = (int) sc[2], x1 = (int) sc[3], z1 = (int) sc[4], d = (int) sc[5];
            World t = new World(-12, -12, 24);
            java.util.Arrays.fill(t.h, 70);
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) t.h[t.i(x, z)] = 70 + d;
            int[] a = {-8, -8, 16};
            int cubes = 0;
            double low = Double.MAX_VALUE, high = -Double.MAX_VALUE, slope = 0;
            for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
                long g = geo(t, mode, x, z);
                int floor = t.h[t.i(x, z)] - 1;
                if (g < 0) {   // flat ground away from the hole stays a cube; one beside a different column is a wall
                    boolean beside = false;
                    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) beside |= t.h[t.i(x + i, z + j)] != floor + 1;
                    if (beside) cubes++;
                    low = Math.min(low, floor + 1);
                    high = Math.max(high, floor + 1);
                    continue;
                }
                float[] c = SmoothRules.corners(g);
                for (int j = 0; j <= 8; j++) for (int i = 0; i <= 8; i++) {
                    double v = floor + SmoothRules.surface(c, i / 8.0, j / 8.0);
                    low = Math.min(low, v);
                    high = Math.max(high, v);
                    if (i < 8) slope = Math.max(slope, Math.abs(SmoothRules.surface(c, (i + 1) / 8.0, j / 8.0) - SmoothRules.surface(c, i / 8.0, j / 8.0)) * 8);
                    if (j < 8) slope = Math.max(slope, Math.abs(SmoothRules.surface(c, i / 8.0, (j + 1) / 8.0) - SmoothRules.surface(c, i / 8.0, j / 8.0)) * 8);
                }
            }
            // soil walls (RockRelief wears them into rock shapes): none where the slopes cover every side (the block under a
            // two-deep rim, the foot of a mound two high; user 2026-10-26 "怎么还是有岩石形状的泥土")
            int walls = 0;
            for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++)
                for (int y = t.h[t.i(x, z)] - 4; y < t.h[t.i(x, z)]; y++) if (com.antaurora.apofirstlight.ground.GroundClass.soilWall(t, x, y, z)) walls++;
            int seams = edgeSeams(t, mode, a, "dig " + name);
            overlayFit(t, mode, a, "dig " + name);
            if (Math.abs(d) <= 2) {
                require(cubes == 0, "dig " + name + ": every block around it slopes (" + cubes + " cubes or walls)");
                require(walls == 0, "dig " + name + ": no soil walls (" + walls + ")");
                require(Math.abs((d < 0 ? low : high) - (70 + d)) < 1e-6, "dig " + name + ": drawn at its true " + (d < 0 ? "depth" : "height") + " (" + (d < 0 ? low : high) + ")");
            } else {
                require(cubes > 0 && walls > 0, "dig " + name + ": a three-deep hole keeps its walls (" + walls + " soil walls)");
            }
            String along = "";
            if (name.startsWith("trench")) {   // the trench floor along its middle line, x = 0.5, z from the first block to the last
                double worst = 0;
                for (int z = z0; z <= z1; z++) {
                    long g = geo(t, mode, 0, z);
                    if (g < 0) { worst = 9; continue; }
                    float[] c = SmoothRules.corners(g);
                    int from = z == z0 ? 4 : 0, to = z == z1 ? 4 : 8;   // the ends: from the block's middle on
                    for (int j = from; j <= to; j++) worst = Math.max(worst, Math.abs(t.h[t.i(0, z)] - 1 + SmoothRules.surface(c, 0.5, j / 8.0) - (70 + d)));
                }
                require(worst < 1e-6, "dig " + name + ": the floor runs the whole length at the trench depth (off by " + worst + ")");
                along = String.format(Locale.ROOT, ", floor line off by %.3f", worst);
            }
            System.out.printf(Locale.ROOT, "dig %-14s: %d cubes / walls beside the edit, %d soil walls, drawn %.3f..%.3f, steepest %.2f (rise per run), %d seam points%s%n", name, cubes, walls, low, high, slope, seams, along);
        }
    }

    /**
     * Dig R1: the wet ground overlays (ClientLiquid, ClientRainWet) draw a top as 2 x 2 quads from #surface at their
     * corners, each split on SmoothRules#swNe's diagonal: they lie on the drawn top everywhere (sampled every 1/8).
     */
    static void overlayFit(World t, int mode, int[] a, String what) {
        double worst = 0;
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
            long g = geo(t, mode, x, z);
            if (g < 0) continue;
            float[] c = SmoothRules.corners(g);
            for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
                double x0 = i * 0.5, z0 = j * 0.5;
                boolean flip = SmoothRules.swNe(c, x0, z0, x0 + 0.5, z0 + 0.5);
                float[] q = {(float) SmoothRules.surface(c, x0, z0), (float) SmoothRules.surface(c, x0 + 0.5, z0),
                        (float) SmoothRules.surface(c, x0, z0 + 0.5), (float) SmoothRules.surface(c, x0 + 0.5, z0 + 0.5)};
                for (int v = 0; v <= 4; v++) for (int u = 0; u <= 4; u++) {
                    double fu = u / 4.0, fv = v / 4.0, drawn = flip
                            ? (fu + fv <= 1 ? q[0] + (q[1] - q[0]) * fu + (q[2] - q[0]) * fv : q[3] + (q[2] - q[3]) * (1 - fu) + (q[1] - q[3]) * (1 - fv))
                            : (fv >= fu ? q[0] + (q[3] - q[2]) * fu + (q[2] - q[0]) * fv : q[0] + (q[1] - q[0]) * fu + (q[3] - q[1]) * fv);
                    worst = Math.max(worst, Math.abs(drawn - SmoothRules.surface(c, x0 + fu * 0.5, z0 + fv * 0.5)));
                }
            }
        }
        require(worst < 1e-5, what + ": wet ground quads lie on the top (off by " + worst + ")");
    }

    /**
     * Dig R1: where two sloped blocks meet with both corners of the shared edge at one height, the drawn tops meet along the
     * whole edge (the midpoints of fan tops too), sampled every 1/8. Returns the number of points that part.
     */
    static int edgeSeams(World t, int mode, int[] a, String what) {
        int points = 0, bad = 0;
        for (int z = a[1]; z < a[1] + a[2] - 1; z++) for (int x = a[0]; x < a[0] + a[2] - 1; x++) {
            long g = geo(t, mode, x, z);
            if (g < 0 || t.above[t.i(x, z)] >= 2) continue;
            float[] c = SmoothRules.corners(g);
            int fa = t.h[t.i(x, z)] - 1;
            for (int side = 0; side < 2; side++) {   // east, south
                int nx = x + (side == 0 ? 1 : 0), nz = z + (side == 1 ? 1 : 0);
                long gn = geo(t, mode, nx, nz);
                if (gn < 0 || t.above[t.i(nx, nz)] >= 2) continue;
                float[] cn = SmoothRules.corners(gn);
                int fb = t.h[t.i(nx, nz)] - 1;
                // both ends of the edge meet?
                double a0 = fa + (side == 0 ? c[1] : c[2]), b0 = fb + (side == 0 ? cn[0] : cn[0]);
                double a1 = fa + c[3], b1 = fb + (side == 0 ? cn[2] : cn[1]);
                if (Math.abs(a0 - b0) > 1e-6 || Math.abs(a1 - b1) > 1e-6) continue;
                for (int k = 0; k <= 8; k++) {
                    double f = k / 8.0;
                    double va = fa + (side == 0 ? SmoothRules.surface(c, 1, f) : SmoothRules.surface(c, f, 1));
                    double vb = fb + (side == 0 ? SmoothRules.surface(cn, 0, f) : SmoothRules.surface(cn, f, 0));
                    points++;
                    if (Math.abs(va - vb) > 1e-6) bad++;
                }
            }
        }
        require(bad == 0, what + ": sloped tops meet along shared edges (" + bad + " of " + points + " points part)");
        return bad;
    }

    /** A straight one-block step: mode B spreads it over two blocks (1:2), mode A cuts the upper block at 45 degrees. */
    static void straightStep() {
        World t = new World(-10, -10, 20);
        for (int z = -10; z < 10; z++) for (int x = -10; x < 10; x++) t.h[t.i(x, z)] = x <= 0 ? 72 : 71;
        float[] u = SmoothRules.corners(geo(t, SmoothRules.MODE_B, 0, 0)), l = SmoothRules.corners(geo(t, SmoothRules.MODE_B, 1, 0));
        require(u[0] == 1 && u[2] == 1 && u[1] == 0.5F && u[3] == 0.5F, "B: upper block falls to 0.5 at the step " + java.util.Arrays.toString(u));
        require(l[0] == 1.5F && l[2] == 1.5F && l[1] == 1 && l[3] == 1, "B: lower block rises to 1.5 at the step " + java.util.Arrays.toString(l));
        float[] ua = SmoothRules.corners(geo(t, SmoothRules.MODE_A, 0, 0));
        require(ua[0] == 1 && ua[1] == 0 && geo(t, SmoothRules.MODE_A, 1, 0) == -1, "A: only the upper block slopes, 45 degrees");
        // collision along the slope, mode B: box tops every 0.25 m from x = -1 to x = 3
        StringBuilder sb = new StringBuilder();
        double prev = Double.NaN, maxStep = 0;
        for (int x = -1; x <= 2; x++) {
            long g = geo(t, SmoothRules.MODE_B, x, 0);
            int floor = t.h[t.i(x, 0)] - 1;
            int[] b = SmoothRules.boxes(g);
            for (int i = 0; i < SmoothRules.GRID; i++) {
                double top = floor + (g < 0 ? 16 : b[i]) / 16.0;   // row j = 0
                if (!Double.isNaN(prev)) maxStep = Math.max(maxStep, Math.abs(top - prev));
                prev = top;
                sb.append(String.format(Locale.ROOT, "%.3f ", top));
            }
        }
        require(maxStep <= 0.125 + 1e-9, "B ramp collision steps <= 1/8 (" + maxStep + ")");
        System.out.println("B ramp box tops x=-1..2: " + sb.toString().trim() + " (max step " + maxStep + ")");
    }

    /** Every block touching a corner point agrees on its height when it is not pinned; pins agree too. */
    static void corners(World t, int mode, int[] a) {
        int points = 0, mismatched = 0, smoothed = 0, raised = 0, natural = 0;
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
            long g = geo(t, mode, x, z);
            int k = t.i(x, z);
            if (t.top[k] == 0 && t.above[k] != 2 && t.above[k] != 3) natural++;
            if (g < 0) continue;
            smoothed++;
            float[] c = SmoothRules.corners(g);
            if (Math.max(Math.max(c[0], c[1]), Math.max(c[2], c[3])) > 1.001F) raised++;
        }
        // the four blocks around each point: absolute corner heights of those that take part
        for (int pz = a[1] + 1; pz < a[1] + a[2]; pz++) for (int px = a[0] + 1; px < a[0] + a[2]; px++) {
            double v = Double.NaN;
            boolean bad = false;
            int[][] around = {{px - 1, pz - 1, 3}, {px, pz - 1, 2}, {px - 1, pz, 1}, {px, pz, 0}};   // corner index of the point in that block
            for (int[] b : around) {
                long g = geo(t, mode, b[0], b[1]);
                if (g < 0) continue;
                double abs = t.h[t.i(b[0], b[1])] - 1 + SmoothRules.corners(g)[b[2]];
                // a pinned point keeps each block's own height: only compare blocks whose value moved off their own height
                if (Math.abs(abs - t.h[t.i(b[0], b[1])]) < 1e-6) continue;
                if (Double.isNaN(v)) v = abs; else if (Math.abs(v - abs) > 1e-6) bad = true;
            }
            points++;
            if (bad) mismatched++;
        }
        require(mismatched == 0, "mode " + mode + ": corner heights agree (" + mismatched + " of " + points + " points differ)");
        System.out.printf(Locale.ROOT, "mode %s: %d natural tops, %d smoothed (%.1f %%), %d rise above their cell; %d corner points checked%n",
                mode == SmoothRules.MODE_A ? "A" : "B", natural, smoothed, 100.0 * smoothed / natural, raised, points);
    }

    /** Drawn tops close everywhere: on every shared edge the tops meet, or the higher block's side band reaches the lower top. */
    static void watertight(World t, int mode, int[] a) {
        int edges = 0, gaps = 0;
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2] - 1; x++) {
            if (t.above[t.i(x, z)] == 2 || t.above[t.i(x + 1, z)] == 2) continue;   // a trunk fills the cell beside the other block
            // east edge of (x, z) against west edge of (x + 1, z): points (x+1, z) [ne of A, nw of B] and (x+1, z+1) [se of A, sw of B]
            double[] ta = tops(t, mode, x, z, 1, 3), tb = tops(t, mode, x + 1, z, 0, 2);
            double[] la = bands(t, mode, x, z, 3), lb = bands(t, mode, x + 1, z, 2);   // side E of A (a = se, b = ne), side W of B (a = nw, b = sw)
            for (int e = 0; e < 2; e++) {
                edges++;
                double hiTop = Math.max(ta[e], tb[e]), loTop = Math.min(ta[e], tb[e]);
                if (hiTop - loTop < 1e-6) continue;
                // the higher block draws its band down to the lower top (band lowers are per corner of the band's ends)
                double band = ta[e] > tb[e] ? la[e] : lb[e];
                if (Double.isNaN(band) || band > loTop + 1e-6) gaps++;
            }
        }
        require(gaps == 0, "mode " + mode + ": no gaps between drawn tops (" + gaps + " of " + edges + " edge ends)");
    }

    /** Absolute top heights of block (x, z) at two of its corners (a vanilla block: its own surface). */
    static double[] tops(World t, int mode, int x, int z, int c1, int c2) {
        long g = geo(t, mode, x, z);
        int h = t.h[t.i(x, z)];
        if (g < 0) return new double[]{h, h};
        float[] c = SmoothRules.corners(g);
        return new double[]{h - 1 + c[c1], h - 1 + c[c2]};
    }

    /**
     * Absolute bottom of the side band of block (x, z) on side W (2) or E (3), ordered like tops(): for E (se, ne)... reordered
     * to (ne, se) / for W (nw, sw). A vanilla block draws its full side down to its floor when the neighbour is lower.
     */
    static double[] bands(World t, int mode, int x, int z, int side) {
        long g = geo(t, mode, x, z);
        int h = t.h[t.i(x, z)];
        if (g < 0) return new double[]{h - 1, h - 1};
        double qa = (g >>> (16 + 8 * side) & 15) / 4.0, qb = (g >>> (20 + 8 * side) & 15) / 4.0;
        return side == 3 ? new double[]{h - 1 + qb, h - 1 + qa} : new double[]{h - 1 + qa, h - 1 + qb};
    }

    /**
     * Collision boxes against the drawn top, for 2 x 2, 4 x 4 and 8 x 8 boxes: how far feet sink into or float over the
     * drawn surface; the steps between boxes inside a block and across blocks where the drawn tops meet (a walker feels
     * those); and, where the drawn tops do not meet (a step that stays visible: trees, water, cliffs, unloaded columns),
     * whether the collision step matches the visible one.
     */
    static void collision(World t, int mode, int[] a) {
        for (int grid : new int[]{2, 4, 8}) {
            java.util.List<Double> dev = new java.util.ArrayList<>();
            double sink = 0, flt = 0, inside = 0, maxTop = 0;
            int blocks = 0;
            for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
                long g = geo(t, mode, x, z);
                if (g < 0) continue;
                blocks++;
                float[] c = SmoothRules.corners(g);
                int[] b = SmoothRules.boxes(g, grid);
                for (int j = 0; j < grid; j++) for (int i = 0; i < grid; i++) {
                    double top = b[j * grid + i] / 16.0;
                    maxTop = Math.max(maxTop, top);
                    for (int sj = 0; sj <= 4; sj++) for (int si = 0; si <= 4; si++) {
                        double d = SmoothRules.surface(c, (i + si / 4.0) / grid, (j + sj / 4.0) / grid) - top;
                        sink = Math.max(sink, d);
                        flt = Math.max(flt, -d);
                        dev.add(Math.abs(d));
                    }
                    if (i + 1 < grid) inside = Math.max(inside, Math.abs(b[j * grid + i + 1] - b[j * grid + i]) / 16.0);
                    if (j + 1 < grid) inside = Math.max(inside, Math.abs(b[(j + 1) * grid + i] - b[j * grid + i]) / 16.0);
                }
            }
            double continuous = 0, mismatch = 0;
            int continuousEdges = 0, stepEdges = 0;
            for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2] - 1; x++) {
                if (t.above[t.i(x, z)] == 2 || t.above[t.i(x + 1, z)] == 2) continue;   // a trunk fills the cell beside: not walkable either way
                if (geo(t, mode, x, z) < 0 && geo(t, mode, x + 1, z) < 0) continue;
                double[] ta = tops(t, mode, x, z, 1, 3), tb = tops(t, mode, x + 1, z, 0, 2);
                double[] ea = row(t, mode, x, z, true, grid), wb = row(t, mode, x + 1, z, false, grid);
                boolean meets = Math.abs(ta[0] - tb[0]) < 1e-6 && Math.abs(ta[1] - tb[1]) < 1e-6;
                if (meets) continuousEdges++; else stepEdges++;
                for (int j = 0; j < grid; j++) {
                    double fz = (j + 0.5) / grid, visual = (ta[0] - tb[0]) * (1 - fz) + (ta[1] - tb[1]) * fz, box = ea[j] - wb[j];
                    if (meets) continuous = Math.max(continuous, Math.abs(box));
                    else mismatch = Math.max(mismatch, Math.abs(box - visual));
                }
            }
            java.util.Collections.sort(dev);
            double p95 = dev.get((int) (dev.size() * 0.95)), mean = dev.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (grid == SmoothRules.GRID && mode == SmoothRules.MODE_B) {
                require(p95 <= 0.07 && sink <= 0.2, String.format(Locale.ROOT, "B %dx%d: feet close to the drawn top (p95 %.3f, sink %.3f)", grid, grid, p95, sink));
                require(inside <= 0.25 + 1e-9 && continuous <= 0.25 + 1e-9, String.format(Locale.ROOT, "B %dx%d: smooth walking (steps inside %.3f, across meeting tops %.3f)", grid, grid, inside, continuous));
                require(mismatch <= 0.25, String.format(Locale.ROOT, "B %dx%d: visible steps collide as seen (%.3f)", grid, grid, mismatch));
                require(maxTop <= 2.0 + 1e-9, "B: boxes at most 1 block above the cell (" + maxTop + ")");
            }
            System.out.printf(Locale.ROOT, "mode %s %dx%d boxes (%d blocks): |drawn - box| mean %.3f p95 %.3f, sink max %.3f, float max %.3f; steps inside %.3f, across meeting tops %.3f (%d edges); visible steps (%d edges) collide within %.3f of what is drawn; highest box %.3f%n",
                    mode == SmoothRules.MODE_A ? "A" : "B", grid, grid, blocks, mean, p95, sink, flt, inside, continuous, continuousEdges, stepEdges, mismatch, maxTop);
        }
    }

    /** Absolute box tops of the east (or west) row of block (x, z), by j; a vanilla block: its surface. */
    static double[] row(World t, int mode, int x, int z, boolean east, int grid) {
        long g = geo(t, mode, x, z);
        int h = t.h[t.i(x, z)];
        double[] out = new double[grid];
        if (g < 0) { java.util.Arrays.fill(out, h); return out; }
        int[] b = SmoothRules.boxes(g, grid);
        for (int j = 0; j < grid; j++) out[j] = h - 1 + b[j * grid + (east ? grid - 1 : 0)] / 16.0;
        return out;
    }

    /**
     * Digging or placing one block, or putting a roof block high over a column, re-shapes only blocks within
     * SmoothRules#REACH columns of it and inside SmoothRules#affected (2 below to 2 above the edit, and around the column's
     * natural surface before and after): exactly what the game re-meshes and what drops the cached slopes.
     */
    static void edits(World t, int mode, int[] a) {
        Random r = new Random(99 + mode);
        int edits = 0, outside = 0, changedTotal = 0, roofs = 0;
        for (int n = 0; n < 400; n++) {
            int x = a[0] + 4 + r.nextInt(a[2] - 8), z = a[1] + 4 + r.nextInt(a[2] - 8), k = t.i(x, z);
            if (t.top[k] == 2) continue;
            long[] before = snapshot(t, mode, x, z);
            int oldSurface = t.surface(x, z), old = t.h[k], ey;
            int kind = r.nextInt(3);
            if (kind == 2) {   // a roof block five above the ground (a building's roof, an overhang)
                ey = old + 4;
                t.roof[k] = ey;
                roofs++;
            } else {
                boolean dig = kind == 0;
                t.h[k] = dig ? old - 1 : old + 1;   // dig the top block, or place one on it
                ey = dig ? old - 1 : old;
            }
            long[] after = snapshot(t, mode, x, z);
            int[] range = SmoothRules.affected(ey, oldSurface, t.surface(x, z));
            for (int q = 0; q < before.length; q++) {
                if (before[q] == after[q]) continue;
                changedTotal++;
                int dx = q % 7 - 3, dz = q / 7 - 3, yb = (int) (Math.min(before[q] >>> 50, after[q] >>> 50)) - 1;   // that block's level
                if (Math.abs(dx) > SmoothRules.REACH || Math.abs(dz) > SmoothRules.REACH || yb < range[0] || yb > range[1]) {
                    if (outside++ < 6) System.out.printf(Locale.ROOT, "  outside: %s at dx %d dz %d level %d (edit level %d, affected %d..%d, surface %d -> %d, ground %d -> %d)%n",
                            kind == 2 ? "roof" : kind == 0 ? "dig" : "place", dx, dz, yb, ey, range[0], range[1], oldSurface, t.surface(x, z), old, t.h[k]);
                }
            }
            t.h[k] = old;
            t.roof[k] = Integer.MIN_VALUE;
            edits++;
        }
        require(outside == 0, "mode " + mode + ": an edit re-shapes only blocks within SmoothRules.REACH columns and SmoothRules.affected (" + outside + " outside)");
        System.out.printf(Locale.ROOT, "mode %s edits: %d digs / placements / roofs (%d roofs), %d tops re-shaped in total, all within x/z +-REACH and the affected levels%n",
                mode == SmoothRules.MODE_A ? "A" : "B", edits, roofs, changedTotal);
    }

    /** geometry and top level of the 7 x 7 columns around (cx, cz): level in bits 50+ (positive heights) */
    static long[] snapshot(World t, int mode, int cx, int cz) {
        long[] out = new long[49];
        for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++)
            out[(dz + 3) * 7 + dx + 3] = (geo(t, mode, cx + dx, cz + dz) & ((1L << 50) - 1)) | ((long) t.h[t.i(cx + dx, cz + dz)] << 50);
        return out;
    }

    /** Cave floors (air above, ground over the cave) keep their cubes; the surface above the cave is not changed by it. */
    static void underground() {
        World plain = terrain(11, -40, -40, 96, false), caves = terrain(11, -40, -40, 96, false);
        int[] a = area(caves);
        Random r = new Random(5);
        int floors = 0, floorSloped = 0, surfaceChanged = 0, floorsWithStep = 0;
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
            int k = caves.i(x, z);
            // a winding cave 8..12 below the surface, its floor stepping by one here and there
            if (Math.abs(Math.sin(x / 7.0) * 9 - (z % 24 - 12)) > 3) continue;
            caves.caveFloor[k] = caves.h[k] - 12 + (r.nextInt(5) == 0 ? 1 : 0);
            caves.caveTop[k] = caves.h[k] - 8;
        }
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
            int k = caves.i(x, z);
            if (caves.caveTop[k] <= caves.caveFloor[k]) continue;
            floors++;
            int fy = caves.caveFloor[k];
            boolean step = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int n = caves.i(x + d[0], z + d[1]);
                if (n >= 0 && caves.caveTop[n] > caves.caveFloor[n] && caves.caveFloor[n] != fy) step = true;
            }
            if (step) floorsWithStep++;
            if (SmoothRules.geometry(caves, SmoothRules.MODE_B, x, fy, z) >= 0) floorSloped++;
        }
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++)
            if (geo(caves, SmoothRules.MODE_B, x, z) != geo(plain, SmoothRules.MODE_B, x, z)) surfaceChanged++;
        require(floors > 300 && floorsWithStep > 50, "underground: the test has cave floors with one-block steps (" + floors + ", " + floorsWithStep + ")");
        require(floorSloped == 0, "underground: cave floors keep their cubes (" + floorSloped + " of " + floors + " sloped)");
        require(surfaceChanged == 0, "underground: the surface above a cave is not changed by it (" + surfaceChanged + ")");
        System.out.printf(Locale.ROOT, "underground: %d cave floor blocks (%d at a one-block step) all cubes; surface above unchanged%n", floors, floorsWithStep);
    }

    /** Ground under a tree crown (leaves 3..4 above) is still natural surface: the same slopes as without the crown. */
    static void crowns() {
        World bare = terrain(13, -40, -40, 96, false), forest = terrain(13, -40, -40, 96, false);
        int[] a = area(forest);
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) if (((x / 6) + (z / 6)) % 2 == 0) forest.crown[forest.i(x, z)] = true;
        int under = 0, differ = 0, sloped = 0;
        for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
            if (!forest.crown[forest.i(x, z)]) continue;
            under++;
            long g = geo(forest, SmoothRules.MODE_B, x, z);
            if (g >= 0) sloped++;
            if (g != geo(bare, SmoothRules.MODE_B, x, z)) differ++;
        }
        require(sloped > 100 && differ == 0, "crowns: ground under leaves slopes as in the open (" + differ + " of " + under + " differ, " + sloped + " sloped)");
        System.out.printf(Locale.ROOT, "crowns: %d blocks under leaves, %d sloped, all the same as in the open%n", under, sloped);
    }

    /** Ground under a roof (a building, an overhang) keeps its cube, and the slopes around it are pinned at its columns. */
    static void roofs() {
        World t = terrain(17, -40, -40, 96, false);
        int[] a = area(t);
        for (int z = 0; z < 12; z++) for (int x = 0; x < 12; x++) t.roof[t.i(x, z)] = t.h[t.i(x, z)] + 3;
        int roofed = 0, sloped = 0, edgeMoved = 0;
        for (int z = -2; z < 14; z++) for (int x = -2; x < 14; x++) {
            boolean under = x >= 0 && x < 12 && z >= 0 && z < 12;
            long g = geo(t, SmoothRules.MODE_B, x, z);
            if (under) { roofed++; if (g >= 0) sloped++; continue; }
            if (g < 0) continue;
            // a block just west of the roofed square: its east corners touch roofed columns, so they keep its own height
            float[] c = SmoothRules.corners(g);
            if (x == -1 && z >= 1 && z <= 10 && (c[1] != 1 || c[3] != 1)) edgeMoved++;
        }
        require(sloped == 0, "roofs: ground under a roof keeps its cubes (" + sloped + " of " + roofed + ")");
        require(edgeMoved == 0, "roofs: slopes next to a roofed column are pinned there (" + edgeMoved + ")");
        System.out.printf(Locale.ROOT, "roofs: %d roofed tops all cubes; neighbours pinned along the roof%n", roofed);
    }

    /** A full 3D grid of cells (for explosions): ground up to h, air above; surfaces from the game's scan. */
    static final class Grid implements SmoothRules.Cells {
        final int x0, y0, z0, w, h;
        final byte[] c;

        /** solid for the rules but hiding nothing behind it in the renderer (mesh trunks, logs, glass, slabs, torches...) */
        final boolean[] see;

        Grid(int x0, int y0, int z0, int w, int h) { this.x0 = x0; this.y0 = y0; this.z0 = z0; this.w = w; this.h = h; c = new byte[w * w * h]; see = new boolean[c.length]; }

        /** the cell hides a neighbour's face toward it (vanilla / Embeddium culling: a full opaque block) */
        boolean hides(int x, int y, int z) { int k = i(x, y, z); return solid(x, y, z) && (k < 0 || !see[k]); }

        int i(int x, int y, int z) { int a = x - x0, b = y - y0, d = z - z0; return a < 0 || d < 0 || a >= w || d >= w ? -1 : b < 0 ? -2 : b >= h ? -3 : (d * w + a) * h + b; }

        @Override public int cell(int x, int y, int z) {
            int k = i(x, y, z);
            return k == -1 ? SmoothRules.UNKNOWN : k == -2 ? SmoothRules.NATURAL : k == -3 ? SmoothRules.AIR : c[k];
        }

        @Override public int surface(int x, int z) { return i(x, y0, z) == -1 ? SmoothRules.NO_SURFACE : SmoothRules.surface(this, x, z, y0 + h - 1, y0); }

        boolean solid(int x, int y, int z) { int k = cell(x, y, z); return k == SmoothRules.NATURAL || k == SmoothRules.OTHER || k == SmoothRules.TREE; }
    }

    /**
     * A TNT-like crater: a ball of blocks removed one by one in random order (the client's Explosion#finalizeExplosion
     * shuffles them), each removal followed by what the game marks for re-meshing: vanilla's LevelRenderer#setBlocksDirty
     * (the sections touching the block +-1) and TerrainSmoothClient#blockChanged (with the surface table updated block by
     * block, exactly as TerrainSmooth#changed does). Every block whose drawing changed (its slope, or which of its faces show)
     * must sit in a marked section.
     */
    static void explosions() {
        int missed = 0, changedBlocks = 0, craters = 0, seams = 0;
        for (int seed = 0; seed < 40; seed++) {
            Random r = new Random(1000 + seed);
            Grid g = new Grid(-24, 40, -24, 48, 48);
            for (int z = g.z0; z < g.z0 + g.w; z++) for (int x = g.x0; x < g.x0 + g.w; x++) {
                int top = 64 + (int) Math.round(2 * Math.sin(x / 5.0 + seed) + 1.5 * Math.cos(z / 4.0));
                for (int y = g.y0; y < top; y++) g.c[g.i(x, y, z)] = (byte) (y < top - 3 && r.nextInt(16) == 0 ? SmoothRules.OTHER : SmoothRules.NATURAL);   // ores, andesite...
                if (r.nextInt(6) == 0) g.c[g.i(x, top, z)] = SmoothRules.COVER;
            }
            int[][] surface = new int[g.w][g.w];
            for (int z = 0; z < g.w; z++) for (int x = 0; x < g.w; x++) surface[x][z] = g.surface(g.x0 + x, g.z0 + z);
            long[] before = render(g);
            // the ball
            double cx = r.nextInt(16) - 8 + 0.5, cy = 62 + r.nextInt(3), cz = r.nextInt(16) - 8 + 0.5, radius = 3.5 + r.nextDouble() * 2;
            java.util.List<int[]> blow = new java.util.ArrayList<>();
            for (int z = (int) (cz - radius) - 1; z <= cz + radius + 1; z++) for (int y = (int) (cy - radius) - 1; y <= cy + radius + 1; y++)
                for (int x = (int) (cx - radius) - 1; x <= cx + radius + 1; x++) {
                    double d = Math.sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy) + (z + 0.5 - cz) * (z + 0.5 - cz));
                    if (d <= radius * (0.85 + 0.3 * r.nextDouble()) && g.cell(x, y, z) != SmoothRules.AIR) blow.add(new int[]{x, y, z});
                }
            java.util.Collections.shuffle(blow, r);
            java.util.Set<Long> marked = new java.util.HashSet<>();
            for (int[] p : blow) {
                int x = p[0], y = p[1], z = p[2];
                g.c[g.i(x, y, z)] = SmoothRules.AIR;
                // vanilla
                for (int sx = (x - 1) >> 4; sx <= (x + 1) >> 4; sx++) for (int sy = (y - 1) >> 4; sy <= (y + 1) >> 4; sy++)
                    for (int sz = (z - 1) >> 4; sz <= (z + 1) >> 4; sz++) marked.add(section(sx, sy, sz));
                // TerrainSmooth#changed: this column's surface again; TerrainSmoothClient#blockChanged
                int a = x - g.x0, b = z - g.z0, old = surface[a][b], now = g.surface(x, z);
                surface[a][b] = now;
                boolean near = false;
                int R = SmoothRules.REACH;
                for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                    if (a + dx < 0 || b + dz < 0 || a + dx >= g.w || b + dz >= g.w) continue;   // REACH 12 since R2f: past the grid
                    int s = surface[a + dx][b + dz];
                    if (s != SmoothRules.NO_SURFACE && s >= y - 2 && s <= y + 2) near = true;
                }
                if (old == now && !near) continue;
                int[] range = SmoothRules.affected(y, old, now);
                for (int sx = (x - R) >> 4; sx <= (x + R) >> 4; sx++) for (int sy = Math.floorDiv(range[0], 16); sy <= Math.floorDiv(range[1] + 2, 16); sy++)
                    for (int sz = (z - R) >> 4; sz <= (z + R) >> 4; sz++) marked.add(section(sx, sy, sz));
            }
            long[] after = render(g);
            craters++;
            seams += seams(g);
            for (int k = 0; k < before.length; k++) {
                if (before[k] == after[k]) continue;
                changedBlocks++;
                int b = k % g.h, rest = k / g.h, x = g.x0 + rest % g.w, z = g.z0 + rest / g.w, y = g.y0 + b;
                if (!marked.contains(section(x >> 4, y >> 4, z >> 4))) {
                    if (missed++ < 5) System.out.printf(Locale.ROOT, "  unmarked change at %d %d %d (crater at %.1f %.1f %.1f)%n", x, y, z, cx, cy, cz);
                }
            }
        }
        require(missed == 0, "explosions: every block whose drawing changes is in a re-meshed section (" + missed + " not)");
        require(seams == 0, "explosions: side by side on one level, a sloped block and its neighbour meet along their edge (" + seams + " seams)");
        System.out.printf(Locale.ROOT, "explosions: %d craters, %d blocks drawn differently afterwards, %d in sections nobody marked, %d open seams%n", craters, changedBlocks, missed, seams);
    }

    /**
     * Vanilla culls a block's face against any full neighbour, and a sloped block still counts as full. Two solid blocks
     * side by side on one level, at least one sloped, leave a see-through gap when the drawn tops differ along the shared
     * edge AND the culled face would show: below 1.0 (a lowered top beside a block whose face toward it is culled), or above
     * 1.0 where the cell above the other block is not solid (a raised top whose side toward it is culled). A tree's block
     * (TREE: AFL's mesh trunks, roots and logs, noOcclusion) culls nothing, and since terrain rework 13.13 the ground meets
     * a trunk's foot: its sides are #seeThroughNeighbours' (beside mesh trunk / root), not a seam here.
     */
    static int seams(Grid g) {
        int bad = 0;
        // corner indices nw 0, ne 1, sw 2, se 3; east edge of A = (ne, se) against west edge of B = (nw, sw); south: (sw, se) / (nw, ne)
        int[][] edge = {{1, 0, 1, 3, 0, 2}, {0, 1, 2, 3, 0, 1}};   // dx, dz, A corner 1, A corner 2, B corner 1, B corner 2
        for (int z = g.z0 + 1; z < g.z0 + g.w - 2; z++) for (int x = g.x0 + 1; x < g.x0 + g.w - 2; x++) for (int y = g.y0; y < g.y0 + g.h - 1; y++) {
            if (!g.solid(x, y, z) || g.cell(x, y, z) == SmoothRules.TREE) continue;
            long ga = SmoothRules.geometry(g, SmoothRules.MODE_B, x, y, z);
            for (int[] e : edge) {
                int bx = x + e[0], bz = z + e[1];
                if (!g.solid(bx, y, bz) || g.cell(bx, y, bz) == SmoothRules.TREE) continue;
                long gb = SmoothRules.geometry(g, SmoothRules.MODE_B, bx, y, bz);
                if (ga < 0 && gb < 0) continue;
                float[] ca = ga < 0 ? new float[]{1, 1, 1, 1} : SmoothRules.corners(ga), cb = gb < 0 ? new float[]{1, 1, 1, 1} : SmoothRules.corners(gb);
                boolean aboveA = g.solid(x, y + 1, z), aboveB = g.solid(bx, y + 1, bz);
                boolean gap = false;
                for (int k = 0; k < 2; k++) {
                    float ta = ca[e[2 + k]], tb = cb[e[4 + k]];
                    if (Math.abs(ta - tb) < 1e-6) continue;
                    float lo = Math.min(ta, tb), hi = Math.max(ta, tb);
                    boolean aHigher = ta > tb;
                    if (lo < 1 - 1e-6) gap = true;                                      // a lowered top beside a culled face
                    if (hi > 1 + 1e-6 && !(aHigher ? aboveB : aboveA)) gap = true;     // a raised top's culled side, open above the other
                }
                if (gap && bad++ < 5) System.out.printf(Locale.ROOT, "  seam between %d %d %d and %d %d %d: %s / %s, solid above %s / %s%n", x, y, z, bx, y, bz,
                        java.util.Arrays.toString(ca), java.util.Arrays.toString(cb), aboveA, aboveB);
            }
            bad += openSides(g, x, y, z, ga);
        }
        return bad;
    }

    /** SmoothSurfaceModel's sides: per side N, S, W, E the neighbour (dx, dz) and the corner indices at the band's ends a, b */
    static final int[][] SIDES = {{0, -1, 1, 0}, {0, 1, 2, 3}, {-1, 0, 0, 2}, {1, 0, 3, 1}};

    /**
     * A sloped block draws its sides only as bands from the neighbour column's surface up to its top; below the band it
     * counts on the neighbour being solid. Where the neighbour cell at the block's level is not solid (an overhang's cavity
     * after an explosion), nothing is drawn there: a see-through hole into the block. Counted per side.
     */
    static int openSides(Grid g, int x, int y, int z, long geo) {
        if (geo < 0) return 0;
        float[] c = SmoothRules.corners(geo);
        int bad = 0;
        for (int s = 0; s < 4; s++) {
            int nx = x + SIDES[s][0], nz = z + SIDES[s][1];
            float la = (geo >>> (16 + 8 * s) & 15) / 4F, lb = (geo >>> (20 + 8 * s) & 15) / 4F;
            float ua = c[SIDES[s][2]], ub = c[SIDES[s][3]];
            la = Math.min(la, ua);
            lb = Math.min(lb, ub);
            if (Math.max(la, lb) <= 1e-6) continue;   // the whole side is drawn
            if (g.solid(nx, y, nz)) continue;           // the undrawn part (within this cell) is behind a solid neighbour
            // a neighbour one lower whose sloped top rises into this level covers the undrawn part up to its surface
            long below = SmoothRules.geometry(g, SmoothRules.MODE_B, nx, y - 1, nz);
            if (below >= 0) {
                float[] k = SmoothRules.corners(below);
                // the neighbour's corners at the shared edge, seen from the neighbour: N side -> its sw, se; S -> nw, ne; W -> ne, se; E -> nw, sw
                int[][] facing = {{2, 3}, {0, 1}, {1, 3}, {0, 2}};
                int[] f = facing[s];
                // shared corner a of this block is f[?]: N: a = ne -> neighbour se (3), b = nw -> sw (2); S: a = sw -> nw (0), b = se -> ne (1);
                // W: a = nw -> ne (1), b = sw -> se (3); E: a = se -> sw (2), b = ne -> nw (0)
                int[][] match = {{3, 2}, {0, 1}, {1, 3}, {2, 0}};
                if (k[match[s][0]] - 1 >= la - 1e-6 && k[match[s][1]] - 1 >= lb - 1e-6) continue;
            }
            if (bad++ < 3 && failuresShown++ < 6) System.out.printf(Locale.ROOT, "  open side %d of %d %d %d: band from %.2f / %.2f, neighbour cell not solid%n", s, x, y, z, la, lb);
        }
        return bad;
    }

    static int failuresShown;

    /** neighbour kinds of seeThroughNeighbours */
    static final String[] KINDS = {"mesh trunk / root", "stump", "lying log", "glass, slab, torch...", "full block (control)"};

    /**
     * 2026-10-12 (docs/worldgen/terrain_smooth_v1.md S): see-through windows beside tree roots, lying logs and blocks placed on
     * slopes. Rolling ground with what stands on it at the first open level: mesh trees (root plus trunk), stumps, logs lying
     * three long, see-through blocks, and full blocks as the control. A sloped block's side below its band is left to the
     * neighbour cell at its level when the rules count that cell solid; the renderer culls a face only against a block that
     * hides it. Counted per side: the part left to a neighbour that hides nothing, before the fix (nothing drawn there) and
     * after it (SmoothSurfaceModel draws that part itself as a face culled like vanilla's, see #underSides).
     */
    static void seeThroughNeighbours() {
        int[] before = new int[KINDS.length], after = new int[KINDS.length];
        int scenes = 0, sloped = 0, seams = 0, plainUnder = 0;
        for (int seed = 0; seed < 40; seed++) {
            Random r = new Random(5000 + seed);
            Grid g = new Grid(-24, 40, -24, 48, 48);
            byte[][] kind = new byte[g.w][g.w];
            int[][] top = new int[g.w][g.w];
            for (int z = g.z0; z < g.z0 + g.w; z++) for (int x = g.x0; x < g.x0 + g.w; x++) {
                int t = 64 + (int) Math.round(2.5 * Math.sin(x / 5.0 + seed) + 1.8 * Math.cos(z / 4.0 + seed * 0.7));
                top[x - g.x0][z - g.z0] = t;
                for (int y = g.y0; y < t; y++) g.c[g.i(x, y, z)] = SmoothRules.NATURAL;
                if (r.nextInt(6) == 0) g.c[g.i(x, t, z)] = SmoothRules.COVER;
            }
            // the plain ground first: the fix must add nothing there
            for (int z = g.z0 + 1; z < g.z0 + g.w - 1; z++) for (int x = g.x0 + 1; x < g.x0 + g.w - 1; x++) {
                int y = top[x - g.x0][z - g.z0] - 1;
                long geo = SmoothRules.geometry(g, SmoothRules.MODE_B, x, y, z);
                if (geo >= 0) plainUnder += Integer.bitCount(underSides(g, x, y, z, geo) & 15);
            }
            for (int z = g.z0 + 2; z < g.z0 + g.w - 2; z++) for (int x = g.x0 + 2; x < g.x0 + g.w - 2; x++) {
                int t = top[x - g.x0][z - g.z0], roll = r.nextInt(100);
                if (g.cell(x, t, z) != SmoothRules.AIR && g.cell(x, t, z) != SmoothRules.COVER) continue;
                if (roll < 3) {                                           // a mesh tree: root on the first open level, trunk above
                    for (int y = t; y < t + 6; y++) put(g, kind, x, y, z, SmoothRules.TREE, 1);
                } else if (roll < 4) {
                    put(g, kind, x, t, z, SmoothRules.TREE, 2);
                } else if (roll < 5) {                                    // a log lying along x or z where the ground lets it
                    boolean alongX = r.nextBoolean();
                    for (int k = 0; k < 3; k++) {
                        int lx = alongX ? x + k : x, lz = alongX ? z : z + k;
                        int c = g.cell(lx, t, lz);
                        if (c == SmoothRules.AIR || c == SmoothRules.COVER) put(g, kind, lx, t, lz, SmoothRules.TREE, 3);
                    }
                } else if (roll < 7) {
                    put(g, kind, x, t, z, SmoothRules.OTHER, 4);
                } else if (roll < 9) {
                    put(g, kind, x, t, z, SmoothRules.OTHER, 5);
                }
            }
            scenes++;
            seams += seams(g);
            for (int z = g.z0 + 1; z < g.z0 + g.w - 1; z++) for (int x = g.x0 + 1; x < g.x0 + g.w - 1; x++) for (int y = g.y0; y < g.y0 + g.h - 1; y++) {
                if (g.cell(x, y, z) != SmoothRules.NATURAL) continue;
                long geo = SmoothRules.geometry(g, SmoothRules.MODE_B, x, y, z);
                if (geo < 0) continue;
                sloped++;
                int drawn = underSides(g, x, y, z, geo);
                for (int s = 0; s < 4; s++) {
                    int nx = x + SIDES[s][0], nz = z + SIDES[s][1];
                    if (!leftToNeighbour(geo, s) || !g.solid(nx, y, nz) || g.hides(nx, y, nz)) continue;
                    int k = kind[nx - g.x0][nz - g.z0] - 1;
                    if (k < 0) k = 3;
                    before[k]++;
                    if ((drawn >> s & 1) == 0) after[k]++;
                }
            }
        }
        int b = 0, a = 0;
        for (int k = 0; k < KINDS.length; k++) {
            b += before[k];
            a += after[k];
            System.out.printf(Locale.ROOT, "  beside %-22s %4d see-through sides before, %d after%n", KINDS[k] + ":", before[k], after[k]);
        }
        require(before[4] == 0, "see-through neighbours: a full block hides the side it is counted on for");
        require(a == 0, "see-through neighbours: every side part left to a neighbour that hides nothing is drawn (" + a + " not)");
        require(plainUnder == 0, "see-through neighbours: plain ground gets no extra side parts (" + plainUnder + ")");
        require(seams == 0, "see-through neighbours: no lowered top beside a culled face (" + seams + " seams)");
        System.out.printf(Locale.ROOT, "see-through neighbours: %d scenes, %d sloped blocks, %d see-through sides before the fix, %d after; plain ground: %d extra parts; %d seams%n",
                scenes, sloped, b, a, plainUnder, seams);
    }

    static void put(Grid g, byte[][] kind, int x, int y, int z, int cell, int k) {
        int i = g.i(x, y, z);
        if (i < 0) return;
        g.c[i] = (byte) cell;
        g.see[i] = k != 5;
        kind[x - g.x0][z - g.z0] = (byte) k;
    }

    /** Side s of a sloped block has a part below its band, within its own cell, left to the neighbour (SmoothSurfaceModel). */
    static boolean leftToNeighbour(long geo, int s) {
        float[] c = SmoothRules.corners(geo);
        float la = (geo >>> (16 + 8 * s) & 15) / 4F, lb = (geo >>> (20 + 8 * s) & 15) / 4F;
        return Math.min(Math.min(la, c[SIDES[s][2]]), 1) > 1e-6 || Math.min(Math.min(lb, c[SIDES[s][3]]), 1) > 1e-6;
    }

    /**
     * The fix, as TerrainSmoothClient#underSides decides it: the sides whose part below the band SmoothSurfaceModel draws
     * itself (bit s), those whose neighbour cell at this level the rules count solid while it hides nothing.
     */
    static int underSides(Grid g, int x, int y, int z, long geo) {
        int mask = 0;
        for (int s = 0; s < 4; s++) {
            int nx = x + SIDES[s][0], nz = z + SIDES[s][1];
            if (leftToNeighbour(geo, s) && g.solid(nx, y, nz) && !g.hides(nx, y, nz)) mask |= 1 << s;
        }
        return mask;
    }

    static long section(int x, int y, int z) { return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF); }

    /** What each block draws: its slope key and which of its six neighbours hide its faces. */
    static long[] render(Grid g) {
        long[] out = new long[g.c.length];
        for (int z = g.z0 + 1; z < g.z0 + g.w - 1; z++) for (int x = g.x0 + 1; x < g.x0 + g.w - 1; x++) for (int y = g.y0; y < g.y0 + g.h; y++) {
            int k = g.i(x, y, z);
            if (!g.solid(x, y, z)) { out[k] = 0; continue; }
            long faces = (g.solid(x + 1, y, z) ? 1 : 0) | (g.solid(x - 1, y, z) ? 2 : 0) | (g.solid(x, y + 1, z) ? 4 : 0) | (g.solid(x, y - 1, z) ? 8 : 0)
                    | (g.solid(x, y, z + 1) ? 16 : 0) | (g.solid(x, y, z - 1) ? 32 : 0);
            out[k] = SmoothRules.geometry(g, SmoothRules.MODE_B, x, y, z) * 64 + faces + 1;
        }
        return out;
    }

    static void offAndUnknown() {
        World t = terrain(3, -40, -40, 96, false);
        int off = 0, edgeMoved = 0;
        for (int z = t.z0; z < t.z0 + t.w; z++) for (int x = t.x0; x < t.x0 + t.w; x++) {
            if (geo(t, SmoothRules.MODE_OFF, x, z) != -1) off++;
            long g = geo(t, SmoothRules.MODE_B, x, z);
            if (g < 0) continue;
            // the data's first column: its west neighbours are not loaded (unknown), so its west corners keep its height
            float[] c = SmoothRules.corners(g);
            if (x == t.x0 && (c[0] != 1 || c[2] != 1)) edgeMoved++;
            if (z == t.z0 && (c[0] != 1 || c[1] != 1)) edgeMoved++;
        }
        require(off == 0, "mode off: every block vanilla");
        require(edgeMoved == 0, "next to columns not loaded: corners pinned (" + edgeMoved + ")");
        System.out.println("off: vanilla; next to unloaded columns: corners pinned");
    }

    static void bench() {
        World t = terrain(5, -40, -40, 96, true);
        int[] a = area(t);
        long sink = 0;
        for (int warm = 0; warm < 3; warm++) for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) sink += geo(t, SmoothRules.MODE_B, x, z);
        long begin = System.nanoTime();
        int n = 0;
        for (int rep = 0; rep < 20; rep++) for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) { sink += geo(t, SmoothRules.MODE_B, x, z); n++; }
        double ns = (System.nanoTime() - begin) / (double) n;
        System.out.printf(Locale.ROOT, "rules alone: %.0f ns per surface block, surfaces scanned each time (%d calls; the game reads surfaces from the chunk's table and each cell is a getBlockState)%s%n", ns, n, sink == 42 ? "" : "");
    }
}
