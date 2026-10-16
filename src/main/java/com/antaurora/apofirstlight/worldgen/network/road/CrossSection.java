package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * The cross-section of a built road class (C3.2 / C3.3a; docs/worldgen/federal_highways_c3.md, 10 and 11). Pure Java,
 * metres, no Minecraft.
 * <ul>
 *   <li>{@link #R4}: the rural four-lane interstate, the accepted R4 sample section (docs/worldgen/highway_r4_sample_v1.md,
 *   section B; dev class R4Section), same numbers and the same functions;</li>
 *   <li>{@link #R2}: the two-lane state highway (C3.3a): 3.65 m lanes, 2.4 m paved shoulders, undivided, crowned on the
 *   centreline; its roadside is the R4 template at the state's scale.</li>
 * </ul>
 * Lateral offset u: 0 on the centreline, positive to the right of the direction of travel of the station (Minecraft XZ,
 * the same convention as RoadCurve). v = |u|. The design datum E(s) is the height of the crown line (R4: the yellow
 * inner edge line, v = 11; R2: the centreline); heights here are relative to E.
 * <pre>
 *   R4  v  0 ........ 9.5 | 9.5 ... 11 | 11 ......... 18.5 | 18.5 ...... 22 | 22 ->
 *          median turf     | inner      | two 3.75 lanes    | outer shoulder  | roadside slopes (RoadWorks)
 *          (swale, 0.8 m)  | shoulder   | (crown line at 11)|                 |
 *   R2  v  0 ........................... 3.65 | 3.65 ......... 6.05 | 6.05 ->
 *          one 3.65 lane (crown on the centre) | outer shoulder      | roadside slopes
 * </pre>
 * R4 widths are the accepted rural4 of src/dev/highway_mesh_resources/assets/afl_highway_demo/route/sections.json (44 m).
 */
public final class CrossSection {
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
    /** roadside template: 1:4 foreslope into the ditch, round bottom, 1:3 backslope; on fill 1:4 for the clear zone, then 1:3 */
    public static final double FORESLOPE = 0.25, BACKSLOPE = 1.0 / 3, FILL_STEEP = 1.0 / 3, ROUNDING = 0.4;

    /**
     * R4: superelevation for 110 km/h, emax 6 % (Rmin 560 m); runoff with the lane edge 7.5 m from the axis of rotation
     * (the crown line) rising by at most 1:263 (AASHTO maximum relative gradient at 110 km/h), with the 0.75 factor for two
     * rotated lanes: normal crown to 6 % in about 118 m. Clear zone 6 m, ditch 1.0 m deep, 1.2 m bottom.
     */
    public static final CrossSection R4 = new CrossSection("R4", 3.75, 1.5, 3.5, 19.0, 2, 0.06, 560, 0.0038 / (2 * 3.75 * 0.75), 6.0, 1.0, 1.2);
    /**
     * R2 (C3.3a): superelevation for 90 km/h, emax 6 % (AASHTO Rmin 335 m); one lane rotated about the centreline, its edge
     * rising by at most 1:227 (0.44 %, 90 km/h): normal crown to 6 % in about 66 m. Clear zone 5 m, ditch 0.8 m deep, 1.0 m
     * bottom (the state's smaller runoff and lower speed).
     */
    public static final CrossSection R2 = new CrossSection("R2", 3.65, 0, 2.4, 0, 1, 0.06, 335, 0.0044 / 3.65, 5.0, 0.8, 1.0);

    public final String name;
    public final double lane, inner, outer, median;
    /** lanes per direction */
    public final int lanes;
    public final double halfMedian, crown, lanesOut, edge;
    /** two carriageways with a turf median between them (R4); an undivided road is crowned on its centreline (R2) */
    public final boolean divided;
    public final double eMax, rMin, runoffRate;
    public final double clearZone, ditchDepth, ditchBottom;

    private CrossSection(String name, double lane, double inner, double outer, double median, int lanes, double eMax, double rMin,
                         double runoffRate, double clearZone, double ditchDepth, double ditchBottom) {
        this.name = name;
        this.lane = lane;
        this.inner = inner;
        this.outer = outer;
        this.median = median;
        this.lanes = lanes;
        this.halfMedian = median / 2;
        this.crown = halfMedian + inner;
        this.lanesOut = crown + lanes * lane;
        this.edge = lanesOut + outer;
        this.divided = median > 0;
        this.eMax = eMax;
        this.rMin = rMin;
        this.runoffRate = runoffRate;
        this.clearZone = clearZone;
        this.ditchDepth = ditchDepth;
        this.ditchBottom = ditchBottom;
    }

    /** The section of a route class (RoadClassSpec id): interstate R4, state R2; others are not built yet. */
    public static CrossSection of(String roadClass) {
        return switch (roadClass) {
            case "interstate" -> R4;
            case "state" -> R2;
            default -> throw new IllegalArgumentException("no built cross-section for class " + roadClass);
        };
    }

    /** Cross slopes of both carriageways (R2: both halves) at one station. lane: positive falls away from the median (outward). */
    public record Cross(double laneLeft, double laneRight, double innerLeft, double innerRight, double outerLeft, double outerRight,
                        double superelevation) {
        public double lane(int side) { return side < 0 ? laneLeft : laneRight; }
        public double inner(int side) { return side < 0 ? innerLeft : innerRight; }
        public double outer(int side) { return side < 0 ? outerLeft : outerRight; }
    }

    /** Superelevation rate for a signed curvature (1/m); 0 on a tangent. AASHTO e = emax (2x - x^2), x = Rmin / R. */
    public double superelevation(double curvature) {
        double x = Math.min(1, rMin * Math.abs(curvature));
        return eMax * (2 * x - x * x);
    }

    /**
     * The target cross slopes for a signed curvature (RoadCurve: positive turns towards +u). The half on the inside of the
     * curve keeps falling outward (towards the curve centre) at max(2 %, e); the outside one rotates about its crown line
     * from +2 % to -e (falling towards the median or the centreline, i.e. also towards the centre), between e = 1.5 % and
     * 2.5 %. Shoulders follow with a rollover of at most 8 %. RoadDesign spreads these targets along the road with runoff().
     */
    public Cross cross(double curvature) {
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
     * separately) dilated by runoffRate, so the rotation starts before the curve and reaches the full target wherever the
     * target asks for it (forward / backward passes of the cone distance).
     */
    public double[] runoff(double[] target, double ds) {
        int n = target.length;
        double[] up = new double[n], down = new double[n], out = new double[n];
        double step = runoffRate * ds;
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

    /** Whether lateral v lies on the paved band (between the inner and the outer shoulder edges). */
    public boolean paved(double v) { return v >= halfMedian - 1e-9 && v <= edge + 1e-9; }

    /** Pavement height relative to E at lateral u, halfMedian <= |u| <= edge. */
    public double pavement(Cross c, double u) {
        int side = u < 0 ? -1 : 1;
        double v = Math.abs(u);
        if (!paved(v)) throw new IllegalArgumentException("NOT_PAVED " + u);
        if (v <= crown) return -c.inner(side) * (crown - v);
        if (v <= lanesOut) return -c.lane(side) * (v - crown);
        return -c.lane(side) * (lanesOut - crown) - c.outer(side) * (v - lanesOut);
    }

    /** Median turf height relative to E, |u| <= halfMedian (divided only): a round-bottomed swale between the inner shoulder edges. */
    public double median(Cross c, double u) {
        double left = pavement(c, -halfMedian) - TURF_DROP, right = pavement(c, halfMedian) - TURF_DROP;
        double base = left + (right - left) * (u + halfMedian) / median;
        double t = (halfMedian - Math.abs(u)) / halfMedian, a = 1 - (1 - t) * (1 - t);
        return base - MEDIAN_DEPTH * a * a;
    }

    /** The formation surface inside the road: median turf, or the turf under the paved band (pavement minus TURF_DROP). */
    public double formation(Cross c, double u) {
        double v = Math.abs(u);
        if (divided && v <= halfMedian) return median(c, u);
        return pavement(c, Math.copySign(Math.min(v, edge), u)) - TURF_DROP;
    }

    /** The zone at lateral u (paved only between the two inner shoulder edges and the outer shoulder edges). */
    public enum Zone { MEDIAN, INNER_SHOULDER, LANE, OUTER_SHOULDER, ROADSIDE }

    public Zone zone(double u) {
        double v = Math.abs(u);
        if (v < halfMedian) return Zone.MEDIAN;
        if (v < crown) return Zone.INNER_SHOULDER;
        if (v < lanesOut) return Zone.LANE;
        if (v <= edge) return Zone.OUTER_SHOULDER;
        return Zone.ROADSIDE;
    }

    /** Lateral offset of the middle of lane k (0: the one next to the crown line) on one side. */
    public double laneCentre(int k) { return crown + (k + 0.5) * lane; }

    /**
     * Roadside surface beyond the outer shoulder edge (d = v - edge >= 0), height relative to the shoulder hinge h0 (the
     * turf at the shoulder edge), for ground g (also relative to h0): max(fill template, min(cut template, ground)),
     * rounded. Continuous in g: a shallow cut digs the ditch into the ground, a fill buries it, a deep cut keeps it.
     */
    public double roadside(double d, double g) {
        double fill = d <= clearZone ? -FORESLOPE * d : -FORESLOPE * clearZone - FILL_STEEP * (d - clearZone);
        double toDitch = ditchDepth / FORESLOPE;
        double cut = d <= toDitch ? -FORESLOPE * d : d <= toDitch + ditchBottom ? -ditchDepth : -ditchDepth + BACKSLOPE * (d - toDitch - ditchBottom);
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

    @Override
    public String toString() { return name; }
}
