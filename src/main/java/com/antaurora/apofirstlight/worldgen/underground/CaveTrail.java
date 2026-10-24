package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Destination;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Open;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Shape;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Talus;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Trail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * U5b (docs/worldgen/underground_geology_caves_v0_design.md 24.5): the footpath to the start destination's mouth. Pure
 * planning over the terrain plan and the road designs, run at the plan's end (UndergroundPlanner#destinations):
 * <ol>
 *   <li>the trailhead is on the nearest two-lane or county road (R2, C2: no trailhead on a divided highway or a ramp),
 *   never on a bridge or in a tunnel;</li>
 *   <li>the way is the cheapest walk on a GRID m grid (16 directions) from the mouth's rim to that road's shoulder: a
 *   metre costs more the steeper it climbs (1 + (grade / 0.15)^2, so a hillside is taken in switchbacks; steeper than
 *   STEEP only on the mouth's own cut), a stream costs FORD m to wade, and it never crosses another cave's cut or talus,
 *   a highway or a ramp, a town or the home's ground;</li>
 *   <li>the grid's cells are thinned (within SIMPLIFY), rounded (Chaikin twice) and set a metre apart, the trailhead
 *   point on the shoulder in front;</li>
 *   <li>the trailhead sign stands SIGN_OUT beyond the paved edge a few metres along from the trail, facing the road;
 *   marker posts stand MARKER_SIDE to the right of the tread every MARKER_EVERY m and at each bend, none on a cut or in
 *   a stream; the caution sign stands CAUTION_SIDE to the left of the tread CAUTION_BACK m or more before the rim,
 *   facing the walker coming in, clear of the entrance's boulders. Every fixture lies wholly in one chunk, so the chunk
 *   pass stands it on its own ground.</li>
 * </ol>
 */
public final class CaveTrail {
    private CaveTrail() {}

    /** Search grid; the window round the rim and the nearest road (grown by MARGIN + 0.4 of the distance); its cells' cap. */
    static final double GRID = 2, MARGIN = 120;
    /** The wider windows' half sizes round the rim, m; the most cells a window may have. */
    static final double[] WIDER = {1000, 1500};
    static final int MAX_CELLS = 2_400_000;
    /** Roads further than this from the rim are not searched, m. */
    static final double REACH = 1800;
    /** Climb cost scale; steepest grade off the mouth's cut; the mouth's own reach; a ford's cost, m. */
    static final double GRADE = 0.15, STEEP = 1.2, MOUTH = 30, FORD = 30;
    /** Kept clear: a town, the home, a highway's or ramp's corridor beyond its paved edge, m. */
    public static final double TOWN_CLEAR = 15, HOME_CLEAR = 80, HIGHWAY_CLEAR = 6;
    /** The goal: within this of a two-lane or county road's paved edge; the trailhead point beyond it, m. */
    static final double GOAL_OUT = 2, HEAD_OUT = 0.8;
    static final double SIMPLIFY = 1.5;
    /** The tread's worn half width (all bare earth), the verge it frays into, the trunks kept off its line, m. */
    public static final double TREAD = 0.75, VERGE = 1.3, TREE_CLEAR = 2.5;
    /** How far from a segment the chunk index reaches (the verge and the trees kept off). */
    public static final double INDEX_REACH = 3;
    /** Markers: the first, the spacing, the least spacing at a bend, the bend (radians over BEND_SPAN either way), side offset. */
    public static final double MARKER_FIRST = 25, MARKER_EVERY = 45, MARKER_BEND_GAP = 20, BEND = Math.toRadians(35), BEND_SPAN = 8,
            MARKER_SIDE = 1.0, MARKER_END = 10;
    /** The trailhead sign beyond the paved edge, its tries along the road, its least distance off the trail's line, m. */
    static final double SIGN_OUT = 3.0, SIGN_CLEAR = 1.4;
    static final double[] SIGN_ALONG = {3, -3, 4.5, -4.5, 6, -6, 8, -8};
    public static final String SIGN = "trail_sign", MARKER = "trail_marker", CAUTION = "cave_sign";
    /** The caution sign: its least and most distance before the rim along the trail, its offset left of the line, its clearance from a boulder's edge, m. */
    static final int CAUTION_BACK = 6, CAUTION_MAX = 20;
    static final double CAUTION_SIDE = 1.8, CAUTION_ROCK = 1.5;
    /**
     * The sign (tools/build-tunnel-v1.mjs trail_sign): its board wide and high (the name over the distance strip), its
     * bottom over the foot, its two 4 x 4 posts either side of the middle; the marker post's height above the ground, m.
     */
    public static final double SIGN_W = 0.9, SIGN_H = 0.6, SIGN_BOTTOM = 1.0, SIGN_POST_X = 0.3, MARKER_H = 1.2;
    public static final double MILE = 1609.344;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
    private static final byte OPEN = 0, BLOCK = 1, WATER = 2, GOAL = 3, HIGHWAY = 4, TOWN = 5;

    /** Plans every start destination's trail into the plan and indexes them. */
    static void lay(UndergroundPlanner pl) {
        for (Destination d : pl.plan.destinations) {
            if (d.kind() != UndergroundPlan.DestinationKind.START_CAVE) continue;
            Trail t = plan(pl, d);
            if (t != null) {
                pl.plan.trails.add(t);
                pl.plan.note("trail");
            }
        }
        pl.plan.indexTrails(INDEX_REACH);
    }

    /** Whether a road takes a trailhead: undivided two-lane and county roads. */
    public static boolean trailhead(RoadDesign r) {
        String n = r.section.name;
        return "R2".equals(n) || "C2".equals(n);
    }

    /** A bridge or tunnel at s (or within 15 m of one). */
    static boolean structure(RoadDesign r, double s) {
        for (RoadDesign.Tunnel t : r.tunnels) if (s > t.s0() - 15 && s < t.s1() + 15) return true;
        for (RoadDesign.Span sp : r.spans) if (s > sp.s0() - 15 && s < sp.s1() + 15) return true;
        return false;
    }

    static boolean tunnel(RoadDesign r, double s) {
        for (RoadDesign.Tunnel t : r.tunnels) if (t.contains(s)) return true;
        return false;
    }

    static boolean span(RoadDesign r, double s) {
        for (RoadDesign.Span sp : r.spans) if (sp.contains(s)) return true;
        return false;
    }

    /** The search: its window, cells' ground and kind, the shapes near the rim. */
    private static final class Grid {
        final double x0, z0;
        final int nx, nz;
        final float[] h;
        /** Whether a cell's ground and water are worked out yet (#height: only the cells the walk reaches). */
        final boolean[] known;
        final byte[] kind;
        final List<Shape> mouth = new ArrayList<>(), others = new ArrayList<>();
        final TerrainPlanSurfaceView s;

        Grid(double x0, double z0, int nx, int nz, TerrainPlanSurfaceView s) {
            this.x0 = x0;
            this.z0 = z0;
            this.nx = nx;
            this.nz = nz;
            this.s = s;
            h = new float[nx * nz];
            known = new boolean[nx * nz];
            kind = new byte[nx * nz];
        }

        double cx(int i) { return x0 + (i + 0.5) * GRID; }

        double cz(int j) { return z0 + (j + 0.5) * GRID; }

        int col(double x) { return (int) Math.floor((x - x0) / GRID); }

        int row(double z) { return (int) Math.floor((z - z0) / GRID); }

        boolean in(int i, int j) { return i >= 0 && j >= 0 && i < nx && j < nz; }

        /** Cell k's ground; on first use also whether it is a stream (an open cell becomes WATER). */
        float height(int k) {
            if (!known[k]) {
                double x = cx(k % nx), z = cz(k / nx);
                h[k] = (float) ground(x, z);
                if (kind[k] == OPEN && s.water(x, z)) kind[k] = WATER;
                known[k] = true;
            }
            return h[k];
        }

        /** Whether shape sh takes (x, z) from the trail (another cave's cut or talus). */
        static boolean cuts(Shape sh, double x, double z, double plan) {
            return (sh instanceof Open o && o.floorAt(x, z) < plan - 0.5) || (sh instanceof Talus t && t.surfaceAt(x, z) > plan + 0.3);
        }

        /** The planned ground at (x, z) after the mouth's own cuts and talus. */
        double ground(double x, double z) {
            double g = s.heightAt(x, z);
            for (Shape sh : mouth) {
                if (sh instanceof Open o) g = Math.min(g, o.floorAt(x, z));
                else if (sh instanceof Talus t) {
                    double[] b = t.bounds();
                    if (x >= b[0] && x <= b[3] && z >= b[2] && z <= b[5]) g = Math.max(g, t.surfaceAt(x, z));
                }
            }
            return g;
        }

        /** Whether another cave's cut or talus takes (x, z). */
        boolean cut(double x, double z, double plan) {
            for (Shape sh : others) {
                double[] b = sh.bounds();
                if (x < b[0] || x > b[3] || z < b[2] || z > b[5]) continue;
                if (cuts(sh, x, z, plan)) return true;
            }
            return false;
        }
    }

    /** The terrain plan's heights and water (TerrainPlanSurface), as the trail reads them. */
    interface TerrainPlanSurfaceView {
        double heightAt(double x, double z);

        boolean water(double x, double z);
    }

    static TerrainPlanSurfaceView view(UndergroundPlanner pl) {
        var s = pl.s;
        double[] w = new double[1];
        return new TerrainPlanSurfaceView() {
            public double heightAt(double x, double z) { return s.heightAt(x, z); }

            public boolean water(double x, double z) {
                if (s.waterAt(x, z)) return true;
                return s.rivers != null && s.rivers.nearestEdge(x, z, 3, w) < 1.5;
            }
        };
    }

    /** A road point the trail may start from: design, s, its centre, the road's paved half width. */
    private record Head(int road, double s, double x, double z, double edge) {}

    /** The trail of a destination, null (and a note) when no two-lane or county road is in reach or no way is found. */
    static Trail plan(UndergroundPlanner pl, Destination d) {
        double[] rim = d.route().get(0);
        double rx = rim[0], rz = rim[2];
        List<RoadDesign> roads = pl.p.roads;
        List<Head> heads = new ArrayList<>();
        Head nearest = null;
        double nd = Double.MAX_VALUE;
        for (int ri = 0; ri < roads.size(); ri++) {
            RoadDesign r = roads.get(ri);
            if (!trailhead(r)) continue;
            for (double t = 0; t <= r.length; t += 4) {
                double[] c = r.point(t, 0);
                double dd = Math.hypot(c[0] - rx, c[1] - rz);
                // a trailhead outside the towns and the home's ground (the trail could not leave it)
                if (dd > REACH || structure(r, t) || pl.p.townDistance(c[0], c[1]) < TOWN_CLEAR + r.section.edge + GOAL_OUT + 2
                        || pl.p.homeDistance(c[0], c[1]) < HOME_CLEAR + r.section.edge + GOAL_OUT + 2) continue;
                Head h = new Head(ri, t, c[0], c[1], r.section.edge);
                heads.add(h);
                if (dd < nd) { nd = dd; nearest = h; }
            }
        }
        // the goals: the trailheads and the towns' edges, whichever is nearer to walk. The window round the rim and the
        // nearest road first; when a highway or the towns shut the way, wider squares
        Search found = null;
        String why = null;
        List<double[]> windows = new ArrayList<>();
        if (nearest != null) {
            double grow = MARGIN + 0.4 * nd;
            windows.add(new double[]{Math.min(rx, nearest.x) - grow, Math.min(rz, nearest.z) - grow, Math.max(rx, nearest.x) + grow, Math.max(rz, nearest.z) + grow});
        }
        for (double h : WIDER) windows.add(new double[]{rx - h, rz - h, rx + h, rz + h});
        for (double[] w : windows) {
            Search sr = search(pl, heads, rx, rz, w);
            if (sr.goal >= 0) { found = sr; break; }
            why = sr.why;
        }
        if (found == null) { pl.plan.note(why); return null; }
        Grid g = found.g;
        int nx = g.nx, goal = found.goal, start = found.start;
        int[] prev = found.prev;
        // the cells goal (road) -> start (rim)
        List<double[]> pts = new ArrayList<>();
        for (int k = goal; k >= 0; k = prev[k]) {
            pts.add(new double[]{g.cx(k % nx), g.cz(k / nx)});
            if (k == start) break;
        }
        // the trailhead: the goal's nearest road point, HEAD_OUT beyond its paved edge on the trail's side
        double gx = pts.get(0)[0], gz = pts.get(0)[1];
        RoadDesign road = null;
        double bs = 0, edge = 0;
        int side = 1;
        if (!found.town) {
            Head head = null;
            double hd = Double.MAX_VALUE;
            for (Head h : heads) {
                double dd = Math.hypot(h.x - gx, h.z - gz);
                if (dd < hd) { hd = dd; head = h; }
            }
            road = roads.get(head.road);
            edge = head.edge;
            bs = head.s;
            double bd = Double.MAX_VALUE;
            for (double t = Math.max(0, head.s - 4); t <= Math.min(road.length, head.s + 4); t += 0.25) {
                double[] c = road.point(t, 0);
                double dd = Math.hypot(c[0] - gx, c[1] - gz);
                if (dd < bd) { bd = dd; bs = t; }
            }
            double[] c0 = road.point(bs, 0), c1 = road.point(bs, 1);
            double ux = c1[0] - c0[0], uz = c1[1] - c0[1];
            side = (gx - c0[0]) * ux + (gz - c0[1]) * uz >= 0 ? 1 : -1;
            double[] th = road.point(bs, side * (edge + HEAD_OUT));
            pts.set(0, new double[]{th[0], th[1]});
        }
        pts.set(pts.size() - 1, new double[]{rx, rz});
        double[] line = resample(chaikin(chaikin(simplify(pts, SIMPLIFY))), g);
        int n = line.length / 3;
        double len = n - 1;
        double grade = 0;
        for (int i = 0; i + 10 < n; i++) grade = Math.max(grade, Math.abs(line[(i + 10) * 3 + 2] - line[i * 3 + 2]) / 10);
        int fords = 0;
        boolean wet = false;
        for (int i = 0; i < n; i++) {
            boolean w = g.s.water(line[i * 3], line[i * 3 + 1]);
            if (w && !wet) fords++;
            wet = w;
        }
        TunnelMesh.Placement sign = road != null ? sign(road, bs, side, edge, line, len) : townSign(pl.p, line, len);
        if (sign == null) pl.plan.note("trail sign no room");
        TunnelMesh.Placement caution = caution(line, g, pl.plan);
        if (caution == null) pl.plan.note("trail caution sign no room");
        List<TunnelMesh.Placement> markers = markers(line, g);
        return new Trail(d.id(), line, len, grade, fords, road != null ? road.id : "town:" + nearestTown(pl.p, gx, gz), sign, caution, markers);
    }

    /** A window's search: its grid, the way back, the rim's cell, the goal cell reached (-1 none, why). */
    private record Search(Grid g, int[] prev, int start, int goal, String why, boolean town) {}

    /** Builds the grid over window {x0, z0, x1, z1} and walks it from the rim to the nearest trailhead. */
    private static Search search(UndergroundPlanner pl, List<Head> all, double rx, double rz, double[] w) {
        int nx = (int) Math.ceil((w[2] - w[0]) / GRID), nz = (int) Math.ceil((w[3] - w[1]) / GRID);
        if ((long) nx * nz > MAX_CELLS) return new Search(null, null, -1, -1, "trail window too big", false);
        Grid g = new Grid(w[0], w[1], nx, nz, view(pl));
        shapes(pl.plan, g, rx, rz);
        // the kinds: the towns, the home and the other caves' cuts blocked, the towns' edges goals, then the highways'
        // corridors and the trailheads' goal discs (last: a shoulder is never blocked). Ground and streams per cell as
        // the walk reaches it (Grid#height)
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            double x = g.cx(i), z = g.cz(j), td = pl.p.townDistance(x, z);
            int k = j * nx + i;
            if (td < TOWN_CLEAR || pl.p.homeDistance(x, z) < HOME_CLEAR) g.kind[k] = BLOCK;
            else if (td < TOWN_CLEAR + 2 * GRID) g.kind[k] = TOWN;
        }
        for (Shape sh : g.others) {
            double[] b = sh.bounds();
            for (int j = Math.max(0, g.row(b[2])); j <= Math.min(nz - 1, g.row(b[5])); j++)
                for (int i = Math.max(0, g.col(b[0])); i <= Math.min(nx - 1, g.col(b[3])); i++) {
                    double x = g.cx(i), z = g.cz(j);
                    if (Grid.cuts(sh, x, z, g.s.heightAt(x, z))) g.kind[j * nx + i] = BLOCK;
                }
        }
        for (RoadDesign r : pl.p.roads) {
            if (trailhead(r)) continue;
            double rad = r.section.edge + HIGHWAY_CLEAR;
            for (double t = 0; t <= r.length; t += 3) {
                if (tunnel(r, t) || span(r, t)) continue;
                double[] c = r.point(t, 0);
                if (c[0] < w[0] - rad || c[0] > w[2] + rad || c[1] < w[1] - rad || c[1] > w[3] + rad) continue;
                disc(g, c[0], c[1], rad, HIGHWAY);
            }
        }
        // never a trailhead in a highway's or ramp's corridor (a crossroad at an interchange)
        for (int k = 0; k < g.kind.length; k++) if (g.kind[k] == TOWN && highwayNear(g, k % nx, k / nx)) g.kind[k] = OPEN;
        for (Head h : all) {
            if (!g.in(g.col(h.x), g.row(h.z)) || g.kind[g.row(h.z) * nx + g.col(h.x)] == HIGHWAY) continue;
            disc(g, h.x, h.z, h.edge + GOAL_OUT, GOAL);
        }
        if (!g.in(g.col(rx), g.row(rz))) return new Search(null, null, -1, -1, "trail rim off the window", false);
        int start = g.row(rz) * nx + g.col(rx);
        int[] prev = new int[nx * nz];
        double[] reach = new double[5];
        int goal = walk(g, start, rx, rz, prev, reach);
        String why = goal >= 0 ? null : String.format(java.util.Locale.ROOT,
                "trail no way (%d of %d x %d cells reached, %.0f m from a road at best; stopped by cuts, towns or home %d, highways %d, steep %d)",
                (int) reach[0], nx, nz, reach[1], (int) reach[2], (int) reach[3], (int) reach[4]);
        return new Search(g, prev, start, goal, why, goal >= 0 && g.kind[goal] == TOWN);
    }

    /** Whether a highway's corridor is within 3 cells of cell (i, j). */
    private static boolean highwayNear(Grid g, int i, int j) {
        for (int b = Math.max(0, j - 3); b <= Math.min(g.nz - 1, j + 3); b++)
            for (int a = Math.max(0, i - 3); a <= Math.min(g.nx - 1, i + 3); a++) if (g.kind[b * g.nx + a] == HIGHWAY) return true;
        return false;
    }

    /** The shapes of the window: the mouth's (bounds within 4 m of the rim) and the others' cuts and talus. */
    private static void shapes(UndergroundPlan plan, Grid g, double rx, double rz) {
        Set<Integer> seen = new HashSet<>();
        double c = UndergroundPlan.INDEX_CELL;
        for (double z = g.z0; z <= g.z0 + g.nz * GRID + c; z += c)
            for (double x = g.x0; x <= g.x0 + g.nx * GRID + c; x += c)
                for (int id : plan.shapesNear(Math.min(x, g.x0 + g.nx * GRID), Math.min(z, g.z0 + g.nz * GRID))) {
                    if (!seen.add(id)) continue;
                    Shape sh = plan.shapes.get(id);
                    if (!(sh instanceof Open) && !(sh instanceof Talus)) continue;
                    double[] b = sh.bounds();
                    if (b[3] < g.x0 || b[0] > g.x0 + g.nx * GRID || b[5] < g.z0 || b[2] > g.z0 + g.nz * GRID) continue;
                    boolean mouth = rx >= b[0] - 4 && rx <= b[3] + 4 && rz >= b[2] - 4 && rz <= b[5] + 4;
                    (mouth ? g.mouth : g.others).add(sh);
                }
    }

    private static void disc(Grid g, double x, double z, double r, byte kind) {
        int i0 = g.col(x - r), i1 = g.col(x + r), j0 = g.row(z - r), j1 = g.row(z + r);
        for (int j = Math.max(0, j0); j <= Math.min(g.nz - 1, j1); j++)
            for (int i = Math.max(0, i0); i <= Math.min(g.nx - 1, i1); i++)
                if (Math.hypot(g.cx(i) - x, g.cz(j) - z) <= r) g.kind[j * g.nx + i] = kind;
    }

    /**
     * Cheapest walk from start to the nearest goal cell (A*: a step costs at least its length, so the straight distance
     * to the goals is a lower bound; their 8-neighbour chamfer distance over 1.0824 never exceeds it); prev holds the way
     * back; -1 none (why in reach: {cells settled, the least distance to a goal reached}).
     */
    private static int walk(Grid g, int start, double rx, double rz, int[] prev, double[] reach) {
        int nx = g.nx, nz = g.nz, m = nx * nz;
        float[] lb = goalDistance(g);
        float[] cost = new float[m];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        boolean[] done = new boolean[m];
        Heap q = new Heap();
        cost[start] = 0;
        q.push(start, lb[start]);
        int settled = 0;
        double nearest = lb[start];
        int[] stops = new int[3];
        while (q.n > 0) {
            int k = q.pop();
            if (done[k]) continue;
            done[k] = true;
            settled++;
            nearest = Math.min(nearest, lb[k]);
            if (g.kind[k] == GOAL || g.kind[k] == TOWN) return k;
            double c = cost[k];
            int i = k % nx, j = k / nx;
            boolean nearMouth = Math.hypot(g.cx(i) - rx, g.cz(j) - rz) < MOUTH;
            for (int[] dd : DIRS) {
                int a = i + dd[0], b = j + dd[1];
                if (!g.in(a, b)) continue;
                int o = b * nx + a;
                if (done[o]) continue;
                if (g.kind[o] == BLOCK || g.kind[o] == HIGHWAY) { stops[g.kind[o] == BLOCK ? 0 : 1]++; continue; }
                // a knight's move also crosses its two side cells: none of them blocked
                if (Math.abs(dd[0]) + Math.abs(dd[1]) == 3) {
                    int si = i + Integer.signum(dd[0]) * (Math.abs(dd[0]) == 2 ? 1 : 0), sj = j + Integer.signum(dd[1]) * (Math.abs(dd[1]) == 2 ? 1 : 0);
                    if (g.kind[sj * nx + si] == BLOCK || g.kind[sj * nx + si] == HIGHWAY) continue;
                }
                double L = GRID * Math.hypot(dd[0], dd[1]), gr = Math.abs(g.height(o) - g.height(k)) / L;
                double f;
                if (gr > STEEP) {
                    if (!nearMouth) { stops[2]++; continue; }
                    f = 40;
                } else f = 1 + (gr / GRADE) * (gr / GRADE);
                double nc = c + L * f + (g.kind[o] == WATER ? FORD * L / GRID : 0);
                if (nc < cost[o]) {
                    cost[o] = (float) nc;
                    prev[o] = k;
                    q.push(o, (float) (nc + lb[o]));
                }
            }
        }
        reach[0] = settled;
        reach[1] = nearest;
        reach[2] = stops[0];
        reach[3] = stops[1];
        reach[4] = stops[2];
        return -1;
    }

    /** Each cell's 8-neighbour chamfer distance to the goal cells over 1.0824 (a lower bound of the straight one), m. */
    private static float[] goalDistance(Grid g) {
        int nx = g.nx, nz = g.nz;
        float[] d = new float[nx * nz];
        float inf = Float.POSITIVE_INFINITY, a = (float) GRID, b = (float) (GRID * Math.sqrt(2));
        for (int k = 0; k < d.length; k++) d[k] = g.kind[k] == GOAL || g.kind[k] == TOWN ? 0 : inf;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int k = j * nx + i;
            float v = d[k];
            if (i > 0) v = Math.min(v, d[k - 1] + a);
            if (j > 0) {
                v = Math.min(v, d[k - nx] + a);
                if (i > 0) v = Math.min(v, d[k - nx - 1] + b);
                if (i + 1 < nx) v = Math.min(v, d[k - nx + 1] + b);
            }
            d[k] = v;
        }
        for (int j = nz - 1; j >= 0; j--) for (int i = nx - 1; i >= 0; i--) {
            int k = j * nx + i;
            float v = d[k];
            if (i + 1 < nx) v = Math.min(v, d[k + 1] + a);
            if (j + 1 < nz) {
                v = Math.min(v, d[k + nx] + a);
                if (i + 1 < nx) v = Math.min(v, d[k + nx + 1] + b);
                if (i > 0) v = Math.min(v, d[k + nx - 1] + b);
            }
            d[k] = v;
        }
        for (int k = 0; k < d.length; k++) d[k] /= 1.0824f;
        return d;
    }

    /** A binary min-heap of cells by key (stale entries are skipped by the search). */
    private static final class Heap {
        int[] cell = new int[1 << 12];
        float[] key = new float[1 << 12];
        int n;

        void push(int c, float k) {
            if (n == cell.length) {
                cell = Arrays.copyOf(cell, n * 2);
                key = Arrays.copyOf(key, n * 2);
            }
            int i = n++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (key[p] <= k) break;
                cell[i] = cell[p];
                key[i] = key[p];
                i = p;
            }
            cell[i] = c;
            key[i] = k;
        }

        int pop() {
            int top = cell[0];
            int c = cell[--n];
            float k = key[n];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= n) break;
                int r = l + 1, m = r < n && key[r] < key[l] ? r : l;
                if (key[m] >= k) break;
                cell[i] = cell[m];
                key[i] = key[m];
                i = m;
            }
            cell[i] = c;
            key[i] = k;
            return top;
        }
    }

    /** Ramer-Douglas-Peucker on {x, z} points, ends kept. */
    static List<double[]> simplify(List<double[]> p, double tol) {
        boolean[] keep = new boolean[p.size()];
        keep[0] = keep[p.size() - 1] = true;
        rdp(p, 0, p.size() - 1, tol, keep);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < p.size(); i++) if (keep[i]) out.add(p.get(i));
        return out;
    }

    private static void rdp(List<double[]> p, int a, int b, double tol, boolean[] keep) {
        if (b <= a + 1) return;
        double[] A = p.get(a), B = p.get(b);
        double dx = B[0] - A[0], dz = B[1] - A[1], L = Math.hypot(dx, dz);
        int best = -1;
        double bd = tol;
        for (int i = a + 1; i < b; i++) {
            double[] q = p.get(i);
            double d = L < 1e-9 ? Math.hypot(q[0] - A[0], q[1] - A[1]) : Math.abs((q[0] - A[0]) * dz - (q[1] - A[1]) * dx) / L;
            if (d > bd) { bd = d; best = i; }
        }
        if (best < 0) return;
        keep[best] = true;
        rdp(p, a, best, tol, keep);
        rdp(p, best, b, tol, keep);
    }

    /** One Chaikin corner cut, ends kept. */
    static List<double[]> chaikin(List<double[]> p) {
        if (p.size() < 3) return p;
        List<double[]> out = new ArrayList<>();
        out.add(p.get(0));
        for (int i = 0; i + 1 < p.size(); i++) {
            double[] a = p.get(i), b = p.get(i + 1);
            if (i > 0) out.add(new double[]{0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]});
            if (i + 2 < p.size()) out.add(new double[]{0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]});
        }
        out.add(p.get(p.size() - 1));
        return out;
    }

    /** Points a metre apart along the polyline as {x, z, y}, y the ground. */
    private static double[] resample(List<double[]> p, Grid g) {
        double total = 0;
        for (int i = 0; i + 1 < p.size(); i++) total += Math.hypot(p.get(i + 1)[0] - p.get(i)[0], p.get(i + 1)[1] - p.get(i)[1]);
        int n = Math.max(2, (int) Math.round(total) + 1);
        double step = total / (n - 1);
        double[] out = new double[n * 3];
        int seg = 0;
        double at = 0;
        for (int k = 0; k < n; k++) {
            double want = k * step;
            while (seg + 2 < p.size() && at + Math.hypot(p.get(seg + 1)[0] - p.get(seg)[0], p.get(seg + 1)[1] - p.get(seg)[1]) < want) {
                at += Math.hypot(p.get(seg + 1)[0] - p.get(seg)[0], p.get(seg + 1)[1] - p.get(seg)[1]);
                seg++;
            }
            double[] a = p.get(seg), b = p.get(seg + 1);
            double L = Math.hypot(b[0] - a[0], b[1] - a[1]), f = L < 1e-9 ? 0 : Math.max(0, Math.min(1, (want - at) / L));
            double x = a[0] + (b[0] - a[0]) * f, z = a[1] + (b[1] - a[1]) * f;
            out[k * 3] = x;
            out[k * 3 + 1] = z;
            out[k * 3 + 2] = g.ground(x, z);
        }
        return out;
    }

    /** A part standing upright at (x, y, z), its front facing the horizontal (fx, fz); ex = ey x ez (as RoadSigns). */
    static TunnelMesh.Placement part(String mesh, String bone, double x, double y, double z, double fx, double fz) {
        double l = Math.hypot(fx, fz);
        double[] ez = {fx / l, 0, fz / l}, ey = {0, 1, 0}, ex = {ez[2], 0, -ez[0]};
        return new TunnelMesh.Placement(mesh, bone, x, y, z, ex, ey, ez);
    }

    /** Whether every cell of a part is in one chunk. */
    public static boolean oneChunk(TunnelMesh.Placement p) {
        long[] cells = TunnelFixtures.Part.of(p).cells();
        int cx = Integer.MIN_VALUE, cz = 0;
        for (long c : cells) {
            int x = (int) (c >> 38), z = (int) (c << 26 >> 38);
            if (cx == Integer.MIN_VALUE) { cx = x >> 4; cz = z >> 4; }
            else if (x >> 4 != cx || z >> 4 != cz) return false;
        }
        return true;
    }

    /** The sign's distance bone: tenths of a mile, 1 .. 20. */
    public static String signBone(double length) {
        return "d" + Math.max(1, Math.min(20, (int) Math.round(length / MILE * 10)));
    }

    /** The trailhead sign beside the trail's start, facing the road; null when no try fits. */
    private static TunnelMesh.Placement sign(RoadDesign road, double s, int side, double edge, double[] line, double len) {
        for (double a : SIGN_ALONG) {
            double t = s + a;
            if (t < 0 || t > road.length) continue;
            double[] c = road.point(t, 0), q = road.point(t, side * (edge + SIGN_OUT));
            double d = Double.MAX_VALUE;
            for (int i = 0; i < Math.min(line.length / 3, 30); i++) d = Math.min(d, Math.hypot(line[i * 3] - q[0], line[i * 3 + 1] - q[1]));
            if (d < SIGN_CLEAR) continue;
            TunnelMesh.Placement p = part(SIGN, signBone(len), q[0], line[2], q[1], c[0] - q[0], c[1] - q[1]);
            if (oneChunk(p)) return p;
        }
        return null;
    }

    /** The town nearest (x, z) by its limit's middle (its name). */
    static String nearestTown(GeoProtect p, double x, double z) {
        String best = "?";
        double bd = Double.MAX_VALUE;
        for (var t : p.towns) {
            double[] c = t.world((t.limit[0] + t.limit[1]) / 2, (t.limit[2] + t.limit[3]) / 2);
            double d = Math.hypot(c[0] - x, c[1] - z);
            if (d < bd) { bd = d; best = t.name; }
        }
        return best;
    }

    /**
     * The sign at a town's edge: beside the trail's start (SIGN_CLEAR + 0.4 to either side of its first metres), facing
     * the town (down the town distance); null when neither side fits in one chunk.
     */
    private static TunnelMesh.Placement townSign(GeoProtect p, double[] line, double len) {
        double x = line[0], z = line[1];
        double fx = p.townDistance(x - 4, z) - p.townDistance(x + 4, z), fz = p.townDistance(x, z - 4) - p.townDistance(x, z + 4);
        if (Math.hypot(fx, fz) < 1e-6) { fx = x - line[6]; fz = z - line[7]; }
        double l = Math.hypot(fx, fz);
        fx /= l;
        fz /= l;
        for (double off : new double[]{SIGN_CLEAR + 0.4, -(SIGN_CLEAR + 0.4), SIGN_CLEAR + 1.4, -(SIGN_CLEAR + 1.4)}) {
            double qx = x - fz * off, qz = z + fx * off;
            double dmin = Double.MAX_VALUE;
            for (int i = 0; i < Math.min(line.length / 3, 30); i++) dmin = Math.min(dmin, Math.hypot(line[i * 3] - qx, line[i * 3 + 1] - qz));
            if (dmin < SIGN_CLEAR) continue;
            TunnelMesh.Placement s = part(SIGN, signBone(len), qx, line[2], qz, fx, fz);
            if (oneChunk(s)) return s;
        }
        return null;
    }

    /**
     * The caution sign before the mouth: CAUTION_BACK .. CAUTION_MAX m back from the rim along the trail, CAUTION_SIDE to
     * the left of the line (else the right), facing back down it; never in a stream or within CAUTION_ROCK of a boulder
     * (the entrance rocks, EntranceRocks), in one chunk; null when nothing fits.
     */
    private static TunnelMesh.Placement caution(double[] line, Grid g, UndergroundPlan plan) {
        int n = line.length / 3;
        for (int back = CAUTION_BACK; back <= CAUTION_MAX; back++) {
            int k = n - 1 - back;
            if (k < 1) break;
            double tx = line[(k + 1) * 3] - line[(k - 1) * 3], tz = line[(k + 1) * 3 + 1] - line[(k - 1) * 3 + 1], L = Math.hypot(tx, tz);
            if (L < 1e-6) continue;
            tx /= L;
            tz /= L;
            // the walker's left going in (+t) is (tz, -tx), x east, z south
            for (double side : new double[]{CAUTION_SIDE, -CAUTION_SIDE}) {
                double x = line[k * 3] + tz * side, z = line[k * 3 + 1] - tx * side;
                if (g.s.water(x, z) || boulderNear(plan, x, z)) continue;
                TunnelMesh.Placement p = part(CAUTION, null, x, g.ground(x, z), z, -tx, -tz);
                if (oneChunk(p)) return p;
            }
        }
        return null;
    }

    /** Whether a planned boulder's edge (its length's half round its column) is within CAUTION_ROCK + the sign's half width of (x, z). */
    private static boolean boulderNear(UndergroundPlan plan, double x, double z) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
            for (UndergroundPlan.Rock r : plan.rocksIn(cx + dx, cz + dz))
                if (r.boulder() && Math.hypot(r.x() + 0.5 - x, r.z() + 0.5 - z) < UndergroundPlan.ROCK_LENGTH[r.form()] / 2 + CAUTION_ROCK + SIGN_W / 2) return true;
        return false;
    }

    /** The marker posts: the first, then every MARKER_EVERY and at the bends, off cuts and streams, each in one chunk. */
    private static List<TunnelMesh.Placement> markers(double[] line, Grid g) {
        int n = line.length / 3;
        List<TunnelMesh.Placement> out = new ArrayList<>();
        double last = -1e9;
        int span = (int) BEND_SPAN;
        for (int i = (int) MARKER_FIRST; i < n - MARKER_END; i++) {
            boolean due = i - last >= MARKER_EVERY;
            if (!due && i - last >= MARKER_BEND_GAP && i >= span && i + span < n) {
                double h0 = Math.atan2(line[i * 3 + 1] - line[(i - span) * 3 + 1], line[i * 3] - line[(i - span) * 3]);
                double h1 = Math.atan2(line[(i + span) * 3 + 1] - line[i * 3 + 1], line[(i + span) * 3] - line[i * 3]);
                double turn = Math.abs(Math.atan2(Math.sin(h1 - h0), Math.cos(h1 - h0)));
                due = turn > BEND;
            }
            if (!due) continue;
            TunnelMesh.Placement p = marker(line, i, g);
            if (p == null) continue;
            out.add(p);
            last = i;
        }
        return out;
    }

    /** A marker at point i (or a little on, within 3 m), on the tread's right, facing back down the trail; null none. */
    private static TunnelMesh.Placement marker(double[] line, int i, Grid g) {
        int n = line.length / 3;
        for (int k = i; k < Math.min(n - 1, i + 4); k++) {
            double tx = line[(k + 1) * 3] - line[(k - 1) * 3], tz = line[(k + 1) * 3 + 1] - line[(k - 1) * 3 + 1], L = Math.hypot(tx, tz);
            if (L < 1e-6) continue;
            tx /= L;
            tz /= L;
            // right of the walker going in (+t): (-tz, tx) with x east, z south
            double x = line[k * 3] - tz * MARKER_SIDE, z = line[k * 3 + 1] + tx * MARKER_SIDE;
            double plan = g.s.heightAt(x, z);
            if (g.s.water(x, z) || g.cut(x, z, plan) || g.ground(x, z) < plan - 0.5) continue;
            TunnelMesh.Placement p = part(MARKER, null, x, g.ground(x, z), z, -tx, -tz);
            if (oneChunk(p)) return p;
        }
        return null;
    }
}
