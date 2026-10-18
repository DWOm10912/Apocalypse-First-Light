package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Material;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Triangle;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Vertex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The concrete of a built road's bridges for one chunk (C3.2; docs/worldgen/federal_highways_c3.md, 10.11): the mesh the
 * client draws, while the world holds only the invisible collision (road_structure, road_surface; RoadWorks.column).
 * Pure Java on a RoadDesign, like RoadMesh.
 * <ul>
 *   <li>deck: each carriageway its own deck (a divided road, R4; an undivided one, R2, one deck across the centreline),
 *   swept along the span: a 0.3 m slab with 0.6 m overhangs on a box girder down to the span's soffit, and on both edges
 *   an F-shape-like concrete parapet 1.07 m above the pavement edge, 0.6 m wide, its road face battered 0.2 m; the open
 *   median between the two decks; parapet end caps at the span's ends;</li>
 *   <li>piers (C3.3a; group E, 2026-10-15): a span longer than MAX_SPAN rests on intermediate multi-column bents: under
 *   each girder a cap beam CAP_DEPTH deep and CAP_WIDTH along the road, as wide as the girder, on a row of round columns
 *   (#columns) from the ground (the scanned foot of their cells, which stand on the ground like the abutment band, and the
 *   probes beside them) to the cap; the footings and shafts are buried;</li>
 *   <li>abutments: in the first and last metre of a span, a wall across the whole formation, from the ground (the
 *   scanned foot of the collision) up to the slab's or the girder's underside, and beyond the decks the wing walls up to
 *   the embankment's design (the median, the fill template), with their tops and their lateral ends;</li>
 *   <li>damage: a structure cell whose collision top is not the generated one is broken: its mesh is cut away, what
 *   is left of its collision is drawn as a plain concrete box, and the faces of the intact cells around it are closed.
 *   A wing cell with no structure at all is simply absent (the chunk pass leaves it out where the ground already stands
 *   at the embankment's height, or over water).</li>
 *   <li>overpasses (C3.3b group 2, RoadDesign.Span#over): the same deck, bent and abutment walls over the road they span
 *   (RoadWorks.Column#deck); no wing walls (the approach embankment spills through under the deck's ends); a broken deck
 *   cell cuts the mesh from its underside up and a dug pavement cell under it up to its pavement only.</li>
 * </ul>
 * Texture: the reinforced concrete sprite, 1 m per tile. World triangles are clipped to the chunk (RoadMesh.clipInto);
 * the repairs of broken cells are chunk-relative already.
 */
public final class BridgeMesh {
    public static final double PARAPET_HEIGHT = 1.07, PARAPET_WIDTH = 0.6, PARAPET_BATTER = 0.2, SLAB = 0.3, GIRDER_INSET = 0.6;
    /** A column whose centre lies CELL_IN .. CELL_OUT m outside a deck's edge is a parapet cell. */
    public static final double CELL_IN = 0.1, CELL_OUT = 1.3;
    /** RoadWorks.Column data of a BARRIER that is a wing wall, not a parapet. */
    public static final int WING = 1;
    /** Lateral reach of the abutment and wing walls (where the embankment blends into the ground), m. */
    public static final double WALL_REACH = RoadWorks.MAX_HALF_WIDTH - RoadWorks.BLEND;
    /**
     * A wing wall's coping stands COPING above the embankment's design, and the copings and the parapets reach TAIL behind
     * the span end: a band cell's square reaches at most 0.71 m beyond its centre, so the mesh covers every cell of the
     * band; the coping keeps the concrete clear of the turf it overlaps (2026-10-15).
     */
    public static final double COPING = 0.15, TAIL = 0.75;
    /**
     * Walls reach FOOT_SINK under the scanned foot of their cell: the ground under road_structure is smoothed with its
     * neighbours (TerrainSmooth counts the block as cover) and may dip up to a block under its cube top, which would open a
     * slit under a face that stopped at the foot; inside the ground the face is hidden (2026-10-15).
     */
    public static final double FOOT_SINK = 1.0;
    /** C3.3a: the longest span without a pier, m. */
    public static final double MAX_SPAN = 40;
    /**
     * Group E (2026-10-15): a bent's cap beam, CAP_DEPTH under the girder's soffit and CAP_WIDTH along the road; its round
     * columns have COLUMN_SIDES facets.
     */
    public static final double CAP_DEPTH = 1.2, CAP_WIDTH = 1.4;
    static final int COLUMN_SIDES = 24;
    /** The ground the client assumes for its expectations: below everything, so every possible wing cell is expected. */
    private static final RoadWorks.Ground LOW = (x, z) -> -1e9;

    private BridgeMesh() {}

    /**
     * Top of a wing wall at lateral u for the span end at station sOut: the median's formation, or beside the road the
     * fill template from the shoulder hinge; NaN on a deck or beyond the walls' reach.
     */
    public static double wingTop(RoadDesign d, double sOut, double u) {
        CrossSection sec = d.section;
        double v = Math.abs(u);
        if (sec.divided && v < sec.halfMedian) return d.formationY(sOut, u) + COPING;
        if (v <= sec.edge || v > WALL_REACH) return Double.NaN;
        return d.formationY(sOut, Math.copySign(sec.edge, u)) + sec.roadside(v - sec.edge, -1e9) + COPING;
    }

    /** Within the decks' footprint: between the parapets' outer faces (a divided road's open median excluded). */
    public static boolean footprint(CrossSection sec, double v) {
        return (!sec.divided || v > sec.halfMedian - PARAPET_WIDTH) && v < sec.edge + PARAPET_WIDTH;
    }

    /** Under a deck's box girder (between the slab's overhangs; an undivided road's girder runs across the centre). */
    public static boolean girder(CrossSection sec, double v) {
        return (!sec.divided || v >= sec.halfMedian + GIRDER_INSET) && v <= sec.edge - GIRDER_INSET;
    }

    /** The pier whose cap band (CAP_WIDTH along the road) holds station s, or NaN. */
    static double pierAt(RoadDesign.Span sp, double s) {
        for (double p : sp.piers()) if (Math.abs(s - p) <= CAP_WIDTH / 2) return p;
        return Double.NaN;
    }

    /** A bent column's radius: 0.6 m under an interstate's (divided) deck, 0.5 m under a state road's. */
    public static double columnRadius(CrossSection sec) { return sec.divided ? 0.6 : 0.5; }

    /** A girder's lateral range on one side (u, low to high); an undivided road's one girder runs across the centre. */
    static double[] girderRange(CrossSection sec, int side) {
        double gi = sec.edge - GIRDER_INSET;
        if (!sec.divided) return new double[]{-gi, gi};
        double ga = sec.halfMedian + GIRDER_INSET;
        return side < 0 ? new double[]{-gi, -ga} : new double[]{ga, gi};
    }

    /**
     * The lateral offsets of a bent's columns (group E): under each girder three (two when it is narrower than 9 m), the
     * outer ones 1.5 m in from the girder's sides, as a US multi-column bent spaces them.
     */
    public static double[] columns(CrossSection sec) {
        List<Double> out = new ArrayList<>();
        for (int side : sec.divided ? new int[]{-1, 1} : new int[]{1}) {
            double[] g = girderRange(sec, side);
            double a = g[0] + 1.5, b = g[1] - 1.5;
            out.add(a);
            if (g[1] - g[0] >= 9) out.add((a + b) * 0.5);
            out.add(b);
        }
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** Whether world point (x, z) lies in one of the bent columns of pier p. */
    static boolean inColumn(RoadDesign d, double p, double x, double z) {
        double r = columnRadius(d.section);
        for (double u : columns(d.section)) {
            double[] c = d.point(p, u);
            if (Math.hypot(x - c[0], z - c[1]) <= r) return true;
        }
        return false;
    }

    /** The flat underside of pier p's cap beam under the girder on one side: CAP_DEPTH under the lower soffit over its width. */
    public static double capBottom(RoadDesign d, RoadDesign.Span sp, double p, int side) {
        return Math.min(soffit(d, sp, p - CAP_WIDTH / 2, side), soffit(d, sp, p + CAP_WIDTH / 2, side)) - CAP_DEPTH;
    }

    /**
     * A wing wall's top as the mesh draws it and the collision follows it: wingTop at the walls' lateral breaks, linear
     * between them; NaN on the decks' footprint (|u| between the parapets' outer faces) and beyond the walls' reach at
     * that span end and side (RoadDesign.Span#reach: where the embankment meets the ground, group D).
     */
    public static double wingStrip(RoadDesign d, RoadDesign.Span sp, double sOut, double u) {
        double v = Math.abs(u);
        if (sp.over() || footprint(d.section, v) || v > Math.min(WALL_REACH, sp.reach(sOut, u)) + 1e-9) return Double.NaN;
        double[] breaks = breaks(d.section);
        int k = Arrays.binarySearch(breaks, u);
        if (k >= 0) return wingTop(d, sOut, u);
        int i = -k - 2;
        if (i < 0 || i + 1 >= breaks.length) return Double.NaN;
        double a = wingTop(d, sOut, breaks[i]), b = wingTop(d, sOut, breaks[i + 1]);
        if (Double.isNaN(a) || Double.isNaN(b)) return wingTop(d, sOut, u);
        return a + (b - a) * (u - breaks[i]) / (breaks[i + 1] - breaks[i]);
    }

    /**
     * The bridge's solid at world point (x, z): {bottom, top} of its mesh there (bottom -infinity: down to the ground),
     * or null where the mesh has nothing (2026-10-15). On the decks' footprint: the pavement or the parapet's top, down to
     * the slab's or the girder's underside, in the abutment band (the first metre of a span) and in the walls' tails
     * (TAIL behind a span end) down to the ground; beside the decks the wing walls' copings, from TAIL behind the span
     * end to the wall face. road_structure's collision is this solid within its block (RoadStructureBlock), so no
     * collision stands outside the mesh, whatever cell a skewed crossing puts in the band.
     */
    public static double[] solid(RoadWorks works, double x, double z) {
        // a box culvert's concrete (group D; never within NEAR of a bridge: RoadStructures)
        double[] c = works.culvertSolid(x, z);
        if (c != null) return c;
        // an overpass's deck over another road (C3.3b group 2) is the structure there
        RoadWorks.Hit h = works.overHit(x, z);
        if (h == null) h = works.hit(x, z, RoadWorks.REACH);
        if (h == null || h.beyond() > 0) return null;
        RoadDesign d = h.road();
        double s = h.s(), u = h.u(), v = h.v();
        End en = end(d, s);
        if (en == null) return null;
        RoadDesign.Span sp = en.span();
        double t = en.t(), sOut = en.sOut();
        CrossSection sec = d.section;
        double hm = sec.halfMedian, edge = sec.edge;
        if (footprint(sec, v)) {
            int side = u < 0 ? -1 : 1;
            double top;
            if (sec.paved(v)) top = d.pavementY(s, u);
            else if (v > edge) top = d.pavementY(s, side * edge) + PARAPET_HEIGHT * Math.min(1, (v - edge) / PARAPET_BATTER);
            else top = d.pavementY(s, side * hm) + PARAPET_HEIGHT * Math.min(1, (hm - v) / PARAPET_BATTER);
            if (t <= 1) return new double[]{Double.NEGATIVE_INFINITY, top};
            boolean girder = girder(sec, v);
            // a bent (group E): its columns from the ground, its cap beam under the girder
            double p = girder ? pierAt(sp, s) : Double.NaN;
            if (!Double.isNaN(p))
                return new double[]{inColumn(d, p, x, z) ? Double.NEGATIVE_INFINITY : capBottom(d, sp, p, side), top};
            double bottom = girder ? soffit(d, sp, s, side) : d.pavementY(s, side * (v > (hm + edge) * 0.5 ? edge : hm)) - SLAB;
            return new double[]{bottom, top};
        }
        if (t > 1) return null;
        double f = wingStrip(d, sp, sOut, u);
        return Double.isNaN(f) ? null : new double[]{Double.NEGATIVE_INFINITY, f};
    }

    /** The span whose end walls reach station s (TAIL behind its end to its middle), its nearer end, and s's depth into it. */
    record End(RoadDesign.Span span, double sOut, double t) {}

    /** Whether a station lies within margin of a span end's wing zone (end() could be non-null within margin of it). */
    static boolean nearEnd(RoadDesign d, double s, double margin) {
        for (RoadDesign.Span q : d.spans)
            if (q.s1() - q.s0() >= 2 && s >= q.s0() - TAIL - margin && s <= q.s1() + TAIL + margin) return true;
        return false;
    }

    static End end(RoadDesign d, double s) {
        for (RoadDesign.Span q : d.spans) {
            if (q.s1() - q.s0() < 2 || s < q.s0() - TAIL || s > q.s1() + TAIL) continue;
            boolean first = s - q.s0() <= q.s1() - s;
            return new End(q, first ? q.s0() : q.s1(), first ? s - q.s0() : q.s1() - s);
        }
        return null;
    }

    /**
     * A wing wall's coping at a point beside the decks, from TAIL behind a span end to the wall face 1 m into it
     * (wingStrip); NaN elsewhere. The turf under it lies COPING lower (RoadWorks.column, group D).
     */
    public static double wing(RoadWorks works, double x, double z) {
        return wing(works.hit(x, z, RoadWorks.REACH));
    }

    /** wing() at the point of a hit (RoadWorks#hit with REACH). */
    static double wing(RoadWorks.Hit h) {
        if (h == null || h.beyond() > 0) return Double.NaN;
        RoadDesign d = h.road();
        End en = end(d, h.s());
        if (en == null || en.t() > 1 || footprint(d.section, h.v())) return Double.NaN;
        return wingStrip(d, en.span(), en.sOut(), h.u());
    }

    /**
     * The collision boxes of a road_structure block at (x, y, z), block-local {x0, y0, z0, x1, y1, z1}: solid() and a
     * tunnel's solids (TunnelWorks#solids: the lining, the walkways, the portals; several intervals per point) on 8 x 8
     * columns of the block, heights to 1/32, runs along x merged. Empty where the mesh has nothing in the block.
     */
    public static List<double[]> boxes(RoadWorks works, int x, int y, int z) {
        List<double[]> out = new ArrayList<>();
        for (int j = 0; j < 8; j++) {
            int start = -1;
            double[] run = null;
            for (int i = 0; i <= 8; i++) {
                double[] cur = i < 8 ? clipped(works, x + (i + 0.5) / 8, y, z + (j + 0.5) / 8) : null;
                if (start >= 0 && Arrays.equals(cur, run)) continue;
                if (start >= 0) for (int k = 0; k < run.length; k += 2) out.add(new double[]{start / 8.0, run[k], j / 8.0, i / 8.0, run[k + 1], (j + 1) / 8.0});
                start = cur == null ? -1 : i;
                run = cur;
            }
        }
        return out;
    }

    /** The solid intervals at world point (x, z) within block row y, block-local and quantised to 1/32; null: none. */
    private static double[] clipped(RoadWorks works, double x, int y, double z) {
        double[] c = solid(works, x, z), t = TunnelWorks.solids(works, x, z);
        if (c == null && t == null) return null;
        double[] all = c == null ? t : t == null ? c : concat(c, t);
        double[] out = new double[all.length];
        int n = 0;
        for (int k = 0; k + 1 < all.length; k += 2) {
            double lo = Math.max(0, Math.round((all[k] - y) * 32) / 32.0), hi = Math.min(1, Math.round((all[k + 1] - y) * 32) / 32.0);
            if (!(hi > lo)) continue;
            out[n++] = lo;
            out[n++] = hi;
        }
        return n == 0 ? null : Arrays.copyOf(out, n);
    }

    private static double[] concat(double[] a, double[] b) {
        double[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** The soffit (girder underside) of the deck on one side at station s: the span's depth, kept under the slab. */
    static double soffit(RoadDesign d, RoadDesign.Span sp, double s, int side) {
        CrossSection sec = d.section;
        // an undivided deck's one girder: the same soffit on both halves (the lower pavement edge)
        double low = sec.divided ? Math.min(d.pavementY(s, side * sec.edge), d.pavementY(s, side * sec.halfMedian))
                : Math.min(d.pavementY(s, sec.edge), d.pavementY(s, -sec.edge));
        return Math.min(d.datum(s) - sp.depth(), low - SLAB - 0.2);
    }

    // ------------------------------------------------------------------ expected and found cells

    /**
     * A structure cell as the chunk pass made it: kind; y, the collision block (DECK, ABUTMENT) or the parapet / wall top
     * block; top, surface, layers: the topmost block (road_surface or road_structure) and its layers; wing; capTop: the
     * height an intact cell's face reaches (pavement, parapet or wall top); meshBottom: the mesh's underside under a
     * hanging cell (NaN: the cell stands on the ground, use the scanned foot). Kind OPEN: a ground probe in front of a wall
     * or a pier (group D): the scan finds the ground there (Found.foot, its top face) and the wall reaches down to it.
     */
    public record Expect(int index, RoadWorks.Kind kind, int y, int top, boolean surface, int layers, boolean wing, double capTop, double meshBottom) {
        public boolean probe() { return kind == RoadWorks.Kind.OPEN; }
    }

    /**
     * A structure cell as found in the world: its topmost structure block, that block's kind and layers, the lowest one;
     * for a probe the ground's top block (top) and its top face (foot).
     */
    public record Found(int top, boolean surface, int layers, int foot) {}

    public enum State { PRESENT, BROKEN, ABSENT }

    /**
     * An intact cell is PRESENT, one whose top is not the generated one BROKEN (group D: a wing cell always has its top
     * block, so a missing one is broken too); a probe is PRESENT when it found the ground.
     */
    public static State state(Expect e, Found f) {
        if (e == null) return State.ABSENT;
        if (e.probe()) return f != null ? State.PRESENT : State.ABSENT;
        if (f != null && f.top() == e.top() && f.surface() == e.surface() && f.layers() == e.layers()) return State.PRESENT;
        return State.BROKEN;
    }

    /** Whether a cell holds structure as generated (a probe is not structure). */
    static boolean intact(Expect e, Found f) { return e != null && !e.probe() && state(e, f) == State.PRESENT; }

    /** A ground probe this far in front of a wall face (1 m into a span) or a pier face, m. */
    public static final double PROBE = 0.4;

    /**
     * The structure cells of chunk (cx, cz) and its ring (RoadCells indices): bridge cells, box culvert cells (with
     * concrete), and the ground probes in front of the span ends' walls and the piers; empty when no structure is near.
     */
    public static Map<Integer, Expect> expected(RoadWorks works, int cx, int cz) {
        Map<Integer, Expect> out = new HashMap<>();
        for (int lz = -1; lz <= 16; lz++) for (int lx = -1; lx <= 16; lx++) {
            Culvert.Cell cl = works.culvertCell(cx * 16 + lx, cz * 16 + lz);
            if (cl == null || cl.layers() == 0) continue;
            int i = RoadCells.index(lx, lz);
            out.put(i, new Expect(i, RoadWorks.Kind.CULVERT, cl.top(), cl.top(), false, cl.layers(), false, cl.top() + cl.layers() / 32.0,
                    cl.wall() ? Double.NaN : cl.culvert().soffit));
        }
        boolean near = false;
        for (RoadDesign d : works.roads()) {
            double[] r = stations(d, cx, cz);
            if (r != null) for (RoadDesign.Span sp : d.spans) near |= sp.s0() <= r[1] && sp.s1() >= r[0];
        }
        if (!near) return out;
        for (int lz = -1; lz <= 16; lz++) for (int lx = -1; lx <= 16; lx++) {
            int x = cx * 16 + lx, z = cz * 16 + lz;
            // an overpass's deck (C3.3b group 2) stands over the finish of the road under it
            RoadWorks.Hit h = works.overHit(x + 0.5, z + 0.5);
            if (h == null) h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
            if (h == null || h.beyond() > 0) continue;
            RoadDesign d = h.road();
            RoadDesign.Span sp = d.span(h.sc());
            if (sp == null) continue;
            RoadWorks.Column c = works.column(x, z, LOW);
            if (c.deck() != null) c = c.deck();
            boolean end = RoadWorks.band(d, sp, x, z);
            int side = h.u() < 0 ? -1 : 1, i = RoadCells.index(lx, lz);
            double v = h.v();
            switch (c.kind()) {
                case DECK, ABUTMENT -> {
                    boolean girder = girder(d.section, v);
                    double bottom = c.kind() == RoadWorks.Kind.ABUTMENT ? Double.NaN
                            : girder ? soffit(d, sp, h.sc(), side) : c.paveLo() - SLAB;
                    // a cell under a bent's cap beam hangs down to the cap's underside (group E)
                    double cap = c.kind() == RoadWorks.Kind.DECK ? RoadWorks.cap(d, sp, x, z) : Double.NaN;
                    if (!Double.isNaN(cap)) bottom = Math.min(bottom, cap);
                    out.put(i, new Expect(i, c.kind(), c.y(), c.top(), true, c.upper() > 0 ? c.upper() : c.layers(), false, c.paveLo(), bottom));
                }
                case BARRIER -> {
                    boolean wing = c.data() == WING;
                    double bottom = wing || end ? Double.NaN : c.paveLo() - SLAB;
                    out.put(i, new Expect(i, c.kind(), c.y(), c.y(), false, c.layers(), wing, c.y() + c.layers() / 32.0, bottom));
                }
                default -> { }
            }
        }
        // the ground probes: PROBE in front of each span end's wall face, on every wall strip, and PROBE beside each bent
        // column, both ways along the road (group E)
        for (RoadDesign d : works.roads()) {
            double[] r = stations(d, cx, cz);
            if (r == null) continue;
            double[] breaks = breaks(d.section);
            double cr = columnRadius(d.section);
            for (RoadDesign.Span sp : d.spans) {
                if (sp.s1() - sp.s0() < 2 || sp.s0() > r[1] || sp.s1() < r[0]) continue;
                for (double p : sp.piers())
                    for (double u : columns(d.section))
                        for (double ds : new double[]{-cr - PROBE, cr + PROBE}) {
                            double[] q = d.point(p + ds, u);
                            int lx = (int) Math.floor(q[0]) - cx * 16, lz = (int) Math.floor(q[1]) - cz * 16;
                            if (lx < -1 || lx > 16 || lz < -1 || lz > 16) continue;
                            int i = RoadCells.index(lx, lz), top = (int) Math.ceil(d.datum(p)) + 2;
                            out.putIfAbsent(i, new Expect(i, RoadWorks.Kind.OPEN, top, top, false, 0, false, Double.NaN, Double.NaN));
                        }
                List<Double> at = new ArrayList<>(List.of(sp.s0() + 1 + PROBE, sp.s1() - 1 - PROBE));
                for (double s : at) {
                    int top = (int) Math.ceil(d.datum(s)) + 2;
                    for (int k = 0; k + 1 < breaks.length; k++) {
                        double[] p = d.point(s, (breaks[k] + breaks[k + 1]) * 0.5);
                        int lx = (int) Math.floor(p[0]) - cx * 16, lz = (int) Math.floor(p[1]) - cz * 16;
                        if (lx < -1 || lx > 16 || lz < -1 || lz > 16) continue;
                        int i = RoadCells.index(lx, lz);
                        out.putIfAbsent(i, new Expect(i, RoadWorks.Kind.OPEN, top, top, false, 0, false, Double.NaN, Double.NaN));
                    }
                }
            }
        }
        return out;
    }

    /** The inner cells (lx | lz << 4) whose structure is broken. */
    public static boolean[] broken(Map<Integer, Expect> expect, Map<Integer, Found> found) {
        boolean[] out = new boolean[256];
        if (found == null) return out;
        for (Expect e : expect.values())
            if (RoadCells.inner(e.index()) && !e.probe() && state(e, found.get(e.index())) == State.BROKEN)
                out[RoadCells.lx(e.index()) | RoadCells.lz(e.index()) << 4] = true;
        return out;
    }

    // ------------------------------------------------------------------ geometry

    /**
     * A chunk's drawn triangles after its pavement (RoadClient's mesh, and the server's hit meshes, meshhit/RoadStructureHits):
     * the bridges, the dug paved cells and the broken structure cells cut out, the holes lined, the broken cells repaired.
     */
    public static List<Triangle> finish(List<Triangle> pavement, List<RoadDesign> rs, RoadWorks works, int cx, int cz,
                                        Map<Integer, RoadCells.Cell> cells, BitSet dug, Map<Integer, Expect> expect, Map<Integer, Found> found) {
        List<Triangle> tris = new ArrayList<>(pavement);
        tris.addAll(chunk(works, cx, cz, expect, found));
        // the removed cells, each over a height range: a broken structure cell from its mesh's underside up (the road under
        // an overpass's deck stays, C3.3b group 2), a dug paved cell up to its pavement (the deck over it stays)
        boolean[] removed = new boolean[256];
        double[] lo = new double[256], hi = new double[256];
        Arrays.fill(lo, Double.POSITIVE_INFINITY);
        Arrays.fill(hi, Double.NEGATIVE_INFINITY);
        if (found != null)
            for (Expect e : expect.values()) {
                if (!RoadCells.inner(e.index()) || e.probe() || state(e, found.get(e.index())) != State.BROKEN) continue;
                int k = RoadCells.lx(e.index()) | RoadCells.lz(e.index()) << 4;
                removed[k] = true;
                lo[k] = Math.min(lo[k], Double.isNaN(e.meshBottom()) ? Double.NEGATIVE_INFINITY : e.meshBottom() - 0.5);
                hi[k] = Double.POSITIVE_INFINITY;
            }
        for (int i = dug.nextSetBit(0); i >= 0; i = dug.nextSetBit(i + 1)) {
            if (!RoadCells.inner(i)) continue;
            int k = RoadCells.lx(i) | RoadCells.lz(i) << 4;
            RoadCells.Cell cl = cells.get(i);
            removed[k] = true;
            lo[k] = Double.NEGATIVE_INFINITY;
            hi[k] = Math.max(hi[k], cl == null ? Double.POSITIVE_INFINITY : cl.y() + 2.0);
        }
        boolean any = false;
        for (boolean b : removed) any |= b;
        if (any) tris = RoadMesh.without(tris, removed, lo, hi);
        if (!dug.isEmpty()) tris.addAll(RoadCells.walls(works, rs, cells, dug, cx, cz));
        tris.addAll(repairs(expect, found, cells));
        // the tunnels (Tunnel V1): drawn whole, never cut by broken cells (no damage scan for their cells yet)
        tris.addAll(TunnelMesh.chunk(works, cx, cz));
        return tris;
    }

    /**
     * The structure triangles of all roads in chunk (cx, cz), chunk-relative: decks and parapets always; the box culverts
     * always (their faces reach into the bed, group D); the abutment, wing and pier walls only once the cells were
     * scanned (found != null), since they stand on the scanned feet.
     */
    public static List<Triangle> chunk(RoadWorks works, int cx, int cz, Map<Integer, Expect> expect, Map<Integer, Found> found) {
        List<Triangle> out = new ArrayList<>();
        if (expect.isEmpty()) return out;
        List<Triangle> world = new ArrayList<>();
        for (RoadDesign d : works.roads()) {
            double[] r = stations(d, cx, cz);
            if (r == null) continue;
            for (RoadDesign.Span sp : d.spans) {
                if (sp.s0() > r[1] || sp.s1() < r[0]) continue;
                deck(world, d, sp, r[0], r[1]);
                if (found != null) walls(world, d, sp, r[0], r[1], cx, cz, expect, found);
            }
        }
        Culvert[] cs = works.culvertsNear(cx * 16 + 8, cz * 16 + 8);
        if (cs != null)
            for (Culvert c : cs) {
                double[] b = c.bounds(0.5);
                if (b[2] < cx * 16 || b[0] > cx * 16 + 16 || b[3] < cz * 16 || b[1] > cz * 16 + 16) continue;
                c.mesh(world);
            }
        for (Triangle t : world) RoadMesh.clipInto(out, t, cx * 16, cz * 16);
        return out;
    }

    /** The station range of a road whose deck or walls can reach chunk (cx, cz), with margin; null when it misses. */
    static double[] stations(RoadDesign d, int cx, int cz) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4; i++) for (int j = 0; j <= 4; j++) {
            double[] l = d.local(cx * 16 + i * 4, cz * 16 + j * 4, WALL_REACH + 4);
            if (l == null) continue;
            lo = Math.min(lo, l[0]);
            hi = Math.max(hi, l[0]);
        }
        return lo > hi ? null : new double[]{lo - 4, hi + 4};
    }

    /**
     * The cross-section of one deck (w outward from the centreline, y), clockwise from the pavement's outer edge. An
     * undivided road's half section ends on the centreline (its girder's bottom, then the centre plane, never drawn).
     */
    private static double[][] section(RoadDesign d, RoadDesign.Span sp, double s, int side) {
        double hm = d.section.halfMedian, edge = d.section.edge;
        double hE = d.pavementY(s, side * edge), hM = d.pavementY(s, side * hm), so = soffit(d, sp, s, side);
        double top = PARAPET_HEIGHT;
        if (!d.section.divided)
            return new double[][]{
                    {edge, hE}, {edge + PARAPET_BATTER, hE + top}, {edge + PARAPET_WIDTH, hE + top}, {edge + PARAPET_WIDTH, hE - SLAB},
                    {edge - GIRDER_INSET, hE - SLAB}, {edge - GIRDER_INSET, so}, {0, so}, {0, hM}};
        return new double[][]{
                {edge, hE}, {edge + PARAPET_BATTER, hE + top}, {edge + PARAPET_WIDTH, hE + top}, {edge + PARAPET_WIDTH, hE - SLAB},
                {edge - GIRDER_INSET, hE - SLAB}, {edge - GIRDER_INSET, so}, {hm + GIRDER_INSET, so}, {hm + GIRDER_INSET, hM - SLAB},
                {hm - PARAPET_WIDTH, hM - SLAB}, {hm - PARAPET_WIDTH, hM + top}, {hm - PARAPET_BATTER, hM + top}, {hm, hM}};
    }

    private static void deck(List<Triangle> out, RoadDesign d, RoadDesign.Span sp, double lo, double hi) {
        double hm = d.section.halfMedian, edge = d.section.edge;
        boolean divided = d.section.divided;
        double a = Math.max(sp.s0(), lo), b = Math.min(sp.s1(), hi);
        if (b - a < 1e-6) return;
        List<Double> st = new ArrayList<>();
        st.add(a);
        for (double s = Math.floor(a) + 1; s < b - 1e-6; s++) if (s > a + 1e-6) st.add(s);
        st.add(b);
        for (int side : new int[]{-1, 1}) {
            for (int k = 0; k + 1 < st.size(); k++) {
                double sa = st.get(k), sb = st.get(k + 1), base = Math.floor(sa + 1e-9);
                double[][] pa = section(d, sp, sa, side), pb = section(d, sp, sb, side);
                double[] lat = lateral(d, (sa + sb) * 0.5);
                // the pavement (last point -> P0) is RoadMesh's; an undivided deck's centre plane is inside the girder
                for (int i = 0; i + 1 < pa.length; i++) {
                    if (Math.abs(pa[i][0]) < 1e-9 && Math.abs(pa[i + 1][0]) < 1e-9) continue;
                    double dw = (pa[i + 1][0] + pb[i + 1][0] - pa[i][0] - pb[i][0]) * 0.5, dy = (pa[i + 1][1] + pb[i + 1][1] - pa[i][1] - pb[i][1]) * 0.5;
                    double len = Math.hypot(dw, dy);
                    if (len < 1e-6) continue;
                    // outward normal of a clockwise section: (-dy, dw) in (w, y)
                    double nw = -dy / len, ny = dw / len;
                    double nx = nw * side * lat[0], nz = nw * side * lat[1];
                    int n = Math.max(1, (int) Math.ceil(len - 1e-6));
                    double tu = len / n;
                    for (int j = 0; j < n; j++) {
                        double t0 = (double) j / n, t1 = (double) (j + 1) / n;
                        Vertex a0 = on(d, sa, side, pa[i], pa[i + 1], t0, 0, sa - base), a1 = on(d, sa, side, pa[i], pa[i + 1], t1, tu, sa - base),
                                b1 = on(d, sb, side, pb[i], pb[i + 1], t1, tu, sb - base), b0 = on(d, sb, side, pb[i], pb[i + 1], t0, 0, sb - base);
                        polygon(out, List.of(a0, a1, b1, b0), nx, ny, nz);
                    }
                }
            }
            // the parapets' tails, TAIL behind each span end (over the abutment band's cells), with their end caps
            for (int e = 0; e < 2; e++) {
                double sEnd = e == 0 ? sp.s0() - TAIL : sp.s1() + TAIL;
                double ra = Math.max(Math.min(sEnd, e == 0 ? sp.s0() : sp.s1()), lo), rb = Math.min(Math.max(sEnd, e == 0 ? sp.s0() : sp.s1()), hi);
                List<Double> ts = new ArrayList<>();
                ts.add(ra);
                for (double s = Math.floor(ra) + 1; s < rb - 1e-6; s++) if (s > ra + 1e-6) ts.add(s);
                ts.add(rb);
                for (int k = 0; k + 1 < ts.size() && rb - ra > 1e-6; k++) {
                    double ta = ts.get(k), tb = ts.get(k + 1);
                    double[] lat = lateral(d, (ta + tb) * 0.5);
                    double[][] pa = section(d, sp, ta, side), pb = section(d, sp, tb, side);
                    double base = Math.floor(ta + 1e-9);
                    // outer: road face, top, outer face, underside; inner: underside, median face, top, road face
                    double[][][] outer = {{pa[0], pa[1], pa[2], pa[3], {edge, pa[0][1] - SLAB}}, {pb[0], pb[1], pb[2], pb[3], {edge, pb[0][1] - SLAB}}};
                    double[][][][] parts = divided
                            ? new double[][][][]{outer, {{{hm, pa[11][1] - SLAB}, pa[8], pa[9], pa[10], pa[11]}, {{hm, pb[11][1] - SLAB}, pb[8], pb[9], pb[10], pb[11]}}}
                            : new double[][][][]{outer};
                    for (double[][][] q : parts)
                        for (int i = 0; i + 1 < q[0].length; i++) {
                            double dw = (q[0][i + 1][0] + q[1][i + 1][0] - q[0][i][0] - q[1][i][0]) * 0.5, dy = (q[0][i + 1][1] + q[1][i + 1][1] - q[0][i][1] - q[1][i][1]) * 0.5;
                            double len = Math.hypot(dw, dy);
                            if (len < 1e-6) continue;
                            double nw = -dy / len, ny = dw / len;
                            int n = Math.max(1, (int) Math.ceil(len - 1e-6));
                            double tu = len / n;
                            for (int j = 0; j < n; j++) {
                                double t0 = (double) j / n, t1 = (double) (j + 1) / n;
                                Vertex a0 = on(d, ta, side, q[0][i], q[0][i + 1], t0, 0, ta - base), a1 = on(d, ta, side, q[0][i], q[0][i + 1], t1, tu, ta - base),
                                        b1 = on(d, tb, side, q[1][i], q[1][i + 1], t1, tu, tb - base), b0 = on(d, tb, side, q[1][i], q[1][i + 1], t0, 0, tb - base);
                                polygon(out, List.of(a0, a1, b1, b0), nw * side * lat[0], ny, nw * side * lat[1]);
                            }
                        }
                }
                if (sEnd < lo || sEnd > hi) continue;
                double[] t = tangent(d, sEnd);
                double sg = e == 0 ? -1 : 1;
                double[][] p = section(d, sp, sEnd, side);
                cap(out, d, sEnd, side, new double[][]{p[0], p[1], p[2], p[3], {edge, p[0][1] - SLAB}}, edge, p[1][1], sg * t[0], sg * t[1]);
                if (divided)
                    cap(out, d, sEnd, side, new double[][]{p[8], p[9], p[10], p[11], {hm, p[11][1] - SLAB}}, hm - PARAPET_WIDTH, p[9][1], sg * t[0], sg * t[1]);
            }
        }
    }

    private static void cap(List<Triangle> out, RoadDesign d, double s, int side, double[][] poly, double w0, double top, double nx, double nz) {
        List<Vertex> p = new ArrayList<>();
        for (double[] q : poly) {
            double[] xz = d.point(s, side * q[0]);
            p.add(new Vertex(xz[0], q[1], xz[1], clamp(q[0] - w0), clamp((top - q[1]) / 1.4)));
        }
        polygon(out, p, nx, 0, nz);
    }

    private static Vertex on(RoadDesign d, double s, int side, double[] p, double[] q, double t, double tu, double tv) {
        double w = p[0] + (q[0] - p[0]) * t, y = p[1] + (q[1] - p[1]) * t;
        double[] xz = d.point(s, side * w);
        return new Vertex(xz[0], y, xz[1], tu, tv);
    }

    private enum Zone { MEDIAN, INNER, GIRDER, OUTER, WING }

    private static Zone zone(CrossSection sec, double v) {
        if (sec.divided && v < sec.halfMedian - PARAPET_WIDTH) return Zone.MEDIAN;
        if (sec.divided && v < sec.halfMedian + GIRDER_INSET) return Zone.INNER;
        if (v < sec.edge - GIRDER_INSET) return Zone.GIRDER;
        if (v < sec.edge + PARAPET_WIDTH) return Zone.OUTER;
        return Zone.WING;
    }

    /** Lateral strip edges of the end walls and piers: every 0.5 m, plus the parapets' and girders' faces; per section. */
    private static final double[] BREAKS_R4 = makeBreaks(CrossSection.R4), BREAKS_R2 = makeBreaks(CrossSection.R2);

    static double[] breaks(CrossSection sec) { return sec == CrossSection.R4 ? BREAKS_R4 : sec == CrossSection.R2 ? BREAKS_R2 : makeBreaks(sec); }

    private static double[] makeBreaks(CrossSection sec) {
        TreeSet<Double> set = new TreeSet<>();
        for (int k = (int) Math.round(-WALL_REACH * 2); k <= (int) Math.round(WALL_REACH * 2); k++) set.add(k * 0.5);
        List<Double> faces = new ArrayList<>(List.of(sec.edge - GIRDER_INSET, sec.edge + PARAPET_WIDTH));
        if (sec.divided) { faces.add(sec.halfMedian - PARAPET_WIDTH); faces.add(sec.halfMedian + GIRDER_INSET); }
        for (double b : faces) { set.add(b); set.add(-b); }
        return set.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double wallTop(RoadDesign d, RoadDesign.Span sp, Zone z, double sOut, double sf, double u, int side) {
        return switch (z) {
            case MEDIAN, WING -> wingTop(d, sOut, u);
            case INNER -> d.pavementY(sf, side * d.section.halfMedian) - SLAB;
            case GIRDER -> soffit(d, sp, sf, side);
            case OUTER -> d.pavementY(sf, side * d.section.edge) - SLAB;
        };
    }

    /**
     * The walls of both span ends. Abutment strips (under the decks): the front face 1 m inside the span from the foot to
     * the slab's or the girder's underside, with the lateral ends of a run. Median and wing strips (group D, 2026-10-15):
     * one continuous prism per 0.5 m strip, as far out as the span end's reach, from TAIL behind the end to the face 1 m
     * inside: the coping (wingStrip) split at the end, the front face, the back face down to the foot (under the turf),
     * and the outer lateral end at the reach. Every strip's foot is the lowest ground known under it and in front of it
     * (#foot), FOOT_SINK deeper.
     */
    private static void walls(List<Triangle> out, RoadDesign d, RoadDesign.Span sp, double lo, double hi, int cx, int cz,
                              Map<Integer, Expect> expect, Map<Integer, Found> found) {
        if (sp.s1() - sp.s0() < 2) return;
        double[] breaks = breaks(d.section);
        piers(out, d, sp, lo, hi, cx, cz, expect, found, breaks);
        for (int e = 0; e < 2; e++) {
            double sOut = e == 0 ? sp.s0() : sp.s1(), sgn = e == 0 ? 1 : -1, sf = sOut + sgn;
            if (sOut < lo || sOut > hi) continue;
            double[] t = tangent(d, sf), lat = lateral(d, sf);
            int n = breaks.length - 1;
            State[] state = new State[n];
            double[] foot = new double[n];
            double[] ta = new double[n], tb = new double[n];
            Zone[] zones = new Zone[n];
            for (int i = 0; i < n; i++) {
                double ua = breaks[i], ub = breaks[i + 1], um = (ua + ub) * 0.5;
                int side = um < 0 ? -1 : 1;
                zones[i] = zone(d.section, Math.abs(um));
                if (zones[i] == Zone.MEDIAN || zones[i] == Zone.WING) continue;
                ta[i] = wallTop(d, sp, zones[i], sOut, sf, ua, side);
                tb[i] = wallTop(d, sp, zones[i], sOut, sf, ub, side);
                int idx = stripCell(d, sOut, sgn, um, cx, cz);
                if (idx < 0) { state[i] = null; continue; }   // outside the window: not drawn here
                state[i] = Double.isNaN(ta[i]) || Double.isNaN(tb[i]) ? State.ABSENT : state(expect.get(idx), found.get(idx));
                if (state[i] == State.PRESENT) {
                    foot[i] = foot(d, sOut, sgn, 0, 1, ua, ub, cx, cz, expect, found);
                    if (Double.isNaN(foot[i])) foot[i] = found.get(idx).foot() - FOOT_SINK;
                }
            }
            for (int i = 0; i < n; i++) {
                double ua = breaks[i], ub = breaks[i + 1], tu0 = ua - Math.floor(ua + 1e-9), tu1 = tu0 + (ub - ua);
                if (zones[i] == Zone.MEDIAN || zones[i] == Zone.WING) {
                    wingPrism(out, d, sp, sOut, sgn, ua, ub, cx, cz, expect, found);
                    continue;
                }
                if (state[i] != State.PRESENT) continue;
                double[] fa = d.point(sf, ua), fb = d.point(sf, ub);
                wall(out, fa[0], fa[1], fb[0], fb[1], foot[i], foot[i], ta[i], tb[i], tu0, tu1, sgn * t[0], 0, sgn * t[1]);
                // a lateral end of a run: the next strip has no abutment wall (no expected cell) and is no wing strip
                if (i == 0 || state[i - 1] == State.ABSENT || isWing(zones[i - 1]) && Double.isNaN(wingStrip(d, sp, sOut, ua - 0.01))) {
                    double[] o = d.point(sOut, ua);
                    wall(out, o[0], o[1], fa[0], fa[1], foot[i], foot[i], ta[i], ta[i], 0, 1, -lat[0], 0, -lat[1]);
                }
                if (i == n - 1 || state[i + 1] == State.ABSENT || isWing(zones[i + 1]) && Double.isNaN(wingStrip(d, sp, sOut, ub + 0.01))) {
                    double[] o = d.point(sOut, ub);
                    wall(out, o[0], o[1], fb[0], fb[1], foot[i], foot[i], tb[i], tb[i], 0, 1, lat[0], 0, lat[1]);
                }
            }
        }
    }

    private static boolean isWing(Zone z) { return z == Zone.MEDIAN || z == Zone.WING; }

    /**
     * One median or wing wall strip ua .. ub of span end sOut, world space: the coping over t = -TAIL .. 0 and 0 .. 1, the
     * front face at t = 1 and the back face at t = -TAIL from the foot, and the lateral end where the next strip outward
     * has no wall (the reach). Nothing where the strip has no wall.
     */
    private static void wingPrism(List<Triangle> out, RoadDesign d, RoadDesign.Span sp, double sOut, double sgn, double ua, double ub,
                                  int cx, int cz, Map<Integer, Expect> expect, Map<Integer, Found> found) {
        double ha = wingStrip(d, sp, sOut, ua), hb = wingStrip(d, sp, sOut, ub);
        if (Double.isNaN(ha) || Double.isNaN(hb)) return;
        double foot = foot(d, sOut, sgn, -TAIL, 1, ua, ub, cx, cz, expect, found);
        if (Double.isNaN(foot)) foot = Math.min(ha, hb) - 2 - FOOT_SINK;
        double tu0 = ua - Math.floor(ua + 1e-9), tu1 = tu0 + (ub - ua);
        double[] ts = {-TAIL, 0, 1};
        for (int k = 0; k < 2; k++) {
            double t0 = ts[k], t1 = ts[k + 1];
            double[] a0 = d.point(sOut + sgn * t0, ua), b0 = d.point(sOut + sgn * t0, ub), b1 = d.point(sOut + sgn * t1, ub), a1 = d.point(sOut + sgn * t1, ua);
            double v0 = t0 + TAIL, v1 = Math.min(1, t1 + TAIL);
            polygon(out, List.of(new Vertex(a0[0], ha, a0[1], tu0, clamp(v0)), new Vertex(b0[0], hb, b0[1], tu1, clamp(v0)),
                    new Vertex(b1[0], hb, b1[1], tu1, clamp(v1)), new Vertex(a1[0], ha, a1[1], tu0, clamp(v1))), 0, 1, 0, true);
        }
        double[] tf = tangent(d, sOut + sgn), tb = tangent(d, sOut - sgn * TAIL);
        double[] fa = d.point(sOut + sgn, ua), fb = d.point(sOut + sgn, ub), ba = d.point(sOut - sgn * TAIL, ua), bb = d.point(sOut - sgn * TAIL, ub);
        wall(out, fa[0], fa[1], fb[0], fb[1], foot, foot, ha, hb, tu0, tu1, sgn * tf[0], 0, sgn * tf[1]);
        wall(out, ba[0], ba[1], bb[0], bb[1], foot, foot, ha, hb, tu0, tu1, -sgn * tb[0], 0, -sgn * tb[1]);
        // the outer lateral end (the strip's larger |u|), where the wall stops
        double uo = Math.abs(ub) > Math.abs(ua) ? ub : ua, ho = uo == ub ? hb : ha, sg = uo < 0 ? -1 : 1;
        if (Double.isNaN(wingStrip(d, sp, sOut, uo + sg * 0.01)) && !footprint(d.section, Math.abs(uo) + 0.01)) {
            double[] lat = lateral(d, sOut);
            double[] p = d.point(sOut - sgn * TAIL, uo), q = d.point(sOut + sgn, uo);
            wall(out, p[0], p[1], q[0], q[1], foot, foot, ho, ho, 0, 1 + TAIL, sg * lat[0], 0, sg * lat[1]);
        }
    }

    /**
     * The lowest ground a wall strip ua .. ub stands on over t = t0 .. t1 (stations sOut + sgn t), FOOT_SINK deeper: the
     * scanned feet of the intact structure cells under it and the ground the probes found PROBE in front of its face
     * (C4: a wall stood on the foot of its middle cell, and the valley in front fell away under it); NaN when none is known.
     */
    private static double foot(RoadDesign d, double sOut, double sgn, double t0, double t1, double ua, double ub, int cx, int cz,
                               Map<Integer, Expect> expect, Map<Integer, Found> found) {
        double f = Double.POSITIVE_INFINITY;
        double[] us = {ua + 0.01, (ua + ub) * 0.5, ub - 0.01};
        int m = Math.max(1, (int) Math.ceil((t1 - t0) / 0.5));
        for (int j = 0; j <= m + 1; j++) {
            boolean probe = j == m + 1;
            double t = probe ? t1 + PROBE : t0 + (t1 - t0) * j / m;
            for (double u : us) {
                double[] p = d.point(sOut + sgn * t, u);
                int lx = (int) Math.floor(p[0]) - cx * 16, lz = (int) Math.floor(p[1]) - cz * 16;
                if (lx < -1 || lx > 16 || lz < -1 || lz > 16) continue;
                int idx = RoadCells.index(lx, lz);
                Expect e = expect.get(idx);
                Found fd = found.get(idx);
                if (e == null || fd == null || e.probe() != probe) continue;
                if (!probe && (state(e, fd) != State.PRESENT || !Double.isNaN(e.meshBottom()))) continue;
                f = Math.min(f, fd.foot());
            }
        }
        return f == Double.POSITIVE_INFINITY ? Double.NaN : f - FOOT_SINK;
    }

    /**
     * The multi-column bents of a span (group E, 2026-10-15). Under each girder a cap beam: its faces across the road at
     * p -+ CAP_WIDTH / 2 from its flat underside (capBottom) up to the soffit, strip by strip on the lateral breaks, its
     * underside, and its lateral ends at the girder's sides; it hangs from the deck, so it is drawn whatever the cells
     * under it hold. Under the cap the round columns (#columns, COLUMN_SIDES facets), each from its foot (#columnFoot) to
     * the cap's underside, drawn where the cell at its centre is intact.
     */
    private static void piers(List<Triangle> out, RoadDesign d, RoadDesign.Span sp, double lo, double hi, int cx, int cz,
                              Map<Integer, Expect> expect, Map<Integer, Found> found, double[] breaks) {
        CrossSection sec = d.section;
        double r = columnRadius(sec);
        for (double p : sp.piers()) {
            if (p < lo || p > hi) continue;
            double sa = p - CAP_WIDTH / 2, sb = p + CAP_WIDTH / 2;
            double[] t = tangent(d, p), lat = lateral(d, p);
            for (int side : sec.divided ? new int[]{-1, 1} : new int[]{1}) {
                double[] g = girderRange(sec, side);
                double bottom = capBottom(d, sp, p, side);
                List<Double> us = new ArrayList<>();
                us.add(g[0]);
                for (double b : breaks) if (b > g[0] + 1e-6 && b < g[1] - 1e-6) us.add(b);
                us.add(g[1]);
                for (int i = 0; i + 1 < us.size(); i++) {
                    double ua = us.get(i), ub = us.get(i + 1), tu0 = ua - Math.floor(ua + 1e-9), tu1 = tu0 + (ub - ua);
                    int sd = ua + ub < 0 ? -1 : 1;
                    for (int k = 0; k < 2; k++) {
                        double s = k == 0 ? sa : sb, sg = k == 0 ? -1 : 1, top = soffit(d, sp, s, sd);
                        double[] a = d.point(s, ua), b = d.point(s, ub);
                        wall(out, a[0], a[1], b[0], b[1], bottom, bottom, top, top, tu0, tu1, sg * t[0], 0, sg * t[1]);
                    }
                    // the underside, in two halves along the road (a tile is 1 m; the cap is CAP_WIDTH)
                    for (int k = 0; k < 2; k++) {
                        double s0 = k == 0 ? sa : p, s1 = k == 0 ? p : sb;
                        double[] a0 = d.point(s0, ua), b0 = d.point(s0, ub), b1 = d.point(s1, ub), a1 = d.point(s1, ua);
                        polygon(out, List.of(new Vertex(a0[0], bottom, a0[1], tu0, 0), new Vertex(b0[0], bottom, b0[1], tu1, 0),
                                new Vertex(b1[0], bottom, b1[1], tu1, s1 - s0), new Vertex(a1[0], bottom, a1[1], tu0, s1 - s0)), 0, -1, 0);
                    }
                }
                // the cap's lateral ends, flush with the girder's sides
                for (int k = 0; k < 2; k++) {
                    double u = g[k], sg = k == 0 ? -1 : 1;
                    int sd = u < 0 ? -1 : 1;
                    double[] a = d.point(sa, u), b = d.point(sb, u);
                    wall(out, a[0], a[1], b[0], b[1], bottom, bottom, soffit(d, sp, sa, sd), soffit(d, sp, sb, sd), 0, CAP_WIDTH,
                            sg * lat[0], 0, sg * lat[1]);
                }
            }
            for (double u : columns(sec)) {
                double[] c = d.point(p, u);
                int lx = (int) Math.floor(c[0]) - cx * 16, lz = (int) Math.floor(c[1]) - cz * 16;
                if (lx < -1 || lx > 16 || lz < -1 || lz > 16) continue;
                int idx = RoadCells.index(lx, lz);
                if (state(expect.get(idx), found.get(idx)) != State.PRESENT) continue;
                double foot = columnFoot(c, r, cx, cz, expect, found), top = capBottom(d, sp, p, u < 0 ? -1 : 1);
                if (Double.isNaN(foot)) foot = found.get(idx).foot() - FOOT_SINK;
                for (int k = 0; k < COLUMN_SIDES; k++) {
                    double a0 = 2 * Math.PI * k / COLUMN_SIDES, a1 = 2 * Math.PI * (k + 1) / COLUMN_SIDES, am = (a0 + a1) * 0.5;
                    // clockwise seen from above, so the face looks outward like a wall from a to b with its normal on the right
                    wall(out, c[0] + r * Math.cos(a1), c[1] + r * Math.sin(a1), c[0] + r * Math.cos(a0), c[1] + r * Math.sin(a0),
                            foot, foot, top, top, r * (2 * Math.PI - a1), r * (2 * Math.PI - a0), Math.cos(am), 0, Math.sin(am));
                }
            }
        }
    }

    /**
     * The lowest ground a bent column of radius r centred at c stands on, FOOT_SINK deeper: the scanned feet of the intact
     * ground-standing cells its circle covers and the ground its probes found PROBE beside it; NaN when none is known.
     */
    private static double columnFoot(double[] c, double r, int cx, int cz, Map<Integer, Expect> expect, Map<Integer, Found> found) {
        double f = Double.POSITIVE_INFINITY;
        for (int k = 0; k <= 16; k++) {
            boolean probe = k == 16;
            double rr = probe ? r + PROBE : k == 0 ? 0 : r * 0.95;
            int n = probe ? 8 : k == 0 ? 1 : 1;
            for (int j = 0; j < n; j++) {
                double a = probe ? 2 * Math.PI * j / n : 2 * Math.PI * (k - 1) / 15;
                double x = c[0] + rr * Math.cos(a), z = c[1] + rr * Math.sin(a);
                int lx = (int) Math.floor(x) - cx * 16, lz = (int) Math.floor(z) - cz * 16;
                if (lx < -1 || lx > 16 || lz < -1 || lz > 16) continue;
                int idx = RoadCells.index(lx, lz);
                Expect e = expect.get(idx);
                Found fd = found.get(idx);
                if (e == null || fd == null || e.probe() != probe) continue;
                if (!probe && (state(e, fd) != State.PRESENT || !Double.isNaN(e.meshBottom()))) continue;
                f = Math.min(f, fd.foot());
            }
        }
        return f == Double.POSITIVE_INFINITY ? Double.NaN : f - FOOT_SINK;
    }

    /** The cell (RoadCells index) a wall strip at lateral um of the span end sOut belongs to; -1 outside the window. */
    private static int stripCell(RoadDesign d, double sOut, double sgn, double um, int cx, int cz) {
        double[] c = d.point(sOut + sgn * 0.5, um);
        int lx = (int) Math.floor(c[0]) - cx * 16, lz = (int) Math.floor(c[1]) - cz * 16;
        return lx < -1 || lx > 16 || lz < -1 || lz > 16 ? -1 : RoadCells.index(lx, lz);
    }

    /**
     * The repairs of broken cells in chunk (cx, cz), chunk-relative: what is left of a broken cell's collision as a plain
     * box, and the faces of intact neighbours towards it (from their foot, or the remnant's top, to their top; a paved
     * neighbour's face stops at its collision block, where RoadCells' asphalt wall takes over).
     */
    public static List<Triangle> repairs(Map<Integer, Expect> expect, Map<Integer, Found> found, Map<Integer, RoadCells.Cell> cells) {
        List<Triangle> out = new ArrayList<>();
        if (found == null) return out;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Expect e : expect.values()) {
            int i = e.index();
            if (!RoadCells.inner(i)) continue;
            Found f = found.get(i);
            if (state(e, f) != State.BROKEN) continue;
            int lx = RoadCells.lx(i), lz = RoadCells.lz(i);
            double rTop = f == null ? Double.NEGATIVE_INFINITY : f.top() + f.layers() / 32.0;
            if (f != null) {
                polygon(out, List.of(new Vertex(lx, rTop, lz, 0, 0), new Vertex(lx + 1, rTop, lz, 1, 0), new Vertex(lx + 1, rTop, lz + 1, 1, 1),
                        new Vertex(lx, rTop, lz + 1, 0, 1)), 0, 1, 0);
                polygon(out, List.of(new Vertex(lx, f.foot(), lz, 0, 0), new Vertex(lx + 1, f.foot(), lz, 1, 0),
                        new Vertex(lx + 1, f.foot(), lz + 1, 1, 1), new Vertex(lx, f.foot(), lz + 1, 0, 1)), 0, -1, 0);
            }
            for (int[] dir : dirs) {
                int n = RoadCells.index(lx + dir[0], lz + dir[1]);
                Expect en = expect.get(n);
                Found fn = found.get(n);
                double ax, az, bx, bz;
                if (dir[0] != 0) { ax = bx = dir[0] > 0 ? lx + 1 : lx; az = lz; bz = lz + 1; }
                else { az = bz = dir[1] > 0 ? lz + 1 : lz; ax = lx; bx = lx + 1; }
                if (intact(en, fn)) {
                    boolean paved = en.surface() && cells.containsKey(i) && cells.containsKey(n);
                    double top = paved ? en.y() : en.capTop();
                    double bottom = Math.max(rTop, Double.isNaN(en.meshBottom()) ? fn.foot() : Math.min(fn.foot(), en.meshBottom()));
                    if (top > bottom + 1e-3) wall(out, ax, az, bx, bz, bottom, bottom, top, top, 0, 1, -dir[0], 0, -dir[1]);
                } else if (f != null) {
                    wall(out, ax, az, bx, bz, f.foot(), f.foot(), rTop, rTop, 0, 1, dir[0], 0, dir[1]);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A vertical face over the ground line a -> b (x, z), bottom and top heights at both ends, split into 1 m bands of
     * height for the texture (tu along the line, tv down from each band's top); a line longer than the texture's metre
     * (a pier's 1.2 m end) is cut where tu crosses a whole metre, so tu stays within 0 .. 1 (C3.3a).
     */
    static void wall(List<Triangle> out, double ax, double az, double bx, double bz, double botA, double botB, double topA, double topB,
                     double tuA, double tuB, double nx, double ny, double nz) {
        double fl = Math.floor(Math.min(tuA, tuB) + 1e-9);
        if (Math.max(tuA, tuB) > fl + 1 + 1e-9) {
            double cut = fl + 1, q = (cut - tuA) / (tuB - tuA);
            double mx = ax + (bx - ax) * q, mz = az + (bz - az) * q, mb = botA + (botB - botA) * q, mt = topA + (topB - topA) * q;
            wall(out, ax, az, mx, mz, botA, mb, topA, mt, tuA, cut, nx, ny, nz);
            wall(out, mx, mz, bx, bz, mb, botB, mt, topB, cut, tuB, nx, ny, nz);
            return;
        }
        tuA -= fl;
        tuB -= fl;
        double lo = Math.min(botA, botB), hi = Math.max(topA, topB);
        if (!(hi > lo)) return;
        for (int k = (int) Math.floor(lo); k < hi; k++) {
            // the face in (t along the line, y), cut to k <= y <= k + 1
            List<double[]> p = List.of(new double[]{0, botA}, new double[]{1, botB}, new double[]{1, topB}, new double[]{0, topA});
            p = clipY(p, k, true);
            p = clipY(p, k + 1, false);
            if (p.size() < 3) continue;
            List<Vertex> v = new ArrayList<>(p.size());
            for (double[] q : p)
                v.add(new Vertex(ax + (bx - ax) * q[0], q[1], az + (bz - az) * q[0], tuA + (tuB - tuA) * q[0], clamp(k + 1 - q[1])));
            polygon(out, v, nx, ny, nz);
        }
    }

    private static List<double[]> clipY(List<double[]> in, double edge, boolean lower) {
        List<double[]> out = new ArrayList<>(6);
        if (in.isEmpty()) return out;
        double[] a = in.get(in.size() - 1);
        double da = a[1] - edge;
        boolean ia = lower ? da >= 0 : da <= 0;
        for (double[] b : in) {
            double db = b[1] - edge;
            boolean ib = lower ? db >= 0 : db <= 0;
            if (ia != ib) {
                double t = da / (da - db);
                out.add(new double[]{a[0] + (b[0] - a[0]) * t, edge});
            }
            if (ib) out.add(b);
            a = b;
            da = db;
            ia = ib;
        }
        return out;
    }

    static void polygon(List<Triangle> out, List<Vertex> p, double nx, double ny, double nz) { polygon(out, p, nx, ny, nz, false); }

    /**
     * A convex polygon, fan-triangulated, each triangle wound to face (nx, ny, nz); with geometric, the stored normal is
     * the triangle's own (oriented to that side), else the given one. Degenerate triangles are dropped.
     */
    static void polygon(List<Triangle> out, List<Vertex> p, double nx, double ny, double nz, boolean geometric) {
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= n;
        ny /= n;
        nz /= n;
        for (int i = 1; i + 1 < p.size(); i++) {
            Vertex a = p.get(0), b = p.get(i), c = p.get(i + 1);
            double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
            double gx = uy * vz - uz * vy, gy = uz * vx - ux * vz, gz = ux * vy - uy * vx, g = Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (g < 1e-9) continue;
            if (gx * nx + gy * ny + gz * nz < 0) { Vertex t = b; b = c; c = t; gx = -gx; gy = -gy; gz = -gz; }
            if (geometric) out.add(new Triangle(Material.CONCRETE, a, b, c, gx / g, gy / g, gz / g));
            else out.add(new Triangle(Material.CONCRETE, a, b, c, nx, ny, nz));
        }
    }

    /** Unit vector of increasing lateral offset u at station s (world x, z). */
    static double[] lateral(RoadDesign d, double s) {
        double[] a = d.point(s, 0), b = d.point(s, 1);
        double x = b[0] - a[0], z = b[1] - a[1], l = Math.hypot(x, z);
        return new double[]{x / l, z / l};
    }

    /** Unit vector of increasing station at s (world x, z). */
    static double[] tangent(RoadDesign d, double s) {
        double[] a = d.point(s - 0.05, 0), b = d.point(s + 0.05, 0);
        double x = b[0] - a[0], z = b[1] - a[1], l = Math.hypot(x, z);
        return new double[]{x / l, z / l};
    }

    private static double clamp(double t) { return Math.max(0, Math.min(1, t)); }
}
