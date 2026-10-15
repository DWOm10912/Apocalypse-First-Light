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
 *   <li>deck: each carriageway its own deck, swept along the span: a 0.3 m slab with 0.6 m overhangs on a box girder down
 *   to the span's soffit, and on both edges an F-shape-like concrete parapet 1.07 m above the pavement edge, 0.6 m wide,
 *   its road face battered 0.2 m; the open median between the two decks; parapet end caps at the span's ends;</li>
 *   <li>abutments: in the first and last metre of a span, a wall across the whole formation, from the ground (the
 *   scanned foot of the collision) up to the slab's or the girder's underside, and beyond the decks the wing walls up to
 *   the embankment's design (the median, the fill template), with their tops and their lateral ends;</li>
 *   <li>damage: a structure cell whose collision top is not the generated one is broken: its mesh is cut away, what
 *   is left of its collision is drawn as a plain concrete box, and the faces of the intact cells around it are closed.
 *   A wing cell with no structure at all is simply absent (the chunk pass leaves it out where the ground already stands
 *   at the embankment's height, or over water).</li>
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
    private static final double HM = R4CrossSection.HALF_MEDIAN, EDGE = R4CrossSection.EDGE;
    /** The ground the client assumes for its expectations: below everything, so every possible wing cell is expected. */
    private static final RoadWorks.Ground LOW = (x, z) -> -1e9;

    private BridgeMesh() {}

    /**
     * Top of a wing wall at lateral u for the span end at station sOut: the median's formation, or beside the road the
     * fill template from the shoulder hinge; NaN on a deck or beyond the walls' reach.
     */
    public static double wingTop(RoadDesign d, double sOut, double u) {
        double v = Math.abs(u);
        if (v < HM) return d.formationY(sOut, u) + COPING;
        if (v <= EDGE || v > WALL_REACH) return Double.NaN;
        return d.formationY(sOut, Math.copySign(EDGE, u)) + R4CrossSection.roadside(v - EDGE, -1e9) + COPING;
    }

    /**
     * A wing wall's top as the mesh draws it and the collision follows it: wingTop at the walls' lateral breaks, linear
     * between them; NaN on the decks' footprint (|u| between the parapets' outer faces) and beyond the walls' reach.
     */
    public static double wingStrip(RoadDesign d, double sOut, double u) {
        double v = Math.abs(u);
        if (v > HM - PARAPET_WIDTH && v < EDGE + PARAPET_WIDTH || v > WALL_REACH) return Double.NaN;
        int k = Arrays.binarySearch(BREAKS, u);
        if (k >= 0) return wingTop(d, sOut, u);
        int i = -k - 2;
        if (i < 0 || i + 1 >= BREAKS.length) return Double.NaN;
        double a = wingTop(d, sOut, BREAKS[i]), b = wingTop(d, sOut, BREAKS[i + 1]);
        if (Double.isNaN(a) || Double.isNaN(b)) return wingTop(d, sOut, u);
        return a + (b - a) * (u - BREAKS[i]) / (BREAKS[i + 1] - BREAKS[i]);
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
        RoadWorks.Hit h = works.hit(x, z, RoadWorks.REACH);
        if (h == null || h.beyond() > 0) return null;
        RoadDesign d = h.road();
        double s = h.s(), u = h.u(), v = h.v();
        RoadDesign.Span sp = null;
        double t = 0, sOut = 0;
        for (RoadDesign.Span q : d.spans) {
            if (q.s1() - q.s0() < 2 || s < q.s0() - TAIL || s > q.s1() + TAIL) continue;
            sp = q;
            boolean first = s - q.s0() <= q.s1() - s;
            sOut = first ? q.s0() : q.s1();
            t = first ? s - q.s0() : q.s1() - s;
            break;
        }
        if (sp == null) return null;
        if (v > HM - PARAPET_WIDTH && v < EDGE + PARAPET_WIDTH) {
            int side = u < 0 ? -1 : 1;
            double top;
            if (v >= HM && v <= EDGE) top = d.pavementY(s, u);
            else if (v > EDGE) top = d.pavementY(s, side * EDGE) + PARAPET_HEIGHT * Math.min(1, (v - EDGE) / PARAPET_BATTER);
            else top = d.pavementY(s, side * HM) + PARAPET_HEIGHT * Math.min(1, (HM - v) / PARAPET_BATTER);
            if (t <= 1) return new double[]{Double.NEGATIVE_INFINITY, top};
            double bottom = v >= HM + GIRDER_INSET && v <= EDGE - GIRDER_INSET ? soffit(d, sp, s, side)
                    : d.pavementY(s, side * (v > (HM + EDGE) * 0.5 ? EDGE : HM)) - SLAB;
            return new double[]{bottom, top};
        }
        if (t > 1) return null;
        double f = wingStrip(d, sOut, u);
        return Double.isNaN(f) ? null : new double[]{Double.NEGATIVE_INFINITY, f};
    }

    /**
     * The collision boxes of a road_structure block at (x, y, z), block-local {x0, y0, z0, x1, y1, z1}: solid() on 8 x 8
     * columns of the block, heights to 1/32, runs along x merged. Empty where the mesh has nothing in the block.
     */
    public static List<double[]> boxes(RoadWorks works, int x, int y, int z) {
        List<double[]> out = new ArrayList<>();
        for (int j = 0; j < 8; j++) {
            int start = -1;
            double runLo = 0, runHi = 0;
            for (int i = 0; i <= 8; i++) {
                double lo = Double.NaN, hi = Double.NaN;
                if (i < 8) {
                    double[] c = solid(works, x + (i + 0.5) / 8, z + (j + 0.5) / 8);
                    if (c != null) {
                        lo = Math.max(0, Math.round((c[0] - y) * 32) / 32.0);
                        hi = Math.min(1, Math.round((c[1] - y) * 32) / 32.0);
                        if (!(hi > lo)) lo = hi = Double.NaN;
                    }
                }
                if (start >= 0 && lo == runLo && hi == runHi) continue;
                if (start >= 0) out.add(new double[]{start / 8.0, runLo, j / 8.0, i / 8.0, runHi, (j + 1) / 8.0});
                start = Double.isNaN(lo) ? -1 : i;
                runLo = lo;
                runHi = hi;
            }
        }
        return out;
    }

    /** The soffit (girder underside) of the deck on one side at station s: the span's depth, kept under the slab. */
    static double soffit(RoadDesign d, RoadDesign.Span sp, double s, int side) {
        double slab = Math.min(d.pavementY(s, side * EDGE), d.pavementY(s, side * HM)) - SLAB - 0.2;
        return Math.min(d.datum(s) - sp.depth(), slab);
    }

    // ------------------------------------------------------------------ expected and found cells

    /**
     * A structure cell as the chunk pass made it: kind; y, the collision block (DECK, ABUTMENT) or the parapet / wall top
     * block; top, surface, layers: the topmost block (road_surface or road_structure) and its layers; wing; capTop: the
     * height an intact cell's face reaches (pavement, parapet or wall top); meshBottom: the mesh's underside under a
     * hanging cell (NaN: the cell stands on the ground, use the scanned foot).
     */
    public record Expect(int index, RoadWorks.Kind kind, int y, int top, boolean surface, int layers, boolean wing, double capTop, double meshBottom) {}

    /** A structure cell as found in the world: its topmost structure block, that block's kind and layers, the lowest one. */
    public record Found(int top, boolean surface, int layers, int foot) {}

    public enum State { PRESENT, BROKEN, ABSENT }

    public static State state(Expect e, Found f) {
        if (e == null) return State.ABSENT;
        if (f != null && f.top() == e.top() && f.surface() == e.surface() && f.layers() == e.layers()) return State.PRESENT;
        return f == null && e.wing() ? State.ABSENT : State.BROKEN;
    }

    /** The structure cells of chunk (cx, cz) and its ring (RoadCells indices); empty when no span is near. */
    public static Map<Integer, Expect> expected(RoadWorks works, int cx, int cz) {
        Map<Integer, Expect> out = new HashMap<>();
        boolean near = false;
        for (RoadDesign d : works.roads()) {
            double[] r = stations(d, cx, cz);
            if (r != null) for (RoadDesign.Span sp : d.spans) near |= sp.s0() <= r[1] && sp.s1() >= r[0];
        }
        if (!near) return out;
        for (int lz = -1; lz <= 16; lz++) for (int lx = -1; lx <= 16; lx++) {
            int x = cx * 16 + lx, z = cz * 16 + lz;
            RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
            if (h == null || h.beyond() > 0) continue;
            RoadDesign d = h.road();
            RoadDesign.Span sp = d.span(h.sc());
            if (sp == null) continue;
            RoadWorks.Column c = works.column(x, z, LOW);
            boolean end = RoadWorks.band(d, sp, x, z);
            int side = h.u() < 0 ? -1 : 1, i = RoadCells.index(lx, lz);
            double v = h.v();
            switch (c.kind()) {
                case DECK, ABUTMENT -> {
                    boolean girder = v >= HM + GIRDER_INSET && v <= EDGE - GIRDER_INSET;
                    double bottom = c.kind() == RoadWorks.Kind.ABUTMENT ? Double.NaN
                            : girder ? soffit(d, sp, h.sc(), side) : c.paveLo() - SLAB;
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
        return out;
    }

    /** The inner cells (lx | lz << 4) whose structure is broken. */
    public static boolean[] broken(Map<Integer, Expect> expect, Map<Integer, Found> found) {
        boolean[] out = new boolean[256];
        if (found == null) return out;
        for (Expect e : expect.values())
            if (RoadCells.inner(e.index()) && state(e, found.get(e.index())) == State.BROKEN)
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
        boolean[] removed = broken(expect, found);
        for (int i = dug.nextSetBit(0); i >= 0; i = dug.nextSetBit(i + 1))
            if (RoadCells.inner(i)) removed[RoadCells.lx(i) | RoadCells.lz(i) << 4] = true;
        boolean any = false;
        for (boolean b : removed) any |= b;
        if (any) tris = RoadMesh.without(tris, removed);
        if (!dug.isEmpty()) tris.addAll(RoadCells.walls(rs, cells, dug, cx, cz));
        tris.addAll(repairs(expect, found, cells));
        return tris;
    }

    /**
     * The bridge triangles of all roads in chunk (cx, cz), chunk-relative: decks and parapets always; the abutment and
     * wing walls only once the cells were scanned (found != null), since they stand on the scanned foot.
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
        for (Triangle t : world) RoadMesh.clipInto(out, t, cx * 16, cz * 16);
        if (found != null) wings(out, works, cx, cz, expect, found);
        return out;
    }

    /**
     * The median and wing walls, cell by cell, chunk-relative (2026-10-15). Each intact ground-standing cell of the
     * abutment band (RoadWorks.band: its square reaches into the first metre of the span) draws its own part of the walls,
     * which is exactly its collision (solid()): the coping (wingStrip) from TAIL behind the span end to the wall face 1 m
     * into the span, the face down to its foot (FOOT_SINK into the ground), the coping's back edge over the turf, the walls' lateral ends at their
     * reach, and its sides towards cells that are not part of the walls. A straight wall line on a skewed crossing thus
     * never leaves a block of invisible collision beside the mesh (the cell's ground is not replaced either:
     * RoadChunkPass), and a wing the pass left out (ground at its height) leaves its own cell to that ground.
     */
    static void wings(List<Triangle> out, RoadWorks works, int cx, int cz, Map<Integer, Expect> expect, Map<Integer, Found> found) {
        int[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (Expect e : expect.values()) {
            int i = e.index();
            if (!RoadCells.inner(i) || !Double.isNaN(e.meshBottom())) continue;
            Found f = found.get(i);
            if (state(e, f) != State.PRESENT) continue;
            int lx = RoadCells.lx(i), lz = RoadCells.lz(i);
            double x = cx * 16 + lx, z = cz * 16 + lz;
            RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
            if (h == null) continue;
            RoadDesign d = h.road();
            RoadDesign.Span sp = d.span(h.sc());
            if (sp == null || sp.s1() - sp.s0() < 2) continue;
            boolean first = h.sc() - sp.s0() <= sp.s1() - h.sc();
            double sOut = first ? sp.s0() : sp.s1(), sgn = first ? 1 : -1;
            List<double[]> sq = new ArrayList<>(4);
            for (int[] c : square) {
                double[] l = d.local(x + c[0], z + c[1], RoadWorks.REACH + 2);
                if (l == null) { sq = null; break; }
                sq.add(new double[]{lx + c[0], lz + c[1], sgn * (l[0] - sOut), l[1]});
            }
            if (sq == null) continue;
            sq = clip(clip(sq, 2, -TAIL, false), 2, 1, true);
            if (sq.size() < 3) continue;
            double[] tan = tangent(d, sOut + sgn * 0.5), lat = lateral(d, sOut + sgn * 0.5);
            for (int k = 0; k + 1 < BREAKS.length; k++) {
                double ua = BREAKS[k], ub = BREAKS[k + 1];
                if (Double.isNaN(wingStrip(d, sOut, (ua + ub) * 0.5))) continue;
                List<double[]> p = clip(clip(sq, 3, ua, false), 3, ub, true);
                if (p.size() < 3) continue;
                int n = p.size();
                double[] top = new double[n];
                for (int q = 0; q < n; q++) top[q] = wingStrip(d, sOut, Math.max(ua, Math.min(ub, p.get(q)[3])));
                List<Vertex> v = new ArrayList<>(n);
                for (int q = 0; q < n; q++) v.add(new Vertex(p.get(q)[0], top[q], p.get(q)[1], clamp(p.get(q)[0] - lx), clamp(p.get(q)[1] - lz)));
                polygon(out, v, 0, 1, 0, true);
                for (int q = 0; q < n; q++) {
                    double[] a = p.get(q), b = p.get((q + 1) % n);
                    double ha = top[q], hb = top[(q + 1) % n], tua = clamp(Math.abs(a[3] - Math.floor(Math.min(a[3], b[3]) + 1e-9))), tub = clamp(Math.abs(b[3] - Math.floor(Math.min(a[3], b[3]) + 1e-9)));
                    if (Math.abs(a[2] - 1) < 1e-9 && Math.abs(b[2] - 1) < 1e-9) {
                        // the wall face, into the span
                        wall(out, a[0], a[1], b[0], b[1], f.foot() - FOOT_SINK, f.foot() - FOOT_SINK, ha, hb, tua, tub, sgn * tan[0], 0, sgn * tan[1]);
                    } else if (Math.abs(a[2] + TAIL) < 1e-9 && Math.abs(b[2] + TAIL) < 1e-9) {
                        // the coping's back edge over the turf
                        double drop = COPING + 0.35;
                        wall(out, a[0], a[1], b[0], b[1], ha - drop, hb - drop, ha, hb, tua, tub, -sgn * tan[0], 0, -sgn * tan[1]);
                    } else if (Math.abs(Math.abs(a[3]) - WALL_REACH) < 1e-9 && Math.abs(Math.abs(b[3]) - WALL_REACH) < 1e-9) {
                        // the walls' lateral end
                        double sg = a[3] < 0 ? -1 : 1;
                        wall(out, a[0], a[1], b[0], b[1], f.foot() - FOOT_SINK, f.foot() - FOOT_SINK, ha, hb, clamp(a[2] + TAIL), clamp(b[2] + TAIL), sg * lat[0], 0, sg * lat[1]);
                    } else {
                        int dx = 0, dz = 0;
                        if (Math.abs(a[0] - b[0]) < 1e-9 && (Math.abs(a[0] - lx) < 1e-9 || Math.abs(a[0] - lx - 1) < 1e-9)) dx = a[0] > lx + 0.5 ? 1 : -1;
                        else if (Math.abs(a[1] - b[1]) < 1e-9 && (Math.abs(a[1] - lz) < 1e-9 || Math.abs(a[1] - lz - 1) < 1e-9)) dz = a[1] > lz + 0.5 ? 1 : -1;
                        else continue;
                        int nb = RoadCells.index(lx + dx, lz + dz);
                        Expect en = expect.get(nb);
                        if (en != null && Double.isNaN(en.meshBottom()) && state(en, found.get(nb)) == State.PRESENT) continue;
                        double sa = dx != 0 ? a[1] - lz : a[0] - lx, sb = dx != 0 ? b[1] - lz : b[0] - lx;
                        wall(out, a[0], a[1], b[0], b[1], f.foot() - FOOT_SINK, f.foot() - FOOT_SINK, ha, hb, clamp(sa), clamp(sb), dx, 0, dz);
                    }
                }
            }
        }
    }

    /** Clip a polygon of (x, z, t, u) to component k <= edge (lower) or >= edge. */
    private static List<double[]> clip(List<double[]> in, int k, double edge, boolean lower) {
        List<double[]> out = new ArrayList<>(6);
        if (in.size() < 3) return out;
        double[] a = in.get(in.size() - 1);
        double da = a[k] - edge;
        boolean ia = lower ? da <= 1e-9 : da >= -1e-9;
        for (double[] b : in) {
            double db = b[k] - edge;
            boolean ib = lower ? db <= 1e-9 : db >= -1e-9;
            if (ia != ib) {
                double t = da / (da - db);
                double[] c = new double[4];
                for (int j = 0; j < 4; j++) c[j] = a[j] + (b[j] - a[j]) * t;
                c[k] = edge;
                out.add(c);
            }
            if (ib) out.add(b);
            a = b;
            da = db;
            ia = ib;
        }
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

    /** The cross-section of one deck (w outward from the centreline, y), clockwise from the pavement's outer edge. */
    private static double[][] section(RoadDesign d, RoadDesign.Span sp, double s, int side) {
        double hE = d.pavementY(s, side * EDGE), hM = d.pavementY(s, side * HM), so = soffit(d, sp, s, side);
        double top = PARAPET_HEIGHT;
        return new double[][]{
                {EDGE, hE}, {EDGE + PARAPET_BATTER, hE + top}, {EDGE + PARAPET_WIDTH, hE + top}, {EDGE + PARAPET_WIDTH, hE - SLAB},
                {EDGE - GIRDER_INSET, hE - SLAB}, {EDGE - GIRDER_INSET, so}, {HM + GIRDER_INSET, so}, {HM + GIRDER_INSET, hM - SLAB},
                {HM - PARAPET_WIDTH, hM - SLAB}, {HM - PARAPET_WIDTH, hM + top}, {HM - PARAPET_BATTER, hM + top}, {HM, hM}};
    }

    private static void deck(List<Triangle> out, RoadDesign d, RoadDesign.Span sp, double lo, double hi) {
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
                // the pavement (P11 -> P0) is RoadMesh's
                for (int i = 0; i + 1 < pa.length; i++) {
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
                    double[][][] outer = {{pa[0], pa[1], pa[2], pa[3], {EDGE, pa[0][1] - SLAB}}, {pb[0], pb[1], pb[2], pb[3], {EDGE, pb[0][1] - SLAB}}};
                    double[][][] inner = {{{HM, pa[11][1] - SLAB}, pa[8], pa[9], pa[10], pa[11]}, {{HM, pb[11][1] - SLAB}, pb[8], pb[9], pb[10], pb[11]}};
                    for (double[][][] q : new double[][][][]{outer, inner})
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
                cap(out, d, sEnd, side, new double[][]{p[0], p[1], p[2], p[3], {EDGE, p[0][1] - SLAB}}, EDGE, p[1][1], sg * t[0], sg * t[1]);
                cap(out, d, sEnd, side, new double[][]{p[8], p[9], p[10], p[11], {HM, p[11][1] - SLAB}}, HM - PARAPET_WIDTH, p[9][1], sg * t[0], sg * t[1]);
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

    private static Zone zone(double v) {
        if (v < HM - PARAPET_WIDTH) return Zone.MEDIAN;
        if (v < HM + GIRDER_INSET) return Zone.INNER;
        if (v < EDGE - GIRDER_INSET) return Zone.GIRDER;
        if (v < EDGE + PARAPET_WIDTH) return Zone.OUTER;
        return Zone.WING;
    }

    /** Lateral strip edges of the end walls: every 0.5 m, plus the parapets' and girders' faces. */
    private static final double[] BREAKS;

    static {
        TreeSet<Double> set = new TreeSet<>();
        for (int k = (int) Math.round(-WALL_REACH * 2); k <= (int) Math.round(WALL_REACH * 2); k++) set.add(k * 0.5);
        for (double b : new double[]{HM - PARAPET_WIDTH, HM + GIRDER_INSET, EDGE - GIRDER_INSET, EDGE + PARAPET_WIDTH}) { set.add(b); set.add(-b); }
        BREAKS = set.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double wallTop(RoadDesign d, RoadDesign.Span sp, Zone z, double sOut, double sf, double u, int side) {
        return switch (z) {
            case MEDIAN, WING -> wingTop(d, sOut, u);
            case INNER -> d.pavementY(sf, side * HM) - SLAB;
            case GIRDER -> soffit(d, sp, sf, side);
            case OUTER -> d.pavementY(sf, side * EDGE) - SLAB;
        };
    }

    /** The abutment and wing walls of both span ends: front face at 1 m inside the span, tops, lateral ends. */
    private static void walls(List<Triangle> out, RoadDesign d, RoadDesign.Span sp, double lo, double hi, int cx, int cz,
                              Map<Integer, Expect> expect, Map<Integer, Found> found) {
        if (sp.s1() - sp.s0() < 2) return;
        for (int e = 0; e < 2; e++) {
            double sOut = e == 0 ? sp.s0() : sp.s1(), sgn = e == 0 ? 1 : -1, sf = sOut + sgn;
            if (sOut < lo || sOut > hi) continue;
            double[] t = tangent(d, sf), lat = lateral(d, sf);
            int n = BREAKS.length - 1;
            State[] state = new State[n];
            double[] foot = new double[n];
            double[] ta = new double[n], tb = new double[n];
            Zone[] zones = new Zone[n];
            for (int i = 0; i < n; i++) {
                double ua = BREAKS[i], ub = BREAKS[i + 1], um = (ua + ub) * 0.5;
                int side = um < 0 ? -1 : 1;
                zones[i] = zone(Math.abs(um));
                ta[i] = wallTop(d, sp, zones[i], sOut, sf, ua, side);
                tb[i] = wallTop(d, sp, zones[i], sOut, sf, ub, side);
                int idx = stripCell(d, sOut, sgn, um, cx, cz);
                if (idx < 0) { state[i] = null; continue; }   // outside the window: not drawn here
                Found f = found.get(idx);
                state[i] = Double.isNaN(ta[i]) || Double.isNaN(tb[i]) ? State.ABSENT : state(expect.get(idx), f);
                if (state[i] == State.PRESENT) foot[i] = f.foot() - FOOT_SINK;
            }
            for (int i = 0; i < n; i++) {
                // the median and wing walls are drawn cell by cell (wings())
                if (state[i] != State.PRESENT || zones[i] == Zone.MEDIAN || zones[i] == Zone.WING) continue;
                double ua = BREAKS[i], ub = BREAKS[i + 1], tu0 = ua - Math.floor(ua + 1e-9), tu1 = tu0 + (ub - ua);
                double[] fa = d.point(sf, ua), fb = d.point(sf, ub);
                wall(out, fa[0], fa[1], fb[0], fb[1], foot[i], foot[i], ta[i], tb[i], tu0, tu1, sgn * t[0], 0, sgn * t[1]);
                // a lateral end of a run: the next strip has no wall (no expected cell, or a wing the pass left out)
                if (i == 0 || state[i - 1] == State.ABSENT) {
                    double[] o = d.point(sOut, ua);
                    wall(out, o[0], o[1], fa[0], fa[1], foot[i], foot[i], ta[i], ta[i], 0, 1, -lat[0], 0, -lat[1]);
                }
                if (i == n - 1 || state[i + 1] == State.ABSENT) {
                    double[] o = d.point(sOut, ub);
                    wall(out, o[0], o[1], fb[0], fb[1], foot[i], foot[i], tb[i], tb[i], 0, 1, lat[0], 0, lat[1]);
                }
            }
        }
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
                if (state(en, fn) == State.PRESENT) {
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
     * height for the texture (tu along the line, tv down from each band's top).
     */
    static void wall(List<Triangle> out, double ax, double az, double bx, double bz, double botA, double botB, double topA, double topB,
                     double tuA, double tuB, double nx, double ny, double nz) {
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
