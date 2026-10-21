package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;

/**
 * The road signs of the C3.3b junctions and town corners (docs/worldgen/federal_junctions_c3_3b.md 3 and 4) as fixtures
 * (TunnelFixtures; block/TunnelFixtureBlock road_&lt;mesh&gt;; meshes and faces in tools/build-tunnel-v1.mjs). Pure
 * geometry over the built designs and junctions (RoadWorks#junctions), so every chunk pass places the same signs:
 * <ul>
 *   <li>STOP (R1-1) on each minor approach, on the right of the traffic toward the host, OFFSET beyond the minor's
 *   paved edge at the fillet's tangent point T2 (the stop bar lies on the pad ahead of it, within about 15 m of the
 *   host's edge);</li>
 *   <li>side road (W2-2) on the host, SIDE_ROAD before the mouth in both directions, on the right of the traffic, OFFSET
 *   beyond the paved edge; the side road drawn on the side the minor joins as the driver sees it (bone l / r);</li>
 *   <li>curve (W1-2) with its 30 MPH advisory plaque (W13-1P; the corner's 50 km/h design speed, signs give speeds in mph)
 *   at each town corner (RoadDesign#corners), CURVE before the
 *   curve's start (where the curvature first exceeds CURVE_K) in both directions, its arrow the way the road turns for
 *   that traffic (bone l / r);</li>
 *   <li>interchange ramp terminals (C3.3b group 2, docs/worldgen/federal_interchanges_c3_3b.md 10; a ramp is one-way, its
 *   traffic running +s): the exit ramp's STOP as above, DO NOT ENTER (R5-1) at the other T2 on its left (the right of a
 *   driver turning in the wrong way), facing the crossroad, and WRONG WAY (R5-1a) WRONG_WAY further down the ramp on the
 *   same side, facing the same way; an entrance ramp has none of these, and the crossroad no side road warning for
 *   either.</li>
 *   <li>the interchanges' guide signs (C3.3b group 2 package B): RoadGuides.</li>
 * </ul>
 * A sign stands OFFSET beyond the paved edge on the roadside's 1:4 foreslope (CrossSection#roadside: the same in cut and
 * fill), the STOP sign on the pad's own roadside likewise (RoadWorks#padSurface). Its foot is on the turf as the chunk
 * pass draws it (RoadWorks#turfTop): near the daylight line the turf's corners blend toward the natural surface (C3.3a
 * group B), up to half a metre off the design's foreslope (probe jun1, 2026-10-16); the design's foreslope where the
 * column is not turf or no ground is given (the offline checks). Never on a bridge or in a tunnel's reach, on a pad or on
 * another road's pavement: such a warning moves toward its junction or curve in STEP steps down to its shortest distance,
 * else it is left out.
 */
public final class RoadSigns {
    /**
     * Sign faces: R1-1 0.75 m across the flats, the W2-2 / W1-2 diamonds of 0.9 m sides (DIAMOND: corner to corner), the
     * plaque 0.45 m; the lowest face's bottom over the foot (rural, 5 ft), the curve sign's diamond over its plaque, m.
     */
    public static final double STOP = 0.75, DIAMOND = 0.9 * Math.sqrt(2), PLAQUE = 0.45, BOTTOM = 1.5, CURVE_BOTTOM = BOTTOM + PLAQUE + 0.05;
    /** Offset beyond the paved edge; the warnings' distances before the mouth or the curve, their shortest, the steps, m. */
    public static final double OFFSET = 2, SIDE_ROAD = 150, SIDE_ROAD_MIN = 80, CURVE = 120, CURVE_MIN = 60, STEP = 10;
    /** R5-1 DO NOT ENTER across (30 in), R5-1a WRONG WAY wide and high (36 x 24 in), m. */
    public static final double DO_NOT_ENTER = 0.75, WRONG_WAY_W = 0.9, WRONG_WAY_H = 0.6;
    /**
     * Town Framework V1 (TownSigns, 2026-10-19): a town STOP's face bottom over the foot (7 ft, where people walk), the ALL
     * WAY plaque R1-3P (18 x 6 in) and its gap under the STOP, the speed limit R2-1 (24 x 30 in, the roadside's 5 ft), m.
     */
    public static final double TOWN_BOTTOM = 2.1, ALL_WAY_W = 0.45, ALL_WAY_H = 0.15, ALL_WAY_GAP = 0.05, SPEED_W = 0.6, SPEED_H = 0.75;
    /** WRONG WAY: its distance down the exit ramp from the DO NOT ENTER sign, its shortest, m. */
    public static final double WRONG_WAY = 60, WRONG_WAY_MIN = 30;
    /** A town corner's curve starts where its curvature first exceeds this, 1/m. */
    public static final double CURVE_K = 1 / 1500.0;
    /** How far from a town corner its curve's ends are searched; a bridge's or a tunnel's reach kept clear, m. */
    static final double CURVE_SPAN = 400, STRUCTURE_CLEAR = 20;

    private RoadSigns() {}

    /** Every junction, town corner and interchange sign of the network, standing on the turf ground draws (null: on the design). */
    public static List<TunnelMesh.Placement> all(RoadWorks works, RoadWorks.Ground ground) {
        List<TunnelMesh.Placement> out = new ArrayList<>();
        for (Junction j : works.junctions()) {
            if (j.minor.section.oneWay) {
                // an interchange ramp's terminal: an exit ramp's only
                if (!j.atEnd) continue;
                out.add(stop(works, ground, j));
                out.add(doNotEnter(works, ground, j));
                TunnelMesh.Placement w = wrongWay(works, ground, j);
                if (w != null) out.add(w);
                continue;
            }
            TunnelMesh.Placement stop = stop(works, ground, j);
            if (stop != null) out.add(stop);
            for (int k : new int[]{-1, 1}) {
                TunnelMesh.Placement w = sideRoad(works, ground, j, k);
                if (w != null) out.add(w);
            }
        }
        for (RoadDesign d : works.roads())
            for (double c : d.corners)
                for (int k : new int[]{-1, 1}) {
                    TunnelMesh.Placement w = curve(works, ground, d, c, k);
                    if (w != null) out.add(w);
                }
        // the interchanges' guide signs (C3.3b group 2 package B)
        out.addAll(RoadGuides.all(works, ground));
        // the framework towns' limit signs and street name blades (Town Framework V1)
        out.addAll(TownSigns.all(works, ground));
        return out;
    }

    /** The STOP sign of a junction's minor approach (at T2 on the approach side), facing the traffic toward the host. */
    static TunnelMesh.Placement stop(RoadWorks works, RoadWorks.Ground ground, Junction j) {
        RoadDesign m = j.minor;
        // the approach lane lies on the right of the traffic toward the host: u has the sign of dir (k 1: u > 0)
        double s = j.t2(j.dir > 0 ? 1 : 0), em = m.section.edge;
        double[] p = m.point(s, j.dir * (em + OFFSET)), e = m.point(s, j.dir * em), t = BridgeMesh.tangent(m, s);
        double y = foot(works, ground, p[0], p[1], j.height(e[0], e[1]) - CrossSection.TURF_DROP + m.section.roadside(OFFSET, -1e9));
        return part("stop_sign", null, p[0], y, p[1], -j.dir * t[0], -j.dir * t[1]);
    }

    /**
     * DO NOT ENTER at an exit ramp's terminal (atEnd, dir +1): at T2 on the ramp's left (u < 0), OFFSET beyond its paved
     * edge, facing the crossroad (+s).
     */
    static TunnelMesh.Placement doNotEnter(RoadWorks works, RoadWorks.Ground ground, Junction j) {
        RoadDesign m = j.minor;
        double s = j.t2(0), em = m.section.edge;
        double[] p = m.point(s, -(em + OFFSET)), e = m.point(s, -em), t = BridgeMesh.tangent(m, s);
        double y = foot(works, ground, p[0], p[1], j.height(e[0], e[1]) - CrossSection.TURF_DROP + m.section.roadside(OFFSET, -1e9));
        return part("do_not_enter_sign", null, p[0], y, p[1], t[0], t[1]);
    }

    /** WRONG WAY on an exit ramp: WRONG_WAY down the ramp from T2 on its left, facing the crossroad; null where no place is clear. */
    static TunnelMesh.Placement wrongWay(RoadWorks works, RoadWorks.Ground ground, Junction j) {
        for (double dist = WRONG_WAY; dist >= WRONG_WAY_MIN - 1e-9; dist -= STEP) {
            // the right of a wrong-way driver (travelling -s)
            TunnelMesh.Placement p = roadside(works, ground, j.minor, j.t2(0) - dist, -1, "wrong_way_sign", null);
            if (p != null) return p;
        }
        return null;
    }

    /**
     * The side road warning for the host's traffic travelling k (+1: +s, on u > 0), SIDE_ROAD before the mouth; null
     * where no place is clear.
     */
    static TunnelMesh.Placement sideRoad(RoadWorks works, RoadWorks.Ground ground, Junction j, int k) {
        RoadDesign h = j.host;
        String bone = j.side == k ? "r" : "l";
        for (double dist = SIDE_ROAD; dist >= SIDE_ROAD_MIN - 1e-9; dist -= STEP) {
            double s = j.sMouth - k * dist;
            TunnelMesh.Placement p = roadside(works, ground, h, s, k, "side_road_sign", bone);
            if (p != null) return p;
        }
        return null;
    }

    /**
     * The curve warning for traffic travelling k toward a town corner at station c: CURVE before the curve's start, its
     * arrow the way the road turns; null where the curve's start is not found or no place is clear.
     */
    static TunnelMesh.Placement curve(RoadWorks works, RoadWorks.Ground ground, RoadDesign d, double c, int k) {
        // the curve's start for this traffic: from the corner back toward it until the curvature falls under CURVE_K
        double start = Double.NaN;
        for (double q = 0; q <= CURVE_SPAN; q += 1) {
            double s = c - k * q;
            if (s < 0 || s > d.length) break;
            if (Math.abs(d.curvature(s)) < CURVE_K) { start = s; break; }
        }
        if (Double.isNaN(start)) return null;
        // turning right for this traffic: the corner's heading leans toward its right at the start (both directions)
        double[] tc = BridgeMesh.tangent(d, c), ts = BridgeMesh.tangent(d, start);
        boolean right = tc[0] * -ts[1] + tc[1] * ts[0] > 0;
        for (double dist = CURVE; dist >= CURVE_MIN - 1e-9; dist -= STEP) {
            TunnelMesh.Placement p = roadside(works, ground, d, start - k * dist, k, "curve_sign", right ? "r" : "l");
            if (p != null) return p;
        }
        return null;
    }

    /**
     * A warning on the right of road d's traffic travelling k at station s, OFFSET beyond the paved edge on the
     * foreslope, facing that traffic; null off the road's stations or where the place is not clear.
     */
    static TunnelMesh.Placement roadside(RoadWorks works, RoadWorks.Ground ground, RoadDesign d, double s, int k, String mesh, String bone) {
        if (s < 0 || s > d.length) return null;
        if (d.span(s) != null || d.tunnel(s, STRUCTURE_CLEAR) != null) return null;
        for (double q : new double[]{-STRUCTURE_CLEAR, STRUCTURE_CLEAR}) if (d.span(Math.max(0, Math.min(d.length, s + q))) != null) return null;
        double e = d.section.edge, u = k * (e + OFFSET);
        double[] p = d.point(s, u);
        if (!clear(works, d, p[0], p[1])) return null;
        double y = foot(works, ground, p[0], p[1], d.formationY(s, k * e) + d.section.roadside(OFFSET, -1e9));
        double[] t = BridgeMesh.tangent(d, s);
        return part(mesh, bone, p[0], y, p[1], -k * t[0], -k * t[1]);
    }

    /** Whether a sign may stand at (x, z): off every pad and off every other road's pavement and shoulder (1.5 m). */
    private static boolean clear(RoadWorks works, RoadDesign d, double x, double z) {
        if (works.pad(x, z) != null) return false;
        RoadDesign[] near = works.roadsNear(x, z);
        if (near != null)
            for (RoadDesign q : near) {
                if (q == d) continue;
                double[] l = q.local(x, z, q.section.edge + 3);
                if (l != null && l[0] >= -1.5 && l[0] <= q.length + 1.5 && Math.abs(l[1]) <= q.section.edge + 1.5) return false;
            }
        return true;
    }

    /** A sign's foot at (x, z): on the turf as drawn, else (not turf, or no ground) the design's height. */
    private static double foot(RoadWorks works, RoadWorks.Ground ground, double x, double z, double design) {
        if (ground == null) return design;
        double t = works.turfTop(x, z, ground);
        return Double.isNaN(t) ? design : t;
    }

    /** A part standing upright at (x, y, z), its front facing the horizontal (fx, fz); ex = ey x ez. */
    static TunnelMesh.Placement part(String mesh, String bone, double x, double y, double z, double fx, double fz) {
        double l = Math.hypot(fx, fz);
        double[] ez = {fx / l, 0, fz / l}, ey = {0, 1, 0}, ex = {ez[2], 0, -ez[0]};
        return new TunnelMesh.Placement(mesh, bone, x, y, z, ex, ey, ez);
    }
}
