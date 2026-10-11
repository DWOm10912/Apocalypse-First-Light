package com.antaurora.apofirstlight.dev.terrainsmooth;

import java.util.Locale;
import java.util.Random;

/**
 * Offline checks of the terrain smoothing rules (SmoothRules, P1): no Minecraft, a synthetic world of columns. Run by
 * tools/terrain-smooth/check.mjs. Prints the measured numbers; exits 1 on a failed check.
 */
final class SmoothCheck {
    static int failures;

    static void require(boolean ok, String what) { if (!ok) { failures++; System.out.println("FAIL " + what); } }

    /** Columns: surface H, kind of top (natural / other / water), cover (plant) or log on top. */
    static final class World implements SmoothRules.Cells {
        final int x0, z0, w;
        final int[] h;
        final byte[] top, above;   // top: 0 natural, 1 other, 2 water; above: 0 air, 1 plant, 2 log (3 high), 3 leaves
        World(int x0, int z0, int w) { this.x0 = x0; this.z0 = z0; this.w = w; h = new int[w * w]; top = new byte[w * w]; above = new byte[w * w]; }
        int i(int x, int z) { int a = x - x0, b = z - z0; return a < 0 || b < 0 || a >= w || b >= w ? -1 : b * w + a; }
        @Override public int cell(int x, int y, int z) {
            int k = i(x, z);
            if (k < 0) return SmoothRules.UNKNOWN;
            int hh = h[k];
            if (top[k] == 2) {   // water: a bed of natural ground two below the water surface
                if (y >= hh) return SmoothRules.AIR;
                return y >= hh - 2 ? SmoothRules.FLUID : SmoothRules.NATURAL;
            }
            if (y < hh - 1) return SmoothRules.NATURAL;
            if (y == hh - 1) return top[k] == 0 ? SmoothRules.NATURAL : SmoothRules.OTHER;
            if (above[k] == 1 && y == hh) return SmoothRules.COVER;
            if (above[k] == 2 && y >= hh && y < hh + 3) return SmoothRules.OTHER;
            if (above[k] == 3 && y == hh) return SmoothRules.LEAF;
            return SmoothRules.AIR;
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

    static int[] area(World t) { return new int[]{t.x0 + 2, t.z0 + 2, t.w - 4}; }

    static long geo(World t, int mode, int[] a, int x, int z) {
        int k = t.i(x, z);
        return SmoothRules.geometry(t, mode, a[0], a[1], a[2], x, t.h[k] - 1, z);
    }

    public static void main(String[] args) {
        straightStep();
        for (int mode : new int[]{SmoothRules.MODE_A, SmoothRules.MODE_B}) {
            World t = terrain(7 + mode, -40, -40, 96, true);
            int[] a = area(t);
            corners(t, mode, a);
            watertight(t, mode, a);
            collision(t, mode, a);
            edits(t, mode, a);
        }
        offAndArea();
        bench();
        System.out.println(failures == 0 ? "TERRAIN SMOOTH CHECK OK" : "TERRAIN SMOOTH CHECK FAILED: " + failures);
        if (failures != 0) System.exit(1);
    }

    /** A straight one-block step: mode B spreads it over two blocks (1:2), mode A cuts the upper block at 45 degrees. */
    static void straightStep() {
        World t = new World(-10, -10, 20);
        for (int z = -10; z < 10; z++) for (int x = -10; x < 10; x++) t.h[t.i(x, z)] = x <= 0 ? 72 : 71;
        int[] a = {-8, -8, 16};
        float[] u = SmoothRules.corners(geo(t, SmoothRules.MODE_B, a, 0, 0)), l = SmoothRules.corners(geo(t, SmoothRules.MODE_B, a, 1, 0));
        require(u[0] == 1 && u[2] == 1 && u[1] == 0.5F && u[3] == 0.5F, "B: upper block falls to 0.5 at the step " + java.util.Arrays.toString(u));
        require(l[0] == 1.5F && l[2] == 1.5F && l[1] == 1 && l[3] == 1, "B: lower block rises to 1.5 at the step " + java.util.Arrays.toString(l));
        float[] ua = SmoothRules.corners(geo(t, SmoothRules.MODE_A, a, 0, 0));
        require(ua[0] == 1 && ua[1] == 0 && geo(t, SmoothRules.MODE_A, a, 1, 0) == -1, "A: only the upper block slopes, 45 degrees");
        // collision along the slope, mode B: box tops every 0.25 m from x = -1 to x = 3
        StringBuilder sb = new StringBuilder();
        double prev = Double.NaN, maxStep = 0;
        for (int x = -1; x <= 2; x++) {
            long g = geo(t, SmoothRules.MODE_B, a, x, 0);
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
            long g = geo(t, mode, a, x, z);
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
                long g = geo(t, mode, a, b[0], b[1]);
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
            double[] ta = tops(t, mode, a, x, z, 1, 3), tb = tops(t, mode, a, x + 1, z, 0, 2);
            double[] la = bands(t, mode, a, x, z, 3), lb = bands(t, mode, a, x + 1, z, 2);   // side E of A (a = se, b = ne), side W of B (a = nw, b = sw)
            for (int e = 0; e < 2; e++) {
                edges++;
                double hiTop = Math.max(ta[e], tb[e]), loTop = Math.min(ta[e], tb[e]);
                if (hiTop - loTop < 1e-6) continue;
                // the higher block draws its band down to the lower top (band lowers are per corner of the band's ends)
                double band = ta[e] > tb[e] ? la[e] : lb[e];
                if (Double.isNaN(band) || band > loTop + 1e-6) {
                    if (gaps++ < 3 && System.getenv("SMOOTH_DEBUG") != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int dz = -1; dz <= 2; dz++) { for (int dx = -1; dx <= 2; dx++) { int q = t.i(x + dx, z + dz); sb.append(t.h[q]).append(t.top[q] == 0 ? "" : t.top[q] == 1 ? "o" : "w").append(t.above[q] == 2 ? "L" : t.above[q] == 3 ? "f" : t.above[q] == 1 ? "p" : "").append(' '); } sb.append("| "); }
                        System.out.printf(Locale.ROOT, "gap mode %d at %d %d end %d: tops A %.2f B %.2f bands A %.2f B %.2f geoA %d geoB %d; columns x-1..x+2 rows z-1..z+2: %s%n",
                                mode, x, z, e, ta[e], tb[e], la[e], lb[e], geo(t, mode, a, x, z), geo(t, mode, a, x + 1, z), sb);
                    }
                }
            }
        }
        require(gaps == 0, "mode " + mode + ": no gaps between drawn tops (" + gaps + " of " + edges + " edge ends)");
    }

    /** Absolute top heights of block (x, z) at two of its corners (a vanilla block: its own surface). */
    static double[] tops(World t, int mode, int[] a, int x, int z, int c1, int c2) {
        long g = geo(t, mode, a, x, z);
        int h = t.h[t.i(x, z)];
        if (g < 0) return new double[]{h, h};
        float[] c = SmoothRules.corners(g);
        return new double[]{h - 1 + c[c1], h - 1 + c[c2]};
    }

    /**
     * Absolute bottom of the side band of block (x, z) on side W (2) or E (3), ordered like tops(): for E (se, ne)... reordered
     * to (ne, se) / for W (nw, sw). NaN where nothing is drawn below the top (a vanilla block draws its full side down to its
     * floor when the neighbour is lower, which is the same as a band from its floor).
     */
    static double[] bands(World t, int mode, int[] a, int x, int z, int side) {
        long g = geo(t, mode, a, x, z);
        int h = t.h[t.i(x, z)];
        if (g < 0) return new double[]{h - 1, h - 1};   // vanilla: the whole side shows wherever the neighbour is lower
        double qa = (g >>> (16 + 8 * side) & 15) / 4.0, qb = (g >>> (20 + 8 * side) & 15) / 4.0;
        // side E: band end a = se, b = ne -> order (ne, se) = (qb, qa); side W: a = nw, b = sw -> (qa, qb)
        return side == 3 ? new double[]{h - 1 + qb, h - 1 + qa} : new double[]{h - 1 + qa, h - 1 + qb};
    }

    /**
     * Collision boxes against the drawn top, for 2 x 2, 4 x 4 and 8 x 8 boxes: how far feet sink into or float over the
     * drawn surface; the steps between boxes inside a block and across blocks where the drawn tops meet (a walker feels
     * those); and, where the drawn tops do not meet (a step that stays visible: trees, water, cliffs, the area border),
     * whether the collision step matches the visible one.
     */
    static void collision(World t, int mode, int[] a) {
        for (int grid : new int[]{2, 4, 8}) {
            java.util.List<Double> dev = new java.util.ArrayList<>();
            double sink = 0, flt = 0, inside = 0, maxTop = 0;
            int blocks = 0;
            for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) {
                long g = geo(t, mode, a, x, z);
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
                if (geo(t, mode, a, x, z) < 0 && geo(t, mode, a, x + 1, z) < 0) continue;
                double[] ta = tops(t, mode, a, x, z, 1, 3), tb = tops(t, mode, a, x + 1, z, 0, 2);   // (ne, se) of the west block, (nw, sw) of the east one
                double[] ea = row(t, mode, a, x, z, true, grid), wb = row(t, mode, a, x + 1, z, false, grid);
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
    static double[] row(World t, int mode, int[] a, int x, int z, boolean east, int grid) {
        long g = geo(t, mode, a, x, z);
        int h = t.h[t.i(x, z)];
        double[] out = new double[grid];
        if (g < 0) { java.util.Arrays.fill(out, h); return out; }
        int[] b = SmoothRules.boxes(g, grid);
        for (int j = 0; j < grid; j++) out[j] = h - 1 + b[j * grid + (east ? grid - 1 : 0)] / 16.0;
        return out;
    }

    /** Digging or placing one block changes only blocks within 1 column and 2 below to 1 above it (the re-mesh radius). */
    static void edits(World t, int mode, int[] a) {
        Random r = new Random(99 + mode);
        int edits = 0, outside = 0, changedTotal = 0;
        for (int n = 0; n < 300; n++) {
            int x = a[0] + 4 + r.nextInt(a[2] - 8), z = a[1] + 4 + r.nextInt(a[2] - 8), k = t.i(x, z);
            if (t.top[k] == 2) continue;
            long[] before = snapshot(t, mode, a, x, z);
            int[] heights = heightsAround(t, x, z);
            int old = t.h[k];
            boolean dig = r.nextBoolean();
            t.h[k] = dig ? old - 1 : old + 1;   // dig the top block, or place one on it
            int ey = dig ? old - 1 : old;       // the edited cell
            long[] after = snapshot(t, mode, a, x, z);
            int[] heightsAfter = heightsAround(t, x, z);
            for (int q = 0; q < before.length; q++) {
                if (before[q] == after[q]) continue;
                changedTotal++;
                int dx = q % 7 - 3, dz = q / 7 - 3;
                int yb = Math.min(heights[q], heightsAfter[q]) - 1;   // that column's top block level
                if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || yb < ey - 2 || yb > ey + 1) outside++;
            }
            t.h[k] = old;
            edits++;
        }
        require(outside == 0, "mode " + mode + ": an edit changes only blocks within 1 column and 2 levels (" + outside + " outside)");
        System.out.printf(Locale.ROOT, "mode %s edits: %d single-block digs / placements, %d tops re-shaped in total, all within x/z +-1 and y -2..+2 of the edit%n",
                mode == SmoothRules.MODE_A ? "A" : "B", edits, changedTotal);
    }

    static long[] snapshot(World t, int mode, int[] a, int cx, int cz) {
        long[] out = new long[49];
        for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) out[(dz + 3) * 7 + dx + 3] = geo(t, mode, a, cx + dx, cz + dz) ^ ((long) t.h[t.i(cx + dx, cz + dz)] << 50);
        return out;
    }

    static int[] heightsAround(World t, int cx, int cz) {
        int[] out = new int[49];
        for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) out[(dz + 3) * 7 + dx + 3] = t.h[t.i(cx + dx, cz + dz)];
        return out;
    }

    static void offAndArea() {
        World t = terrain(3, -40, -40, 96, false);
        int[] a = area(t);
        int off = 0, outside = 0, borderMoved = 0;
        for (int z = t.z0 + 1; z < t.z0 + t.w - 1; z++) for (int x = t.x0 + 1; x < t.x0 + t.w - 1; x++) {
            if (geo(t, SmoothRules.MODE_OFF, a, x, z) != -1) off++;
            boolean in = SmoothRules.inArea(a[0], a[1], a[2], x, z);
            long g = geo(t, SmoothRules.MODE_B, a, x, z);
            if (!in && g != -1) outside++;
            if (in && g >= 0 && (x == a[0] || z == a[1] || x == a[0] + a[2] - 1 || z == a[1] + a[2] - 1)) {
                // corners on the area border are pinned: those corners keep the block's own height
                float[] c = SmoothRules.corners(g);
                int h = t.h[t.i(x, z)];
                if (x == a[0] && (c[0] != 1 || c[2] != 1)) borderMoved++;
                if (z == a[1] && (c[0] != 1 || c[1] != 1)) borderMoved++;
            }
        }
        require(off == 0, "mode off: every block vanilla");
        require(outside == 0, "outside the area: every block vanilla");
        require(borderMoved == 0, "area border corners pinned (" + borderMoved + ")");
        System.out.println("off / outside the area: vanilla; border corners pinned");
    }

    static void bench() {
        World t = terrain(5, -40, -40, 96, true);
        int[] a = area(t);
        long sink = 0;
        for (int warm = 0; warm < 3; warm++) for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) sink += geo(t, SmoothRules.MODE_B, a, x, z);
        long begin = System.nanoTime();
        int n = 0;
        for (int rep = 0; rep < 20; rep++) for (int z = a[1]; z < a[1] + a[2]; z++) for (int x = a[0]; x < a[0] + a[2]; x++) { sink += geo(t, SmoothRules.MODE_B, a, x, z); n++; }
        double ns = (System.nanoTime() - begin) / (double) n;
        System.out.printf(Locale.ROOT, "rules alone: %.0f ns per block (%d calls; in the game each cell read is a getBlockState, so expect more)%s%n", ns, n, sink == 42 ? "" : "");
    }
}
