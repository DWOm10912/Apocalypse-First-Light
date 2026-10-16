package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * A road tunnel's bore section (Tunnel V1; docs/worldgen/federal_tunnels_v1.md, 3): the three-centred horseshoe of the
 * concept sheets (design/worldgen/federal_tunnels/source/geom.mjs, the source of these numbers), in bore coordinates: x
 * across the bore from its inner wall face (the egress walkway's wall), y up from the bore's base (the higher pavement
 * height at the two barrier toes). Pure Java.
 * <ul>
 *   <li>R4 (interstate): one bore per carriageway (twin bores), the inner wall at |u| = 8.8 so the lanes keep their open
 *   road positions (lanes |u| 11.0 .. 18.5): walkways 1.20 (inner, egress) and 0.75, shoulders 1.00 and 3.00;</li>
 *   <li>R2 (state): one bore across the centreline, walls at u = +-5.75: walkways 0.90, shoulders 1.20;</li>
 *   <li>both: walkways 0.80 high (the F-shape barrier's top), clearance 5.10, side arcs rs, crown arc rc from the 35 degree
 *   tangent points, the springline where the clearance envelope's corners stay 0.2 m inside the side arcs (at least 2.2 m
 *   over the walkway); lining 0.45 cast in place + 0.30 shotcrete (LINING).</li>
 * </ul>
 */
public final class TunnelSection {
    /** Barrier toe out from the walkway edge; lining thickness (final + initial); walkway height; clearance, m. */
    public static final double TOE = 0.25, LINING = 0.75, WALK_H = 0.80, CLEARANCE = 5.10;
    /** Wall panels run from the walkway up to PANEL_TOP over the bore's base (three 1.2 m rows), the dark arch above, m. */
    public static final double PANEL_TOP = WALK_H + 3 * 1.2;
    /** Arch facets, degrees. */
    public static final double FACET = 5;

    public static final TunnelSection R4 = new TunnelSection(true, 1.20, 0.75, 1.00, 3.00, 2 * 3.75, 5.0, 8.8);
    public static final TunnelSection R2 = new TunnelSection(false, 0.90, 0.90, 1.20, 1.20, 2 * 3.65, 4.5, -5.75);

    /** Twin bores (one per carriageway) or one bore across the centreline. */
    public final boolean twin;
    public final double walkL, walkR, shoulderL, shoulderR, rs, phi;
    /** Wall to wall, the arch's centre x, the crown arc's centres' offsets, the springline and the crown (from the base). */
    public final double width, xa, dx, k, rc, hc, crown;
    /** The inner wall's lateral position: R4 |u| of x = 0; R2 the u of x = 0 (x = u - inner). */
    public final double inner;
    /** Barrier toes (x). */
    public final double toeL, toeR;

    private TunnelSection(boolean twin, double walkL, double walkR, double shL, double shR, double lanes, double rs, double inner) {
        this.twin = twin;
        this.walkL = walkL;
        this.walkR = walkR;
        this.shoulderL = shL;
        this.shoulderR = shR;
        this.rs = rs;
        this.phi = Math.toRadians(35);
        this.inner = inner;
        width = walkL + shL + lanes + shR + walkR;
        xa = width / 2;
        dx = width / 2 - rs;
        k = dx / Math.tan(phi);
        rc = rs + Math.hypot(dx, k);
        double h = 0;
        for (double x : new double[]{walkL, width - walkR}) {
            double e = Math.abs(x - xa) - dx, r = rs - 0.2;
            h = Math.max(h, CLEARANCE - Math.sqrt(Math.max(0, r * r - e * e)));
        }
        hc = Math.max(h, WALK_H + 2.2);
        crown = hc - k + rc;
        toeL = walkL + TOE;
        toeR = width - walkR - TOE;
    }

    public static TunnelSection of(CrossSection sec) { return sec.divided ? R4 : R2; }

    /** The bores: R4 -1 (left carriageway) and +1; R2 one bore, 0. */
    public int[] sides() { return twin ? new int[]{-1, 1} : new int[]{0}; }

    /** The bore holding lateral u (R4 by its sign; R2 0). */
    public int side(double u) { return twin ? (u < 0 ? -1 : 1) : 0; }

    /** Bore x of lateral u in bore side. */
    public double x(int side, double u) { return twin ? Math.abs(u) - inner : u - inner; }

    /** Lateral u of bore x in bore side. */
    public double u(int side, double x) { return twin ? side * (inner + x) : inner + x; }

    /** The crown top of the lining's outside over the base, m. */
    public double crownTop() { return crown + LINING; }

    /**
     * The arch surface offset off from the inner surface (0: inner, LINING: the lining's outside) at bore x, over the
     * base: the side arcs down to the springline, the vertical walls under it; NaN outside the walls (|x - xa| beyond
     * the side arcs' reach).
     */
    public double arch(double x, double off) {
        double e = x - xa, r = rc + off, s = rs + off;
        if (Math.abs(e) <= r * Math.sin(phi)) return hc - k + Math.sqrt(r * r - e * e);
        double ex = Math.abs(e) - dx;
        if (ex > s + 1e-9) return Double.NaN;
        return hc + Math.sqrt(Math.max(0, s * s - ex * ex));
    }

    /** Whether bore x lies within the lining's outside (from -LINING to width + LINING). */
    public boolean withinLining(double x) { return x >= -LINING && x <= width + LINING; }

    /**
     * The arch polyline (bore x, y over the base) at offset off from the inner surface, from the left springline over the
     * crown to the right springline, every FACET degrees (the side arcs and the crown arc meet at the tangent points).
     */
    public double[][] archPoints(double off) {
        double s = rs + off, r = rc + off, a1 = 90 + Math.toDegrees(phi), a2 = 90 - Math.toDegrees(phi);
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        int n1 = (int) Math.ceil((180 - a1) / FACET - 1e-9);
        for (int i = 0; i < n1; i++) {
            double a = Math.toRadians(180 - (180 - a1) * i / n1);
            pts.add(new double[]{xa - dx + s * Math.cos(a), hc + s * Math.sin(a)});
        }
        int n2 = (int) Math.ceil((a1 - a2) / FACET - 1e-9);
        for (int i = 0; i < n2; i++) {
            double a = Math.toRadians(a1 - (a1 - a2) * i / n2);
            pts.add(new double[]{xa + r * Math.cos(a), hc - k + r * Math.sin(a)});
        }
        for (int i = 0; i <= n1; i++) {
            double a = Math.toRadians(a2 - a2 * i / n1);
            pts.add(new double[]{xa + dx + s * Math.cos(a), hc + s * Math.sin(a)});
        }
        return pts.toArray(new double[0][]);
    }

    /**
     * The barrier's face over the pavement at bore x (0 at the toe, WALK_H at the walkway edge; the F-shape's two slopes:
     * toe to 0.25 m over 0.17 m, then to the top over 0.08 m), NaN off the barrier; left (x near walkL) or right.
     */
    public double barrier(double x, boolean left) {
        double t = left ? toeL - x : x - toeR;   // 0 at the toe .. TOE at the walkway edge
        if (t < -1e-9 || t > TOE + 1e-9) return Double.NaN;
        return t <= TOE - 0.08 ? 0.25 * t / (TOE - 0.08) : 0.25 + (WALK_H - 0.25) * (t - (TOE - 0.08)) / 0.08;
    }
}
