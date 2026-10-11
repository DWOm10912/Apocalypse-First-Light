package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-site scoring on the Terrain V2 plan (docs/worldgen/highway_r4_sample_v1.md, F step 2). Pure: the game (R4Site,
 * protection checks on top) and the offline probe run this same code. The suitability terms of the national corridor
 * study (no sea, marsh or river near the line, away from the shore, a stable layer, outside the fold belt), plus open
 * farmland / meadow (the agri_plain ecology zone, fewer trees to clear) and a little natural relief along the line, so the
 * sample shows some cut and fill. Candidates follow the real alignment of the recipe the plan command will use (the bend
 * included), sampled every 16 m along and every 20 m across out to the 40 m earthworks limit. The plan is only a guide:
 * plan surveys the real blocks.
 */
public final class R4SiteSearch {
    public static final double BEND = 8, SHORE_CLEAR = 64, RIVER_CLEAR = 48, MAX_BELT = 0.2, MAX_CROSS = 0.04, MAX_EST = 3.0, MIN_OPEN = 0.6;
    public static final int MIN_STABLE = 6, GRID = 96;
    /** Rejection reasons, in the order they are tested (cheap first, the river distance last). */
    public static final String[] REASONS = {"sea_or_drowned_valley", "marsh", "shore_within_64m", "fold_belt", "shallow_stable_layer",
            "cross_slope_over_4pc", "cut_fill_estimate_over_3m", "open_farmland_under_60pc", "river_within_48m"};

    public record Candidate(double x0, double z0, double heading, double length, double bend, double midX, double midZ, double midY,
                            double relief, double crossSlope, double estCut, double open, double riverClear, double score) {
        public String describe() {
            return String.format(Locale.ROOT, "heading %.0f bend %+.0f length %.0f: relief %.1f m, cross slope %.1f %%, est. cut/fill %.1f m, open farmland %.0f %%, river %s",
                    heading, bend, length, relief, crossSlope * 100, estCut, open * 100, riverClear >= 1e9 ? "none near" : String.format(Locale.ROOT, "%.0f m", riverClear));
        }
    }

    /** Every suitable candidate (best first) and how many were rejected for which reason. */
    public record Result(List<Candidate> candidates, int evaluated, Map<String, Integer> rejected) {
        public String rejections() {
            StringBuilder sb = new StringBuilder();
            rejected.forEach((k, v) -> { if (v > 0) sb.append(sb.length() == 0 ? "" : ", ").append(k).append(' ').append(v); });
            return sb.toString();
        }
    }

    /** One alignment shape (heading, bend) sampled relative to its start: centreline and the +-40 m lines every 16 m. */
    private record Shape(double heading, double bend, double[][] centre, double[][][] across, double[] mid) {}

    private R4SiteSearch() {}

    private static Shape shape(double heading, double bend, double length) {
        var r = new R4Recipe(R4Recipe.SCHEMA, 0, 0, heading, length, bend, R4Recipe.defaultLeg(length), null);
        var d = new R4Design(r.alignment(), R4Profile.of(new double[(int) Math.round(length / R4Profile.DS) + 1], length));
        int n = (int) (length / 16) + 1;
        double[][] c = new double[n][];
        double[][][] a = new double[n][5][];
        for (int i = 0; i < n; i++) {
            double s = Math.min(length, i * 16);
            c[i] = d.centre(s);
            for (int k = 0; k < 5; k++) a[i][k] = d.point(s, -40 + 20 * k);
        }
        return new Shape(heading, bend, c, a, d.centre(length / 2));
    }

    /** Segments whose midpoints lie on a 96 m grid within radius of (cx, cz), 24 headings (a right bend driven the other way is a left bend). */
    public static Result search(TerrainPlanSurface p, double cx, double cz, int radius, double length) {
        List<Shape> shapes = new ArrayList<>();
        for (int h = 0; h < 360; h += 15) shapes.add(shape(h, BEND, length));
        int[] rejected = new int[REASONS.length];
        List<Candidate> out = new ArrayList<>();
        int evaluated = 0;
        double[] width = new double[1];
        for (double gx = cx - radius; gx <= cx + radius; gx += GRID) for (double gz = cz - radius; gz <= cz + radius; gz += GRID) {
            if (Math.hypot(gx - cx, gz - cz) > radius) continue;
            for (var sh : shapes) {
                evaluated++;
                var c = score(p, sh, Math.floor(gx - sh.mid[0]) + 0.5, Math.floor(gz - sh.mid[1]) + 0.5, length, rejected, width);
                if (c != null) out.add(c);
            }
        }
        out.sort(Comparator.comparingDouble(Candidate::score));
        Map<String, Integer> why = new LinkedHashMap<>();
        for (int i = 0; i < REASONS.length; i++) why.put(REASONS[i], rejected[i]);
        return new Result(out, evaluated, why);
    }

    /** A segment of this shape starting at (x0, z0); null (and the reason counted) when unsuitable. */
    private static Candidate score(TerrainPlanSurface p, Shape sh, double x0, double z0, double length, int[] rejected, double[] width) {
        int n = sh.centre.length;
        double[] centre = new double[n];
        double cross = 0;
        int open = 0, samples = 0;
        for (int i = 0; i < n; i++) {
            for (double[] q : sh.across[i]) {
                double x = x0 + q[0], z = z0 + q[1];
                int reason = -1;
                if (p.waterAt(x, z)) reason = 0;
                else if (p.waterClass(x, z) == 3) reason = 1;
                else if (p.shoreDistance(x, z) < SHORE_CLEAR) reason = 2;
                else if (p.beltWeight(x, z) > MAX_BELT) reason = 3;
                else if (p.stableDepth(x, z) < MIN_STABLE) reason = 4;
                if (reason >= 0) { rejected[reason]++; return null; }
                if (p.ecology != null) {
                    samples++;
                    if (p.ecology.zone()[TerrainPlanSurface.cellIndex(p, x, z)] == EcologyPlan.AGRI_PLAIN) open++;
                }
            }
            centre[i] = p.heightAt(x0 + sh.centre[i][0], z0 + sh.centre[i][1]);
            double[] l = sh.across[i][0], r = sh.across[i][4];
            cross = Math.max(cross, Math.abs(p.heightAt(x0 + l[0], z0 + l[1]) - p.heightAt(x0 + r[0], z0 + r[1])) / 80);
        }
        if (cross > MAX_CROSS) { rejected[5]++; return null; }
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double v : centre) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double relief = hi - lo;
        // a crude cut / fill estimate: the centreline against a 3 % grade-limited, 48 m smoothed line
        double[] smooth = R4Profile.gauss(centre, 3);
        double step = R4Profile.MAX_GRADE * 16;
        for (int i = 1; i < n; i++) smooth[i] = Math.max(smooth[i - 1] - step, Math.min(smooth[i - 1] + step, smooth[i]));
        for (int i = n - 2; i >= 0; i--) smooth[i] = Math.max(smooth[i + 1] - step, Math.min(smooth[i + 1] + step, smooth[i]));
        double est = 0;
        for (int i = 0; i < n; i++) est = Math.max(est, Math.abs(centre[i] - smooth[i] - R4Profile.RAISE));
        if (est > MAX_EST) { rejected[6]++; return null; }
        double share = samples == 0 ? 0 : (double) open / samples;
        if (share < MIN_OPEN) { rejected[7]++; return null; }
        // rivers last (the index scan is the costly query): no bridges in this round, so no river water within 48 m of a
        // sample (every corridor point then stays at least about 35 m from the water). RiverNetwork.edgeDistance is
        // capped at the index reach (a few metres beyond the banks); nearestEdge measures out to the asked distance.
        double river = Double.POSITIVE_INFINITY;
        if (p.rivers != null) for (int i = 0; i < n; i++) for (double[] q : sh.across[i]) {
            double e = p.rivers.nearestEdge(x0 + q[0], z0 + q[1], RIVER_CLEAR + 32, width);
            river = Math.min(river, e);
            if (e < RIVER_CLEAR) { rejected[8]++; return null; }
        }
        // want: a few metres of relief (shows cut and fill), little cross slope, open land
        double score = Math.abs(relief - 6) * 0.5 + cross * 100 + (1 - share) * 10 + est;
        double mx = x0 + sh.mid[0], mz = z0 + sh.mid[1];
        return new Candidate(x0, z0, sh.heading, length, sh.bend, mx, mz, p.heightAt(mx, mz), relief, cross, est, share,
                river >= RIVER_CLEAR + 32 ? 1e9 : river, score);
    }
}
