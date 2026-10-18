package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;

/**
 * The works of a road tunnel (Tunnel V1; docs/worldgen/federal_tunnels_v1.md): where the planned mountain stays, where
 * the bores, their portals and approaches are cut, and what the invisible collision (road_structure) holds. Pure Java on
 * a RoadDesign and its TunnelSection, shared by the density (RoadWorks#densitySurface), the column finish
 * (RoadWorks.column), the chunk pass (#cells), the collision (#solids, BridgeMesh.boxes) and the mesh (TunnelMesh).
 * <p>
 * Stations: d, the depth into the mountain from the nearer portal (s0 or s1), is >= 0 in the tunnel and < 0 on the
 * approach (t = -d in front of the portal).
 * <ul>
 *   <li>surface (the density): over the bores the mountain, with at least COVER over the lining's crown (a backfilled
 *   mound, 1:1.5 sides, where the planned ground is lower: the planner's portals often have almost no cover); behind
 *   the headwall the rock face cut at CUT : 1 from its top; on the approach the formation, the shelf beside it (flat at
 *   the shoulder hinge out to the walls) and behind the retaining walls the rock cut at CUT : 1 or the fill at 1:1.5 from
 *   the wall top, blended into the open road's works from WALL_LEN to APPROACH;</li>
 *   <li>bores: the lining (inner surface to LINING outside it, the walls under the springline down to the floor), the
 *   walkways and barriers, the pavement's own solid (collision in mixed cells), the open bore inside; the walkways and
 *   barriers run on LEAD beyond each portal, their last LEAD_RAMP sloping down;</li>
 *   <li>portals: a cast concrete headwall HEAD thick across both bores (HEAD_HALF, HEAD_TOP over the portal's datum),
 *   its coping, a RING round each opening standing RING_OUT proud, retaining walls WALL_T thick along the approach,
 *   their tops falling from the headwall's top to WALL_END over WALL_LEN;</li>
 *   <li>cross passages (R4, tunnels of PASSAGE_MIN or more, every PASSAGE_SPACING): PASSAGE_W x PASSAGE_H from the
 *   egress walkway of one bore to the other's, a SHELL of structure round them.</li>
 * </ul>
 * Inside the tunnel the planned ground is never water (the works ignore it).
 */
public final class TunnelWorks {
    private TunnelWorks() {}

    /** Approach length beyond each portal (templates, equipment), and where its blend into the open road starts, m. */
    public static final double APPROACH = 60, BLEND_FROM = 40;
    /** Headwall thickness; portal ring width (from the inner surface) and projection; coping height and projection, m. */
    public static final double HEAD = 1.0, RING = 0.9, RING_OUT = 0.35, COPING_H = 0.3, COPING_OUT = 0.15;
    /** Retaining walls: length, thickness, end height over the datum, m. */
    public static final double WALL_LEN = 40, WALL_T = 0.6, WALL_END = 0.6;
    /** Rock cut slope (vertical per horizontal); fill slope (vertical per horizontal); least cover over the crown, m. */
    public static final double CUT = 2.0, FILL = 1 / 1.5, COVER = 2.0;
    /** Walkway and barrier lead-ins beyond the portals and their sloped ends; solid under pavement and walks, m. */
    public static final double LEAD = 16, LEAD_RAMP = 4, BASE_DEPTH = 1.2;
    /** Cross passages: from this tunnel length; spacing; opening width and height; structure shell round them, m. */
    public static final double PASSAGE_MIN = 300, PASSAGE_SPACING = 250, PASSAGE_W = 2.4, PASSAGE_H = 2.2, SHELL = 0.3;
    /** The density keeps the rock over the cleared band in front of the headwall and the walls (#densitySurface), m. */
    public static final double DENSITY_BAND = 6;
    /**
     * A level bench at the wall's top behind each retaining wall before its rock cut starts (the cut never leans on the
     * wall's top); the rock kept round the bores and under the approaches against the caves and the carvers (#rock), m.
     */
    public static final double BENCH = 1.5, ROCK = 4.0;
    /** RoadWorks.Column data of a TUNNEL column on an approach: everything above its floor is cleared. */
    public static final int APPROACH_COLUMN = 1;
    /**
     * RoadWorks.Column data of a TUNNEL column whose square also holds finished ground in front of the works (an
     * approach's or a portal's floor): RoadWorks.finish lays the open road's finish under the works (RoadWorks.TUNNEL_OVER),
     * so the ground runs on under the walls and lead-ins like a bridge's structure stands on the ground.
     */
    public static final int ON_GROUND = 2;

    /** Headwall half width: the retaining walls' faces. */
    public static double headHalf(TunnelSection ts) { return ts.twin ? 25.5 : 13.0; }

    /** Headwall top over the portal's datum. */
    public static double headTop(TunnelSection ts) { return ts.twin ? 12.6 : 11.0; }

    /** Half width of the backfill mound's top (the linings' outsides plus 0.75 m). */
    static double moundHalf(TunnelSection ts) { return ts.twin ? ts.inner + ts.width + TunnelSection.LINING + 0.75 : ts.width / 2 + TunnelSection.LINING + 0.75; }

    /** The nearer portal of station s: {portal station, d (depth into the mountain), sign of +s into the mountain}. */
    public static double[] portal(RoadDesign.Tunnel tn, double s) {
        return s - tn.s0() <= tn.s1() - s ? new double[]{tn.s0(), s - tn.s0(), 1} : new double[]{tn.s1(), tn.s1() - s, -1};
    }

    /** The cross passages' stations (R4, tunnels of PASSAGE_MIN or more): equally spaced, at most PASSAGE_SPACING apart. */
    public static double[] passages(TunnelSection ts, RoadDesign.Tunnel tn) {
        if (!ts.twin || tn.length() < PASSAGE_MIN) return new double[0];
        int n = (int) Math.ceil(tn.length() / PASSAGE_SPACING - 1e-9);
        double[] out = new double[n - 1];
        for (int i = 1; i < n; i++) out[i - 1] = tn.s0() + tn.length() * i / n;
        return out;
    }

    /** A bore's base at station s (the higher pavement height of its two barrier toes). */
    public static double base(RoadDesign d, TunnelSection ts, int side, double s) {
        return Math.max(toe(d, ts, side, s, true), toe(d, ts, side, s, false));
    }

    /** Pavement height at a bore's barrier toe. */
    public static double toe(RoadDesign d, TunnelSection ts, int side, double s, boolean left) {
        return d.pavementY(clamp(d, s), ts.u(side, left ? ts.toeL : ts.toeR));
    }

    /** Walkway and barrier height scale on a lead-in (1 within LEAD - LEAD_RAMP of the portal, 0 at LEAD). */
    public static double leadScale(double t) {
        if (t <= LEAD - LEAD_RAMP) return 1;
        return Math.max(0, (LEAD - t) / LEAD_RAMP);
    }

    /** A retaining wall's top over the datum at approach distance t (0 .. WALL_LEN). */
    public static double wallHeight(TunnelSection ts, double t) {
        return WALL_END + (headTop(ts) - WALL_END) * (1 - Math.max(0, Math.min(1, t / WALL_LEN)));
    }

    /** The bench's width behind the walls at approach distance t: BENCH along the walls, narrowing to 0 at APPROACH. */
    static double bench(double t) {
        return t <= WALL_LEN ? BENCH : BENCH * Math.max(0, (APPROACH - t) / (APPROACH - WALL_LEN));
    }

    /**
     * The retaining walls' top at station s, t from the portal; beyond WALL_LEN the template's height there (falling by
     * WALL_END to the datum at APPROACH): the bench's height behind the walls and the caps' over a wall column.
     */
    public static double wallTop(RoadDesign d, TunnelSection ts, double s, double t) {
        return d.datum(clamp(d, s)) + wallHeight(ts, Math.min(t, WALL_LEN)) - (t > WALL_LEN ? WALL_END * (t - WALL_LEN) / (APPROACH - WALL_LEN) : 0);
    }

    static double clamp(RoadDesign d, double s) { return Math.max(0, Math.min(d.length, s)); }

    // ------------------------------------------------------------------ surfaces

    /**
     * The finished surface of a point of a road near a tunnel (h on road d, tn the tunnel whose approaches hold h's
     * station), g the planned ground, open the road's own finished surface there without the tunnel (NaN: natural).
     * density: the density's height (the rock kept over the cleared bands in front of the headwall and the walls, so the
     * noise's 4-block interpolation never slopes the ground behind a wall down to the road). NaN where it stays natural.
     */
    public static double surface(RoadWorks.Hit h, RoadDesign.Tunnel tn, double g, double open, boolean density) {
        RoadDesign d = h.road();
        TunnelSection ts = TunnelSection.of(d.section);
        CrossSection sec = d.section;
        double s = h.s(), sc = clamp(d, s), v = h.v();
        double[] pt = portal(tn, s);
        double dIn = pt[1], yP = d.datum(pt[0]), hw = headHalf(ts), top = yP + headTop(ts);
        double out;
        if (dIn >= 0) {
            double rock = top + CUT * Math.max(dIn - HEAD, v - hw - WALL_T);
            out = Math.max(mound(d, ts, sc, v), Math.min(g, rock));
        } else {
            double t = -dIn;
            if (t >= APPROACH) return open;
            double shelf = d.formationY(sc, Math.copySign(sec.edge, h.u()));
            double wall = wallTop(d, ts, s, t);
            double tmpl;
            if (v <= sec.edge) tmpl = d.formationY(sc, h.u());
            else if (v <= hw) tmpl = shelf;
            // under a wall the finished ground in front runs on (the wall stands on it, like a bridge's structure)
            else if (!density && t <= WALL_LEN && v <= hw + WALL_T) tmpl = shelf;
            else {
                // behind the wall a level bench at its top, then the rock cut or the fill
                double dist = Math.max(0, v - hw - WALL_T - bench(t));
                tmpl = g >= wall ? Math.min(g, wall + CUT * dist) : Math.max(g, wall - FILL * dist);
            }
            // where the density keeps rock over a band the finish clears, the finish is never natural (2026-10-16: the
            // kept rock stood as pillars in front of the walls wherever the shelf met the planned ground)
            boolean band = false;
            if (t <= WALL_LEN && v >= hw - DENSITY_BAND && v <= hw + WALL_T) {
                band = true;
                if (density) tmpl = Math.max(tmpl, wall);
            }
            if (t < DENSITY_BAND && v <= hw + WALL_T) {
                band = true;
                if (density) tmpl = Math.max(tmpl, top);
            }
            if (t > BLEND_FROM) {
                double w = CrossSection.smooth((t - BLEND_FROM) / (APPROACH - BLEND_FROM));
                tmpl += ((Double.isNaN(open) ? g : open) - tmpl) * w;
            }
            out = tmpl;
            if (v <= sec.edge + 0.5 || band) return out;   // on the formation and over the bands: always the works'
        }
        return Math.abs(out - g) < RoadWorks.NATURAL_TOLERANCE ? Double.NaN : out;
    }

    /** The backfill mound over the bores at (station, |u|): COVER over the higher crown, 1:1.5 sides; -infinity off it. */
    static double mound(RoadDesign d, TunnelSection ts, double s, double v) {
        double crown = Double.NEGATIVE_INFINITY;
        for (int side : ts.sides()) crown = Math.max(crown, base(d, ts, side, s) + ts.crownTop());
        double half = moundHalf(ts);
        return crown + COVER - Math.max(0, v - half) * FILL;
    }

    // ------------------------------------------------------------------ solids and openings

    /**
     * What one point of the works holds: solid intervals (road_structure: lining, walls, walkways, the pavement's own
     * solid), open intervals (the bores and passages, cut out of the mountain), and on an approach its floor (the finished
     * ground: everything above it is cleared).
     */
    public static final class Sample {
        final double[] solid = new double[16], open = new double[8];
        int ns, no;
        /** On an approach (d < 0); its floor (NaN behind the walls: natural). */
        boolean approach;
        double floor = Double.NaN;
        /** On the pavement between the barrier toes of a bore, away from the portal's concrete (PAVED columns). */
        boolean road;
        /** Holds concrete other than the pavement's own solid. */
        boolean concrete;

        void solid(double lo, double hi) {
            if (!(hi > lo)) return;
            pavement(lo, hi);
            concrete = true;
        }

        /** The pavement's own solid: collision in a cell mixed with concrete, never a reason for a TUNNEL column. */
        void pavement(double lo, double hi) {
            if (!(hi > lo) || ns + 2 > solid.length) return;
            solid[ns++] = lo;
            solid[ns++] = hi;
        }

        void open(double lo, double hi) {
            if (!(hi > lo) || no + 2 > open.length) return;
            open[no++] = lo;
            open[no++] = hi;
        }

        public boolean any() { return ns > 0 || no > 0; }

        public double[] solids() { return java.util.Arrays.copyOf(solid, ns); }

        double bottom() {
            double b = Double.POSITIVE_INFINITY;
            for (int i = 0; i < ns; i += 2) b = Math.min(b, solid[i]);
            for (int i = 0; i < no; i += 2) b = Math.min(b, open[i]);
            if (approach && !Double.isNaN(floor)) b = Math.min(b, floor);
            return b;
        }

        double top() {
            double t = Double.NEGATIVE_INFINITY;
            for (int i = 1; i < ns; i += 2) t = Math.max(t, solid[i]);
            for (int i = 1; i < no; i += 2) t = Math.max(t, open[i]);
            return t;
        }
    }

    /** The sample of point (station s, lateral u) of road d near tunnel tn. */
    public static Sample sample(RoadDesign d, RoadDesign.Tunnel tn, double s, double u) {
        TunnelSection ts = TunnelSection.of(d.section);
        CrossSection sec = d.section;
        Sample out = new Sample();
        double[] pt = portal(tn, s);
        double dIn = pt[1], yP = d.datum(pt[0]), hw = headHalf(ts), top = yP + headTop(ts), v = Math.abs(u), sc = clamp(d, s);
        if (dIn >= 0) {
            double pc = Double.NaN;
            for (double p : passages(ts, tn)) if (Math.abs(s - p) <= PASSAGE_W / 2 + SHELL) pc = p;
            bore(out, d, ts, s, u, 1, true, !Double.isNaN(pc) && Math.abs(s - pc) <= PASSAGE_W / 2);
            if (!Double.isNaN(pc) && v < ts.inner - TunnelSection.LINING) passage(out, d, ts, s, u, Math.abs(s - pc) <= PASSAGE_W / 2);
            if (dIn <= HEAD && v <= hw) {
                int side = ts.side(u);
                double x = ts.x(side, u);
                if (x >= 0 && x <= ts.width) out.solid(base(d, ts, side, s) + ts.arch(x, 0), top);
                else out.solid(yP - 3, top);
            }
            if (dIn <= HEAD + COPING_OUT && v <= hw + COPING_OUT) out.solid(top - COPING_H, top);
            out.road = out.road && dIn > HEAD + COPING_OUT;
            return out;
        }
        double t = -dIn;
        if (t >= APPROACH) return out;
        out.approach = true;
        if (v <= sec.edge) out.floor = sec.paved(v) ? d.pavementY(sc, u) : d.formationY(sc, u);
        else if (v <= hw) out.floor = d.formationY(sc, Math.copySign(sec.edge, u));
        double scale = leadScale(t);
        if (scale > 0) bore(out, d, ts, s, u, scale, false, false);
        if (t <= RING_OUT) {
            int side = ts.side(u);
            double x = ts.x(side, u), b = base(d, ts, side, s);
            if (x >= 0 && x <= ts.width) out.solid(b + ts.arch(x, 0), b + ts.arch(x, RING));
            else if (x >= -RING && x <= ts.width + RING) out.solid(b - BASE_DEPTH, b + ts.arch(x, RING));
        }
        if (t <= COPING_OUT && v <= hw + COPING_OUT) out.solid(top - COPING_H, top);
        if (t <= WALL_LEN && v >= hw && v <= hw + WALL_T)
            out.solid(d.formationY(sc, Math.copySign(sec.edge, u)) - 1.0, d.datum(sc) + wallHeight(ts, t));
        // the backfill behind a wall and past its end, up to the wall's top: the collision under the caps the mesh lays
        // over a wall column's square behind the wall (never a reason for a TUNNEL column)
        else if (v > hw && v <= hw + WALL_T + BENCH && t <= WALL_LEN + BENCH)
            out.pavement(d.formationY(sc, Math.copySign(sec.edge, u)) - 1.0, wallTop(d, ts, s, t));
        out.road = false;
        return out;
    }

    /**
     * A bore's solids and openings at (s, u): scale the walkways' and barriers' height (lead-ins), lining with the lining
     * and the open bore (inside the tunnel), passage: the inner wall is cut for a cross passage here.
     */
    private static void bore(Sample out, RoadDesign d, TunnelSection ts, double s, double u, double scale, boolean lining, boolean passage) {
        int side = ts.side(u);
        double x = ts.x(side, u);
        if (x < -TunnelSection.LINING || x > ts.width + TunnelSection.LINING) return;
        double pl = toe(d, ts, side, s, true), pr = toe(d, ts, side, s, false), b = Math.max(pl, pr), bottom = Math.min(pl, pr) - BASE_DEPTH;
        double walkL = pl + TunnelSection.WALK_H * scale, walkR = pr + TunnelSection.WALK_H * scale;
        double in = b + ts.arch(x, 0), outer = b + ts.arch(x, TunnelSection.LINING);
        if (lining) {
            if (x < 0 || x > ts.width) {
                if (passage && x < 0) {
                    out.solid(bottom, walkL);
                    out.open(walkL, walkL + PASSAGE_H);
                    out.solid(walkL + PASSAGE_H, outer);
                } else out.solid(bottom, outer);
                return;
            }
            out.solid(in, outer);
        } else if (x < 0 || x > ts.width) return;
        double floor;
        if (x <= ts.walkL) floor = walkL;
        else if (x <= ts.toeL) floor = pl + ts.barrier(x, true) * scale;
        else if (x < ts.toeR) {
            floor = d.pavementY(clamp(d, s), u);
            out.pavement(floor - BASE_DEPTH, floor);
            out.road = true;
            if (lining) out.open(floor, in);
            return;
        } else if (x < ts.width - ts.walkR) floor = pr + ts.barrier(x, false) * scale;
        else floor = walkR;
        out.solid(bottom, floor);
        if (lining) out.open(floor, in);
    }

    /** A cross passage through the pillar between the bores (|u| inside the linings): its opening and its shell. */
    private static void passage(Sample out, RoadDesign d, TunnelSection ts, double s, double u, boolean inside) {
        double a = toe(d, ts, -1, s, true) + TunnelSection.WALK_H, b = toe(d, ts, 1, s, true) + TunnelSection.WALK_H;
        double f = a + (b - a) * (u + ts.inner) / (2 * ts.inner);
        if (inside) {
            out.solid(f - SHELL, f);
            out.open(f, f + PASSAGE_H);
            out.solid(f + PASSAGE_H, f + PASSAGE_H + SHELL);
        } else out.solid(f - SHELL, f + PASSAGE_H + SHELL);
    }

    // ------------------------------------------------------------------ columns and cells

    /** The tunnel (bores or approaches) whose works reach a hit, or null (a bridge span takes precedence). */
    public static RoadDesign.Tunnel near(RoadWorks.Hit h) {
        if (h == null) return null;
        RoadDesign d = h.road();
        if (d.tunnels.isEmpty() || h.v() > RoadWorks.MAX_HALF_WIDTH) return null;
        RoadDesign.Tunnel tn = d.tunnel(h.s(), APPROACH);
        if (tn == null || h.beyond() > 0 && !tn.near(h.s(), APPROACH)) return null;
        return h.beyond() == 0 && d.span(h.sc()) != null ? null : tn;
    }

    /** The 4 x 4 samples of block column (x, z) on road d (index i + 4 j), null where the point is off the road. */
    static Sample[] samples(RoadDesign d, RoadDesign.Tunnel tn, int x, int z) {
        Sample[] out = new Sample[16];
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
            double[] l = d.local(x + (i + 0.5) / 4, z + (j + 0.5) / 4, RoadWorks.REACH);
            out[i + 4 * j] = l == null ? new Sample() : sample(d, tn, l[0], l[1]);
        }
        return out;
    }

    /**
     * The finish of a column near a tunnel (RoadWorks.column), or null where the open road's own finish applies (an
     * approach column without concrete in front of the walls). In the tunnel: NONE (the mountain), PAVED with
     * RoadWorks.TUNNEL_PAVED (a square wholly on a bore's pavement: the open bore carved above), else TUNNEL (y, bottom: the
     * works' top block and lowest block; roof: the guard's top). On an approach: TUNNEL with APPROACH_COLUMN where
     * concrete stands in it, NONE behind the walls.
     */
    public static RoadWorks.Column column(RoadWorks.Hit centre, RoadDesign.Tunnel tn, int x, int z, double[] pave) {
        RoadDesign d = centre.road();
        TunnelSection ts = TunnelSection.of(d.section);
        double dc = portal(tn, centre.s())[1];
        boolean approach = dc < 0;
        Sample[] ss = samples(d, tn, x, z);
        boolean solid = false, any = false, road = true, front = false;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, floor = Double.NEGATIVE_INFINITY;
        for (Sample s : ss) {
            solid |= s.concrete;
            any |= s.any();
            road &= s.road;
            if (s.any()) { lo = Math.min(lo, s.bottom()); hi = Math.max(hi, s.top()); }
            if (s.approach && !Double.isNaN(s.floor)) { front = true; floor = Math.max(floor, s.floor); }
        }
        if (approach) {
            // behind the walls the natural rock, past the bench's turf columns (their corners stay on the bench)
            if (!solid) return centre.v() > headHalf(ts) + WALL_T + Math.max(0, bench(-dc) - 0.75) ? RoadWorks.Column.NONE : null;
            int y = (int) Math.ceil(hi) - 1;
            return new RoadWorks.Column(RoadWorks.Kind.TUNNEL, y, 0, 0, APPROACH_COLUMN | (front ? ON_GROUND : 0), (int) Math.floor(lo),
                    front ? floor : 0, front ? floor : 0, Integer.MAX_VALUE);
        }
        if (!any) return RoadWorks.Column.NONE;
        int roof = (int) Math.ceil(hi) - 1;
        if (pave != null && road) {
            RoadWorks.Column c = RoadWorks.paved(RoadWorks.Kind.PAVED, pave, 0);
            return new RoadWorks.Column(c.kind(), c.y(), c.layers(), c.upper(), RoadWorks.TUNNEL_PAVED, 0, c.paveLo(), c.paveHi(), roof);
        }
        return new RoadWorks.Column(RoadWorks.Kind.TUNNEL, roof, 0, 0, front ? ON_GROUND : 0, (int) Math.floor(lo),
                front ? floor : 0, front ? floor : 0, roof);
    }

    /**
     * Whether ground point (x, z) of road d lies in or behind a tunnel's concrete front (inside the portal, or past the
     * walls' faces on an approach): a finish under the works keeps its floor there (RoadWorks.finish), the earth retained
     * by the concrete, never drawn up through it.
     */
    public static boolean behind(RoadDesign d, RoadDesign.Tunnel tn, double x, double z) {
        double[] l = d.local(x, z, RoadWorks.REACH);
        if (l == null) return false;
        return portal(tn, l[0])[1] >= 0 || Math.abs(l[1]) > headHalf(TunnelSection.of(d.section));
    }

    /** Whether column (x, z) of road d holds a retaining wall of tunnel tn (one of its samples in a wall's footprint). */
    public static boolean wallColumn(RoadDesign d, RoadDesign.Tunnel tn, int x, int z) {
        double hw = headHalf(TunnelSection.of(d.section));
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
            double[] l = d.local(x + (i + 0.5) / 4, z + (j + 0.5) / 4, RoadWorks.REACH);
            if (l == null) continue;
            double t = -portal(tn, l[0])[1], v = Math.abs(l[1]);
            if (t > 0 && t <= WALL_LEN && v >= hw && v <= hw + WALL_T) return true;
        }
        return false;
    }

    /** The top cell of a tunnel's works in column (x, z) (its highest solid or opening), Integer.MIN_VALUE when none. */
    public static int worksTop(RoadWorks works, int x, int z) {
        RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
        RoadDesign.Tunnel tn = near(h);
        if (tn == null) return Integer.MIN_VALUE;
        double hi = Double.NEGATIVE_INFINITY;
        for (Sample s : samples(h.road(), tn, x, z)) if (s.any()) hi = Math.max(hi, s.top());
        return hi == Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : (int) Math.ceil(hi) - 1;
    }

    /**
     * The rock kept round a tunnel against the plan's caves and the carvers (PlanStabilityDensity's hard floor,
     * WorldCarverStabilityMixin) in column (x, z): {lo, hi}. In the tunnel from ROCK under the works' lowest solid to ROCK
     * over the linings' crown, ROCK beyond the linings' outsides; on an approach from ROCK under the works up (to the
     * ground: the density caps it at its surface), ROCK beyond the bench. Null away from a tunnel. The stable layer hangs
     * from the planned surface only, so a bore deep under a mountain had none: a big cave opened round it (2026-10-16).
     */
    public static double[] rock(RoadWorks works, int x, int z) {
        RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
        RoadDesign.Tunnel tn = near(h);
        if (tn == null) return null;
        RoadDesign d = h.road();
        TunnelSection ts = TunnelSection.of(d.section);
        double s = h.s(), v = h.v(), dIn = portal(tn, s)[1];
        double lo = Double.POSITIVE_INFINITY, crown = Double.NEGATIVE_INFINITY;
        for (int side : ts.sides()) {
            lo = Math.min(lo, Math.min(toe(d, ts, side, s, true), toe(d, ts, side, s, false)) - BASE_DEPTH);
            crown = Math.max(crown, base(d, ts, side, s) + ts.crownTop());
        }
        if (dIn >= 0) return v > moundHalf(ts) - 0.75 + ROCK ? null : new double[]{lo - ROCK, crown + ROCK};
        if (v > headHalf(ts) + WALL_T + BENCH + ROCK) return null;
        double shelf = d.formationY(clamp(d, s), Math.copySign(d.section.edge, h.u())) - 1.0;
        return new double[]{Math.min(lo, shelf) - ROCK, Double.POSITIVE_INFINITY};
    }

    /** Cell states (#cells). */
    public static final byte NATURAL = 0, STRUCTURE = 1, AIR = 2;

    /**
     * The cells of a tunnel column from y0 to y1 ([y - y0]): STRUCTURE where any sample's solid reaches into the cell (its
     * collision is the solid within it, BridgeMesh.boxes), else AIR where a sample's opening holds the cell's middle (or
     * an approach sample's floor lies under it), else NATURAL.
     */
    public static byte[] cells(RoadWorks works, int x, int z, int y0, int y1) {
        byte[] out = new byte[Math.max(0, y1 - y0 + 1)];
        RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
        RoadDesign.Tunnel tn = near(h);
        if (tn == null) return out;
        Sample[] ss = samples(h.road(), tn, x, z);
        for (int y = y0; y <= y1; y++) {
            byte st = NATURAL;
            for (Sample s : ss) {
                for (int i = 0; i < s.ns; i += 2)
                    if (Math.min(s.solid[i + 1], y + 1) - Math.max(s.solid[i], y) > 1 / 32.0) { st = STRUCTURE; break; }
                if (st == STRUCTURE) break;
                if (st == AIR) continue;
                for (int i = 0; i < s.no; i += 2) if (s.open[i] <= y + 0.5 && s.open[i + 1] >= y + 0.5) st = AIR;
                if (s.approach && !Double.isNaN(s.floor) && s.floor <= y + 0.5) st = AIR;
            }
            out[y - y0] = st;
        }
        return out;
    }

    /**
     * The tunnel's solids at world point (x, z) (the collision of road_structure within its block), as {lo, hi, lo, hi,
     * ...}; null where none reaches the point. The equipment is not among them: its parts are fixtures with their own
     * collision (TunnelFixtures, V1.1; Tunnel V1 merged their boxes in here).
     */
    public static double[] solids(RoadWorks works, double x, double z) {
        RoadWorks.Hit h = works.hit(x, z, RoadWorks.REACH);
        RoadDesign.Tunnel tn = near(h);
        if (tn == null) return null;
        Sample s = sample(h.road(), tn, h.s(), h.u());
        return s.ns == 0 ? null : s.solids();
    }

    /**
     * Whether a point lies over a tunnel's bores, clear of its portals (the forest may grow there: the guard keeps only
     * the works' cells, RoadWorks#clear).
     */
    public static boolean overBores(RoadWorks.Hit h) {
        RoadDesign.Tunnel tn = near(h);
        return tn != null && portal(tn, h.s())[1] > HEAD + 8;
    }

    /** Every tunnel of a design list with its road, for the offline checks. */
    public static List<Object[]> all(List<RoadDesign> roads) {
        List<Object[]> out = new ArrayList<>();
        for (RoadDesign d : roads) for (RoadDesign.Tunnel t : d.tunnels) out.add(new Object[]{d, t});
        return out;
    }
}
