package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The roadside start's anchor (C3.4, docs/worldgen/main_c3_interface_contract_v1.md 9.1; the survivor scene
 * {@code afl:roadside}, docs/gameplay/survivor_background_skills_v1_plan.md 8; C's answer of 2026-10-15): where the first
 * survivors of a roadside world stand, a pure planning query on the built network (RoadWorks), so it answers before any
 * chunk loads, with the very heights the chunks are later built to.
 *
 * <p>Only a state highway's (R2, not a town's crossroad) outer shoulder, its middle: never an interstate's pavement or a
 * lane. The run of points and RUN_CLEAR beyond both ends keep clear of bridges and culverts, tunnels and their approaches,
 * superelevated curves and their runoff (the lanes at the normal crown), no-passing zones (where the sight is short), the
 * road's ends, junctions and their pads (interchange ramps included), towns, and any overpass above. Of the stations that
 * pass, every SCAN m, the one nearest the reference point wins, on the shoulder on the reference's side (ties: the
 * network's road order, then the lower station), so a world always gets the same spot.
 *
 * <p>N points on one shoulder, STEP apart in the direction of that side's traffic, all on the paved shoulder; their y is
 * the pavement's (the block above it is free: the shoulder is paved at that height and nothing is built over it), their
 * yaw (Minecraft degrees) faces along the traffic. Null when the world has no roads (the gates off, the network empty) or
 * no shoulder passes: the caller falls back to {@code wilderness_ring}.
 */
public final class RoadsideStart {
    /** Between the survivors' points along the shoulder, m. */
    public static final double STEP = 3;
    /** Candidate stations along every state highway, m. */
    public static final double SCAN = 10;
    /** Clear road kept before the first point and after the last, and the sampling of the run, m. */
    public static final double RUN_CLEAR = 20, SAMPLE = 2;
    /** Clearances from the road's ends, junction mouths, bridge and culvert, tunnel portals and town limits, m. */
    public static final double END_CLEAR = 150, JUNCTION_CLEAR = 150, SPAN_CLEAR = 30, CULVERT_CLEAR = 30, TUNNEL_CLEAR = 60, TOWN_CLEAR = 150;
    /** How far a lane's slope may stray from the normal crown (the superelevation runoff starts beyond it). */
    public static final double CROWN_TOLERANCE = 0.001;

    public record Point(double x, double y, double z, float yaw) {}

    /**
     * The anchor: the road and the station of the first point, its side (+1: the right of increasing stations), the lateral
     * offset of the points (the outer shoulder's middle), the points.
     */
    public record Anchor(String road, double s, int side, double u, List<Point> points) {}

    private record Candidate(RoadDesign road, double s, int side, double d2) {}

    private RoadsideStart() {
    }

    /** The anchor for a server level's network, or null (no roads in this world). */
    @Nullable
    public static Anchor find(ServerLevel level, double x, double z, int count) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        return n == null ? null : find(n.works(), x, z, count);
    }

    /** The anchor of count (at least 1) points nearest (x, z), or null. */
    @Nullable
    public static Anchor find(RoadWorks works, double x, double z, int count) {
        if (works == null || works.isEmpty()) return null;
        int n = Math.max(1, count);
        double run = (n - 1) * STEP;
        List<Candidate> all = new ArrayList<>();
        for (RoadDesign d : works.roads()) {
            if (!state(d)) continue;
            double u = shoulder(d);
            for (double s = END_CLEAR + RUN_CLEAR; s + run <= d.length - END_CLEAR - RUN_CLEAR; s += SCAN) {
                // the shoulder on the reference's side
                double[] l = d.point(s + run / 2, u), r = d.point(s + run / 2, -u);
                double dl = sq(l[0] - x, l[1] - z), dr = sq(r[0] - x, r[1] - z);
                all.add(dl <= dr ? new Candidate(d, s, 1, dl) : new Candidate(d, s, -1, dr));
            }
        }
        all.sort(Comparator.comparingDouble(Candidate::d2));
        for (Candidate c : all) {
            // the first point is the one the traffic on that side reaches first (on the left it runs to lower stations)
            double s0 = c.side > 0 ? c.s : c.s + run;
            if (clear(works, c.road, Math.min(c.s, c.s + run), Math.max(c.s, c.s + run), c.side)) return anchor(c.road, s0, c.side, n);
        }
        return null;
    }

    /** A state highway's own design (R2): not a town's crossroad (town_), nor a ramp or county road (their sections). */
    static boolean state(RoadDesign d) {
        return d.section == CrossSection.R2 && !d.id.startsWith("town_");
    }

    /** The outer shoulder's middle, |u|. */
    static double shoulder(RoadDesign d) {
        return d.section.lane + d.section.outer / 2;
    }

    /** Whether the run from a to b (stations) on that side passes every clearance. */
    static boolean clear(RoadWorks works, RoadDesign d, double a, double b, int side) {
        double u = side * shoulder(d);
        if (a - RUN_CLEAR < END_CLEAR || b + RUN_CLEAR > d.length - END_CLEAR) return false;
        for (Junction j : works.junctionsOf(d)) {
            double at = j.host == d ? j.sMouth : j.sEnd;
            if (at > a - JUNCTION_CLEAR && at < b + JUNCTION_CLEAR) return false;
        }
        for (RoadDesign.Span sp : d.spans) if (sp.s1() > a - SPAN_CLEAR && sp.s0() < b + SPAN_CLEAR) return false;
        for (double s = a - RUN_CLEAR; s <= b + RUN_CLEAR + 1e-9; s += SAMPLE) {
            if (d.noPassing(s) || d.tunnel(s, TUNNEL_CLEAR) != null) return false;
            CrossSection.Cross c = d.cross(s);
            if (Math.abs(c.laneLeft() - CrossSection.LANE_SLOPE) > CROWN_TOLERANCE || Math.abs(c.laneRight() - CrossSection.LANE_SLOPE) > CROWN_TOLERANCE)
                return false;
            double[] p = d.point(s, u);
            for (Culvert cv : d.culverts) if (sq(cv.ox - p[0], cv.oz - p[1]) < CULVERT_CLEAR * CULVERT_CLEAR) return false;
            for (TownPlan t : works.towns().plans()) {
                double[] l = t.local(p[0], p[1]);
                if (t.inside(l[0], l[1], TOWN_CLEAR)) return false;
            }
            if (works.pad(p[0], p[1]) != null || works.overHit(p[0], p[1]) != null) return false;
            // the point is this road's (no other road's formation or ramp takes it)
            RoadWorks.Hit h = works.hit(p[0], p[1], d.section.edge + 1);
            if (h == null || h.road() != d || h.beyond() > 0) return false;
        }
        return true;
    }

    private static Anchor anchor(RoadDesign d, double s0, int side, int n) {
        double u = side * shoulder(d);
        List<Point> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double s = s0 + side * i * STEP;
            double[] p = d.point(s, u), a = d.point(s - 1, 0), b = d.point(s + 1, 0);
            // facing the traffic's way on that side: +s on the right, -s on the left
            double tx = side * (b[0] - a[0]), tz = side * (b[1] - a[1]);
            float yaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
            points.add(new Point(p[0], d.pavementY(s, u), p[1], yaw));
        }
        return new Anchor(d.id, s0, side, u, List.copyOf(points));
    }

    private static double sq(double x, double z) { return x * x + z * z; }
}
