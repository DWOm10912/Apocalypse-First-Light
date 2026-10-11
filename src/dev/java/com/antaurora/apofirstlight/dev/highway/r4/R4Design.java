package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.dev.highwaymesh.RoadAlignment;

import java.util.List;
import java.util.Optional;

/**
 * One R4 test segment: horizontal alignment (the shared RoadAlignment kernel: tangents with quintic transition curves),
 * vertical profile and the R4 cross-section. Pure Java; immutable; every height the mesh, the collision columns and the
 * turf corners use comes from here. Stations run 0 .. length; beyond the ends the alignment continues straight (the end
 * tapers of the earthworks), with no pavement.
 */
public final class R4Design {
    /** Protection half width (m) used as the alignment's corridor margin. */
    public static final double PROTECTION = 41;
    public final RoadAlignment alignment;
    public final R4Profile profile;
    public final double length;
    private final double[] px, pz;          // centreline every 1 m, from -END to length + END, for the projection
    public static final double END = 24;

    public R4Design(RoadAlignment alignment, R4Profile profile) {
        if (profile.length > alignment.length + 1e-6) throw new IllegalArgumentException("PROFILE_LONGER_THAN_ALIGNMENT");
        this.alignment = alignment;
        this.profile = profile;
        this.length = profile.length;
        int n = (int) Math.ceil(length + 2 * END) + 1;
        px = new double[n];
        pz = new double[n];
        for (int i = 0; i < n; i++) {
            double[] p = centre(i - END);
            px[i] = p[0];
            pz[i] = p[1];
        }
        // lane slopes on the profile's 2 m lattice: the curvature targets with the superelevation runoff
        int m = profile.size();
        double[] tl = new double[m], tr = new double[m];
        for (int i = 0; i < m; i++) {
            var c = R4Section.cross(curvature(i * R4Profile.DS));
            tl[i] = c.laneLeft();
            tr[i] = c.laneRight();
        }
        laneLeft = R4Section.runoff(tl, R4Profile.DS);
        laneRight = R4Section.runoff(tr, R4Profile.DS);
    }

    private final double[] laneLeft, laneRight;

    /** A polyline alignment (2 or 3 control points) inside one straight corridor wide enough for the protection band. */
    public static RoadAlignment alignment(List<RoadAlignment.Point> controls) {
        var a = controls.get(0);
        var b = controls.get(controls.size() - 1);
        double maxOff = 0;
        double dx = b.x() - a.x(), dz = b.z() - a.z(), len = Math.hypot(dx, dz);
        for (var p : controls) maxOff = Math.max(maxOff, Math.abs(((p.x() - a.x()) * dz - (p.z() - a.z()) * dx) / len));
        var corridor = new RoadAlignment.Corridor(a, b, PROTECTION + maxOff + 8);
        return new RoadAlignment(controls, List.of(corridor), PROTECTION, 240, 300);
    }

    /** Centreline point at station s (straight extension beyond the ends). */
    public double[] centre(double s) {
        if (s < 0) {
            var f = alignment.frame(0);
            return new double[]{f.point().x() + f.tangent().x() * s, f.point().z() + f.tangent().z() * s};
        }
        if (s > length) {
            var f = alignment.frame(length);
            return new double[]{f.point().x() + f.tangent().x() * (s - length), f.point().z() + f.tangent().z() * (s - length)};
        }
        var f = alignment.frame(s);
        return new double[]{f.point().x(), f.point().z()};
    }

    /** Unit tangent at station s. */
    public double[] tangent(double s) {
        var f = alignment.frame(Math.max(0, Math.min(length, s)));
        return new double[]{f.tangent().x(), f.tangent().z()};
    }

    public double curvature(double s) {
        return s < 0 || s > length ? 0 : alignment.frame(s).curvature();
    }

    /** The cross-section at station s: lane slopes linear between the 2 m samples (like the datum), shoulders derived. */
    public R4Section.Cross cross(double s) {
        double x = Math.max(0, Math.min(length, s)) / R4Profile.DS;
        int i = Math.min(laneLeft.length - 2, (int) Math.floor(x));
        double t = x - i;
        return R4Section.lanes(laneLeft[i] + (laneLeft[i + 1] - laneLeft[i]) * t, laneRight[i] + (laneRight[i + 1] - laneRight[i]) * t,
                R4Section.superelevation(curvature(s)));
    }

    /** Datum (crown line height) at station s. */
    public double datum(double s) { return profile.at(s); }

    /** World XZ of (station, lateral). Positive lateral is to the right: (-tz, tx). */
    public double[] point(double s, double u) {
        double[] c = centre(s), t = tangent(s);
        return new double[]{c[0] - t[1] * u, c[1] + t[0] * u};
    }

    /** Pavement height at (s, u), 0 <= s <= length, paved band. */
    public double pavementY(double s, double u) { return datum(s) + R4Section.pavement(cross(s), u); }

    /** The formation turf (median swale, or the turf just under the paved band) at (s, u), |u| <= EDGE. */
    public double formationY(double s, double u) { return datum(s) + R4Section.formation(cross(s), u); }

    /** Station and lateral offset of a world point, stations from -END to length + END; empty farther out. */
    public Optional<double[]> local(double x, double z) {
        int best = -1;
        double bd = Double.POSITIVE_INFINITY;
        for (int i = 0; i < px.length; i++) {
            double d = (x - px[i]) * (x - px[i]) + (z - pz[i]) * (z - pz[i]);
            if (d < bd) { bd = d; best = i; }
        }
        double s = best - END;
        for (int it = 0; it < 8; it++) {
            double[] c = centre(s), t = tangent(s);
            double along = (x - c[0]) * t[0] + (z - c[1]) * t[1];
            double next = Math.max(-END, Math.min(length + END, s + along));
            if (Math.abs(next - s) < 1e-10) break;
            s = next;
        }
        double[] c = centre(s), t = tangent(s);
        double along = (x - c[0]) * t[0] + (z - c[1]) * t[1];
        if (Math.abs(along) > 0.01) return Optional.empty();
        double u = -(x - c[0]) * t[1] + (z - c[1]) * t[0];
        return Optional.of(new double[]{s, u});
    }

    /** Surface query for future vehicles and the dev sample command: height, unit normal, unit tangent, zone. */
    public record Surface(double station, double lateral, double y, double nx, double ny, double nz, double tx, double tz,
                          R4Section.Zone zone, boolean paved) {}

    public Optional<Surface> surface(double x, double z) {
        var l = local(x, z);
        if (l.isEmpty()) return Optional.empty();
        double s = l.get()[0], u = l.get()[1];
        if (s < 0 || s > length || Math.abs(u) > R4Section.EDGE) return Optional.empty();
        var zone = R4Section.zone(u);
        boolean paved = zone != R4Section.Zone.MEDIAN;
        double h = 0.01;
        double y = paved ? pavementY(s, u) : formationY(s, u);
        double ya = paved ? pavementY(Math.max(0, s - h), u) : formationY(Math.max(0, s - h), u);
        double yb = paved ? pavementY(Math.min(length, s + h), u) : formationY(Math.min(length, s + h), u);
        double ul = Math.max(paved && u > 0 ? R4Section.HALF_MEDIAN : -R4Section.EDGE, u - h), ur = Math.min(paved && u < 0 ? -R4Section.HALF_MEDIAN : R4Section.EDGE, u + h);
        double yl = paved ? pavementY(s, ul) : formationY(s, ul), yr = paved ? pavementY(s, ur) : formationY(s, ur);
        double[] t = tangent(s);
        double ds = Math.min(length, s + h) - Math.max(0, s - h);
        // along-track and across-track slopes -> normal
        double gs = (yb - ya) / ds, gu = (yr - yl) / (ur - ul);
        // world directions: along t = (tx, tz), across n = (-tz, tx)
        double ax = t[0], az = t[1], cx = -t[1], cz = t[0];
        double[] along = {ax, gs, az}, across = {cx, gu, cz};
        double nx = along[1] * across[2] - along[2] * across[1], ny = along[2] * across[0] - along[0] * across[2], nz = along[0] * across[1] - along[1] * across[0];
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (ny < 0) nl = -nl;
        return Optional.of(new Surface(s, u, y, nx / nl, ny / nl, nz / nl, t[0], t[1], zone, paved));
    }
}
