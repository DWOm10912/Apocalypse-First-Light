package com.antaurora.apofirstlight.dev.highway.r4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Earthworks planner (docs/worldgen/highway_r4_sample_v1.md, B / D / E): from the surveyed natural ground and the design,
 * the finished surface at every block corner and, column by column, the blocks to remove and place. Pure Java: the
 * Minecraft side supplies the ground and checks and executes the writes.
 * <p>
 * The finished surface F at a block corner:
 * <ul>
 *   <li>formation (|u| <= 22): the median swale, or the turf just under the paved band;</li>
 *   <li>roadside (|u| > 22): the shoulder hinge plus R4Section.roadside (1:4 foreslope, ditch, 1:3 backslope, or fill
 *   slope), which meets the ground at the daylight line by construction;</li>
 *   <li>beyond the segment ends: the end cross-section approaching the ground at 1:4;</li>
 *   <li>a corner whose result is within 3 cm of the ground is natural: it takes the highest of its four natural column
 *   tops, so the graded zone meets the untouched blocks on their top edges.</li>
 * </ul>
 * Corner heights are quantised to 1/8 block in world space before a block is fitted, so neighbouring blocks share them.
 */
public final class R4Earthworks {
    /** natural / engineered threshold and the widest the engineered zone may reach (m from the centreline) */
    public static final double NATURAL_TOLERANCE = 0.03, MAX_HALF_WIDTH = 40, MAX_CUT = 4.0, MAX_FILL = 4.0, END_SLOPE = 0.25;

    /** The surveyed natural ground. */
    public interface Ground {
        /** Y of the topmost natural solid block of the column (vegetation excluded); Integer.MIN_VALUE when unknown. */
        int top(int x, int z);
        /** Plant blocks above the top (0..2). */
        int plants(int x, int z);
        /** The top block would die when buried (grass, podzol, mycelium...). */
        boolean grassy(int x, int z);
    }

    public enum Kind { AIR, DIRT, TURF, ASPHALT, GRAVEL, STONE, COLLISION }

    /** One block write; data: turf corners (nw | ne << 4 | sw << 8 | se << 12 | shift << 16) or collision layers. */
    public record Write(int x, int y, int z, Kind kind, int data) {}

    public enum ColumnType { GRADED, PAVED }

    /** A modified column: natural top, new surface top block, lowest written block, cut / fill depth at the centre. */
    public record Column(int x, int z, ColumnType type, int naturalTop, int surface, int bottom, double cut, double fill) {}

    public record Problem(String code, int x, int z, String detail) {
        @Override public String toString() { return code + " at " + x + "," + z + (detail.isEmpty() ? "" : " (" + detail + ")"); }
    }

    public record Plan(List<Write> writes, List<Column> columns, List<Problem> problems, Stats stats) {
        public boolean ok() { return problems.isEmpty(); }
    }

    public record Stats(int graded, int paved, int removed, int placed, int[] byKind, double maxCut, double maxFill,
                        double maxHalfWidth, int clampedTurf, double maxCollisionError, int liftedCorners) {
        @Override public String toString() {
            return String.format(Locale.ROOT, "columns graded=%d paved=%d; writes removed=%d placed=%d (dirt=%d turf=%d asphalt=%d gravel=%d stone=%d collision=%d); "
                            + "max cut=%.2f m fill=%.2f m; engineered half width=%.1f m; turf corners clamped=%d lifted=%d; collision error max=%.3f m",
                    graded, paved, removed, placed, byKind[Kind.DIRT.ordinal()], byKind[Kind.TURF.ordinal()], byKind[Kind.ASPHALT.ordinal()],
                    byKind[Kind.GRAVEL.ordinal()], byKind[Kind.STONE.ordinal()], byKind[Kind.COLLISION.ordinal()], maxCut, maxFill, maxHalfWidth,
                    clampedTurf, liftedCorners, maxCollisionError);
        }
    }

    private R4Earthworks() {}

    /**
     * Ground samples for the profile (station i * DS, i = 0 .. length / DS): the trimmed mean (a fifth off each end) of the
     * column surfaces every 2 m across the formation, so a tree stump, a pit or one high bank does not steer the grade.
     */
    public static double[] groundProfile(R4Design flat, Ground ground) {
        int n = (int) Math.round(flat.length / R4Profile.DS) + 1;
        double[] g = new double[n];
        for (int i = 0; i < n; i++) {
            double st = i * R4Profile.DS;
            List<Double> v = new ArrayList<>();
            for (double u = -R4Section.EDGE; u <= R4Section.EDGE + 1e-9; u += 2) {
                double[] p = flat.point(st, u);
                int t = ground.top((int) Math.floor(p[0]), (int) Math.floor(p[1]));
                if (t != Integer.MIN_VALUE) v.add(t + 1.0);
            }
            if (v.isEmpty()) throw new IllegalStateException("NO_GROUND_AT_STATION " + st);
            v.sort(Double::compare);
            int cut = v.size() / 5;
            double sum = 0;
            int cnt = 0;
            for (int k = cut; k < v.size() - cut; k++) { sum += v.get(k); cnt++; }
            g[i] = sum / cnt;
        }
        return g;
    }

    /** Column bounds [minX, maxX) x [minZ, maxZ) that hold the whole engineered zone of a design. */
    public static int[] bounds(R4Design d) {
        double x0 = Double.POSITIVE_INFINITY, z0 = x0, x1 = -x0, z1 = -x0;
        for (double s = -R4Design.END; s <= d.length + R4Design.END; s += 2) {
            double[] c = d.centre(s);
            x0 = Math.min(x0, c[0]); x1 = Math.max(x1, c[0]); z0 = Math.min(z0, c[1]); z1 = Math.max(z1, c[1]);
        }
        double m = MAX_HALF_WIDTH + 2;
        return new int[]{(int) Math.floor(x0 - m), (int) Math.floor(z0 - m), (int) Math.ceil(x1 + m), (int) Math.ceil(z1 + m)};
    }

    public static Plan plan(R4Design d, Ground ground, int minX, int minZ, int maxX, int maxZ) {
        List<Problem> problems = new ArrayList<>();
        int w = maxX - minX, h = maxZ - minZ;
        // column surface heights (top block + 1) with a one-column ring for the corner means
        double[] surf = new double[(w + 2) * (h + 2)];
        for (int i = 0; i < w + 2; i++) for (int j = 0; j < h + 2; j++) {
            int t = ground.top(minX - 1 + i, minZ - 1 + j);
            surf[i * (h + 2) + j] = t == Integer.MIN_VALUE ? Double.NaN : t + 1;
        }
        // corners (minX..maxX) x (minZ..maxZ)
        int cw = w + 1, ch = h + 1;
        double[] fin = new double[cw * ch];
        boolean[] eng = new boolean[cw * ch], under = new boolean[cw * ch];
        double maxHalf = 0;
        for (int i = 0; i < cw; i++) for (int j = 0; j < ch; j++) {
            int x = minX + i, z = minZ + j;
            double a = surf[i * (h + 2) + j], b = surf[(i + 1) * (h + 2) + j], c = surf[i * (h + 2) + j + 1], e = surf[(i + 1) * (h + 2) + j + 1];
            double mean = (a + b + c + e) / 4, max = Math.max(Math.max(a, b), Math.max(c, e));
            double f = mean;
            boolean engineered = false;
            var l = d.local(x, z);
            if (l.isPresent() && Double.isFinite(mean)) {
                double s = l.get()[0], u = l.get()[1], v = Math.abs(u);
                double sc = Math.max(0, Math.min(d.length, s)), beyond = s < 0 ? -s : s > d.length ? s - d.length : 0;
                double design;
                if (v <= R4Section.EDGE) design = d.formationY(sc, u);
                else {
                    double h0 = d.formationY(sc, Math.copySign(R4Section.EDGE, u));
                    design = h0 + R4Section.roadside(v - R4Section.EDGE, mean - h0);
                }
                if (beyond > 0) design = approach(mean, design, beyond * END_SLOPE);
                engineered = Math.abs(design - mean) >= NATURAL_TOLERANCE || v <= R4Section.EDGE + 0.5 && beyond == 0;
                if (engineered) {
                    f = design;
                    // under the pavement or its bevels: quantise down, the turf never rises through the asphalt
                    boolean underPavement = v >= R4Section.HALF_MEDIAN - R4Section.BEVEL_RUN && v <= R4Section.EDGE + R4Section.BEVEL_RUN
                            && s >= -R4Section.BEVEL_RUN && s <= d.length + R4Section.BEVEL_RUN;
                    if (underPavement) f = Math.floor(f * 8 + 1e-9) / 8;
                    under[i * ch + j] = underPavement;
                    if (v > MAX_HALF_WIDTH) problems.add(new Problem("ENVELOPE_TOO_WIDE", x, z, String.format(Locale.ROOT, "%.1f m from the centreline", v)));
                    maxHalf = Math.max(maxHalf, v);
                }
            }
            if (!Double.isFinite(mean)) problems.add(new Problem("UNKNOWN_GROUND", x, z, ""));
            fin[i * ch + j] = engineered ? f : max;
            eng[i * ch + j] = engineered;
        }
        int lifted = lift(fin, eng, under, cw, ch, minX, minZ, problems);
        List<Write> writes = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        int graded = 0, paved = 0, clamped = 0;
        double maxCut = 0, maxFill = 0, maxColErr = 0;
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
            int k00 = i * ch + j, k10 = (i + 1) * ch + j, k01 = i * ch + j + 1, k11 = (i + 1) * ch + j + 1;
            if (!eng[k00] && !eng[k10] && !eng[k01] && !eng[k11]) continue;
            int x = minX + i, z = minZ + j, n = ground.top(x, z), p = ground.plants(x, z);
            if (n == Integer.MIN_VALUE) continue;   // already reported
            double centre = (fin[k00] + fin[k10] + fin[k01] + fin[k11]) / 4, natural = n + 1;
            double[] paveRange = pavedRange(d, x, z);
            if (paveRange != null) {
                // the asphalt block's top stays 2 cm under the lowest pavement point of the column (never through the mesh);
                // the invisible collision layers above it reach the column's mean pavement height (two blocks when > 1 m)
                int top32 = (int) Math.round((paveRange[0] + paveRange[1]) * 0.5 * 32), yc = (int) Math.floor(paveRange[0] - 0.02);
                int layers = top32 - yc * 32, upper = layers > 32 ? layers - 32 : 0;
                if (upper > 0) layers = 32;
                maxColErr = Math.max(maxColErr, Math.max(Math.abs(top32 / 32.0 - paveRange[0]), Math.abs(top32 / 32.0 - paveRange[1])));
                double cut = Math.max(0, natural - top32 / 32.0), fill = Math.max(0, top32 / 32.0 - natural);
                if (cut > MAX_CUT) problems.add(new Problem("CUT_TOO_DEEP", x, z, String.format(Locale.ROOT, "%.2f m", cut)));
                if (fill > MAX_FILL) problems.add(new Problem("FILL_TOO_HIGH", x, z, String.format(Locale.ROOT, "%.2f m", fill)));
                maxCut = Math.max(maxCut, cut);
                maxFill = Math.max(maxFill, fill);
                writes.add(new Write(x, yc, z, Kind.COLLISION, layers));
                if (upper > 0) writes.add(new Write(x, yc + 1, z, Kind.COLLISION, upper));
                int top = upper > 0 ? yc + 1 : yc;
                writes.add(new Write(x, yc - 1, z, Kind.ASPHALT, 0));
                writes.add(new Write(x, yc - 2, z, Kind.GRAVEL, 0));
                writes.add(new Write(x, yc - 3, z, Kind.STONE, 0));
                int bottom = yc - 3;
                for (int y = n + 1; y <= yc - 4; y++) { writes.add(new Write(x, y, z, Kind.DIRT, 0)); bottom = Math.min(bottom, y); }
                if (n <= yc - 4 && ground.grassy(x, z)) { writes.add(new Write(x, n, z, Kind.DIRT, 0)); bottom = Math.min(bottom, n); }
                for (int y = top + 1; y <= n + p; y++) writes.add(new Write(x, y, z, Kind.AIR, 0));
                columns.add(new Column(x, z, ColumnType.PAVED, n, top, bottom, cut, fill));
                paved++;
                continue;
            }
            double[] q = {fin[k00], fin[k10], fin[k01], fin[k11]};   // nw, ne, sw, se
            int[] fit = fit(q);
            if (fit[6] != 0) clamped++;
            int y = fit[0];
            double cut = Math.max(0, natural - centre), fill = Math.max(0, centre - natural);
            // roadside slopes may run deeper than the pavement limits only inside the envelope check above
            maxCut = Math.max(maxCut, cut);
            maxFill = Math.max(maxFill, fill);
            int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16;
            int bottom = y;
            if (y > n) {
                for (int yy = n + 1; yy < y; yy++) { writes.add(new Write(x, yy, z, Kind.DIRT, 0)); bottom = Math.min(bottom, yy); }
                if (ground.grassy(x, z)) { writes.add(new Write(x, n, z, Kind.DIRT, 0)); bottom = Math.min(bottom, n); }
            } else {
                for (int yy = y + 1; yy <= n; yy++) writes.add(new Write(x, yy, z, Kind.AIR, 0));
            }
            writes.add(new Write(x, y, z, Kind.TURF, data));
            for (int yy = n + 1; yy <= n + p; yy++) if (yy > y) writes.add(new Write(x, yy, z, Kind.AIR, 0));
            columns.add(new Column(x, z, ColumnType.GRADED, n, y, bottom, cut, fill));
            graded++;
        }
        // removals top-down, then placements bottom-up (gravel and everything else always lands on a placed or natural solid)
        writes.sort(Comparator.<Write>comparingInt(wr -> wr.kind == Kind.AIR ? 0 : 1)
                .thenComparingInt(wr -> wr.kind == Kind.AIR ? -wr.y : wr.y).thenComparingInt(Write::x).thenComparingInt(Write::z));
        int[] byKind = new int[Kind.values().length];
        int removed = 0, placed = 0;
        for (var wr : writes) { byKind[wr.kind.ordinal()]++; if (wr.kind == Kind.AIR) removed++; else placed++; }
        return new Plan(List.copyOf(writes), List.copyOf(columns), List.copyOf(problems),
                new Stats(graded, paved, removed, placed, byKind, maxCut, maxFill, maxHalf, clamped, maxColErr, lifted));
    }

    /** min / max pavement height over the column when the whole 1 x 1 square is paved (one side, 0 <= s <= length), else null. */
    static double[] pavedRange(R4Design d, int x, int z) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        int side = 0;
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            var l = d.local(x + a * 0.5, z + b * 0.5);
            if (l.isEmpty()) return null;
            double s = l.get()[0], u = l.get()[1], v = Math.abs(u);
            if (s < 0 || s > d.length || v <= R4Section.HALF_MEDIAN || v >= R4Section.EDGE) return null;
            int sg = u < 0 ? -1 : 1;
            if (side != 0 && sg != side) return null;
            side = sg;
            double y = d.pavementY(s, u);
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return new double[]{lo, hi};
    }

    /**
     * One graded_turf block spans at most one block between its lowest and highest corner. Where a graded block is steeper
     * (a natural step of the terrain next to the daylight line, where natural corners sit on the higher block's top edge),
     * its low corners are lifted in the shared corner field, so neighbouring blocks still share every corner; repeated
     * until stable. Corners under the pavement are never lifted (reported instead). Returns the number of lifts.
     */
    static int lift(double[] fin, boolean[] eng, boolean[] under, int cw, int ch, int minX, int minZ, List<Problem> problems) {
        int lifted = 0;
        for (int pass = 0; pass < 64; pass++) {
            boolean changed = false;
            for (int i = 0; i + 1 < cw; i++) for (int j = 0; j + 1 < ch; j++) {
                int[] k = {i * ch + j, (i + 1) * ch + j, i * ch + j + 1, (i + 1) * ch + j + 1};
                if (!eng[k[0]] && !eng[k[1]] && !eng[k[2]] && !eng[k[3]]) continue;
                long hi = Long.MIN_VALUE;
                boolean finite = true;
                for (int c : k) { finite &= Double.isFinite(fin[c]); hi = Math.max(hi, Math.round(fin[c] * 8)); }
                if (!finite) continue;   // unknown ground, reported already
                for (int c : k) {
                    if (Math.round(fin[c] * 8) >= hi - 8) continue;
                    if (under[c]) {
                        problems.add(new Problem("STEEP_GROUND_AT_PAVEMENT", minX + i, minZ + j, ""));
                        return lifted;
                    }
                    fin[c] = (hi - 8) / 8.0;
                    lifted++;
                    changed = true;
                }
            }
            if (!changed) return lifted;
        }
        problems.add(new Problem("STEEP_GROUND_NOT_RESOLVED", minX, minZ, ""));
        return lifted;
    }

    /** From g, move towards f by no more than the drop allowed so far: the end taper (1:4 longitudinal). */
    static double approach(double g, double f, double allowed) {
        double diff = f - g;
        return g + Math.copySign(Math.max(0, Math.abs(diff) - allowed), diff);
    }

    /**
     * Fit four corner heights (nw, ne, sw, se) to one graded_turf block: {y, shift, nw, ne, sw, se, clamped}. Corners are
     * quantised to 1/8 in world space; shift 0 holds corners in [y, y + 1], shift 1 in [y + 0.5, y + 1.5].
     */
    static int[] fit(double[] f) {
        int[] q = new int[4];
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) { q[i] = (int) Math.round(f[i] * 8); lo = Math.min(lo, q[i]); hi = Math.max(hi, q[i]); }
        // A: corners in (y, y + 1], the highest corner strictly above the floor (never a zero-height face on the block below)
        int ya = Math.floorDiv(hi - 1, 8);
        if (lo >= ya * 8) return pack(ya, 0, q, ya * 8, false);
        // B: corners in [y + 0.5, y + 1.5]
        int yb = Math.floorDiv(hi - 4 - 1, 8);
        if (lo >= yb * 8 + 4 && hi <= yb * 8 + 12) return pack(yb, 1, q, yb * 8 + 4, false);
        // too steep for one block: keep the high corners, lift the low ones (reported)
        return pack(ya, 0, q, ya * 8, true);
    }

    private static int[] pack(int y, int shift, int[] q, int base, boolean clamp) {
        int[] out = new int[7];
        out[0] = y;
        out[1] = shift;
        for (int i = 0; i < 4; i++) out[2 + i] = Math.max(0, Math.min(8, q[i] - base));
        out[6] = clamp ? 1 : 0;
        return out;
    }
}
