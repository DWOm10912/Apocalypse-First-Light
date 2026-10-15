package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The terrain works of the built roads of one world (C3.2; docs/worldgen/federal_highways_c3.md, 10): the finished
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
 *   <li>roadside: the shoulder hinge plus R4CrossSection.roadside (1:4 foreslope, 1 m ditch, 1:3 backslope, or the
 *   1:4 / 1:3 fill slope), which meets the ground at the daylight line by construction; beyond MAX_HALF_WIDTH - 4 the
 *   surface is blended into the ground so it never ends in a step;</li>
 *   <li>beyond the route's ends: the end cross-section approaching the ground at 1:4;</li>
 *   <li>within a bridge span: no works (the natural valley under the deck);</li>
 *   <li>a point whose result is within 3 cm of the ground is natural (NaN);</li>
 *   <li>roadside works never enter water (a river, lake or the sea beside the road, as at a skewed crossing): the
 *   water keeps its natural bed and flows on; over the formation itself water is always bridged (the route design).</li>
 * </ul>
 * Where two roads are near, the one with the smaller lateral offset wins (C3.2 builds one route; junction geometry is
 * C3.3).
 */
public final class RoadWorks {
    public static final double NATURAL_TOLERANCE = 0.03, MAX_HALF_WIDTH = 48, BLEND = 4, END_SLOPE = 0.25;
    /** Lateral reach of the works from the centreline (index margin), m. */
    public static final double REACH = MAX_HALF_WIDTH + 2;
    /** Half width the forest and the spawn keep clear of (the engineered zone of a normal cut or fill), m. */
    public static final double CLEAR = 40;

    /** The natural ground: the planned surface Y at a point (the density's ground before the works). */
    public interface Ground { double at(double x, double z); }

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

    public RoadWorks(List<RoadDesign> roads, Water water) {
        this.roads = List.copyOf(roads);
        this.water = water;
        Map<Long, List<RoadDesign>> index = new HashMap<>();
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

    /** The road a point lies on within maxDist of a centreline (the smallest lateral offset), or null. */
    public Hit hit(double x, double z, double maxDist) {
        RoadDesign[] near = byChunk.get(key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
        if (near == null) return null;
        Hit best = null;
        for (RoadDesign d : near) {
            double[] l = d.local(x, z, maxDist);
            if (l != null && (best == null || Math.abs(l[1]) < best.v())) best = new Hit(d, l[0], l[1]);
        }
        return best;
    }

    /** Finished surface at (x, z) over the natural ground g there; NaN where the works leave the ground natural. */
    public double surface(double x, double z, double g) {
        Hit h = hit(x, z, REACH);
        return h == null ? Double.NaN : surface(h, x, z, g);
    }

    /** The finished surface of a point already located on a road (h at world x, z). */
    public double surface(Hit h, double x, double z, double g) {
        RoadDesign d = h.road();
        double v = h.v(), sc = h.sc(), beyond = h.beyond();
        if (v > MAX_HALF_WIDTH) return Double.NaN;
        if (beyond == 0 && d.span(sc) != null) return Double.NaN;   // under a bridge: the natural valley
        double design;
        if (v <= R4CrossSection.EDGE) design = d.formationY(sc, h.u());
        else {
            double h0 = d.formationY(sc, Math.copySign(R4CrossSection.EDGE, h.u()));
            design = h0 + R4CrossSection.roadside(v - R4CrossSection.EDGE, g - h0);
        }
        if (beyond > 0) design = approach(g, design, beyond * END_SLOPE);
        if (v > MAX_HALF_WIDTH - BLEND) {
            double t = R4CrossSection.smooth((v - (MAX_HALF_WIDTH - BLEND)) / BLEND);
            design += (g - design) * t;
        }
        boolean formation = v <= R4CrossSection.EDGE + 0.5 && beyond == 0;
        if (!formation && Math.abs(design - g) < NATURAL_TOLERANCE) return Double.NaN;
        if (!formation && water.wet(x, z)) return Double.NaN;
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

    /** Whether a point is inside a road's clearance (forest roots, spawn): within CLEAR + extra of a centreline, ends included. */
    public boolean clear(double x, double z, double extra) {
        return hit(x, z, CLEAR + extra) != null;
    }

    // ------------------------------------------------------------------ columns

    /** What one block column becomes. */
    public enum Kind {
        /** natural: nothing to do */
        NONE,
        /** pavement: collision layers at y (and y + 1 when upper > 0), asphalt, gravel, stone below, fill to the ground */
        PAVED,
        /** graded turf block at y (data: corners), fill below or clear above */
        TURF,
        /** bridge deck: collision layers at y (upper as PAVED), reinforced concrete from bottom to y - 1 */
        DECK,
        /** bridge deck end: a deck column whose concrete continues down to the ground (the abutment wall) */
        ABUTMENT,
        /** bridge parapet or wing wall: reinforced concrete from bottom up to y (at the span's ends from the ground) */
        BARRIER,
        /** under a bridge, off the deck: natural ground; only clear what the density's interpolation left above it */
        OPEN
    }

    /**
     * One column's finish. y: the collision / turf / barrier top block; layers, upper: collision layers (1..32) of y and
     * y + 1 (0: none); data: turf corners (nw | ne << 4 | sw << 8 | se << 12 | shift << 16); bottom: the lowest deck
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
        Hit centre = hit(x + 0.5, z + 0.5, REACH);
        if (centre == null) return Column.NONE;
        RoadDesign d = centre.road();
        double[] pave = pavedRange(d, x, z);
        boolean onRoute = centre.beyond() == 0;
        RoadDesign.Span span = onRoute ? d.span(centre.sc()) : null;
        if (span == null) {
            // a column touching a span from outside belongs to the earthworks; one inside a span is handled below
            if (pave != null) return paved(Kind.PAVED, pave, 0);
            double[] q = new double[4];
            boolean any = false;
            int k = 0;
            for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
                double g = ground.at(x + a, z + b), f = surface(x + a, z + b, g);
                if (!Double.isNaN(f)) {
                    any = true;
                    Hit h = hit(x + a, z + b, REACH);
                    if (h != null && underPavement(h)) f = Math.floor(f * 8 + 1e-9) / 8;
                }
                q[k++] = Double.isNaN(f) ? g : f;
            }
            if (!any) return Column.NONE;
            // corner order nw, ne, sw, se = (x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)
            int[] fit = fit(q);
            int data = fit[2] | fit[3] << 4 | fit[4] << 8 | fit[5] << 12 | fit[1] << 16;
            return new Column(Kind.TURF, fit[0], 0, 0, data, 0, 0, 0);
        }
        // on a bridge span
        double v = centre.v(), sc = centre.sc();
        double deckTop = d.datum(sc);
        int bottom = (int) Math.floor(deckTop - span.depth());
        boolean end = sc - span.s0() < 1.0 || span.s1() - sc < 1.0;
        if (pave != null) {
            Column c = paved(end ? Kind.ABUTMENT : Kind.DECK, pave, 0);
            return new Column(c.kind(), c.y(), c.layers(), c.upper(), 0, bottom, c.paveLo(), c.paveHi());
        }
        boolean outer = v >= R4CrossSection.EDGE - 0.5 && v <= R4CrossSection.EDGE + 1.5;
        boolean inner = v >= R4CrossSection.HALF_MEDIAN - 1.5 && v <= R4CrossSection.HALF_MEDIAN + 0.5;
        if (outer || inner) {
            double edge = d.pavementY(sc, Math.copySign(outer ? R4CrossSection.EDGE : R4CrossSection.HALF_MEDIAN, centre.u()));
            // a 1 m concrete parapet above the pavement edge; at the span's ends it stands on the ground (the abutment)
            int foot = end ? Math.min(bottom, (int) Math.floor(ground.at(x + 0.5, z + 0.5))) : bottom;
            return new Column(Kind.BARRIER, (int) Math.round(edge + 1.0) - 1, 0, 0, 0, foot, edge, edge);
        }
        double g = ground.at(x + 0.5, z + 0.5);
        if (end) {
            // wing wall: the roadside earthworks just outside the span, retained by concrete at the span's end
            double sOut = sc - span.s0() < 1.0 ? span.s0() - 0.01 : span.s1() + 0.01;
            double f = surface(new Hit(d, sOut, centre.u()), x + 0.5, z + 0.5, g);
            if (!Double.isNaN(f) && f > g + 0.5) return new Column(Kind.BARRIER, (int) Math.floor(f), 0, 0, 0, (int) Math.floor(g), f, f);
        }
        return new Column(Kind.OPEN, (int) Math.floor(deckTop), 0, 0, 0, (int) Math.floor(g), 0, 0);
    }

    /** A paved (or deck) column: the asphalt / deck top 2 cm under the lowest pavement point, collision to the mean. */
    public static Column paved(Kind kind, double[] pave, int bottom) {
        int top32 = (int) Math.round((pave[0] + pave[1]) * 0.5 * 32), yc = (int) Math.floor(pave[0] - 0.02);
        int layers = top32 - yc * 32, upper = 0;
        if (layers > 32) { upper = layers - 32; layers = 32; }
        if (layers < 1) layers = 1;
        return new Column(kind, yc, layers, upper, 0, bottom, pave[0], pave[1]);
    }

    /** Under the pavement or its bevels (the turf there rounds down). */
    static boolean underPavement(Hit h) {
        double v = h.v(), s = h.s();
        return v >= R4CrossSection.HALF_MEDIAN - R4CrossSection.BEVEL_RUN && v <= R4CrossSection.EDGE + R4CrossSection.BEVEL_RUN
                && s >= -R4CrossSection.BEVEL_RUN && s <= h.road().length + R4CrossSection.BEVEL_RUN;
    }

    /** min / max pavement height over the column when the whole 1 x 1 square is paved (one side, on the route), else null. */
    public static double[] pavedRange(RoadDesign d, int x, int z) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        int side = 0;
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            double[] l = d.local(x + a * 0.5, z + b * 0.5, R4CrossSection.EDGE + 2);
            if (l == null) return null;
            double s = l[0], u = l[1], v = Math.abs(u);
            if (s < 0 || s > d.length || v <= R4CrossSection.HALF_MEDIAN || v >= R4CrossSection.EDGE) return null;
            int sg = u < 0 ? -1 : 1;
            if (side != 0 && sg != side) return null;
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
