package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * The R4 rural four-lane cross-section of the formal interstate (C3.2; docs/worldgen/federal_highways_c3.md, 10): the
 * accepted R4 sample section (docs/worldgen/highway_r4_sample_v1.md, section B; dev class R4Section), same numbers and
 * the same functions, moved into the formal package. Pure Java, metres, no Minecraft.
 * <p>
 * Lateral offset u: 0 on the centreline, positive to the right of the direction of travel of the station (Minecraft XZ,
 * the same convention as RoadCurve). v = |u|. The design datum E(s) is the height of the crown
 * line (the yellow inner edge line, v = 11) of both carriageways; heights here are relative to E.
 * <pre>
 *   v  0 ........ 9.5 | 9.5 ... 11 | 11 ......... 18.5 | 18.5 ...... 22 | 22 ->
 *      median turf     | inner      | two 3.75 lanes    | outer shoulder  | roadside slopes (Earthworks)
 *      (swale, 0.8 m)  | shoulder   | (crown line at 11)|                 |
 * </pre>
 * Widths are the accepted rural4 of src/dev/highway_mesh_resources/assets/afl_highway_demo/route/sections.json (44 m).
 */
public final class R4CrossSection {
    public static final double LANE = 3.75, INNER = 1.5, OUTER = 3.5, MEDIAN = 19.0;
    public static final double HALF_MEDIAN = MEDIAN / 2, CROWN = HALF_MEDIAN + INNER, LANES_OUT = CROWN + 2 * LANE, EDGE = LANES_OUT + OUTER;
    /** normal crown: lanes fall 2 % outward, outer shoulders 4 % outward, inner shoulders 4 % towards the median */
    public static final double LANE_SLOPE = 0.02, SHOULDER_SLOPE = 0.04, MAX_ROLLOVER = 0.08;
    /**
     * The formation turf lies this far under the pavement (the mesh is depth-offset, so it wins even coplanar). Turf corners
     * under the paved band and its edges are quantised down (1/8 block), so the turf sits 2 .. 15 cm below the asphalt;
     * the pavement edges are 45-degree bevels 0.3 m deep, deeper than the lowest turf next to them (also with the chord of
     * a block across the shoulder hinge), so no gap opens under an edge. Most of a bevel is buried; the visible part is the
     * pavement edge drop to the turf.
     */
    public static final double TURF_DROP = 0.02, BEVEL_DROP = 0.30, BEVEL_RUN = 0.30;
    public static final double MEDIAN_DEPTH = 0.8;
    /** roadside template: 1:4 foreslope into a ditch 1.0 m below the shoulder edge, 1.2 m round bottom, 1:3 backslope;
     *  on fill 1:4 for the first 6 m (clear zone), then 1:3 */
    public static final double FORESLOPE = 0.25, BACKSLOPE = 1.0 / 3, FILL_STEEP = 1.0 / 3, CLEAR_ZONE = 6.0,
            DITCH_DEPTH = 1.0, DITCH_BOTTOM = 1.2, ROUNDING = 0.4;
    /** superelevation: design speed 110 km/h, emax 6 %, AASHTO e = emax (2x - x^2), x = Rmin / R */
    public static final double E_MAX = 0.06, R_MIN = 560;
    /**
     * Superelevation runoff: the lane edge 7.5 m from the axis of rotation (the crown line) may rise or fall relative to it
     * by at most 1:263 (AASHTO maximum relative gradient at 110 km/h), with the 0.75 factor for two rotated lanes, so a
     * cross slope changes by at most 0.0038 / (7.5 x 0.75) per metre: normal crown to 6 % takes about 118 m.
     */
    public static final double RUNOFF_RATE = 0.0038 / (2 * LANE * 0.75);

    private R4CrossSection() {}

    /** Cross slopes of both carriageways at one station. lane: positive falls away from the median (outward). */
    public record Cross(double laneLeft, double laneRight, double innerLeft, double innerRight, double outerLeft, double outerRight,
                        double superelevation) {
        public double lane(int side) { return side < 0 ? laneLeft : laneRight; }
        public double inner(int side) { return side < 0 ? innerLeft : innerRight; }
        public double outer(int side) { return side < 0 ? outerLeft : outerRight; }
    }

    /** Superelevation rate for a signed curvature (1/m); 0 on a tangent. */
    public static double superelevation(double curvature) {
        double x = Math.min(1, R_MIN * Math.abs(curvature));
        return E_MAX * (2 * x - x * x);
    }

    /**
     * The target cross slopes for a signed curvature (RoadCurve: positive turns towards +u). The carriageway on the
     * inside of the curve keeps falling outward (towards the curve centre) at max(2 %, e); the outside one rotates about its
     * crown line from +2 % to -e (falling towards the median, i.e. also towards the centre), between e = 1.5 % and 2.5 %.
     * Shoulders follow with a rollover of at most 8 %. RoadDesign spreads these targets along the road with runoff().
     */
    public static Cross cross(double curvature) {
        double e = superelevation(curvature);
        double w = smooth((e - 0.015) / 0.01);
        double inside = Math.max(LANE_SLOPE, e), outside = LANE_SLOPE * (1 - w) - e * w;
        double left, right;
        if (curvature > 0) { right = inside; left = outside; } else if (curvature < 0) { left = inside; right = outside; } else { left = right = LANE_SLOPE; }
        return lanes(left, right, e);
    }

    /** A cross-section from the two lane slopes (shoulders derived). */
    public static Cross lanes(double left, double right, double e) {
        return new Cross(left, right, innerSlope(left), innerSlope(right), outerSlope(left), outerSlope(right), e);
    }

    /**
     * Runoff along the road: lane slopes sampled every ds metres, each departure from the normal crown (up and down
     * separately) dilated by RUNOFF_RATE, so the rotation starts before the curve and reaches the full target wherever the
     * target asks for it (forward / backward passes of the cone distance).
     */
    public static double[] runoff(double[] target, double ds) {
        int n = target.length;
        double[] up = new double[n], down = new double[n], out = new double[n];
        double step = RUNOFF_RATE * ds;
        for (int i = 0; i < n; i++) { up[i] = Math.max(0, target[i] - LANE_SLOPE); down[i] = Math.max(0, LANE_SLOPE - target[i]); }
        for (double[] a : new double[][]{up, down}) {
            for (int i = 1; i < n; i++) a[i] = Math.max(a[i], a[i - 1] - step);
            for (int i = n - 2; i >= 0; i--) a[i] = Math.max(a[i], a[i + 1] - step);
        }
        for (int i = 0; i < n; i++) out[i] = LANE_SLOPE + up[i] - down[i];
        return out;
    }

    /** Inner shoulder slope, positive falling towards the median; the crest at the crown line breaks by at most 8 %. */
    static double innerSlope(double lane) {
        return lane <= 0 ? Math.max(SHOULDER_SLOPE, -lane) : Math.min(SHOULDER_SLOPE, MAX_ROLLOVER - lane);
    }

    /** Outer shoulder slope, positive falling outward; the break at the lane edge is at most 8 %. */
    static double outerSlope(double lane) {
        return lane >= 0 ? Math.max(SHOULDER_SLOPE, lane) : Math.min(SHOULDER_SLOPE, lane + MAX_ROLLOVER);
    }

    /** Pavement height relative to E at lateral u, HALF_MEDIAN <= |u| <= EDGE. */
    public static double pavement(Cross c, double u) {
        int side = u < 0 ? -1 : 1;
        double v = Math.abs(u);
        if (v < HALF_MEDIAN - 1e-9 || v > EDGE + 1e-9) throw new IllegalArgumentException("NOT_PAVED " + u);
        if (v <= CROWN) return -c.inner(side) * (CROWN - v);
        if (v <= LANES_OUT) return -c.lane(side) * (v - CROWN);
        return -c.lane(side) * (LANES_OUT - CROWN) - c.outer(side) * (v - LANES_OUT);
    }

    /** Median turf height relative to E, |u| <= HALF_MEDIAN: a round-bottomed swale between the two inner shoulder edges. */
    public static double median(Cross c, double u) {
        double left = pavement(c, -HALF_MEDIAN) - TURF_DROP, right = pavement(c, HALF_MEDIAN) - TURF_DROP;
        double base = left + (right - left) * (u + HALF_MEDIAN) / MEDIAN;
        double t = (HALF_MEDIAN - Math.abs(u)) / HALF_MEDIAN, a = 1 - (1 - t) * (1 - t);
        return base - MEDIAN_DEPTH * a * a;
    }

    /** The formation surface inside the road: median turf, or the turf under the paved band (pavement minus TURF_DROP). */
    public static double formation(Cross c, double u) {
        double v = Math.abs(u);
        if (v <= HALF_MEDIAN) return median(c, u);
        return pavement(c, Math.copySign(Math.min(v, EDGE), u)) - TURF_DROP;
    }

    /** The zone at lateral u (paved only between the two inner shoulder edges and the outer shoulder edges). */
    public enum Zone { MEDIAN, INNER_SHOULDER, LANE, OUTER_SHOULDER, ROADSIDE }

    public static Zone zone(double u) {
        double v = Math.abs(u);
        if (v < HALF_MEDIAN) return Zone.MEDIAN;
        if (v < CROWN) return Zone.INNER_SHOULDER;
        if (v < LANES_OUT) return Zone.LANE;
        if (v <= EDGE) return Zone.OUTER_SHOULDER;
        return Zone.ROADSIDE;
    }

    /**
     * Roadside surface beyond the outer shoulder edge (d = v - EDGE >= 0), height relative to the shoulder hinge h0 (the
     * turf at the shoulder edge), for ground g (also relative to h0): max(fill template, min(cut template, ground)),
     * rounded. Continuous in g: a shallow cut digs the ditch into the ground, a fill buries it, a deep cut keeps it.
     */
    public static double roadside(double d, double g) {
        double fill = d <= CLEAR_ZONE ? -FORESLOPE * d : -FORESLOPE * CLEAR_ZONE - FILL_STEEP * (d - CLEAR_ZONE);
        double toDitch = DITCH_DEPTH / FORESLOPE;
        double cut = d <= toDitch ? -FORESLOPE * d : d <= toDitch + DITCH_BOTTOM ? -DITCH_DEPTH : -DITCH_DEPTH + BACKSLOPE * (d - toDitch - DITCH_BOTTOM);
        // the rounding grows from zero at the hinge, which stays exactly on the formation edge, and never exceeds the gap
        // between the two templates (fill <= cut; on the common 1:4 foreslope they coincide and the surface is exact)
        double k = Math.min(ROUNDING * Math.min(1, d), cut - fill);
        return smax(fill, smin(cut, g, k), k);
    }

    /** Polynomial smooth min / max (radius k): equal to min / max when the two differ by more than k. */
    public static double smin(double a, double b, double k) {
        if (k < 1e-9) return Math.min(a, b);
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    public static double smax(double a, double b, double k) { return -smin(-a, -b, k); }

    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
