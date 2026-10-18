package com.antaurora.apofirstlight.worldgen.network.route;

import com.antaurora.apofirstlight.worldgen.network.road.RoadCurve;
import com.antaurora.apofirstlight.worldgen.network.road.RoadProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Vertical design of one planned route (C3.1; docs/worldgen/federal_highways_c3.md, 3.3): ground survey on the plan,
 * the profile within the class's grade limits, and the structures the profile needs.
 * <ul>
 *   <li>ground: every 2 m, the trimmed mean (a fifth off each end) of the planned ground across the formation, so one
 *   bank or hollow does not steer the grade; over water the ground is bridged by the line between the dry banks;</li>
 *   <li>grade limit per step: the class grade, its mountain grade where the route runs through mountain or plateau
 *   country (physiography share >= 0.4);</li>
 *   <li>lower bounds: over river, lake or estuary water the deck soffit stays the class clearance above the water
 *   surface; over tidal marsh the road stands on a low embankment;</li>
 *   <li>structures from the result: BRIDGE over water (CULVERT when the water is a stream at most 6 m wide: the road
 *   then only stands the culvert's opening and cover above the water), VIADUCT
 *   where a dry fill would exceed the class's maximum fill, TUNNEL where a cut deeper than the class's maximum runs at
 *   least 60 m, DEEP_CUT where it is shorter.</li>
 * </ul>
 */
public final class RouteDesigner {
    private RouteDesigner() {}

    public enum Kind { BRIDGE, CULVERT, VIADUCT, TUNNEL, DEEP_CUT }

    /** A structure between two stations; height: the deepest water / fill / cut it spans, m; width: the widest river
     *  it crosses (bridges and culverts over rivers; 0 otherwise), m. */
    public record Structure(Kind kind, double s0, double s1, double height, double width, String note) {
        public double length() { return s1 - s0; }
    }

    public record Stats(double length, double maxGrade, double meanGrade, double mountainGradeLength, double minRadius, double minK, double belowK,
                        double maxCut, double maxFill, double earthworks, int bridges, double bridgeLength, int culverts,
                        int viaducts, double viaductLength, int tunnels, double tunnelLength, int deepCuts) {}

    public record Result(RoadCurve curve, RoadProfile profile, double[] ground, byte[] wet, List<Structure> structures, Stats stats) {}

    /** Lateral survey spacing across the formation, m. */
    static final double SURVEY = 4;
    static final double MOUNTAIN_SHARE = 0.4;
    /** Streams up to this width pass under the road in a culvert (box or pipe) instead of a bridge, m. */
    static final double CULVERT_WIDTH = 6.0;
    /** Lowest crown over a culvert: opening (1.8 m) and cover over it, above the water surface, m. */
    static final double CULVERT_COVER = 2.6;

    public static Result design(RoadClassSpec k, RoadCurve curve, RouteTerrain t) { return design(k, curve, t, null); }

    /**
     * The design with a given profile (its samples every DS m, as many as this curve's profile has; null: designed here),
     * the structures and statistics taken from it: one leg of an interstate whose legs were designed as one road through
     * a town (C3.3b group 3, RoadNetworkPlan#designChains).
     */
    public static Result design(RoadClassSpec k, RoadCurve curve, RouteTerrain t, double[] given) {
        double length = Math.floor(curve.length / RoadProfile.DS) * RoadProfile.DS;
        int n = RoadProfile.samples(length);
        double[] ground = new double[n], lower = new double[n], grade = new double[n], gMin = new double[n], gMax = new double[n];
        byte[] wet = new byte[n];
        double[] riverWidth = new double[n];
        Arrays.fill(lower, Double.NaN);
        double[] f = new double[5];
        List<Double> v = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double s = i * RoadProfile.DS;
            curve.frame(s, f);
            v.clear();
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, surface = Double.NEGATIVE_INFINITY;
            int w = RouteTerrain.DRY;
            for (double u = -k.formation; u <= k.formation + 1e-9; u += Math.min(SURVEY, k.formation)) {
                double x = f[0] - f[3] * u, z = f[1] + f[2] * u;
                double h = t.height(x, z);
                v.add(h);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                int wc = t.water(x, z);
                if (wc == RouteTerrain.RIVER || wc == RouteTerrain.LAKE || wc == RouteTerrain.SEA) {
                    w = Math.max(w, wc);
                    surface = Math.max(surface, t.waterSurface(x, z));
                } else if (wc == RouteTerrain.MARSH && w == RouteTerrain.DRY) w = RouteTerrain.MARSH;
            }
            v.sort(Double::compare);
            int cut = v.size() / 5;
            double sum = 0;
            for (int q = cut; q < v.size() - cut; q++) sum += v.get(q);
            ground[i] = sum / (v.size() - 2 * cut);
            gMin[i] = lo;
            gMax[i] = hi;
            wet[i] = (byte) w;
            if (w == RouteTerrain.RIVER) riverWidth[i] = riverWidth(t, f[0], f[1]);
            if (w == RouteTerrain.RIVER && riverWidth[i] <= CULVERT_WIDTH) lower[i] = surface + CULVERT_COVER;
            else if (w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA)
                lower[i] = surface + clearance(k, w, riverWidth[i]) + k.deckDepth() + 0.35;
            else if (w == RouteTerrain.MARSH) lower[i] = RouteTerrainConstants.MARSH_EMBANKMENT;
            grade[i] = t.rugged(f[0], f[1]) >= MOUNTAIN_SHARE ? k.gradeMountain : k.grade;
        }
        // the grade limit changes gradually (100 m), so the clamp never breaks the line where a mountain section begins
        double[] gs = RoadProfile.smooth(grade, 50 / RoadProfile.DS);
        for (int i = 0; i < n; i++) grade[i] = Math.min(Math.max(k.grade, gs[i]), k.gradeMountain);
        // over water the smoothing input is the line between the dry banks (the bridge does not dip into the river)
        double[] input = ground.clone();
        for (int i = 0; i < n; ) {
            if (!isWater(wet[i])) { i++; continue; }
            int a = i;
            while (i < n && isWater(wet[i])) i++;
            double ya = a > 0 ? ground[a - 1] : (i < n ? ground[i] : ground[a]), yb = i < n ? ground[i] : ya;
            for (int q = a; q < i; q++) input[q] = ya + (yb - ya) * (q - a + 1) / (i - a + 1);
        }
        if (given != null && given.length != n) throw new IllegalArgumentException("ROUTE_PROFILE_SAMPLES " + given.length + " != " + n);
        RoadProfile p = given != null ? RoadProfile.of(given) : RoadProfile.design(input, k.sigma, k.raise, k.vSigma(), k.kMin(), grade, lower);
        double[] e = p.samples();
        // structures
        List<Structure> out = new ArrayList<>();
        boolean[] spanned = new boolean[n];
        for (int i = 0; i < n; ) {
            if (!isWater(wet[i]) || spanned[i]) { i++; continue; }
            int a = i;
            double width = 0, deepest = 0;
            boolean river = true;
            while (i < n && isWater(wet[i])) { width = Math.max(width, riverWidth[i]); river &= wet[i] == RouteTerrain.RIVER; deepest = Math.max(deepest, e[i] - gMin[i]); i++; }
            int b = i - 1;
            boolean culvert = river && width <= CULVERT_WIDTH && (b - a + 1) * RoadProfile.DS <= 14;
            // abutments: out to where the fill is moderate (at most 70 % of the class maximum), at most 40 m beyond the
            // water (a longer high fill is a viaduct, found below), and 6 m of bank more
            int a0 = a, b0 = b, reach = (int) Math.round(40 / RoadProfile.DS);
            while (!culvert && a > 0 && a0 - a < reach && e[a - 1] - gMin[a - 1] > 0.7 * k.maxFill) a--;
            while (!culvert && b < n - 1 && b - b0 < reach && e[b + 1] - gMin[b + 1] > 0.7 * k.maxFill) b++;
            int pad = culvert ? 0 : (int) Math.round(6 / RoadProfile.DS);
            a = Math.max(0, a - pad);
            b = Math.min(n - 1, b + pad);
            // a bridge whose abutment reaches the previous bridge becomes one structure with it
            Structure last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (!culvert && last != null && last.kind() == Kind.BRIDGE && a * RoadProfile.DS <= last.s1() + 12) {
                out.remove(out.size() - 1);
                a = (int) Math.round(last.s0() / RoadProfile.DS);
                deepest = Math.max(deepest, last.height());
                width = Math.max(width, last.width());
            }
            for (int q = a; q <= b; q++) spanned[q] = true;
            String what = wetName(wet, a, b);
            out.add(new Structure(culvert ? Kind.CULVERT : Kind.BRIDGE, a * RoadProfile.DS, b * RoadProfile.DS, deepest, width,
                    what.equals("river") ? String.format(Locale.ROOT, "river, water %.0f m, widest %.1f m", countWet(wet, a, b) * RoadProfile.DS, width)
                            : String.format(Locale.ROOT, "%s, water %.0f m", what, countWet(wet, a, b) * RoadProfile.DS)));
        }
        for (int i = 0; i < n; ) {
            if (spanned[i] || e[i] - gMin[i] <= k.maxFill) { i++; continue; }
            int a = i;
            double h = 0;
            while (i < n && !spanned[i] && e[i] - gMin[i] > k.maxFill * 0.85) { h = Math.max(h, e[i] - gMin[i]); i++; }
            if ((i - a) * RoadProfile.DS >= 20) {
                for (int q = a; q < i; q++) spanned[q] = true;
                out.add(new Structure(Kind.VIADUCT, a * RoadProfile.DS, (i - 1) * RoadProfile.DS, h, 0, String.format(Locale.ROOT, "fill would be %.1f m", h)));
            }
        }
        for (int i = 0; i < n; ) {
            if (spanned[i] || gMax[i] - e[i] <= k.maxCut) { i++; continue; }
            int a = i;
            double h = 0;
            while (i < n && gMax[i] - e[i] > k.maxCut) { h = Math.max(h, gMax[i] - e[i]); i++; }
            double len = (i - a) * RoadProfile.DS;
            out.add(new Structure(len >= 60 ? Kind.TUNNEL : Kind.DEEP_CUT, a * RoadProfile.DS, (i - 1) * RoadProfile.DS, h, 0,
                    String.format(Locale.ROOT, "cut %.1f m over %.0f m", h, len)));
            if (len >= 60) for (int q = a; q < i; q++) spanned[q] = true;
        }
        out.sort((x, y) -> Double.compare(x.s0(), y.s0()));
        // statistics (earthworks outside structures)
        double maxCut = 0, maxFill = 0, works = 0, sumG = 0, mountainLen = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                double g = Math.abs(e[i] - e[i - 1]) / RoadProfile.DS;
                sumG += g * RoadProfile.DS;
                if (g > k.grade + 1e-9) mountainLen += RoadProfile.DS;
            }
            if (spanned[i]) continue;
            maxCut = Math.max(maxCut, gMax[i] - e[i]);
            maxFill = Math.max(maxFill, e[i] - gMin[i]);
            works += Math.abs(e[i] - ground[i]) * 2 * k.formation * RoadProfile.DS;
        }
        int[] cnt = new int[Kind.values().length];
        double[] len = new double[Kind.values().length];
        for (Structure s : out) { cnt[s.kind().ordinal()]++; len[s.kind().ordinal()] += s.length(); }
        Stats st = new Stats(length, p.maxGrade(), sumG / Math.max(1, length), mountainLen, curve.minRadius(), p.minK(), p.lengthBelowK(k.kMin()),
                Math.max(0, maxCut), Math.max(0, maxFill), works, cnt[Kind.BRIDGE.ordinal()], len[Kind.BRIDGE.ordinal()],
                cnt[Kind.CULVERT.ordinal()], cnt[Kind.VIADUCT.ordinal()], len[Kind.VIADUCT.ordinal()], cnt[Kind.TUNNEL.ordinal()],
                len[Kind.TUNNEL.ordinal()], cnt[Kind.DEEP_CUT.ordinal()]);
        return new Result(curve, p, ground, wet, List.copyOf(out), st);
    }

    /**
     * Clear height under the deck over water, m: creeks (under 12 m) a flood freeboard of 2.5 m, small rivers (under
     * 30 m) at most 4 m, wide rivers, lakes and estuaries the class's clearance (boats).
     */
    static double clearance(RoadClassSpec k, int water, double riverWidth) {
        if (water == RouteTerrain.RIVER && riverWidth < 12) return 2.5;
        if (water == RouteTerrain.RIVER && riverWidth < 30) return Math.min(4.0, k.clearance);
        return k.clearance;
    }

    static boolean isWater(byte w) { return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA; }

    static int countWet(byte[] wet, int a, int b) {
        int c = 0;
        for (int q = a; q <= b; q++) if (isWater(wet[q])) c++;
        return c;
    }

    static String wetName(byte[] wet, int a, int b) {
        boolean lake = false, sea = false;
        for (int q = a; q <= b; q++) { lake |= wet[q] == RouteTerrain.LAKE; sea |= wet[q] == RouteTerrain.SEA; }
        return sea ? "estuary" : lake ? "lake" : "river";
    }

    /** Width of the river at a point (the nearest river edge query reports its width). */
    static double riverWidth(RouteTerrain t, double x, double z) {
        double[] w = new double[1];
        t.plan.rivers.nearestEdge(x, z, 64, w);
        return w[0];
    }

    /** Small constants shared by the planner. */
    static final class RouteTerrainConstants {
        /** Lowest crown over tidal marsh (the marsh flat is Y63.4; a 1.3 m embankment). */
        static final double MARSH_EMBANKMENT = 64.7;
    }
}
