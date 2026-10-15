package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.List;

/**
 * One built interstate route (C3.2; docs/worldgen/federal_highways_c3.md, 10): the planned alignment (RoadCurve), the
 * planned vertical profile (RoadProfile, the crown line datum E) and the R4 cross-section (R4CrossSection) with its
 * superelevation runoff, plus the bridge spans where the road leaves the ground. The formal counterpart of the dev
 * R4Design, on the C3 plan's curve instead of the sample's RoadAlignment. Pure Java, immutable; every height the
 * terrain works, the chunk pass, the collision columns, the turf corners and the client mesh use comes from here.
 * <p>
 * Stations run 0 .. length; beyond the ends the alignment continues straight for {@link #END} m (the end tapers of the
 * earthworks), with no pavement.
 */
public final class RoadDesign {
    /** Straight extension beyond each end that the end tapers may use, m. */
    public static final double END = 24;

    /** A bridge span: stations s0 .. s1 lie on a deck (natural ground below), deck soffit depth in m. */
    public record Span(double s0, double s1, double depth) {
        public boolean contains(double s) { return s >= s0 && s <= s1; }
    }

    public final String id;
    public final RoadCurve curve;
    public final RoadProfile profile;
    public final double length;
    public final List<Span> spans;
    private final double[] laneLeft, laneRight;

    public RoadDesign(String id, RoadCurve curve, RoadProfile profile, List<Span> spans) {
        if (profile.length > curve.length + RoadProfile.DS + 1e-6) throw new IllegalArgumentException("PROFILE_LONGER_THAN_CURVE " + id);
        this.id = id;
        this.curve = curve;
        this.profile = profile;
        this.length = Math.min(curve.length, profile.length);
        this.spans = List.copyOf(spans);
        // lane slopes on the profile's 2 m lattice: the curvature targets with the superelevation runoff
        int m = profile.size();
        double[] tl = new double[m], tr = new double[m];
        for (int i = 0; i < m; i++) {
            var c = R4CrossSection.cross(curvature(i * RoadProfile.DS));
            tl[i] = c.laneLeft();
            tr[i] = c.laneRight();
        }
        laneLeft = R4CrossSection.runoff(tl, RoadProfile.DS);
        laneRight = R4CrossSection.runoff(tr, RoadProfile.DS);
    }

    public double curvature(double s) { return s < 0 || s > length ? 0 : curve.curvature(s); }

    /** The cross-section at station s: lane slopes linear between the 2 m samples (like the datum), shoulders derived. */
    public R4CrossSection.Cross cross(double s) {
        double x = Math.max(0, Math.min(length, s)) / RoadProfile.DS;
        int i = Math.min(laneLeft.length - 2, (int) Math.floor(x));
        double t = x - i;
        return R4CrossSection.lanes(laneLeft[i] + (laneLeft[i + 1] - laneLeft[i]) * t, laneRight[i] + (laneRight[i + 1] - laneRight[i]) * t,
                R4CrossSection.superelevation(curvature(s)));
    }

    /** Datum (crown line height) at station s. */
    public double datum(double s) { return profile.at(s); }

    /** World XZ of (station, lateral). Positive lateral is to the right: (-tz, tx). Straight beyond the ends. */
    public double[] point(double s, double u) { return curve.point(s, u); }

    /** Pavement height at (s, u), 0 <= s <= length, paved band. */
    public double pavementY(double s, double u) { return datum(s) + R4CrossSection.pavement(cross(s), u); }

    /** The formation turf (median swale, or the turf just under the paved band) at (s, u), |u| <= EDGE. */
    public double formationY(double s, double u) { return datum(s) + R4CrossSection.formation(cross(s), u); }

    /** Station and lateral offset of a world point within maxDist of the centreline (stations -END .. length + END), or null. */
    public double[] local(double x, double z, double maxDist) { return curve.local(x, z, maxDist, END); }

    /** The bridge span holding station s, or null. */
    public Span span(double s) {
        for (Span sp : spans) if (sp.contains(s)) return sp;
        return null;
    }
}
