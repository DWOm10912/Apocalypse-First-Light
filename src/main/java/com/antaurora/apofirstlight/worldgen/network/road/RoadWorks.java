package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

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
 *   1:4 / 1:3 fill slope), which meets the ground at the daylight line by construction; beyond MAX_HALF_WIDTH - 4 the
 *   surface is blended into the ground so it never ends in a step;</li>
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
 */
public final class RoadWorks {
    public static final double NATURAL_TOLERANCE = 0.03, MAX_HALF_WIDTH = 48, BLEND = 4, END_SLOPE = 0.25;
    /** Lateral reach of the works from the centreline (index margin), m. */
    public static final double REACH = MAX_HALF_WIDTH + 2;
    /** Half width the forest and the spawn keep clear of (the engineered zone of a normal cut or fill), m. */
    public static final double CLEAR = 40;
    /** Turf edge to the natural surface (#toNatural): blend height band, and the band kept off the pavement, m. */
    public static final double TO_NATURAL = 1.0, EDGE_KEEP = 1.5, EDGE_RAMP = 3.0;
    /**
     * How far beyond the pavement's bevels a turf column's square must stay to carry plants (a plant's cross fills its
     * square and is offset up to 0.25 m), m (#bare).
     */
    public static final double PLANT_CLEAR = 0.3;
    /** TURF column data flag: no plants (#bare). */
    public static final int BARE = 1 << 17;
    /**
     * PAVED / TURF column data flag: the road's finish over a box culvert's body (group D); the column's bottom is the
     * culvert's bed block less one, the chunk pass lays the culvert under the finish.
     */
    public static final int CULVERT_BODY = 1 << 20;
    /** CULVERT column data: the square reaches the channel, a wall, the roofed channel; keeps the natural ground (Culvert.Cell). */
    public static final int C_CHANNEL = 1, C_WALL = 2, C_ROOF = 4, C_KEEP = 8;

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
    private final Map<Long, RoadDesign[]> byChunk = new HashMap<>();
    private final Map<Long, Culvert[]> culvertsByChunk = new HashMap<>();
    private final List<Culvert> culverts = new ArrayList<>();

    public RoadWorks(List<RoadDesign> roads, Water water) {
        this.roads = List.copyOf(roads);
        this.water = water;
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
            for (double s = -RoadDesign.END; s <= d.length + RoadDesign.END; s += 8) {
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

    public List<RoadDesign> roads() { return roads; }

    public boolean isEmpty() { return roads.isEmpty(); }

    /** Whether any road's works or clearance can reach into this chunk (a cheap test before any per-column work). */
    public boolean touches(int chunkX, int chunkZ) { return byChunk.containsKey(key(chunkX, chunkZ)); }

    public RoadDesign road(String id) {
        for (RoadDesign d : roads) if (d.id.equals(id)) return d;
        return null;
    }

    private RoadDesign[] near(double x, double z) {
        return byChunk.get(key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
    }

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
     * smallest lateral offset of those), else the smallest lateral offset.
     */
    public Hit hit(double x, double z, double maxDist) {
        RoadDesign[] near = near(x, z);
        if (near == null) return null;
        Hit best = null;
        boolean bestOn = false;
        for (RoadDesign d : near) {
            double[] l = d.local(x, z, maxDist);
            if (l == null) continue;
            Hit h = new Hit(d, l[0], l[1]);
            boolean on = onFormation(h);
            if (best == null || on && !bestOn || on == bestOn && h.v() < best.v()) { best = h; bestOn = on; }
        }
        return best;
    }

    /**
     * Finished surface at (x, z) over the natural ground g there; NaN where the works leave the ground natural. A point
     * on a formation takes that road's; elsewhere the works of every road reaching it combine (the class comment).
     */
    public double surface(double x, double z, double g) {
        RoadDesign[] near = near(x, z);
        if (near == null) return Double.NaN;
        if (near.length == 1) {
            double[] l = near[0].local(x, z, REACH);
            return l == null ? Double.NaN : surface(new Hit(near[0], l[0], l[1]), x, z, g);
        }
        Hit[] hs = new Hit[near.length];
        int n = 0;
        Hit on = null;
        for (RoadDesign d : near) {
            double[] l = d.local(x, z, REACH);
            if (l == null) continue;
            Hit h = hs[n++] = new Hit(d, l[0], l[1]);
            if (onFormation(h) && (on == null || h.v() < on.v())) on = h;
        }
        if (on != null) return surface(on, x, z, g);
        if (n <= 1) return n == 0 ? Double.NaN : surface(hs[0], x, z, g);
        double up = 0, down = 0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            double f = surface(hs[i], x, z, g);
            if (Double.isNaN(f)) continue;
            any = true;
            up = Math.max(up, f - g);
            down = Math.max(down, g - f);
        }
        return any ? g + up - down : Double.NaN;
    }

    /** The finished surface of a point already located on a road (h at world x, z). */
    public double surface(Hit h, double x, double z, double g) {
        RoadDesign d = h.road();
        CrossSection sec = d.section;
        double v = h.v(), sc = h.sc(), beyond = h.beyond();
        if (v > MAX_HALF_WIDTH) return Double.NaN;
        if (beyond == 0 && d.span(sc) != null) return Double.NaN;   // under a bridge: the natural valley
        double design;
        if (v <= sec.edge) design = d.formationY(sc, h.u());
        else {
            double h0 = d.formationY(sc, Math.copySign(sec.edge, h.u()));
            design = h0 + sec.roadside(v - sec.edge, g - h0);
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
            double px = x - 1 + i, pz = z - 1 + j;
            off[i + 4 * j] = !Double.isNaN(surface(px, pz, ground.at(px, pz)));
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
     * not next to the pavement: from EDGE_KEEP beyond the paved edge (or the route's end) over EDGE_RAMP.
     */
    static double toNatural(Hit h, double rise) {
        if (h == null) return 0;
        CrossSection sec = h.road().section;
        double dp = Math.hypot(Math.max(0, h.v() - sec.edge), h.beyond());
        return CrossSection.smooth((dp - EDGE_KEEP) / EDGE_RAMP) * CrossSection.smooth(1 - Math.abs(rise) / TO_NATURAL);
    }

    /** RoadWorksRef#height at a cell corner. */
    private double cellHeight(int x, int z, Ground ground) {
        double g = ground.at(x, z), f = surface(x + 0.5, z + 0.5, g);
        return Double.isNaN(f) ? g : f;
    }

    /** Whether a point is inside a road's clearance (forest roots, spawn): within CLEAR + extra of a centreline, ends included. */
    public boolean clear(double x, double z, double extra) {
        return hit(x, z, CLEAR + extra) != null;
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
        CULVERT
    }

    /**
     * One column's finish. y: the collision / turf / barrier top block; layers, upper: collision layers (1..32) of y and
     * y + 1 (0: none; BARRIER: layers of y); data: turf corners (nw | ne << 4 | sw << 8 | se << 12 | shift << 16), for
     * a BARRIER BridgeMesh.WING when it is a wing wall rather than a parapet; bottom: the lowest deck
     * block (DECK, ABUTMENT, BARRIER) or the natural ground the clearing stops at (OPEN); paveLo / paveHi: the column's
     * pavement height range (PAVED, DECK, ABUTMENT).
     */
    public record Column(Kind kind, int y, int layers, int upper, int data, int bottom, double paveLo, double paveHi) {
        public static final Column NONE = new Column(Kind.NONE, 0, 0, 0, 0, 0, 0, 0);
        /** The highest block this column's finish occupies. */
        public int top() { return upper > 0 ? y + 1 : y; }
    }

    /**
     * The finish of block column (x, z). Corners for turf are the finished field at the integer corners (shared by the
     * neighbouring columns of any chunk), quantised to 1/8; under the paved band and its bevels they round down, so the
     * turf never rises through the asphalt.
     */
    public Column column(int x, int z, Ground ground) {
        // a box culvert's own cell (its mouths may reach beyond the works' reach); a cell of its body keeps the finish
        Culvert.Cell cell = culvertCell(x, z);
        if (cell != null && cell.own()) {
            int data = (cell.channel() ? C_CHANNEL : 0) | (cell.wall() ? C_WALL : 0) | (cell.roof() ? C_ROOF : 0) | (cell.keepGround() ? C_KEEP : 0);
            return new Column(Kind.CULVERT, cell.top(), cell.layers(), 0, data, cell.culvert().bed - 1, 0, 0);
        }
        Column c = finish(x, z, ground);
        if (cell != null && (c.kind() == Kind.PAVED || c.kind() == Kind.TURF))
            return new Column(c.kind(), c.y(), c.layers(), c.upper(), c.data() | CULVERT_BODY, cell.culvert().bed - 1, c.paveLo(), c.paveHi());
        return c;
    }

    /** The column's finish without a box culvert's cells (#column). */
    private Column finish(int x, int z, Ground ground) {
        Hit centre = hit(x + 0.5, z + 0.5, REACH);
        if (centre == null) return Column.NONE;
        RoadDesign d = centre.road();
        double[] pave = pavedRange(d, x, z);
        boolean onRoute = centre.beyond() == 0;
        RoadDesign.Span span = onRoute ? d.span(centre.sc()) : null;
        if (span == null) {
            // a column touching a span from outside belongs to the earthworks; one inside a span is handled below
            if (pave != null) return paved(Kind.PAVED, pave, 0);
            // a column on planned water stays water even where a dry corner reaches the works (a stream beside the road,
            // C3.3a), unless the embankment fills it over a box culvert (group D)
            if (!onFormation(centre) && water.wet(x + 0.5, z + 0.5) && !filled(x + 0.5, z + 0.5)) return Column.NONE;
            double[] q = new double[4];
            boolean any = false;
            int k = 0;
            // a wing wall's coping can only lie over a corner near a span end of this road (its corners are within 1 m)
            boolean wings = onRoute && BridgeMesh.nearEnd(d, centre.s(), 2);
            int[] tops = null;
            Hit[] hits = new Hit[4];
            for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
                double g = ground.at(x + a, z + b), f = surface(x + a, z + b, g);
                boolean natural = Double.isNaN(f);
                Hit h = natural ? null : hit(x + a, z + b, REACH);
                // toward the daylight line the turf meets the natural surface as drawn, not the planned ground (group B)
                double w = natural ? 1 : toNatural(h, f - g);
                if (w > 0) {
                    if (tops == null) tops = naturalTops(x, z, ground);
                    double n = naturalSurface(tops, a, b);
                    f = natural ? n : f + (n - g) * w;
                }
                if (!natural) {
                    any = true;
                    if (h != null && underPavement(h)) f = Math.floor(f * 8 + 1e-9) / 8;
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
                Hit h = hits[i] != null ? hits[i] : hit(x + (i & 1), z + (i >> 1), REACH);
                if (h == null || h.beyond() > 0 || !h.road().section.paved(h.v()) || !underPavement(h)) continue;
                double top = h.road().pavementY(h.sc(), h.u());
                if (q[i] > top) q[i] = Math.floor(top * 8 + 1e-9) / 8;
            }
            // corner order nw, ne, sw, se = (x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)
            int[] fit = fit(q);
            int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16 | (bare(x, z) ? BARE : 0);
            return new Column(Kind.TURF, fit[0], 0, 0, data, 0, 0, 0);
        }
        // on a bridge span: the collision of the bridge mesh (BridgeMesh; docs 10.11), every column chosen by its centre
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
        return new Column(Kind.OPEN, (int) Math.floor(deckTop), 0, 0, 0, (int) Math.floor(g), 0, 0);
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
        double w = 1 + 2 * PLANT_CLEAR;
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            Hit h = hit(x - PLANT_CLEAR + a * w, z - PLANT_CLEAR + b * w, REACH);
            if (h != null && underPavement(h, 0.05)) return true;
        }
        return false;
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
