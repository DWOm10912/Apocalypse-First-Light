package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The terrain works of the built roads of one world (C3.2, C3.3a; docs/worldgen/federal_highways_c3.md, 10): the finished
 * surface the road body makes of the planned ground, and what every block column of the road becomes. Pure Java over
 * the road designs and a natural ground function (the Terrain V2 planned surface); no world, no block, no chunk order.
 * The same functions serve
 * <ul>
 *   <li>the density (terrain_works): the planned height a column has with the road built ({@link #surface}), so the
 *   noise itself shapes the cut, the fill and the slopes, and caves keep their stable layer under the new ground;</li>
 *   <li>the chunk pass: the exact finish of each column ({@link #column}): collision layers, asphalt, base and subbase
 *   under the pavement; graded turf with shared corners on the median, the shoulders' rounding and the slopes; the deck,
 *   the barriers and the abutments of a bridge;</li>
 *   <li>the forest and the spawn: where the road keeps clear ({@link #clear}).</li>
 * </ul>
 * Rules (the accepted R4 sample's earthworks, R4Earthworks, on the planned ground instead of surveyed blocks):
 * <ul>
 *   <li>formation (|u| <= EDGE): the median swale, or the turf just under the paved band;</li>
 *   <li>roadside: the shoulder hinge plus CrossSection.roadside (1:4 foreslope, 1 m ditch, 1:3 backslope, or the
 *   1:4 / 1:3 fill slope), which meets the ground at the daylight line by construction, and a deep cut turns into the
 *   rock cut (CrossSection#rockCut: 0.25H:1V lifts with benches; before 2026-10-19 a cut the 1:3 slope could not finish
 *   within the works ended in a wall); beyond MAX_HALF_WIDTH - 4 the surface is blended into the ground (a cut deeper
 *   than the rock cut reaches still ends in a wall there);</li>
 *   <li>beyond the route's ends: the end cross-section approaching the ground at 1:4;</li>
 *   <li>within a bridge span: no works (the natural valley under the deck);</li>
 *   <li>a point whose result is within 3 cm of the ground is natural (NaN);</li>
 *   <li>roadside works never enter water (a river, lake or the sea beside the road, as at a skewed crossing): the
 *   water keeps its natural bed and flows on; over the formation itself water is always bridged (the route design).</li>
 * </ul>
 * Where two roads are near (C3.3a: the trimmed ends at a junction or a town, before C3.3b builds the junctions), a
 * point on a road's formation belongs to that road; elsewhere the roadside works of all the roads reaching the point
 * combine: the highest fill and the deepest cut over the ground, so the finished surface stays continuous where one road's
 * slopes run into another's.
 * <p>
 * Interchanges (C3.3b group 2, docs/worldgen/federal_interchanges_c3_3b.md 6, 7): an attached ramp (RoadDesign.Attach) gives
 * way on its interstate's paved band (the interstate's pavement is the ramp's there) and beyond its attached end (no end
 * taper); beside its outer edge the interstate's roadside reaches the point while that edge is still on the interstate's
 * pavement, the ramp's once it has left it. An overpass's span (RoadDesign.Span#over) holds no ground: the road under it
 * keeps its own finish and the deck's cells stand over it (Column#deck); under the deck's ends the approach embankment
 * runs on as a spill-through slope (#SPILL).
 */
public final class RoadWorks {
    public static final double NATURAL_TOLERANCE = 0.03, MAX_HALF_WIDTH = 48, BLEND = 4, END_SLOPE = 0.25;
    /** Lateral reach of the works from the centreline (index margin), m. */
    public static final double REACH = MAX_HALF_WIDTH + 2;
    /** Half width the forest and the spawn keep clear of (the engineered zone of a normal cut or fill), m. */
    public static final double CLEAR = 40;
    /**
     * Turf edge to the natural surface (#toNatural): blend height band, the band kept off the pavement, and how far in
     * from the natural ground the blend reaches (#joint, 2026-10-25), m.
     */
    public static final double TO_NATURAL = 1.0, EDGE_KEEP = 1.5, EDGE_RAMP = 3.0, JOIN = 3.5;
    /**
     * How far beyond the pavement's bevels a turf column's square must stay to carry plants (a plant's cross fills its
     * square and is offset up to 0.25 m), m (#bare).
     */
    public static final double PLANT_CLEAR = 0.3;
    /** The planned ground the roadside's rock cut reads (#RoadWorks(List, Water, TownWorks, Driveway, Ground)), or null. */
    private final Ground ground;
    /**
     * Solid ground kept under the works' finished surface (formation, cut and fill slopes) against the plan's caves, the
     * carvers and the springs (RoadWorksRef#rock, 2026-10-19), m: the foothills have no hard stable floor, so a cut opened
     * cave mouths and aquifer pockets in its face; as deep as under a town (TownWorks#ROCK).
     */
    public static final double ROCK = 8;
    /** TURF column data flag: no plants (#bare). */
    public static final int BARE = 1 << 17;
    /**
     * PAVED / TURF column data flag: the road's finish over a box culvert's body (group D); the column's bottom is the
     * culvert's bed block less one, the chunk pass lays the culvert under the finish.
     */
    public static final int CULVERT_BODY = 1 << 20;
    /** PAVED column data flag: a bore's pavement (Tunnel V1): the bore is carved above it (TunnelWorks#cells), not the sky. */
    public static final int TUNNEL_PAVED = 1 << 21;
    /**
     * PAVED / TURF column data flag: a tunnel's works stand on this finish (TunnelWorks.ON_GROUND: retaining walls,
     * lead-ins, a portal's front): TunnelWorks#cells from the finish's top up to roof, on an approach (roof MAX_VALUE) up to
     * the works' top and the sky cleared above. The ground runs on under the concrete (Tunnel V1, 2026-10-16).
     */
    public static final int TUNNEL_OVER = 1 << 22;
    /**
     * TURF data: a rock cut's face (CrossSection#rockCut, 2026-10-19): the column's corners spread more than ROCK_SPREAD in
     * a cut, steeper than graded turf can draw; bare stone up to y (its mean finished height), cleared above, no plants.
     * Its corners read as a full block (#turfTop: y + 1).
     */
    public static final int ROCK_CUT = 1 << 23;
    /** The corner spread over which a cut column is a rock face (#ROCK_CUT), m: steeper than about 1:1. */
    public static final double ROCK_SPREAD = 1.5;
    /** CULVERT column data: the square reaches the channel, a wall, the roofed channel; keeps the natural ground (Culvert.Cell). */
    public static final int C_CHANNEL = 1, C_WALL = 2, C_ROOF = 4, C_KEEP = 8;
    /**
     * C3.3b junction pads (Junction): how far beyond a pad's outline its roadside reaches, blended into the roads' works
     * from PAD_KEEP (full) to PAD_SIDE (none), m.
     */
    public static final double PAD_SIDE = 12, PAD_KEEP = 2;
    /**
     * C3.3b group 2: an overpass's spill-through slope under its span's ends (#spill): 1:2 down along the road from the
     * deck's depth under the end's formation; beside the deck that drop fades out over SPILL_FADE, m.
     */
    public static final double SPILL = 0.5, SPILL_FADE = 4;
    /**
     * C3.3b group 2: beside an attached ramp's outer edge the host's roadside turns into the ramp's over this much of the
     * ramp's stations from where its edge leaves the host's pavement (#Attached sX), m.
     */
    public static final double ROADSIDE_TURN = 40;
    /**
     * C3.3b group 3: beyond an open end that another road goes on from (Q's end and a system interchange's ramps: their
     * roadsides differ) the road's roadside (not its formation) runs on fading out over this much, combined with the
     * other's (the highest fill, the deepest cut), so neither ends in a step at the joint, m. Not between two pieces of one
     * road (their roadsides are one).
     */
    public static final double OPEN_FADE = 40;
    /**
     * C3.3b group 2 (#RampMarks): the host's edge line stops where a ramp's edge line leaving it is this far off (centre
     * to centre: their half widths, RoadMesh.LINE / 2 and RoadMesh.DOT_WIDTH / 2, and a gap), m.
     */
    public static final double LINE_CLEAR = 0.3;

    /** The natural ground: the planned surface Y at a point (the density's ground before the works). */
    public interface Ground {
        double at(double x, double z);

        /**
         * Whether the density's hard stable floor (PlanStabilityDensity, "hard": the plains) keeps block column (x, z)
         * solid up to its exact planned height (#naturalTop).
         */
        default boolean hardFloor(int x, int z) { return false; }

        /** The Terrain V2 plan as the works' ground: its planned surface and its hard stable floor. */
        static Ground of(TerrainPlanSurface plan) {
            return new Ground() {
                @Override public double at(double x, double z) { return plan.heightAt(x, z); }
                @Override public boolean hardFloor(int x, int z) { return TerrainPlanSurface.hardDepth(plan.stableDepth(x, z)) > 0; }
            };
        }
    }

    /** Planned water (river, lake, sea) at a point; marsh is not water here (the road crosses it on an embankment). */
    public interface Water {
        boolean wet(double x, double z);

        Water NONE = (x, z) -> false;
    }

    /** Where a point lies on a road: the design, its station and lateral offset (stations -END .. length + END). */
    public record Hit(RoadDesign road, double s, double u) {
        public double v() { return Math.abs(u); }
        /** Station clamped to the route. */
        public double sc() { return Math.max(0, Math.min(road.length, s)); }
        /** Distance beyond the route's ends (0 on the route). */
        public double beyond() { return s < 0 ? -s : s > road.length ? s - road.length : 0; }
    }

    private final List<RoadDesign> roads;
    private final Water water;
    /** The framework towns' works (Town Framework V1): their footprint and slopes join the roads' (#townColumn). */
    private final TownWorks towns;
    /** The forest home's driveway (County road G3): its columns where no road or town decides (#column). */
    private final Driveway drive;
    /** The county road the driveway joins (RoadCounty.ID), or null. */
    private final RoadDesign driveRoad;
    private final Map<Long, RoadDesign[]> byChunk = new HashMap<>();
    private final Map<Long, Culvert[]> culvertsByChunk = new HashMap<>();
    private final List<Culvert> culverts = new ArrayList<>();
    private final List<Junction> junctions = new ArrayList<>();
    private final Map<Long, Junction[]> padsByChunk = new HashMap<>(), sightByChunk = new HashMap<>();

    /**
     * C3.3b group 2: an interchange ramp attached to its interstate (RoadDesign.Attach): the interstate's design (by id; of
     * a split one the piece holding the ramp's nose), the ramp's lateral sign away from it, and the ramp's station where its
     * outer edge leaves the interstate's paved band (between it and the attached end the edge lies on that pavement).
     */
    record Attached(RoadDesign host, int outward, double sX) {}

    private final Map<RoadDesign, Attached> attached = new HashMap<>();
    /** The open ends whose roadside fades out beyond them (#OPEN_FADE): {start, end} by road. */
    private final Map<RoadDesign, boolean[]> fades = new HashMap<>();

    /**
     * C3.3b group 2 (docs/worldgen/federal_interchanges_c3_3b.md 8): where an attached ramp meets its host's markings
     * (RoadMesh), the stations on the host on the side the ramp lies (side; outward: the ramp's lateral sign away from the
     * host). end: where the ramp's lane edge away from the host meets the host's edge line (an exit's taper start, an
     * entrance's taper end); paint: the painted nose (the ramp's lane edge toward the host on that line); nose: the
     * physical nose (the ramp's paved edge on the host's). Along the ramp the host's edge line is dotted over dot0 .. dot1
     * (an exit's taper once the ramp's own edge line is clear of it, an entrance's acceleration lane) and absent over
     * gap0 .. gap1 (where the ramp's edge line takes over: an exit's taper start, an entrance's merge taper).
     */
    public record RampMarks(RoadDesign ramp, RoadDesign host, int side, int outward, boolean exit, double end, double paint, double nose,
                            double dot0, double dot1, double gap0, double gap1) {}

    /** The ramps' marks (#RampMarks) by ramp and by host. */
    private final Map<RoadDesign, List<RampMarks>> marks = new HashMap<>();
    /** Per thread: the columns before their corners agree (#separate), direct-mapped. */
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);
    /** Per thread: which corner points the works leave natural (#natural), direct-mapped. */
    private final ThreadLocal<NaturalCache> naturalCache = ThreadLocal.withInitial(NaturalCache::new);

    public RoadWorks(List<RoadDesign> roads, Water water) { this(roads, water, TownWorks.EMPTY); }

    public RoadWorks(List<RoadDesign> roads, Water water, TownWorks towns) { this(roads, water, towns, Driveway.EMPTY); }

    public RoadWorks(List<RoadDesign> roads, Water water, TownWorks towns, Driveway drive) { this(roads, water, towns, drive, null); }

    /**
     * ground: the planned ground, for the height a deep cut's roadside must reach (CrossSection#roadside, the rock cut);
     * null (the client's copy): the ground at the point itself.
     */
    public RoadWorks(List<RoadDesign> roads, Water water, TownWorks towns, Driveway drive, Ground ground) {
        this.ground = ground;
        this.roads = List.copyOf(roads);
        this.water = water;
        this.towns = towns;
        // the driveway lies across its joint as the county road's pavement at its start (Driveway#joined)
        this.driveRoad = drive.isEmpty() ? null : road(RoadCounty.ID);
        this.drive = drive.joined(driveRoad);
        for (RoadDesign d : this.roads) {
            Attached a = d.attach == null ? null : attach(d);
            if (a == null) continue;
            attached.put(d, a);
            RampMarks m = marks(d, a);
            if (m == null) continue;
            marks.computeIfAbsent(d, k -> new ArrayList<>(1)).add(m);
            marks.computeIfAbsent(a.host(), k -> new ArrayList<>(4)).add(m);
        }
        // the open ends another road goes on from (not a piece of the same road)
        for (RoadDesign d : this.roads)
            for (int e = 0; e < 2; e++) {
                if (!(e == 0 ? d.openStart : d.openEnd)) continue;
                double[] p = d.point(e == 0 ? 0 : d.length, 0);
                boolean piece = false;
                for (RoadDesign o : this.roads) {
                    if (o == d || !RoadJunctions.base(o.id).equals(RoadJunctions.base(d.id))) continue;
                    for (double t : new double[]{0, o.length}) {
                        double[] q = o.point(t, 0);
                        if (Math.hypot(q[0] - p[0], q[1] - p[1]) < 1) piece = true;
                    }
                }
                if (!piece) fades.computeIfAbsent(d, k -> new boolean[2])[e] = true;
            }
        // the T junctions (C3.3b): each minor end that names its host; indexed by the chunks its pad and the pad's
        // roadside reach, and apart by those its sight triangles reach
        Map<Long, List<Junction>> pIndex = new HashMap<>(), sIndex = new HashMap<>();
        for (RoadDesign d : this.roads)
            for (boolean atEnd : new boolean[]{false, true}) {
                String hid = atEnd ? d.hostEnd : d.hostStart;
                RoadDesign host = hid == null ? null : road(hid);
                Junction j = host == null ? null : Junction.of(host, d, atEnd);
                if (j == null) continue;
                junctions.add(j);
                double[] b = j.bounds();
                double r = PAD_SIDE + 2;
                index(pIndex, j, new double[]{b[0] - r, b[1] - r, b[2] + r, b[3] + r});
                index(sIndex, j, j.sightBounds());
            }
        pIndex.forEach((k, l) -> padsByChunk.put(k, l.toArray(new Junction[0])));
        sIndex.forEach((k, l) -> sightByChunk.put(k, l.toArray(new Junction[0])));
        Map<Long, List<RoadDesign>> index = new HashMap<>();
        Map<Long, List<Culvert>> cIndex = new HashMap<>();
        for (RoadDesign d : this.roads)
            for (Culvert c : d.culverts) {
                culverts.add(c);
                double[] b = c.bounds(Culvert.FILL_REACH + 2);
                for (int cx = Math.floorDiv((int) Math.floor(b[0]), 16); cx <= Math.floorDiv((int) Math.floor(b[2]), 16); cx++)
                    for (int cz = Math.floorDiv((int) Math.floor(b[1]), 16); cz <= Math.floorDiv((int) Math.floor(b[3]), 16); cz++)
                        cIndex.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(1)).add(c);
            }
        cIndex.forEach((k, l) -> culvertsByChunk.put(k, l.toArray(new Culvert[0])));
        int reach = (int) Math.ceil(REACH + CLEAR + 16);
        for (RoadDesign d : this.roads) {
            boolean[] fd = fades.get(d);
            double before = fd != null && fd[0] ? Math.max(RoadDesign.END, OPEN_FADE) : RoadDesign.END, after = fd != null && fd[1] ? Math.max(RoadDesign.END, OPEN_FADE) : RoadDesign.END;
            for (double s = -before; s <= d.length + after; s += 8) {
                double[] p = d.point(s, 0);
                int cx0 = Math.floorDiv((int) Math.floor(p[0]) - reach, 16), cx1 = Math.floorDiv((int) Math.floor(p[0]) + reach, 16);
                int cz0 = Math.floorDiv((int) Math.floor(p[1]) - reach, 16), cz1 = Math.floorDiv((int) Math.floor(p[1]) + reach, 16);
                for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
                    List<RoadDesign> l = index.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(1));
                    if (!l.contains(d)) l.add(d);
                }
            }
        }
        index.forEach((k, l) -> byChunk.put(k, l.toArray(new RoadDesign[0])));
    }

    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** The attachment of ramp d to its host (#Attached), or null when the host is not in the network. */
    private Attached attach(RoadDesign d) {
        RoadDesign.Attach at = d.attach;
        double sn = Math.max(0, Math.min(d.length, at.nose()));
        double[] pn = d.point(sn, 0);
        RoadDesign host = null;
        for (RoadDesign q : roads) {
            if (q == d || !(q.id.equals(at.host()) || q.id.startsWith(at.host() + "~"))) continue;
            double[] l = q.local(pn[0], pn[1], REACH);
            if (l != null && l[0] >= 0 && l[0] <= q.length) { host = q; break; }
        }
        if (host == null) return null;
        double[] pa = d.point(sn, 1), la = host.local(pn[0], pn[1], REACH), lb = host.local(pa[0], pa[1], REACH);
        if (la == null || lb == null) return null;
        int outward = Math.abs(lb[1]) > Math.abs(la[1]) ? 1 : -1;
        double edge = host.section.edge, sX = sn;
        for (int i = 0; i <= (int) Math.ceil(sn * 2); i++) {
            double s = at.atEnd() ? d.length - i * 0.5 : i * 0.5;
            if (at.atEnd() ? s < sn : s > sn) break;
            double[] e = d.point(s, outward * d.section.edge), l = host.local(e[0], e[1], REACH);
            // off the host's pavement already at the attached end (a system interchange's direct ramp beside its left-turn
            // ramp, C3.3b group 3): its own roadside all along
            if (l != null && Math.abs(l[1]) >= edge) { sX = i > 0 ? s : at.atEnd() ? d.length + ROADSIDE_TURN : -ROADSIDE_TURN; break; }
        }
        return new Attached(host, outward, sX);
    }

    /**
     * The marks of an attached ramp on its host (#RampMarks), or null where a point of them is off the host. The host's
     * line is cleared once the ramp's edge line is LINE_CLEAR beyond it; the merge taper starts where the entrance's lane
     * edge toward the host leaves the host's edge line.
     */
    private static RampMarks marks(RoadDesign d, Attached a) {
        RoadDesign h = a.host();
        int out = a.outward();
        boolean exit = !d.attach.atEnd();
        double lane = d.section.lanesOut, line = h.section.lanesOut;
        double[] e = hostAt(h, d, exit ? 0 : d.length, out * lane), pa = hostAt(h, d, d.attach.paint(), -out * lane),
                no = hostAt(h, d, d.attach.nose(), -out * d.section.edge);
        if (e == null || pa == null || no == null) return null;
        int side = no[1] > 0 ? 1 : -1;
        // on a one-way host (a system interchange's left-turn ramp, C3.3b group 3) its edge line runs on unbroken: the
        // direct ramp splits off / merges in beside it as a second lane
        if (h.section.oneWay) return new RampMarks(d, h, side, out, exit, e[0], pa[0], no[0], e[0], e[0], e[0], e[0]);
        double dot0, dot1, gap0, gap1;
        if (exit) {
            // the dotted line from where the ramp's edge line is clear of the host's
            double c = Double.NaN;
            for (double s = 0; s <= d.attach.paint(); s += 0.5) {
                double[] q = hostAt(h, d, s, out * lane);
                if (q != null && Math.abs(q[1]) >= line + LINE_CLEAR) { c = q[0]; break; }
            }
            if (Double.isNaN(c)) return null;
            gap0 = e[0];
            gap1 = dot0 = c;
            dot1 = pa[0];
        } else {
            // the dotted line to where the merge taper starts
            double c = Double.NaN;
            for (double s = d.attach.paint(); s <= d.length; s += 0.5) {
                double[] q = hostAt(h, d, s, -out * lane);
                if (q == null || Math.abs(q[1]) < line - 0.05) break;
                c = q[0];
            }
            if (Double.isNaN(c)) return null;
            dot0 = pa[0];
            dot1 = gap0 = c;
            gap1 = e[0];
        }
        return new RampMarks(d, h, side, out, exit, e[0], pa[0], no[0], Math.min(dot0, dot1), Math.max(dot0, dot1), Math.min(gap0, gap1),
                Math.max(gap0, gap1));
    }

    /**
     * Ramp d's point (s, u) in host h's (station, lateral), or null off it (half a metre beyond the host's ends allowed: a direct
     * ramp's split starts at its left-turn ramp's start, C3.3b group 3).
     */
    private static double[] hostAt(RoadDesign h, RoadDesign d, double s, double u) {
        double[] p = d.point(s, u), l = h.local(p[0], p[1], REACH);
        return l == null || l[0] < -0.5 || l[0] > h.length + 0.5 ? null : l;
    }

    /** The ramp marks a road takes part in (C3.3b group 2, #RampMarks): a ramp's own, a host's ramps'; empty: none. */
    public List<RampMarks> marksOf(RoadDesign d) {
        List<RampMarks> l = marks.isEmpty() ? null : marks.get(d);
        return l == null ? List.of() : l;
    }

    /** The interstate an attached ramp lies on (C3.3b group 2), or null (not a ramp, or its host is not built). */
    public RoadDesign attachedHost(RoadDesign d) {
        Attached a = attached.isEmpty() ? null : attached.get(d);
        return a == null ? null : a.host();
    }

    /**
     * Whether a hit gives way: beyond an open end (C3.3b group 3); on an attached ramp (group 2) beyond its attached end
     * (it goes on as its host's pavement, no end taper) and on its host's paved band (the host's pavement is the ramp's
     * there).
     */
    private boolean yields(Hit h, double x, double z) {
        RoadDesign d = h.road();
        // beyond an open end (C3.3b group 3) the road runs on as another
        if (d.openStart && h.s() < 0 || d.openEnd && h.s() > d.length) return true;
        Attached a = attached.isEmpty() ? null : attached.get(d);
        if (a == null) return false;
        if (d.attach.atEnd() ? h.s() > d.length : h.s() < 0) return true;
        return onBand(a.host(), x, z);
    }

    /**
     * The weight of a hit's roadside beyond a fading open end (#OPEN_FADE): 1 at the end to 0 OPEN_FADE beyond, 0 on its
     * formation; NaN where the hit is not beyond such an end.
     */
    private double fade(Hit h) {
        boolean[] f = fades.isEmpty() ? null : fades.get(h.road());
        if (f == null) return Double.NaN;
        RoadDesign d = h.road();
        double t = f[0] && h.s() < 0 ? -h.s() : f[1] && h.s() > d.length ? h.s() - d.length : Double.NaN;
        if (Double.isNaN(t)) return Double.NaN;
        if (h.v() <= d.section.edge + 0.5) return 0;
        return 1 - CrossSection.smooth(t / OPEN_FADE);
    }

    /** Whether (x, z) lies on a road's paved band, on its route. */
    private static boolean onBand(RoadDesign d, double x, double z) {
        double[] l = d.local(x, z, d.section.edge + 1);
        return l != null && l[0] >= 0 && l[0] <= d.length && d.section.paved(Math.abs(l[1]));
    }

    /** Whether an attached ramp's works can reach the chunk of (x, z). */
    private boolean rampsNear(double x, double z) {
        if (attached.isEmpty()) return false;
        RoadDesign[] near = near(x, z);
        if (near != null) for (RoadDesign d : near) if (attached.containsKey(d)) return true;
        return false;
    }

    /** On an overpass's span (C3.3b group 2, RoadDesign.Span#over): the road is in the air there, the ground is another's. */
    public static boolean elevated(Hit h) {
        if (h.beyond() > 0 || h.road().spans.isEmpty()) return false;
        RoadDesign.Span sp = h.road().span(h.sc());
        return sp != null && sp.over();
    }

    /**
     * The overpass deck over a point (C3.3b group 2): the hit of a road whose OVERPASS span holds the point within its deck
     * and parapets (the smallest lateral offset), or null.
     */
    public Hit overHit(double x, double z) {
        RoadDesign[] near = near(x, z);
        if (near == null) return null;
        Hit best = null;
        for (RoadDesign d : near) {
            if (d.spans.isEmpty()) continue;
            double reach = d.section.edge + BridgeMesh.CELL_OUT;
            double[] l = d.local(x, z, reach + 1);
            if (l == null) continue;
            Hit h = new Hit(d, l[0], l[1]);
            if (h.v() > reach || !elevated(h)) continue;
            if (best == null || h.v() < best.v()) best = h;
        }
        return best;
    }

    public List<RoadDesign> roads() { return roads; }

    private volatile TunnelFixtures.Index parts;

    /**
     * The tunnels' equipment parts and the road signs (C3.3b, RoadSigns) as fixtures (TunnelFixtures, by column), built on
     * first use; the signs stand on the turf the world's ground draws (one ground per network: its plan's).
     */
    public TunnelFixtures.Index parts(Ground ground) {
        TunnelFixtures.Index p = parts;
        if (p == null) synchronized (this) {
            if ((p = parts) == null) parts = p = TunnelFixtures.Index.of(roads, RoadSigns.all(this, ground));
        }
        return p;
    }

    /**
     * The graded turf's top as drawn at a point: its column's four fitted corners (#column: quantised, blended toward the
     * natural surface near the daylight line), on the triangle of the north-west to south-east diagonal the point lies in
     * (as RoadSurfaceQuery reads the block); NaN where the column is not turf.
     */
    public double turfTop(double x, double z, Ground ground) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        Column c = column(bx, bz, ground);
        if (c.kind() != Kind.TURF) return Double.NaN;
        int d = c.data();
        double fx = x - bx, fz = z - bz, base = c.y() + ((d >> 16 & 1) != 0 ? 0.5 : 0);
        double nw = (d & 15) / 8.0, ne = (d >> 4 & 15) / 8.0, sw = (d >> 8 & 15) / 8.0, se = (d >> 12 & 15) / 8.0;
        return fx >= fz ? base + nw + (ne - nw) * fx + (se - ne) * fz : base + nw + (se - sw) * fx + (sw - nw) * fz;
    }

    public boolean isEmpty() { return roads.isEmpty(); }

    /** The T junctions (C3.3b). */
    public List<Junction> junctions() { return junctions; }

    /** The junctions a road takes part in (as the host or the minor). */
    public List<Junction> junctionsOf(RoadDesign d) {
        List<Junction> out = new ArrayList<>(0);
        for (Junction j : junctions) if (j.host == d || j.minor == d) out.add(j);
        return out;
    }

    /** The junctions whose pad or the pad's roadside can reach the chunk of (x, z), or null (shared). */
    public Junction[] padsNear(double x, double z) {
        return padsByChunk.isEmpty() ? null : padsByChunk.get(key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
    }

    private static void index(Map<Long, List<Junction>> index, Junction j, double[] q) {
        for (int cx = Math.floorDiv((int) Math.floor(q[0]), 16); cx <= Math.floorDiv((int) Math.floor(q[2]), 16); cx++)
            for (int cz = Math.floorDiv((int) Math.floor(q[1]), 16); cz <= Math.floorDiv((int) Math.floor(q[3]), 16); cz++)
                index.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(1)).add(j);
    }

    /** The pad holding (x, z), or null. */
    public Junction pad(double x, double z) {
        Junction[] ps = padsNear(x, z);
        if (ps != null) for (Junction j : ps) if (j.contains(x, z)) return j;
        return null;
    }

    /** Distance from (x, z) to the nearest pad (0 inside one), or +infinity beyond reach. */
    double padDistance(double x, double z) {
        Junction[] ps = padsNear(x, z);
        double best = Double.POSITIVE_INFINITY;
        if (ps != null)
            for (Junction j : ps) {
                if (!j.near(x, z, PAD_SIDE)) continue;
                if (j.contains(x, z)) return 0;
                best = Math.min(best, j.nearest(x, z)[0]);
            }
        return best;
    }

    /** Whether any road's works or clearance can reach into this chunk (a cheap test before any per-column work). */
    public boolean touches(int chunkX, int chunkZ) {
        return byChunk.containsKey(key(chunkX, chunkZ)) || towns.touches(chunkX, chunkZ) || drive.touches(chunkX, chunkZ);
    }

    /** The framework towns' works (TownWorks.EMPTY without towns). */
    public TownWorks towns() { return towns; }

    /** The forest home's driveway (Driveway.EMPTY without one). */
    public Driveway drive() { return drive; }

    public RoadDesign road(String id) {
        for (RoadDesign d : roads) if (d.id.equals(id)) return d;
        return null;
    }

    private RoadDesign[] near(double x, double z) {
        return byChunk.get(key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
    }

    /** The roads whose works can reach the chunk of (x, z), or null (shared: do not modify). */
    public RoadDesign[] roadsNear(double x, double z) { return near(x, z); }

    // ------------------------------------------------------------------ box culverts (group D)

    /** Every box culvert of the network. */
    public List<Culvert> culverts() { return culverts; }

    /** The box culverts whose works (their trough and the filled stream around it) can reach the chunk of (x, z), or null. */
    public Culvert[] culvertsNear(double x, double z) {
        return culvertsByChunk.get(key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
    }

    /** Whether a wet point is filled by an embankment over a box culvert (Culvert#fills): the stream goes through the culvert. */
    public boolean filled(double x, double z) {
        Culvert[] cs = culvertsNear(x, z);
        if (cs != null) for (Culvert c : cs) if (c.fills(x, z)) return true;
        return false;
    }

    /** The box culvert cell of block column (x, z), or null. */
    public Culvert.Cell culvertCell(int x, int z) {
        Culvert[] cs = culvertsNear(x, z);
        if (cs == null) return null;
        for (Culvert c : cs) {
            Culvert.Cell cell = c.cell(x, z);
            if (cell != null) return cell;
        }
        return null;
    }

    /**
     * The top of a wall's coping at a point: a box culvert's concrete, or a bridge's wing wall (BridgeMesh#wing); NaN
     * elsewhere. h is the point's hit when known; the wing is looked up only where wings is set.
     */
    private double coping(double x, double z, Hit h, boolean wings) {
        double[] c = culvertSolid(x, z);
        if (c != null) return c[1];
        if (!wings) return Double.NaN;
        return BridgeMesh.wing(h != null ? h : hit(x, z, REACH));
    }

    /** A box culvert's concrete at a point: {bottom, top} (Culvert#solid), or null. */
    public double[] culvertSolid(double x, double z) {
        Culvert[] cs = culvertsNear(x, z);
        if (cs == null) return null;
        for (Culvert c : cs) {
            double[] r = c.solid(x, z);
            if (r != null) return r;
        }
        return null;
    }

    /** On the road's formation (on the route, within its paved edge plus the shoulder rounding). */
    static boolean onFormation(Hit h) { return h.beyond() == 0 && h.v() <= h.road().section.edge + 0.5; }

    /**
     * The road a point lies on within maxDist of a centreline, or null: a road whose formation holds the point (the
     * smallest lateral offset of those), else the smallest lateral offset; an overpass's span (#elevated) only where no
     * other road reaches the point, an attached ramp not where it gives way (#yields).
     */
    public Hit hit(double x, double z, double maxDist) {
        RoadDesign[] near = near(x, z);
        if (near == null) return null;
        Hit best = null;
        int bestRank = -1;
        for (RoadDesign d : near) {
            double[] l = d.local(x, z, maxDist);
            if (l == null) continue;
            Hit h = new Hit(d, l[0], l[1]);
            if (yields(h, x, z)) continue;
            int rank = elevated(h) ? 0 : onFormation(h) ? 2 : 1;
            if (best == null || rank > bestRank || rank == bestRank && h.v() < best.v()) { best = h; bestRank = rank; }
        }
        return best;
    }

    /**
     * Finished surface at (x, z) over the natural ground g there; NaN where the works leave the ground natural. A point
     * on a formation takes that road's; elsewhere the works of every road reaching it combine (the class comment).
     */
    public double surface(double x, double z, double g) { return surface(x, z, g, false); }

    /**
     * The density's height at (x, z) over the planned ground g: #surface, but in front of a tunnel's headwall and walls the
     * rock is kept over the band the chunk pass clears (TunnelWorks#surface), so the noise's interpolation never slopes
     * the ground behind them down to the road.
     */
    public double densitySurface(double x, double z, double g) { return surface(x, z, g, true); }

    private double surface(double x, double z, double g, boolean density) {
        if (towns.isEmpty()) return roadSurface(x, z, g, density);
        // a town's footprint is the town's (a road's formation excepted); its slopes combine with the roads' works
        TownWorks.Spot sp = towns.spot(x, z);
        if (sp != null) return onRoad(x, z) ? roadSurface(x, z, g, density) : sp.h();
        double r = roadSurface(x, z, g, density), t = towns.surface(x, z, g);
        if (Double.isNaN(t) || water.wet(x, z) && !filled(x, z)) return r;
        if (Double.isNaN(r)) return t;
        if (onRoad(x, z)) return r;
        return g + Math.max(0, Math.max(r - g, t - g)) - Math.max(0, Math.max(g - r, g - t));
    }

    /** Whether a point is on a road's formation on the ground or on a junction pad: there the road decides, not a town. */
    private boolean onRoad(double x, double z) {
        if (pad(x, z) != null) return true;
        RoadDesign[] near = near(x, z);
        if (near == null) return false;
        for (RoadDesign d : near) {
            double[] l = d.local(x, z, REACH);
            if (l == null) continue;
            Hit h = new Hit(d, l[0], l[1]);
            if (!elevated(h) && onFormation(h)) return true;
        }
        return false;
    }

    /** The roads' finished surface alone (#surface without the towns). */
    private double roadSurface(double x, double z, double g, boolean density) {
        RoadDesign[] near = near(x, z);
        if (near == null) return Double.NaN;
        Junction[] pads = padsNear(x, z);
        if (pads != null) {
            Junction pj = null;
            double pd = Double.POSITIVE_INFINITY;
            double[] pn = null;
            for (Junction j : pads) {
                if (!j.near(x, z, PAD_SIDE)) continue;
                double[] nb = j.nearest(x, z);
                double d = j.contains(x, z) ? 0 : nb[0];
                if (d < pd) { pj = j; pd = d; pn = nb; }
            }
            if (pj != null && pd < PAD_SIDE) return padSurface(pj, pd, pn, near, x, z, g, density);
        }
        if (near.length == 1) {
            double[] l = near[0].local(x, z, REACH);
            if (l == null) return Double.NaN;
            Hit h = new Hit(near[0], l[0], l[1]);
            double fw = fade(h);
            if (!Double.isNaN(fw)) {
                if (fw <= 0) return Double.NaN;
                double f = surface(h, x, z, g, density);
                return Double.isNaN(f) || Math.abs(f - g) * fw < NATURAL_TOLERANCE ? Double.NaN : g + (f - g) * fw;
            }
            return yields(h, x, z) ? Double.NaN : surface(h, x, z, g, density);
        }
        return roads(near, x, z, g, density, null);
    }

    /**
     * The roads' finished surface at a point (no pad): the formation holding it (the smallest lateral offset; not an
     * overpass's span, not where an attached ramp gives way), else the roadside works of the roads reaching it combined, the
     * highest fill and the deepest cut; an overpass's spill-through slope (#spill) fills over them. on[0] is set when a
     * formation decided (on may be null).
     */
    private double roads(RoadDesign[] near, double x, double z, double g, boolean density, boolean[] on) {
        Hit[] hs = new Hit[near.length];
        int n = 0;
        Hit form = null;
        double fUp = 0, fDown = 0;
        boolean faded = false;
        for (RoadDesign d : near) {
            double[] l = d.local(x, z, REACH);
            if (l == null) continue;
            Hit h = new Hit(d, l[0], l[1]);
            double fw = fade(h);
            if (!Double.isNaN(fw)) {
                // beyond an open end another road goes on from: its roadside fading out
                double f = fw <= 0 ? Double.NaN : surface(h, x, z, g, density);
                if (!Double.isNaN(f) && Math.abs(f - g) * fw >= NATURAL_TOLERANCE) {
                    faded = true;
                    fUp = Math.max(fUp, (f - g) * fw);
                    fDown = Math.max(fDown, (g - f) * fw);
                }
                continue;
            }
            if (yields(h, x, z)) continue;
            hs[n++] = h;
            if (!elevated(h) && onFormation(h) && (form == null || h.v() < form.v())) form = h;
        }
        if (form != null) {
            if (on != null) on[0] = true;
            return surface(form, x, z, g, density);
        }
        // beside an attached ramp's outer edge one roadside reaches the point: its host's while that edge is still on the
        // host's pavement, the ramp's once it has left it (the host's slopes would run under the ramp), turning over
        // ROADSIDE_TURN of the ramp's stations (the host's slopes end at its reach, the ramp's run on)
        boolean[] drop = null;
        double pair = Double.NaN;
        if (!attached.isEmpty())
            for (int i = 0; i < n && Double.isNaN(pair); i++) {
                Attached a = attached.get(hs[i].road());
                if (a == null || (hs[i].u() < 0 ? -1 : 1) != a.outward()) continue;
                double into = hs[i].road().attach.atEnd() ? a.sX() - hs[i].s() : hs[i].s() - a.sX();
                double w = CrossSection.smooth(into / ROADSIDE_TURN), fh = g;
                if (drop == null) drop = new boolean[n];
                drop[i] = true;
                for (int j = 0; j < n; j++)
                    if (hs[j].road() == a.host()) {
                        drop[j] = true;
                        double f = surface(hs[j], x, z, g, density);
                        if (!Double.isNaN(f)) fh = f;
                    }
                double fr = w > 0 ? surface(hs[i], x, z, g, density) : Double.NaN;
                pair = fh + ((Double.isNaN(fr) ? g : fr) - fh) * w;
            }
        double up = fUp, down = fDown, spill = Double.NaN;
        boolean any = faded;
        if (!Double.isNaN(pair) && Math.abs(pair - g) >= NATURAL_TOLERANCE) {
            any = true;
            up = Math.max(up, pair - g);
            down = Math.max(down, g - pair);
        }
        for (int i = 0; i < n; i++) {
            if (drop != null && drop[i]) continue;
            double f = surface(hs[i], x, z, g, density);
            if (Double.isNaN(f)) continue;
            if (elevated(hs[i])) {
                if (!(f <= spill)) spill = f;
                continue;
            }
            any = true;
            up = Math.max(up, f - g);
            down = Math.max(down, g - f);
        }
        double f = any ? g + up - down : Double.NaN;
        return Double.isNaN(spill) ? f : Double.isNaN(f) ? spill : Math.max(f, spill);
    }

    /**
     * The finished surface near a junction pad (C3.3b): on the pad its pavement less TURF_DROP; else a road's formation
     * (the host's and the minor's own paved band and shoulders); beyond its outline (distance pd, nearest point pn) the pad's
     * own roadside (CrossSection.R2#roadside from the outline's height), fully within PAD_KEEP, blended into the roads'
     * works out to PAD_SIDE (the host's ditch is filled across the mouth, as over a pipe).
     */
    private double padSurface(Junction j, double pd, double[] pn, RoadDesign[] near, double x, double z, double g, boolean density) {
        if (pd <= 0) return j.height(x, z) - CrossSection.TURF_DROP;
        boolean[] on = new boolean[1];
        double others = roads(near, x, z, g, density, on);
        if (on[0]) return others;
        // the pad's roadside never enters water (as the roads' own)
        if (water.wet(x, z) && !filled(x, z)) return others;
        double h0 = j.height(pn[1], pn[2]) - CrossSection.TURF_DROP;
        double side = h0 + CrossSection.R2.roadside(pd, g - h0);
        double base = Double.isNaN(others) ? g : others;
        double w = pd <= PAD_KEEP ? 1 : CrossSection.smooth(1 - (pd - PAD_KEEP) / (PAD_SIDE - PAD_KEEP));
        double f = base + (side - base) * w;
        if (pd > PAD_KEEP && Math.abs(f - g) < NATURAL_TOLERANCE) return Double.NaN;
        return f;
    }

    /** The finished surface of a point already located on a road (h at world x, z). */
    public double surface(Hit h, double x, double z, double g) { return surface(h, x, z, g, false); }

    private double surface(Hit h, double x, double z, double g, boolean density) {
        RoadDesign.Tunnel tn = TunnelWorks.near(h);
        double open = open(h, x, z, g);
        return tn == null ? open : TunnelWorks.surface(h, tn, g, open, density);
    }

    /** The finished surface of the open road (no tunnel) at a point already located on it. */
    private double open(Hit h, double x, double z, double g) {
        RoadDesign d = h.road();
        CrossSection sec = d.section;
        double v = h.v(), sc = h.sc(), beyond = h.beyond();
        if (v > MAX_HALF_WIDTH) return Double.NaN;
        // under a bridge: the natural valley; under an overpass the spill-through slopes and the road it spans
        RoadDesign.Span sp = beyond == 0 ? d.span(sc) : null;
        if (sp != null) return sp.over() ? spill(h, sp, g) : Double.NaN;
        double design;
        if (v <= sec.edge) design = d.formationY(sc, h.u());
        else {
            double h0 = d.formationY(sc, Math.copySign(sec.edge, h.u()));
            // the height the slope must reach: the ground where the works start blending into it (the rock cut)
            double need = g - h0;
            if (ground != null) {
                double[] e = d.point(h.s(), Math.copySign(MAX_HALF_WIDTH - BLEND, h.u()));
                need = ground.at(e[0], e[1]) - h0;
            }
            design = h0 + sec.roadside(v - sec.edge, g - h0, need);
        }
        if (beyond > 0) design = approach(g, design, beyond * END_SLOPE);
        if (v > MAX_HALF_WIDTH - BLEND) {
            double t = CrossSection.smooth((v - (MAX_HALF_WIDTH - BLEND)) / BLEND);
            design += (g - design) * t;
        }
        boolean formation = onFormation(h);
        if (!formation && Math.abs(design - g) < NATURAL_TOLERANCE) return Double.NaN;
        if (!formation && water.wet(x, z) && !filled(x, z)) return Double.NaN;
        return design;
    }

    /**
     * Under an overpass's span (C3.3b group 2) the approach embankment runs on as a spill-through slope: from the end's
     * cross-section (its formation, beside it the fill template) the span's depth lower at the abutment (beside the deck
     * that drop fades out over SPILL_FADE), then down at SPILL along the road; fill only (NaN under the ground).
     */
    private static double spill(Hit h, RoadDesign.Span sp, double g) {
        RoadDesign d = h.road();
        CrossSection sec = d.section;
        double sc = h.sc(), v = h.v();
        double sEnd = sc - sp.s0() <= sp.s1() - sc ? sp.s0() : sp.s1(), t = Math.abs(sc - sEnd);
        double f = v <= sec.edge ? d.formationY(sEnd, h.u())
                : d.formationY(sEnd, Math.copySign(sec.edge, h.u())) + sec.roadside(v - sec.edge, -1e9);
        double fp = sec.edge + BridgeMesh.PARAPET_WIDTH;
        f -= sp.depth() * (v <= fp ? 1 : CrossSection.smooth(1 - (v - fp) / SPILL_FADE)) + t * SPILL;
        if (v > MAX_HALF_WIDTH - BLEND) f += (g - f) * CrossSection.smooth((v - (MAX_HALF_WIDTH - BLEND)) / BLEND);
        return f > g + NATURAL_TOLERANCE ? f : Double.NaN;
    }

    /** From g, move towards f by no more than the drop allowed so far: the end taper (1:4 longitudinal). */
    static double approach(double g, double f, double allowed) {
        double diff = f - g;
        return g + Math.copySign(Math.max(0, Math.abs(diff) - allowed), diff);
    }

    /** The surface or the natural ground: the continuous field the turf corners are cut from. */
    public double finished(double x, double z, Ground ground) {
        double g = ground.at(x, z), f = surface(x, z, g);
        return Double.isNaN(f) ? g : f;
    }

    /**
     * The block top (top block + 1) the noise makes at block column (x, z), measured 98.8 % exact from the interpolation
     * alone on a C3.3a test save, exact with the floor: the density is 0.125 (G - y), G the planned height with the works
     * applied (RoadWorksRef#height) read at the 4 x 4 cell corners and interpolated bilinearly to the block's corner, so the
     * top block is the highest y below G; where the hard stable floor holds (Ground#hardFloor) the column is solid up to
     * its exact planned height as well.
     */
    public int naturalTop(int x, int z, Ground ground) {
        return (int) Math.ceil(densityHeight(x, z, ground) - 1e-9);
    }

    /** The height the density's surface reaches at block column (x, z) (#naturalTop). */
    double densityHeight(int x, int z, Ground ground) {
        int x0 = Math.floorDiv(x, 4) * 4, z0 = Math.floorDiv(z, 4) * 4;
        double fx = (x - x0) / 4.0, fz = (z - z0) / 4.0;
        double a = cellHeight(x0, z0, ground), b = cellHeight(x0 + 4, z0, ground), c = cellHeight(x0, z0 + 4, ground), e = cellHeight(x0 + 4, z0 + 4, ground);
        double gb = (a + (b - a) * fx) * (1 - fz) + (c + (e - c) * fx) * fz;
        return ground.hardFloor(x, z) ? Math.max(gb, cellHeight(x, z, ground)) : gb;
    }

    /**
     * The natural surface the terrain smoothing draws at corner point (x + a, z + b) of the column whose naturalTops these
     * are (SmoothRules mode B: the mean of the column tops around the point), over the natural columns there only (those
     * the works leave alone: their natural blocks are the ones that meet the turf at this point); all four when none is.
     */
    static double naturalSurface(int[] tops, int a, int b) {
        int sum = 0, n = 0, all = 0;
        for (int j = b; j <= b + 1; j++) for (int i = a; i <= a + 1; i++) {
            int t = tops[i + 3 * j];
            all += t;
            if (tops[9 + i + 3 * j] == 0) { sum += t; n++; }
        }
        return n == 0 ? all * 0.25 : (double) sum / n;
    }

    /**
     * Whether a natural column (one the works leave alone, #naturalTops) meets corner point (x + a, z + b): only there do
     * natural blocks meet the turf. A corner the works leave natural with none (the roadside crossing the planned ground
     * within NATURAL_TOLERANCE in the middle of the works: a ditch's floor or a foreslope at ground level) lies amid turf,
     * and the natural tops there (#naturalSurface: all four, the noise's whole blocks) stood up to a block over it, a lump
     * (2026-10-25): it takes the planned ground instead.
     */
    static boolean joins(int[] tops, int a, int b) {
        for (int j = b; j <= b + 1; j++) for (int i = a; i <= a + 1; i++) if (tops[9 + i + 3 * j] == 0) return true;
        return false;
    }

    /**
     * naturalTop of the 3 x 3 columns x - 1 .. x + 1, z - 1 .. z + 1 ([i + 3 * j]), the cell corners read once, then
     * whether the works reach each of them ([9 + i + 3 * j]: 1 when a corner of it is off the natural ground).
     */
    int[] naturalTops(int x, int z, Ground ground) {
        int x0 = Math.floorDiv(x - 1, 4) * 4, z0 = Math.floorDiv(z - 1, 4) * 4;
        double[] cells = new double[9];
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) cells[i + 3 * j] = Double.NaN;
        int[] out = new int[18];
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
            int cx = x - 1 + i, cz = z - 1 + j, ci = (Math.floorDiv(cx, 4) * 4 - x0) / 4, cj = (Math.floorDiv(cz, 4) * 4 - z0) / 4;
            double fx = (cx - x0 - ci * 4) / 4.0, fz = (cz - z0 - cj * 4) / 4.0;
            double a = cell(cells, ci, cj, x0, z0, ground), b = cell(cells, ci + 1, cj, x0, z0, ground),
                    c = cell(cells, ci, cj + 1, x0, z0, ground), e = cell(cells, ci + 1, cj + 1, x0, z0, ground);
            double gb = (a + (b - a) * fx) * (1 - fz) + (c + (e - c) * fx) * fz;
            if (ground.hardFloor(cx, cz)) gb = Math.max(gb, cellHeight(cx, cz, ground));
            out[i + 3 * j] = (int) Math.ceil(gb - 1e-9);
        }
        // which columns the works reach: any of their corners (the 4 x 4 corner points x - 1 .. x + 2) off the natural ground
        boolean[] off = new boolean[16];
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
            off[i + 4 * j] = !natural(x - 1 + i, z - 1 + j, ground);
        }
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++)
            if (off[i + 4 * j] || off[i + 1 + 4 * j] || off[i + 4 * (j + 1)] || off[i + 1 + 4 * (j + 1)]) out[9 + i + 3 * j] = 1;
        return out;
    }

    private double cell(double[] cells, int i, int j, int x0, int z0, Ground ground) {
        double v = cells[i + 3 * j];
        if (Double.isNaN(v)) v = cells[i + 3 * j] = cellHeight(x0 + 4 * i, z0 + 4 * j, ground);
        return v;
    }

    /**
     * How far (0..1) a turf corner with the works' surface rise over the planned ground moves from the works toward the
     * natural surface (group B, 2026-10-15): the noise's blocks stand above the planned ground (their tops are the next
     * whole block over it, on average half a block higher), so a turf edge at the planned ground lay up to a block under
     * the grass beside it and the first row of natural blocks tilted down to it, a dark stepped band. Within TO_NATURAL of
     * the planned ground the turf blends over to the drawn natural surface (fully where the works meet the ground), and
     * not next to the pavement: from EDGE_KEEP beyond the paved edge (or the route's end) over EDGE_RAMP. Only at the joint
     * with the natural ground (#joint): the rise alone let it in all over a shallow roadside, and the natural tops under a
     * ditch (the noise's blocks, whole-block steps over 4 m cells) pulled its corners up into lumps and over-steep blocks
     * whose lifted corners tore from their neighbours (the user's county road 2026-10-25, docs road_drainage_v1_design 12).
     */
    static double toNatural(Hit h, double rise) {
        if (h == null) return 0;
        CrossSection sec = h.road().section;
        double dp = elevated(h) ? Double.POSITIVE_INFINITY : Math.hypot(Math.max(0, h.v() - sec.edge), h.beyond());
        return CrossSection.smooth((dp - EDGE_KEEP) / EDGE_RAMP) * CrossSection.smooth(1 - Math.abs(rise) / TO_NATURAL);
    }

    /**
     * How near (0..1) corner point (x, z), itself off the natural ground, lies to it, and how far the natural surface as
     * drawn stands over the planned ground there (lift[0]): every corner point R a natural column meets (#open) within
     * JOIN (JOIN points each way) weighs 1 - d / JOIN, d its distance; the result is the largest weight (none: 0), the
     * lift the weighted mean of R's (#drawn(R) - the planned ground at R). The natural blocks are the noise's whole blocks
     * (up to one over the planned ground): the turf climbs to them over JOIN, not at the first block; linearly (a smooth
     * step's steeper middle tore more blocks, the sweep in docs road_drainage_v1_design.md 12). The same for every column
     * sharing the point.
     */
    double joint(int x, int z, Ground ground, double[] lift) {
        double best = 0, sum = 0, weights = 0;
        int r = (int) Math.floor(JOIN);
        for (int j = -r; j <= r; j++) for (int i = -r; i <= r; i++) {
            if (i == 0 && j == 0) continue;
            double w = 1 - Math.sqrt(i * i + j * j) / JOIN;
            if (w <= 0 || !open(x + i, z + j, ground)) continue;
            best = Math.max(best, w);
            sum += w * (drawn(x + i, z + j, ground) - ground.at(x + i, z + j));
            weights += w;
        }
        lift[0] = weights > 0 ? sum / weights : 0;
        return best;
    }

    /** The natural surface as drawn at corner point (x, z) a natural column meets (#naturalSurface: those columns' tops). */
    double drawn(int x, int z, Ground ground) {
        int sum = 0, n = 0;
        for (int cz = z - 1; cz <= z; cz++) for (int cx = x - 1; cx <= x; cx++)
            if (natural(cx, cz, ground) && natural(cx + 1, cz, ground) && natural(cx, cz + 1, ground) && natural(cx + 1, cz + 1, ground)) {
                sum += top(cx, cz, ground);
                n++;
            }
        return (double) sum / n;
    }

    /** Whether a natural column (all four corners natural, #natural) meets corner point (x, z) (#joins). */
    boolean open(int x, int z, Ground ground) {
        if (!natural(x, z, ground)) return false;
        for (int cz = z - 1; cz <= z; cz++) for (int cx = x - 1; cx <= x; cx++)
            if (natural(cx, cz, ground) && natural(cx + 1, cz, ground) && natural(cx, cz + 1, ground) && natural(cx + 1, cz + 1, ground)) return true;
        return false;
    }

    /** Whether the works leave corner point (x, z) natural (#surface NaN), cached per thread. */
    boolean natural(int x, int z, Ground ground) {
        NaturalCache c = naturalCache.get();
        if (c.ground != ground) {
            java.util.Arrays.fill(c.keys, Long.MIN_VALUE);
            c.ground = ground;
        }
        long k = (long) x << 32 | (z & 0xffffffffL);
        int i = (int) ((x * 0x9E3779B1L + z * 0x85EBCA6BL) >>> 7) & (NaturalCache.SIZE - 1);
        if (c.keys[i] == k) return c.values[i];
        boolean n = Double.isNaN(surface(x, z, ground.at(x, z)));
        c.keys[i] = k;
        c.values[i] = n;
        return n;
    }

    /** #naturalTop, cached per thread. */
    private int top(int x, int z, Ground ground) {
        NaturalCache c = naturalCache.get();
        if (c.topGround != ground) {
            java.util.Arrays.fill(c.topKeys, Long.MIN_VALUE);
            c.topGround = ground;
        }
        long k = (long) x << 32 | (z & 0xffffffffL);
        int i = (int) ((x * 0x9E3779B1L + z * 0x85EBCA6BL) >>> 7) & (NaturalCache.SIZE - 1);
        if (c.topKeys[i] == k) return c.tops[i];
        int t = naturalTop(x, z, ground);
        c.topKeys[i] = k;
        c.tops[i] = t;
        return t;
    }

    private static final class ColumnCache {
        static final int SIZE = 1024;
        final long[] keys = new long[SIZE];
        final Column[] values = new Column[SIZE];
        Ground ground;

        ColumnCache() { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
    }

    private static final class NaturalCache {
        static final int SIZE = 4096;
        final long[] keys = new long[SIZE];
        final boolean[] values = new boolean[SIZE];
        final long[] topKeys = new long[SIZE];
        final int[] tops = new int[SIZE];
        Ground ground, topGround;

        NaturalCache() {
            java.util.Arrays.fill(keys, Long.MIN_VALUE);
            java.util.Arrays.fill(topKeys, Long.MIN_VALUE);
        }
    }

    /** RoadWorksRef#height at a cell corner. */
    private double cellHeight(int x, int z, Ground ground) {
        double g = ground.at(x, z), f = densitySurface(x + 0.5, z + 0.5, g);
        return Double.isNaN(f) ? g : f;
    }

    /**
     * Whether a river keeps its planned width at a point (river / lake R2, HydroQuery.Narrow): in a town or by the
     * driveway (their clearances + extra), or within extra of a road's paved edge on its route (its bridges, culverts and
     * the embankments over them), not over a tunnel's bores.
     */
    public boolean holdsRiver(double x, double z, double extra) {
        if (!towns.isEmpty() && towns.clear(x, z, extra)) return true;
        if (drive.clear(x, z, extra)) return true;
        Hit h = hit(x, z, MAX_HALF_WIDTH + extra);
        return h != null && h.beyond() == 0 && h.v() <= h.road().section.edge + extra && !TunnelWorks.overBores(h);
    }

    /** Whether a point is inside a road's clearance (forest roots, spawn): within CLEAR + extra of a centreline, ends included. */
    public boolean clear(double x, double z, double extra) {
        if (!towns.isEmpty() && towns.clear(x, z, extra)) return true;
        if (drive.clear(x, z, extra)) return true;
        Hit h = hit(x, z, CLEAR + extra);
        return h != null && !TunnelWorks.overBores(h);
    }

    // ------------------------------------------------------------------ columns

    /** What one block column becomes. */
    public enum Kind {
        /** natural: nothing to do */
        NONE,
        /** pavement: collision layers at y (and y + 1 when upper > 0), asphalt, gravel, cobblestone below, fill to the ground */
        PAVED,
        /** graded turf block at y (data: corners), fill below or clear above */
        TURF,
        /** bridge deck: collision layers at y (upper as PAVED), road_structure from bottom to y - 1 */
        DECK,
        /** bridge deck end: a deck column whose structure continues down to the ground (the abutment wall) */
        ABUTMENT,
        /** bridge parapet or wing wall: road_structure from bottom up to y, y cut to layers (at the span's ends from the ground) */
        BARRIER,
        /** under a bridge, off the deck: natural ground; only clear what the density's interpolation left above it */
        OPEN,
        /**
         * a box culvert's own cell (group D): y, layers its top concrete block (layers 0: water only), data C_* flags,
         * bottom the bed block less one (gravel under the channel); the water, the concrete and the open channel above
         */
        CULVERT,
        /**
         * a tunnel's works (Tunnel V1, TunnelWorks): cells from bottom up to y by TunnelWorks#cells (road_structure, the
         * carved bore, the mountain); data TunnelWorks.APPROACH_COLUMN: on an approach, cleared above y to the sky
         */
        TUNNEL,
        /**
         * the forest home's driveway (County road G3, Driveway), a column wholly on its gravel: the collision as a PAVED
         * column's (road_surface at y, layers, upper), gravel under it, dirt to the ground, cleared above; drawn by the
         * client's mesh (DriveMesh). Its edges and verge are TURF (#driveColumn).
         */
        DRIVE
    }

    /**
     * One column's finish. y: the collision / turf / barrier top block; layers, upper: collision layers (1..32) of y and
     * y + 1 (0: none; BARRIER: layers of y); data: turf corners (nw | ne << 4 | sw << 8 | se << 12 | shift << 16), for
     * a BARRIER BridgeMesh.WING when it is a wing wall rather than a parapet; bottom: the lowest deck
     * block (DECK, ABUTMENT, BARRIER) or the natural ground the clearing stops at (OPEN); paveLo / paveHi: the column's
     * pavement height range (PAVED, DECK, ABUTMENT); roof: the highest block the guard keeps (a tunnel's works under the
     * mountain: the forest above them grows; Integer.MAX_VALUE: up to the sky); deck: an overpass's deck cells over this
     * finish (C3.3b group 2: DECK, ABUTMENT or BARRIER standing on it), or null.
     */
    public record Column(Kind kind, int y, int layers, int upper, int data, int bottom, double paveLo, double paveHi, int roof, Column deck) {
        public static final Column NONE = new Column(Kind.NONE, 0, 0, 0, 0, 0, 0, 0);

        public Column(Kind kind, int y, int layers, int upper, int data, int bottom, double paveLo, double paveHi) {
            this(kind, y, layers, upper, data, bottom, paveLo, paveHi, Integer.MAX_VALUE, null);
        }

        public Column(Kind kind, int y, int layers, int upper, int data, int bottom, double paveLo, double paveHi, int roof) {
            this(kind, y, layers, upper, data, bottom, paveLo, paveHi, roof, null);
        }

        /** This finish with an overpass's deck cells over it. */
        public Column withDeck(Column d) { return new Column(kind, y, layers, upper, data, bottom, paveLo, paveHi, roof, d); }

        /** The highest block this column's finish occupies. */
        public int top() { return upper > 0 ? y + 1 : y; }
    }

    /**
     * The finish of block column (x, z). Corners for turf are the finished field at the integer corners (shared by the
     * neighbouring columns of any chunk), quantised to 1/8; under the paved band and its bevels they round down, so the
     * turf never rises through the asphalt.
     */
    public Column column(int x, int z, Ground ground) {
        Column c = separate(x, z, ground);
        return turf(c) ? agreed(c, x, z, ground, AGREE) : c;
    }

    /** Rounds of #agreed. */
    private static final int AGREE = 2;

    /** Column (x, z) after the given rounds of #agreed (0: #separate). */
    private Column agreed(int x, int z, Ground ground, int rounds) {
        Column c = separate(x, z, ground);
        return rounds == 0 || !turf(c) ? c : agreed(c, x, z, ground, rounds);
    }

    /** #column before its turf corners agree with its neighbours' (#agreed), cached per thread. */
    private Column separate(int x, int z, Ground ground) {
        ColumnCache cc = columnCache.get();
        if (cc.ground != ground) {
            java.util.Arrays.fill(cc.keys, Long.MIN_VALUE);
            cc.ground = ground;
        }
        long k = (long) x << 32 | (z & 0xffffffffL);
        int i = (int) ((x * 0x9E3779B1L + z * 0x85EBCA6BL) >>> 7) & (ColumnCache.SIZE - 1);
        if (cc.keys[i] == k) return cc.values[i];
        Column c = unpadded(x, z, ground);
        if (!drive.isEmpty() && drive.nearPad(x, z)) c = padded(c, x, z, ground);
        cc.keys[i] = k;
        cc.values[i] = c;
        return c;
    }

    /** Graded turf whose corners meet its neighbours' (#agreed): not a rock cut's face, not under a tunnel's works. */
    private static boolean turf(Column c) {
        return c.kind() == Kind.TURF && (c.data() & (ROCK_CUT | TUNNEL_OVER)) == 0;
    }

    /** Corner k (nw, ne, sw, se) of a graded_turf column, m. */
    static double corner(Column c, int k) {
        int base = c.y() * 8 + ((c.data() >> 16 & 1) != 0 ? 4 : 0);
        return (base + (c.data() >> 4 * k & 15)) / 8.0;
    }

    /**
     * A turf column whose shared corners agree with its neighbours' (2026-10-25, docs road_drainage_v1_design.md 12): each
     * column fits its own four corners to one block (#fit), and one steeper than the block holds lifts its low corners
     * alone; the neighbours sharing such a corner kept it lower, and between them a torn face (sides between turf blocks
     * are culled: a wedge of sky, a loose triangle). Every corner takes the highest of the turf columns sharing it, and the
     * column fits again; AGREE rounds, each over the neighbours after the round before (one that must lift again after the
     * last still tears: rare, at the edge of a steep cut).
     */
    private Column agreed(Column c, int x, int z, Ground ground, int rounds) {
        // this round starts from the column after the round before, as its neighbours' do: all four take the same highest
        if (rounds > 1) c = agreed(c, x, z, ground, rounds - 1);
        double[] q = new double[4];
        boolean changed = false;
        for (int k = 0; k < 4; k++) {
            int px = x + (k & 1), pz = z + (k >> 1);
            q[k] = corner(c, k);
            for (int j = 0; j <= 1; j++) for (int i = 0; i <= 1; i++) {
                int dx = px - 1 + i, dz = pz - 1 + j;
                if (dx == x && dz == z) continue;
                Column d = agreed(dx, dz, ground, rounds - 1);
                if (!turf(d)) continue;
                double v = corner(d, (px - dx) + 2 * (pz - dz));
                if (v > q[k] + 1e-9) {
                    q[k] = v;
                    changed = true;
                }
            }
        }
        if (!changed) return c;
        int[] fit = fit(q);
        int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16 | c.data() & ~0x1ffff;
        return new Column(c.kind(), fit[0], c.layers(), c.upper(), data, c.bottom(), c.paveLo(), c.paveHi(), c.roof(), c.deck());
    }

    /**
     * Column (x, z) by the driveway's pads (Driveway#padY; Main 2026-10-19: the farm gate and the mailbox stand on whole
     * blocks): a turf column, or a natural one (its corners then the natural surface as drawn), with a corner a pad's
     * gets that corner at the pad's whole Y, so a pad's own column is level turf (a full block at Y - 1, Y clear, no
     * plants) and its neighbours share its corners; pavement, gravel and the rest are left as they are.
     */
    private Column padded(Column c, int x, int z, Ground ground) {
        boolean turf = c.kind() == Kind.TURF && (c.data() & (TUNNEL_OVER | CULVERT_BODY)) == 0;
        if (!turf && !(c.kind() == Kind.NONE && ground != null)) return c;
        int[] py = new int[4];
        boolean any = false;
        for (int k = 0; k < 4; k++) {
            py[k] = drive.padY(x + (k & 1), z + (k >> 1));
            any |= py[k] != Integer.MIN_VALUE;
        }
        if (!any) return c;
        double[] q = new double[4];
        if (turf) {
            int base = c.y() * 8 + ((c.data() >> 16 & 1) != 0 ? 4 : 0);
            for (int k = 0; k < 4; k++) q[k] = (base + (c.data() >> 4 * k & 15)) / 8.0;
        } else {
            int[] tops = naturalTops(x, z, ground);
            for (int k = 0; k < 4; k++) q[k] = naturalSurface(tops, k & 1, k >> 1);
        }
        for (int k = 0; k < 4; k++) if (py[k] != Integer.MIN_VALUE) q[k] = py[k];
        int[] fit = fit(q);
        int keep = turf ? c.data() & ~0x1ffff : 0;
        int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16 | keep | (drive.padCell(x, z) != Integer.MIN_VALUE ? BARE : 0);
        return new Column(Kind.TURF, fit[0], 0, 0, data, turf ? c.bottom() : 0, 0, 0, c.roof(), c.deck());
    }

    /** #column without the driveway's pads. */
    private Column unpadded(int x, int z, Ground ground) {
        // a box culvert's own cell (its mouths may reach beyond the works' reach); a cell of its body keeps the finish
        Culvert.Cell cell = culvertCell(x, z);
        if (cell != null && cell.own()) {
            int data = (cell.channel() ? C_CHANNEL : 0) | (cell.wall() ? C_WALL : 0) | (cell.roof() ? C_ROOF : 0) | (cell.keepGround() ? C_KEEP : 0);
            return new Column(Kind.CULVERT, cell.top(), cell.layers(), 0, data, cell.culvert().bed - 1, 0, 0);
        }
        Column c = finish(x, z, ground);
        // the driveway where no road and no town holds the column; over a road's turf too (the county road's end
        // earthworks reach back over the driveway's last metres: under the gravel they would rise through it,
        // county_road_g3 section 6): every turf column with a corner on the gravel or its verge follows the driveway's
        // rule, whose base off the gravel is the road's turf, so the two rules never meet at a corner (2026-10-19)
        if ((c.kind() == Kind.NONE || c.kind() == Kind.TURF && (c.data() & TUNNEL_OVER) == 0) && cell == null) {
            Column dc = driveColumn(x, z, ground);
            if (dc != null) return dc.kind() == Kind.TURF && c.kind() == Kind.TURF
                    ? new Column(Kind.TURF, dc.y(), 0, 0, dc.data() | c.data() & BARE, 0, 0, 0) : dc;
        }
        if (cell != null && (c.kind() == Kind.PAVED || c.kind() == Kind.TURF) && (c.data() & (TUNNEL_PAVED | TUNNEL_OVER)) == 0)
            return new Column(c.kind(), c.y(), c.layers(), c.upper(), c.data() | CULVERT_BODY, cell.culvert().bed - 1, c.paveLo(), c.paveHi(),
                    Integer.MAX_VALUE, c.deck());
        return c;
    }

    /**
     * A finish with a tunnel's works standing on it (works: the TUNNEL column, TunnelWorks.ON_GROUND), or the finish
     * itself (works null): TUNNEL_OVER, the works' roof (MAX_VALUE on an approach: cleared to the sky over the works).
     */
    private static Column over(Column c, Column works) {
        if (works == null) return c;
        int roof = (works.data() & TunnelWorks.APPROACH_COLUMN) != 0 ? Integer.MAX_VALUE : works.roof();
        return new Column(c.kind(), c.y(), c.layers(), c.upper(), c.data() | TUNNEL_OVER, c.bottom(), c.paveLo(), c.paveHi(), roof, c.deck());
    }

    /** The column's finish without a box culvert's cells (#column). */
    private Column finish(int x, int z, Ground ground) {
        Hit centre = hit(x + 0.5, z + 0.5, REACH);
        if (!towns.isEmpty()) {
            Column t = townColumn(x, z, ground, centre);
            if (t != null) return t;
        }
        if (centre == null) return Column.NONE;
        Column base = finish(centre, x, z, ground);
        // an overpass's deck over the column (C3.3b group 2): the finish of the ground below, the deck's cells over it
        Hit over = overHit(x + 0.5, z + 0.5);
        Column deck = over == null ? null : deck(over, pavedRange(over.road(), x, z), x, z, ground);
        if (deck == null) return base;
        return base.kind() == Kind.NONE ? deck : base.withDeck(deck);
    }

    /** The finish of a column whose centre lies on hit centre (an overpass's span: the ground under it). */
    private Column finish(Hit centre, int x, int z, Ground ground) {
        RoadDesign d = centre.road();
        boolean up = elevated(centre);
        // near a junction pad (C3.3b) or an attached ramp (group 2) a column may be paved by two roads together
        boolean nearPad = padsNear(x + 0.5, z + 0.5) != null;
        double[] pave = up ? null : nearPad || rampsNear(x + 0.5, z + 0.5) ? pavedRange(x, z) : pavedRange(d, x, z);
        // a column paved by a road and a town together (the exit crossroad's end at its mouth)
        if (pave == null && !up && towns.touches(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) pave = townPave(x, z);
        boolean onRoute = centre.beyond() == 0;
        RoadDesign.Span span = onRoute && !up ? d.span(centre.sc()) : null;
        // a tunnel's bores, portals and approaches (Tunnel V1); null: the open road's finish (an approach's pavement and shelf)
        RoadDesign.Tunnel tunnel = TunnelWorks.near(centre);
        // works standing on finished ground (TunnelWorks.ON_GROUND): the open road's finish under them, the works above
        Column over = null;
        if (tunnel != null) {
            Column t = TunnelWorks.column(centre, tunnel, x, z, pave);
            if (t != null && (t.data() & TunnelWorks.ON_GROUND) == 0) return t;
            over = t;
        }
        if (span == null) {
            // a column touching a span from outside belongs to the earthworks; one inside a span is handled below
            if (pave != null) return over(paved(Kind.PAVED, pave, 0), over);
            // a column on planned water stays water even where a dry corner reaches the works (a stream beside the road,
            // C3.3a), unless the embankment fills it over a box culvert (group D)
            if (over == null && !onFormation(centre) && water.wet(x + 0.5, z + 0.5) && !filled(x + 0.5, z + 0.5)) return Column.NONE;
            double[] q = new double[4];
            boolean any = false;
            int k = 0;
            // a wing wall's coping can only lie over a corner near a span end of this road (its corners are within 1 m)
            boolean wings = onRoute && BridgeMesh.nearEnd(d, centre.s(), 2);
            int[] tops = null;
            double[] lift = new double[1];
            Hit[] hits = new Hit[4];
            // the deepest corner's cut and whether a corner lies under a pavement or by a pad (a rock face never does)
            double dig = 0;
            boolean edge = false;
            for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
                double g = ground.at(x + a, z + b), f = surface(x + a, z + b, g);
                boolean natural = Double.isNaN(f);
                if (!natural) dig = Math.min(dig, f - g);
                Hit h = natural ? null : hit(x + a, z + b, REACH);
                // toward the daylight line the turf meets the natural surface as drawn, not the planned ground (group B);
                // not by a tunnel, whose density keeps rock over the band it clears (its natural tops are not the ground there)
                double w = tunnel != null ? 0 : natural ? 1 : toNatural(h, f - g);
                // and not next to a junction pad (C3.3b)
                if (nearPad && !natural && w > 0) w *= CrossSection.smooth((padDistance(x + a, z + b) - EDGE_KEEP) / EDGE_RAMP);
                // and only at the joint with the natural ground (#joint), lifted by what the natural surface stands over it
                if (!natural && w > 0) {
                    w *= joint(x + a, z + b, ground, lift);
                    f += lift[0] * w;
                }
                if (tunnel != null && natural) f = g;
                if (natural && w > 0) {
                    if (tops == null) tops = naturalTops(x, z, ground);
                    f = joins(tops, a, b) ? naturalSurface(tops, a, b) : g;
                }
                if (!natural) {
                    any = true;
                    if (h != null && underPavement(h) || nearPad && padDistance(x + a, z + b) <= CrossSection.BEVEL_RUN) {
                        f = Math.floor(f * 8 + 1e-9) / 8;
                        edge = true;
                    }
                }
                // under a tunnel's works the ground in front runs on: a corner in or behind the concrete keeps the floor
                if (over != null) {
                    any = true;
                    if (TunnelWorks.behind(d, tunnel, x + a, z + b)) f = over.paveHi();
                    else f = Math.min(f, over.paveHi());
                }
                // a corner under a wall's coping (a bridge's wing walls, a box culvert's headwall and mouth walls) lies
                // COPING under it, never over it: the earth is retained by the wall, not drawn through it (group D)
                // under the pavement the pavement wins (the turf never rises through the asphalt)
                double cop = h != null && underPavement(h) ? Double.NaN : coping(x + a, z + b, h, wings);
                if (!Double.isNaN(cop) && cop - BridgeMesh.COPING > f) {
                    f = cop - BridgeMesh.COPING;
                    any = true;
                }
                hits[k] = h;
                q[k++] = f;
            }
            if (!any) return Column.NONE;
            // no corner over the pavement itself: a corner on a span's deck or past a trimmed end's taper has no finished
            // field of its own (natural there) and would stand on the embankment's or the natural surface (group D check)
            for (int i = 0; i < 4; i++) {
                Junction pj = nearPad ? pad(x + (i & 1), z + (i >> 1)) : null;
                if (pj != null) {
                    double top = pj.height(x + (i & 1), z + (i >> 1));
                    if (q[i] > top) q[i] = Math.floor(top * 8 + 1e-9) / 8;
                    continue;
                }
                Hit h = hits[i] != null ? hits[i] : hit(x + (i & 1), z + (i >> 1), REACH);
                if (h == null || h.beyond() > 0 || elevated(h) || !h.road().section.paved(h.v()) || !underPavement(h)) continue;
                double top = h.road().pavementY(h.sc(), h.u());
                if (q[i] > top) q[i] = Math.floor(top * 8 + 1e-9) / 8;
            }
            // a rock cut's face (2026-10-19): steeper than graded turf draws, bare stone up to the corners' mean
            double lo = Math.min(Math.min(q[0], q[1]), Math.min(q[2], q[3])), hi = Math.max(Math.max(q[0], q[1]), Math.max(q[2], q[3]));
            if (over == null && tunnel == null && !edge && hi - lo > ROCK_SPREAD && dig < -ROCK_SPREAD) {
                int top = (int) Math.round((q[0] + q[1] + q[2] + q[3]) / 4) - 1;
                return new Column(Kind.TURF, top, 0, 0, 8 | 8 << 4 | 8 << 8 | 8 << 12 | ROCK_CUT | BARE, 0, 0, 0);
            }
            // corner order nw, ne, sw, se = (x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)
            int[] fit = fit(q);
            int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16 | (bare(x, z) ? BARE : 0);
            return over(new Column(Kind.TURF, fit[0], 0, 0, data, 0, 0, 0), over);
        }
        return deck(centre, pave, x, z, ground);
    }

    /**
     * A column on a bridge span (centre: its centre's hit, pave: the deck's pavement range over it, or null): the collision
     * of the bridge mesh (BridgeMesh; docs 10.11), every column chosen by its centre. Beside the decks: a wing wall, or OPEN
     * (the natural valley); under an overpass (C3.3b group 2) nothing there (null): the ground is the road's below.
     */
    private Column deck(Hit centre, double[] pave, int x, int z, Ground ground) {
        RoadDesign d = centre.road();
        RoadDesign.Span span = d.span(centre.sc());
        if (span == null) return null;
        CrossSection sec = d.section;
        double v = centre.v(), sc = centre.sc(), u = centre.u();
        double deckTop = d.datum(sc);
        boolean end = band(d, span, x, z);
        // a column whose square reaches a bent column stands on the ground like the abutment band (C3.3a, group E)
        boolean pier = !end && pier(d, span, x, z);
        // under the girder the deck goes down to its soffit (rounded up: the collision stays inside the mesh), under the
        // slab's overhangs only the slab
        boolean girder = BridgeMesh.girder(sec, v);
        if (pave != null) {
            Column c = paved(end || pier ? Kind.ABUTMENT : Kind.DECK, pave, 0);
            int bottom = girder ? (int) Math.ceil(deckTop - span.depth()) : (int) Math.ceil(pave[0] - BridgeMesh.SLAB);
            // under a bent's cap beam the deck's cells reach down to its underside (group E)
            double cap = end || pier ? Double.NaN : cap(d, span, x, z);
            if (!Double.isNaN(cap)) bottom = Math.min(bottom, (int) Math.ceil(cap - 1e-9));
            return new Column(c.kind(), c.y(), c.layers(), c.upper(), 0, Math.min(bottom, c.y()), c.paveLo(), c.paveHi());
        }
        double outerW = v - sec.edge, innerW = sec.divided ? sec.halfMedian - v : Double.NaN;
        boolean outer = outerW >= BridgeMesh.CELL_IN && outerW <= BridgeMesh.CELL_OUT;
        boolean inner = innerW >= BridgeMesh.CELL_IN && innerW <= BridgeMesh.CELL_OUT;
        if (outer || inner) {
            // the parapet, its top block cut to the parapet's height; at the span's ends it stands on the ground
            double edge = d.pavementY(sc, Math.copySign(outer ? sec.edge : sec.halfMedian, u));
            double top = edge + BridgeMesh.PARAPET_HEIGHT;
            int y = (int) Math.ceil(top) - 1, layers = Math.max(1, Math.min(32, (int) Math.round((top - y) * 32)));
            int bottom = Math.min(y, (int) Math.ceil(edge - BridgeMesh.SLAB));
            int foot = end ? Math.min(bottom, (int) Math.floor(ground.at(x + 0.5, z + 0.5))) : bottom;
            return new Column(Kind.BARRIER, y, layers, 0, 0, foot, edge, edge);
        }
        if (outerW > -1 && outerW < BridgeMesh.CELL_IN || innerW > -1 && innerW < BridgeMesh.CELL_IN) {
            // a deck edge column between the pavement and the parapet's toe: the pavement edge's height on the slab
            double e = d.pavementY(sc, Math.copySign(Math.max(sec.halfMedian, Math.min(sec.edge, v)), u));
            Column c = paved(end || pier ? Kind.ABUTMENT : Kind.DECK, new double[]{e, e}, 0);
            return new Column(c.kind(), c.y(), c.layers(), c.upper(), 0, Math.min(c.y(), (int) Math.ceil(e - BridgeMesh.SLAB)), e, e);
        }
        double g = ground.at(x + 0.5, z + 0.5);
        if (end && !water.wet(x + 0.5, z + 0.5)) {
            // wing wall: the embankment just outside the span, retained by a wall in line with the abutment; its top is the
            // embankment's design (the median, or the fill template beside the road), pure, so the client knows it too. The
            // chunk pass leaves it out where the actual ground already reaches its top (the valley's terrain is the noise's,
            // metres off the planned ground at a river), so it is decided there, not here
            double sOut = sc - span.s0() <= span.s1() - sc ? span.s0() : span.s1(), sgn = sOut == span.s0() ? 1 : -1;
            // the highest point of the coping over the square (the collision follows the mesh below it: BridgeMesh.solid)
            double f = Double.NaN;
            for (int b = 0; b <= 4; b++) for (int a = 0; a <= 4; a++) {
                double[] l = d.local(x + a * 0.25, z + b * 0.25, REACH + 2);
                if (l == null) continue;
                double t = sgn * (l[0] - sOut);
                if (t < -BridgeMesh.TAIL || t > 1) continue;
                double w = BridgeMesh.wingStrip(d, span, sOut, l[1]);
                if (!Double.isNaN(w) && !(w <= f)) f = w;
            }
            if (!Double.isNaN(f)) {
                int y = (int) Math.ceil(f) - 1, layers = Math.max(1, Math.min(32, (int) Math.round((f - y) * 32)));
                return new Column(Kind.BARRIER, y, layers, 0, BridgeMesh.WING, Math.min(y, (int) Math.floor(g)), f, f);
            }
        }
        return span.over() ? null : new Column(Kind.OPEN, (int) Math.floor(deckTop), 0, 0, 0, (int) Math.floor(g), 0, 0);
    }

    /**
     * Whether column (x, z) of a span belongs to the abutment band: its square reaches into the first metre of the span
     * from either end (not only its centre: on a skewed crossing a square reaches 0.71 m beyond its centre, and a cell
     * whose square reaches the walls must stand on the ground with them; 2026-10-15).
     */
    public static boolean band(RoadDesign d, RoadDesign.Span span, int x, int z) {
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            double[] l = d.local(x + a, z + b, REACH + 2);
            if (l != null && Math.min(l[0] - span.s0(), span.s1() - l[0]) < 1.0) return true;
        }
        return false;
    }

    /**
     * Whether column (x, z) of a span reaches a bent column (C3.3a; group E): its square comes within the column's radius
     * of the column's centre.
     */
    public static boolean pier(RoadDesign d, RoadDesign.Span span, int x, int z) {
        if (span.piers().length == 0) return false;
        double r = BridgeMesh.columnRadius(d.section);
        for (double p : span.piers())
            for (double u : BridgeMesh.columns(d.section)) {
                double[] c = d.point(p, u);
                double dx = Math.max(0, Math.max(x - c[0], c[0] - (x + 1))), dz = Math.max(0, Math.max(z - c[1], c[1] - (z + 1)));
                if (dx * dx + dz * dz <= r * r) return true;
            }
        return false;
    }

    /**
     * The underside of the bent cap beam (group E) that column (x, z) of a span reaches, or NaN: the (station, lateral) box
     * of its square meets a cap's band (BridgeMesh.CAP_WIDTH along the road) under a girder; the lower one if it meets two.
     */
    public static double cap(RoadDesign d, RoadDesign.Span span, int x, int z) {
        if (span.piers().length == 0) return Double.NaN;
        double s0 = Double.POSITIVE_INFINITY, s1 = Double.NEGATIVE_INFINITY, u0 = Double.POSITIVE_INFINITY, u1 = Double.NEGATIVE_INFINITY;
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            double[] l = d.local(x + a, z + b, REACH + 2);
            if (l == null) return Double.NaN;
            s0 = Math.min(s0, l[0]); s1 = Math.max(s1, l[0]); u0 = Math.min(u0, l[1]); u1 = Math.max(u1, l[1]);
        }
        double out = Double.NaN;
        for (double p : span.piers()) {
            if (s0 > p + BridgeMesh.CAP_WIDTH / 2 || s1 < p - BridgeMesh.CAP_WIDTH / 2) continue;
            for (int side : d.section.divided ? new int[]{-1, 1} : new int[]{1}) {
                double[] g = BridgeMesh.girderRange(d.section, side);
                if (u1 < g[0] || u0 > g[1]) continue;
                double c = BridgeMesh.capBottom(d, span, p, side);
                if (!(c >= out)) out = c;
            }
        }
        return out;
    }

    /** A paved (or deck) column: the asphalt / deck top 2 cm under the lowest pavement point, collision to the mean. */
    public static Column paved(Kind kind, double[] pave, int bottom) {
        int top32 = (int) Math.round((pave[0] + pave[1]) * 0.5 * 32), yc = (int) Math.floor(pave[0] - 0.02);
        int layers = top32 - yc * 32, upper = 0;
        if (layers > 32) { upper = layers - 32; layers = 32; }
        if (layers < 1) layers = 1;
        return new Column(kind, yc, layers, upper, 0, bottom, pave[0], pave[1]);
    }

    /**
     * Whether turf column (x, z) carries no plants: its square, grown by PLANT_CLEAR, reaches under the pavement or its
     * bevels. A turf column is any column not wholly paved, so the edge columns lie half under the asphalt mesh, and a
     * plant there stood out of the road (user, 2026-10-15). The band's edges are straight over a square, so its corners
     * decide; 5 cm more covers the mesh's chords (2 m stations) against the exact curve.
     */
    public boolean bare(int x, int z) {
        if (!towns.isEmpty() && towns.bare(x, z, PLANT_CLEAR)) return true;
        double w = 1 + 2 * PLANT_CLEAR;
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            Hit h = hit(x - PLANT_CLEAR + a * w, z - PLANT_CLEAR + b * w, REACH);
            if (h != null && underPavement(h, 0.05)) return true;
        }
        // a junction's sight triangles (Junction#inSight) keep clear of plants (C3.3b)
        Junction[] ss = sightByChunk.isEmpty() ? null : sightByChunk.get(key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (ss != null) for (Junction j : ss) if (j.inSight(x + 0.5, z + 0.5)) return true;
        // and a pad and its bevels
        Junction[] ps = padsNear(x + 0.5, z + 0.5);
        if (ps != null)
            for (Junction j : ps) {
                if (!j.near(x + 0.5, z + 0.5, 2)) continue;
                for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
                    double px = x - PLANT_CLEAR + a * w, pz = z - PLANT_CLEAR + b * w;
                    if (j.contains(px, pz) || j.nearest(px, pz)[0] <= CrossSection.BEVEL_RUN + 0.05) return true;
                }
            }
        return false;
    }

    // ------------------------------------------------------------------ the driveway (County road G3)

    /**
     * The driveway's column (Driveway), or null off it and its verge: DRIVE (its collision: #paved over the nine points)
     * where each of the column's nine points lies on the gravel or on the county road's pavement (the joint); else graded
     * turf whose corners on the gravel round down under it (the mesh's skirt hides the step), on the verge blending from
     * the gravel's edge into the base, beyond it the base (the roads' own turf where a road turf column shares the corner,
     * #roadCorner, else the natural surface as drawn); BARE where its square grown by PLANT_CLEAR reaches the gravel.
     */
    private Column driveColumn(int x, int z, Ground ground) {
        if (drive.isEmpty() || !drive.touches(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return null;
        // near it: its centre or a corner (at the joint the centre may lie past the road's start line, the road's)
        boolean reach = false;
        for (int k = 0; k < 5 && !reach; k++) {
            double[] c = k == 4 ? drive.near(x + 0.5, z + 0.5) : drive.near(x + (k & 1), z + (k >> 1));
            reach = c != null && c[0] <= c[2] + Driveway.VERGE + 1;
        }
        if (!reach) return null;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        boolean all = true;
        for (int a = 0; a <= 2 && all; a++) for (int b = 0; b <= 2 && all; b++) {
            double px = x + a * 0.5, pz = z + b * 0.5, y = drive.height(px, pz);
            if (Double.isNaN(y) && driveRoad != null) y = pave(driveRoad, px, pz);
            if (Double.isNaN(y)) all = false;
            else {
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        }
        // and nowhere between them uncovered (at the joint the gravel's end and the pavement's edge cross a column at a
        // slant: a sliver between the nine points showed the sky through the invisible collision, 2026-10-18; the apron
        // runs Driveway.JOIN past the paved edge, so the two always overlap)
        for (int a = 0; a <= 20 && all; a++) for (int b = 0; b <= 20 && all; b++) {
            if (a % 10 == 0 && b % 10 == 0) continue;
            double px = x + a * 0.05, pz = z + b * 0.05;
            all = !Double.isNaN(drive.height(px, pz)) || driveRoad != null && !Double.isNaN(pave(driveRoad, px, pz));
        }
        if (all) return paved(Kind.DRIVE, new double[]{lo, hi}, 0);
        if (ground == null) return null;
        double[] q = new double[4];
        boolean any = false;
        int[] tops = null;
        Column[] around = new Column[9];
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            double px = x + a, pz = z + b, f = drive.height(px, pz);
            if (!Double.isNaN(f)) {
                f = Math.floor(f * 8 + 1e-9) / 8;
                any = true;
                q[a + 2 * b] = f;
                continue;
            }
            // off the gravel the base is the road's earthworks turf where a road turf column shares the corner, else the
            // natural surface: turf sides between two turf blocks are culled, so a corner shared with the road's own turf
            // must be the same height or the sky shows through the wedge between them (2026-10-19)
            double r = roadCorner(around, x, z, a, b, ground);
            double pv = driveRoad == null ? Double.NaN : pave(driveRoad, px, pz);
            if (!Double.isNaN(pv)) {
                // on the county road's pavement at the joint: under the pavement too (natural ground poked up through it)
                f = Double.isNaN(r) ? Math.floor(pv * 8 + 1e-9) / 8 : r;
                any = true;
            } else {
                double nat = r;
                if (Double.isNaN(nat)) {
                    if (tops == null) tops = naturalTops(x, z, ground);
                    nat = naturalSurface(tops, a, b);
                }
                double[] k = drive.near(px, pz);
                if (k != null && k[0] <= k[2] + Driveway.VERGE) {
                    double e = drive.edge(k), w = CrossSection.smooth((k[0] - k[2]) / Driveway.VERGE);
                    f = e + (nat - e) * w;
                    // next to the gravel the turf keeps under its edge
                    if (k[0] - k[2] < 1) f = Math.floor(f * 8 + 1e-9) / 8;
                    any = true;
                } else f = nat;
            }
            q[a + 2 * b] = f;
        }
        if (!any) return null;
        int[] fit = fit(q);
        int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16 | (driveBare(x, z) ? BARE : 0);
        return new Column(Kind.TURF, fit[0], 0, 0, data, 0, 0, 0);
    }

    /**
     * The height the roads' and towns' own finish (#finish, without the driveway) gives corner (x + a, z + b): that of the
     * first turf column of the four sharing it (in an order fixed by the corner, so every column asking gets the same),
     * or NaN where none is turf. around caches the finishes of the 3 x 3 columns around (x, z).
     */
    private double roadCorner(Column[] around, int x, int z, int a, int b, Ground ground) {
        // the turf columns that keep the roads' rule (the ones this corner must meet), those the driveway takes over only
        // when none does; the lowest of them: two may disagree only by a corner lifted to its block's base (#fit: low
        // corners graded_turf cannot hold are lifted), and the lifted one then stands a layer over this corner, its side
        // facing air (drawn, a step of earth), while a lifted height here would stand over a turf in the same layer
        double keep = Double.NaN, other = Double.NaN;
        for (int db = -1; db <= 0; db++) for (int da = -1; da <= 0; da++) {
            int i = a + da + 1 + 3 * (b + db + 1), cx = x + a + da, cz = z + b + db;
            if (around[i] == null) around[i] = finish(cx, cz, ground);
            Column c = around[i];
            if (c.kind() != Kind.TURF) continue;
            // the corner is (-da, -db) of that column: nw, ne, sw, se
            int nib = c.data() >> 4 * (-da - 2 * db) & 15;
            double h = c.y() + (c.data() >> 16 & 1) * 0.5 + nib / 8.0;
            if ((c.data() & TUNNEL_OVER) != 0 || !driveHolds(cx, cz)) keep = Double.isNaN(keep) ? h : Math.min(keep, h);
            else other = Double.isNaN(other) ? h : Math.min(other, h);
        }
        return Double.isNaN(keep) ? other : keep;
    }

    /**
     * Whether #driveColumn gives column (x, z) (it is near the driveway and a corner lies on the gravel, on the county
     * road's pavement or on the verge), without the terrain plan.
     */
    private boolean driveHolds(int x, int z) {
        if (drive.isEmpty() || !drive.touches(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return false;
        boolean reach = false;
        for (int k = 0; k < 5 && !reach; k++) {
            double[] c = k == 4 ? drive.near(x + 0.5, z + 0.5) : drive.near(x + (k & 1), z + (k >> 1));
            reach = c != null && c[0] <= c[2] + Driveway.VERGE + 1;
        }
        if (!reach) return false;
        for (int k = 0; k < 4; k++) {
            double px = x + (k & 1), pz = z + (k >> 1);
            if (!Double.isNaN(drive.height(px, pz)) || driveRoad != null && !Double.isNaN(pave(driveRoad, px, pz))) return true;
            double[] c = drive.near(px, pz);
            if (c != null && c[0] <= c[2] + Driveway.VERGE) return true;
        }
        return false;
    }

    /**
     * Column (x, z)'s driveway collision (Kind.DRIVE, as #column has it there), or null: the client's paved cells
     * (RoadCells#of) without the terrain plan.
     */
    public Column driveCell(int x, int z) {
        Column c = driveColumn(x, z, null);
        return c != null && c.kind() == Kind.DRIVE ? c : null;
    }

    /** Whether column (x, z)'s square grown by PLANT_CLEAR reaches the driveway's gravel (no plants through the mesh). */
    private boolean driveBare(int x, int z) {
        double w = 1 + 2 * PLANT_CLEAR;
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            double[] k = drive.near(x - PLANT_CLEAR + a * w, z - PLANT_CLEAR + b * w);
            if (k != null && k[0] <= k[2] + 0.05) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ towns (Town Framework V1)

    /**
     * A column of a town's works (TownWorks), or null where the roads decide: a road's formation or a pad at the column's
     * centre, or beside the footprint where a road's works reach it (their surface combines the town's slopes, #surface).
     * On the footprint: PAVED where all nine points are paved (by the town, or a road at the crossroad's mouth), else
     * graded turf (lots, yards, the utility strips, the edges of the pavement).
     */
    private Column townColumn(int x, int z, Ground ground, Hit centre) {
        if (!towns.touches(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return null;
        double cx = x + 0.5, cz = z + 0.5;
        if (centre != null && !elevated(centre) && onFormation(centre) || pad(cx, cz) != null) return null;
        TownWorks.Spot sp = towns.spot(cx, cz);
        if (sp == null) {
            if (centre != null) return null;
        } else {
            double[] pave = townPave(x, z);
            if (pave != null) return paved(Kind.PAVED, pave, 0);
        }
        return townTurf(x, z, ground);
    }

    /** The pavement range of column (x, z) when each of its nine points is paved by a town or a road, else null. */
    private double[] townPave(int x, int z) {
        RoadDesign[] near = near(x + 0.5, z + 0.5);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            double px = x + a * 0.5, pz = z + b * 0.5;
            TownWorks.Spot s = towns.spot(px, pz);
            double y = s != null && s.part().paved() ? s.h() : Double.NaN;
            if (Double.isNaN(y) && near != null)
                for (RoadDesign d : near) {
                    y = pave(d, px, pz);
                    if (!Double.isNaN(y)) break;
                }
            if (Double.isNaN(y)) return null;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return new double[]{lo, hi};
    }

    /**
     * A town's turf column: corners on the finished field (#surface); off the footprint at the slope's toe they meet
     * the natural surface as drawn (as the roads' turf, #toNatural, #joint), on the street strips they round down (the turf never
     * rises through a pavement).
     */
    private Column townTurf(int x, int z, Ground ground) {
        double[] q = new double[4];
        boolean any = false;
        int[] tops = null;
        double[] lift = new double[1];
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            double px = x + a, pz = z + b, g = ground.at(px, pz), f = surface(px, pz, g);
            boolean natural = Double.isNaN(f);
            TownWorks.Spot sp = natural ? null : towns.spot(px, pz);
            double w = natural ? 1 : sp != null ? 0 : CrossSection.smooth(1 - Math.abs(f - g) / TO_NATURAL);
            if (!natural && w > 0) {
                w *= joint(x + a, z + b, ground, lift);
                f += lift[0] * w;
            }
            if (natural) {
                if (tops == null) tops = naturalTops(x, z, ground);
                f = joins(tops, a, b) ? naturalSurface(tops, a, b) : g;
            }
            if (!natural) {
                any = true;
                if (sp != null && sp.part() != TownWorks.Part.LOT && sp.part() != TownWorks.Part.YARD) f = Math.floor(f * 8 + 1e-9) / 8;
            }
            q[a + 2 * b] = f;
        }
        if (!any) return Column.NONE;
        int[] fit = fit(q);
        int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16 | (bare(x, z) ? BARE : 0);
        return new Column(Kind.TURF, fit[0], 0, 0, data, 0, 0, 0);
    }

    /** Under the pavement or its bevels (the turf there rounds down). */
    static boolean underPavement(Hit h) { return underPavement(h, 0); }

    /** Under the pavement or its bevels, grown by extra. */
    static boolean underPavement(Hit h, double extra) {
        double v = h.v(), s = h.s(), r = CrossSection.BEVEL_RUN + extra;
        CrossSection sec = h.road().section;
        return v >= sec.halfMedian - r && v <= sec.edge + r && s >= -r && s <= h.road().length + r;
    }

    /**
     * #pavedRange(RoadDesign, int, int) with the junction pads (C3.3b): where a pad holds one of the square's nine points,
     * every one paved by a pad or by any road near (the column's own road first); elsewhere exactly that of the column's
     * road.
     */
    public double[] pavedRange(int x, int z) {
        Junction[] ps = padsNear(x + 0.5, z + 0.5);
        Hit centre = hit(x + 0.5, z + 0.5, REACH);
        // the ground's road (an overpass's span paves nothing on the ground)
        RoadDesign own = centre == null || elevated(centre) ? null : centre.road();
        // an attached ramp (C3.3b group 2) and its host pave a square together
        boolean ramps = rampsNear(x + 0.5, z + 0.5);
        if (ps == null && !ramps) return own == null ? null : pavedRange(own, x, z);
        boolean onPad = false;
        if (ps != null)
            for (int a = 0; a <= 2 && !onPad; a++) for (int b = 0; b <= 2 && !onPad; b++) for (Junction j : ps) if (j.contains(x + a * 0.5, z + b * 0.5)) { onPad = true; break; }
        if (!onPad && !ramps) return own == null ? null : pavedRange(own, x, z);
        RoadDesign[] near = near(x + 0.5, z + 0.5);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            double px = x + a * 0.5, pz = z + b * 0.5, y = Double.NaN;
            if (ps != null) for (Junction j : ps) if (j.contains(px, pz)) { y = j.height(px, pz); break; }
            if (Double.isNaN(y) && own != null) y = pave(own, px, pz);
            if (Double.isNaN(y) && near != null)
                for (RoadDesign d : near) {
                    y = pave(d, px, pz);
                    if (!Double.isNaN(y)) break;
                }
            if (Double.isNaN(y)) return null;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return new double[]{lo, hi};
    }

    /**
     * #paveAt, but NaN where an attached ramp gives way to its host's pavement and on an overpass's span (C3.3b group 2:
     * the deck is not the ground's pavement).
     */
    private double pave(RoadDesign d, double x, double z) {
        double y = paveAt(d, x, z);
        if (Double.isNaN(y)) return y;
        Attached a = attached.isEmpty() ? null : attached.get(d);
        if (a != null && onBand(a.host(), x, z)) return Double.NaN;
        if (!d.spans.isEmpty()) {
            double[] l = d.local(x, z, d.section.edge + 2);
            RoadDesign.Span sp = l == null ? null : d.span(Math.max(0, Math.min(d.length, l[0])));
            if (sp != null && sp.over()) return Double.NaN;
        }
        return y;
    }

    /** A road's pavement height at a point of its paved band (on the route), else NaN. */
    static double paveAt(RoadDesign d, double x, double z) {
        CrossSection sec = d.section;
        double[] l = d.local(x, z, sec.edge + 2);
        if (l == null) return Double.NaN;
        double s = l[0], v = Math.abs(l[1]);
        if (s < 0 || s > d.length || sec.divided && v <= sec.halfMedian || v >= sec.edge) return Double.NaN;
        return d.pavementY(s, l[1]);
    }

    /**
     * The pavement's height at a point for the walls of a dug hole (RoadCells): a pad's, else road d's (its band and a
     * little beyond); without d any road's pavement there, else the driveway's gravel; else NaN.
     */
    public double paveTop(double x, double z, RoadDesign d) {
        Junction j = pad(x, z);
        if (j != null) return j.height(x, z);
        if (d == null) {
            RoadDesign[] near = near(x, z);
            if (near != null)
                for (RoadDesign q : near) {
                    double y = paveAt(q, x, z);
                    if (!Double.isNaN(y)) return y;
                }
            // the forest home's driveway (County road G3): its gravel's height
            return drive.isEmpty() ? Double.NaN : drive.height(x, z);
        }
        double[] l = d.local(x, z, d.section.edge + 4);
        return l == null ? Double.NaN : d.pavementY(l[0], l[1]);
    }

    /**
     * min / max pavement height over the column when the whole 1 x 1 square is paved (on the route; on a divided road on
     * one carriageway), else null.
     */
    public static double[] pavedRange(RoadDesign d, int x, int z) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        CrossSection sec = d.section;
        int side = 0;
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            double[] l = d.local(x + a * 0.5, z + b * 0.5, sec.edge + 2);
            if (l == null) return null;
            double s = l[0], u = l[1], v = Math.abs(u);
            if (s < 0 || s > d.length || sec.divided && v <= sec.halfMedian || v >= sec.edge) return null;
            int sg = u < 0 ? -1 : 1;
            if (sec.divided && side != 0 && sg != side) return null;
            side = sg;
            double y = d.pavementY(s, u);
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return new double[]{lo, hi};
    }

    /**
     * Fit four corner heights (nw, ne, sw, se) to one graded_turf block: {y, shift, nw, ne, sw, se, clamped}. Corners are
     * quantised to 1/8 in world space; shift 0 holds corners in [y, y + 1], shift 1 in [y + 0.5, y + 1.5]. Steeper than
     * one block: the high corners are kept and the low ones lifted (clamped = 1). The R4 sample's fit, unchanged.
     */
    public static int[] fit(double[] f) {
        int[] q = new int[4];
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) { q[i] = (int) Math.round(f[i] * 8); lo = Math.min(lo, q[i]); hi = Math.max(hi, q[i]); }
        int ya = Math.floorDiv(hi - 1, 8);
        if (lo >= ya * 8) return pack(ya, 0, q, ya * 8, false);
        int yb = Math.floorDiv(hi - 4 - 1, 8);
        if (lo >= yb * 8 + 4 && hi <= yb * 8 + 12) return pack(yb, 1, q, yb * 8 + 4, false);
        return pack(ya, 0, q, ya * 8, true);
    }

    private static int[] pack(int y, int shift, int[] q, int base, boolean clamp) {
        int[] out = new int[7];
        out[0] = y;
        out[1] = shift;
        for (int i = 0; i < 4; i++) out[2 + i] = Math.max(0, Math.min(8, q[i] - base));
        out[6] = clamp ? 1 : 0;
        return out;
    }
}
