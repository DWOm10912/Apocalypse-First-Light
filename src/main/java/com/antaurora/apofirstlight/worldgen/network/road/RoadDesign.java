package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.List;

/**
 * One built route (C3.2, C3.3a; docs/worldgen/federal_highways_c3.md, 10 and 11): the planned alignment (RoadCurve),
 * the planned vertical profile (RoadProfile, the crown line datum E) and its class's cross-section (CrossSection: R4 on
 * an interstate, R2 on a state highway) with its superelevation runoff, plus the bridge spans where the road leaves the
 * ground. The formal counterpart of the dev R4Design, on the C3 plan's curve instead of the sample's RoadAlignment. Pure
 * Java, immutable; every height the terrain works, the chunk pass, the collision columns, the turf corners and the
 * client mesh use comes from here.
 * <p>
 * Stations run 0 .. length; beyond the ends the alignment continues straight for {@link #END} m (the end tapers of the
 * earthworks), with no pavement.
 */
public final class RoadDesign {
    /** Straight extension beyond each end that the end tapers may use, m. */
    public static final double END = 24;
    /**
     * No-passing zones of an undivided road (the R2 centre line, C3.3a), by sight distance (MUTCD 3B.02): a station is
     * no-passing when, ahead or behind, the centre line within NO_PASS_SIGHT leaves the straight sight line by more than
     * the cleared width (CrossSection edge + clear zone), or the profile rises above the sight line between two eyes of
     * NO_PASS_EYE; bridges (widened by NO_PASS_LEAD) and the route's ends are no-passing too; passing zones shorter than
     * MIN_PASS and no-passing zones shorter than MIN_NO_PASS are merged away (MUTCD: 400 ft passing zones, 500 ft
     * no-passing zones).
     */
    public static final double NO_PASS_SIGHT = 245, NO_PASS_EYE = 1.08, NO_PASS_LEAD = 30, NO_PASS_END = 60, MIN_PASS = 120, MIN_NO_PASS = 150;

    /**
     * A bridge span: stations s0 .. s1 lie on a deck (natural ground below), deck soffit depth in m; piers: the stations of
     * its intermediate piers (C3.3a: spans longer than BridgeMesh.MAX_SPAN), each a wall pier under the girders; reach:
     * how far out (|u|) the wing walls run at each end and side, {s0 left (u < 0), s0 right, s1 left, s1 right}: to where
     * the embankment meets the ground (group D; RoadStructures), BridgeMesh.WALL_REACH when the network JSON gives none.
     */
    public record Span(double s0, double s1, double depth, double[] piers, double[] reach) {
        public Span(double s0, double s1, double depth, double[] piers) { this(s0, s1, depth, piers, null); }

        public Span(double s0, double s1, double depth) { this(s0, s1, depth, new double[0]); }

        public Span {
            if (reach == null) reach = new double[]{BridgeMesh.WALL_REACH, BridgeMesh.WALL_REACH, BridgeMesh.WALL_REACH, BridgeMesh.WALL_REACH};
        }

        public boolean contains(double s) { return s >= s0 && s <= s1; }

        /** The wing walls' reach at span end sOut (s0 or s1) on the side of lateral u. */
        public double reach(double sOut, double u) { return reach[(sOut == s0 ? 0 : 2) + (u < 0 ? 0 : 1)]; }
    }

    public final String id;
    public final CrossSection section;
    public final RoadCurve curve;
    public final RoadProfile profile;
    public final double length;
    public final List<Span> spans;
    /** The box culverts under the road (group D; their stations are the network JSON's, not trimmed). */
    public final List<Culvert> culverts;
    private final double[] laneLeft, laneRight;
    private final boolean[] noPass;

    public RoadDesign(String id, CrossSection section, RoadCurve curve, RoadProfile profile, List<Span> spans) {
        this(id, section, curve, profile, spans, List.of());
    }

    public RoadDesign(String id, CrossSection section, RoadCurve curve, RoadProfile profile, List<Span> spans, List<Culvert> culverts) {
        if (profile.length > curve.length + RoadProfile.DS + 1e-6) throw new IllegalArgumentException("PROFILE_LONGER_THAN_CURVE " + id);
        this.id = id;
        this.section = section;
        this.curve = curve;
        this.profile = profile;
        this.length = Math.min(curve.length, profile.length);
        this.spans = List.copyOf(spans);
        this.culverts = List.copyOf(culverts);
        // lane slopes on the profile's 2 m lattice: the curvature targets with the superelevation runoff
        int m = profile.size();
        double[] tl = new double[m], tr = new double[m];
        for (int i = 0; i < m; i++) {
            var c = section.cross(curvature(i * RoadProfile.DS));
            tl[i] = c.laneLeft();
            tr[i] = c.laneRight();
        }
        laneLeft = section.runoff(tl, RoadProfile.DS);
        laneRight = section.runoff(tr, RoadProfile.DS);
        noPass = section.divided ? new boolean[m] : noPassing(m);
    }

    private boolean[] noPassing(int m) {
        double[] x = new double[m], z = new double[m], y = new double[m];
        for (int i = 0; i < m; i++) {
            double s = i * RoadProfile.DS;
            double[] p = point(s, 0);
            x[i] = p[0];
            z[i] = p[1];
            y[i] = profile.at(s);
        }
        int sight = (int) Math.round(NO_PASS_SIGHT / RoadProfile.DS);
        double clear = section.edge + section.clearZone;
        // the sight from i to i + sight is blocked (both directions share it; within NO_PASS_SIGHT of an end the end rule decides)
        boolean[] blocked = new boolean[m];
        for (int i = 0; i + sight < m; i++) {
            int j = i + sight;
            double dx = x[j] - x[i], dz = z[j] - z[i], dl = Math.hypot(dx, dz);
            for (int k = i + 1; k < j && !blocked[i]; k++) {
                double t = (double) (k - i) / sight;
                if (Math.abs((x[k] - x[i]) * dz - (z[k] - z[i]) * dx) / dl > clear) blocked[i] = true;
                else if (y[k] > y[i] + NO_PASS_EYE + (y[j] - y[i]) * t) blocked[i] = true;
            }
        }
        boolean[] w = new boolean[m];
        for (int i = 0; i < m; i++) {
            double s = i * RoadProfile.DS;
            // ahead: blocked from i; behind: blocked from i - sight
            w[i] = s < NO_PASS_END || s > length - NO_PASS_END || blocked[i] || (i >= sight && blocked[i - sight]);
            for (Span sp : spans) if (s >= sp.s0() - NO_PASS_LEAD && s <= sp.s1() + NO_PASS_LEAD) w[i] = true;
        }
        close(w, false, (int) Math.round(MIN_PASS / RoadProfile.DS));
        close(w, true, (int) Math.round(MIN_NO_PASS / RoadProfile.DS));
        return w;
    }

    /** Runs of value v shorter than min samples (not touching an end) take the other value. */
    private static void close(boolean[] w, boolean v, int min) {
        int i = 0;
        while (i < w.length) {
            if (w[i] != v) { i++; continue; }
            int j = i;
            while (j < w.length && w[j] == v) j++;
            if (j - i < min && i > 0 && j < w.length) for (int k = i; k < j; k++) w[k] = !v;
            i = j;
        }
    }

    public double curvature(double s) { return s < 0 || s > length ? 0 : curve.curvature(s); }

    /** The cross-section at station s: lane slopes linear between the 2 m samples (like the datum), shoulders derived. */
    public CrossSection.Cross cross(double s) {
        double x = Math.max(0, Math.min(length, s)) / RoadProfile.DS;
        int i = Math.min(laneLeft.length - 2, (int) Math.floor(x));
        double t = x - i;
        return CrossSection.lanes(laneLeft[i] + (laneLeft[i + 1] - laneLeft[i]) * t, laneRight[i] + (laneRight[i + 1] - laneRight[i]) * t,
                section.superelevation(curvature(s)));
    }

    /** Whether the centre line of an undivided road is no-passing (double yellow) at station s; always false when divided. */
    public boolean noPassing(double s) {
        int i = (int) Math.floor(Math.max(0, Math.min(length, s)) / RoadProfile.DS + 1e-9);
        return noPass[Math.min(noPass.length - 1, i)];
    }

    /** Datum (crown line height) at station s. */
    public double datum(double s) { return profile.at(s); }

    /** World XZ of (station, lateral). Positive lateral is to the right: (-tz, tx). Straight beyond the ends. */
    public double[] point(double s, double u) { return curve.point(s, u); }

    /** Pavement height at (s, u), 0 <= s <= length, paved band. */
    public double pavementY(double s, double u) { return datum(s) + section.pavement(cross(s), u); }

    /** The formation turf (median swale, or the turf just under the paved band) at (s, u), |u| <= edge. */
    public double formationY(double s, double u) { return datum(s) + section.formation(cross(s), u); }

    /** Station and lateral offset of a world point within maxDist of the centreline (stations -END .. length + END), or null. */
    public double[] local(double x, double z, double maxDist) { return curve.local(x, z, maxDist, END); }

    /** The bridge span holding station s, or null. */
    public Span span(double s) {
        for (Span sp : spans) if (sp.contains(s)) return sp;
        return null;
    }
}
