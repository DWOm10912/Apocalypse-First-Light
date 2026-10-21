package com.antaurora.apofirstlight.worldgen.network.town;

import java.util.ArrayList;
import java.util.List;

/**
 * A framework town's grid on the block grid (2026-10-19, docs/worldgen/federal_towns_v1.md 7.9): its streets, lots and
 * buildings run along world x and z, whatever the interstate's heading at the exit (block buildings cannot stand at an
 * angle; user, 2026-10-19). Pure geometry, shared by the exit's stubs (TownExits), the reach count and the planner.
 * <ul>
 *   <li>axes: the grid's s axis is the world axis nearest the interstate's tangent at the crossing (#axis), its v axis the
 *   world axis across it toward the side; up to 45 degrees off the interstate;</li>
 *   <li>the exit's crossroad (#path): from the crossing along the interstate's normal for STRAIGHT (the diamond's ramp
 *   terminals and its approach, RoadInterchanges: D + APPROACH, and one control point more), then an arc of RADIUS onto
 *   the grid's v axis, then straight along it; the town's grid takes that straight line for its exit cross street;</li>
 *   <li>Front St: FRONT_CLEAR beyond where the crossroad runs straight on the grid (the corner's box clear of the arc),
 *   never nearer the interstate than TownPlanner.FRONT.</li>
 * </ul>
 */
public final class TownGrid {
    private TownGrid() {}

    /** The crossroad's run along the interstate's normal before it turns onto the grid, its arc's radius, m. */
    public static final double STRAIGHT = 180, RADIUS = 200;
    /** Front St beyond the crossroad's straight start on the grid (the corner box's half length and a margin), m. */
    public static final double FRONT_CLEAR = 25;
    /** The path's integration step, m. */
    private static final double STEP = 0.5;

    /** The world axis nearest the direction (tx, tz), as a unit vector {ax, az}. */
    public static double[] axis(double tx, double tz) {
        return Math.abs(tx) >= Math.abs(tz) ? new double[]{Math.signum(tx), 0} : new double[]{0, Math.signum(tz)};
    }

    /**
     * The crossroad's path out of the crossing (cx, cz) on the interstate's side u (-1 its left, 1 its right; the normal
     * u (-tz, tx) of its tangent (tx, tz)) until it lies extent m across the grid from the crossing (at least to the arc's
     * end).
     *
     * @param pts   the path's points every spacing m from the crossing outward (its last point at the end)
     * @param start the point where the path runs straight on the grid's v axis (the arc's end) {x, z}
     */
    public record Path(List<double[]> pts, double[] start) {}

    public static Path path(double cx, double cz, double tx, double tz, int u, double extent, double spacing) {
        double[] a = axis(tx, tz);
        double nx = -u * tz, nz = u * tx, bx = -u * a[1], bz = u * a[0];
        // the turn from the normal onto the grid's v axis (at most 45 degrees either way)
        double phi = Math.atan2(nx * bz - nz * bx, nx * bx + nz * bz);
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{cx, cz});
        double x = cx, z = cz, dx = nx, dz = nz, run = 0, next = spacing, turned = 0;
        double[] start = null;
        for (int guard = 0; guard < 100_000; guard++) {
            if (start != null && (x - cx) * bx + (z - cz) * bz >= extent) break;
            double step = STEP;
            if (run >= STRAIGHT && start == null) {
                // on the arc: turn toward the grid by step / RADIUS, the last step to the axis exactly
                double left = phi - turned, d = Math.copySign(Math.min(Math.abs(left), step / RADIUS), phi);
                turned += d;
                double c = Math.cos(d), s = Math.sin(d), ndx = dx * c - dz * s, ndz = dx * s + dz * c;
                dx = ndx;
                dz = ndz;
                if (Math.abs(phi - turned) < 1e-12) { dx = bx; dz = bz; }
            }
            x += dx * step;
            z += dz * step;
            run += step;
            if (start == null && run >= STRAIGHT && Math.abs(phi - turned) < 1e-12) start = new double[]{x, z};
            if (run >= next - 1e-9) {
                pts.add(new double[]{x, z});
                next += spacing;
            }
        }
        double[] last = pts.get(pts.size() - 1);
        if (Math.hypot(last[0] - x, last[1] - z) > 1e-6) pts.add(new double[]{x, z});
        if (start == null) start = new double[]{x, z};
        return new Path(pts, start);
    }

    /** The grid's origin for a crossing: the block corner nearest it (lot lines in whole metres from there). */
    public static double[] origin(double cx, double cz) { return new double[]{Math.round(cx), Math.round(cz)}; }

    /**
     * The predicted Front St of side u (as the planner will find it on the built crossroad, RoadInterchanges keeping the
     * stub's path): {s of the exit cross street, |v| of Front St}, in the grid of origin #origin and s axis #axis, v
     * positive toward u.
     */
    public static double[] front(double cx, double cz, double tx, double tz, int u) {
        Path p = path(cx, cz, tx, tz, u, 0, 1e9);
        double[] o = origin(cx, cz), a = axis(tx, tz);
        double bx = -u * a[1], bz = u * a[0];
        double s = (p.start[0] - o[0]) * a[0] + (p.start[1] - o[1]) * a[1], v = (p.start[0] - o[0]) * bx + (p.start[1] - o[1]) * bz;
        return new double[]{s, frontOf(v)};
    }

    /** Front St's |v| for a crossroad straight on the grid from vStraight. */
    public static double frontOf(double vStraight) { return Math.max(TownPlanner.FRONT, Math.ceil(vStraight + FRONT_CLEAR)); }
}
