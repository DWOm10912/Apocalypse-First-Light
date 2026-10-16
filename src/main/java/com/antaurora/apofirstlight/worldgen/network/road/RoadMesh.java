package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The pavement mesh of a built road for one chunk (C3.2, C3.3a; docs/worldgen/federal_highways_c3.md, 10 and 11): the
 * accepted R4 sample's mesh (dev R4Mesh) on a RoadDesign of any CrossSection, cut to the chunk's 16 x 16 square. Pure
 * Java; the client draws it.
 * <ul>
 *   <li>"asphalt": both carriageways (R2: both halves) and their shoulders, the 30-degree safety edges along the sides
 *   and the ends; texture period 4 m both ways, the lateral period measured outward from the median edge (R2: the
 *   centreline);</li>
 *   <li>R4 markings: "yellow" inner edge line, "white" outer edge line and the 3 m / 12 m lane line;</li>
 *   <li>R2 markings (C3.3a): "white" edge lines, and the "yellow" centre line: a 3 m / 12 m broken line where passing is
 *   allowed, two solid lines 0.10 m apart in the no-passing zones (RoadDesign.noPassing: curves, crests, bridges, the
 *   ends);</li>
 *   <li>every line 0.15 m wide and coplanar with the asphalt (the renderer offsets them in depth), split on the crown
 *   and shoulder breaks.</li>
 * </ul>
 * Stations come from one global lattice (2 m, plus the dash ends), the same in every chunk; each triangle is generated
 * once in world space and clipped to the chunk, so neighbouring chunks meet without gaps or overlaps whatever the load
 * order. Coordinates are relative to the chunk's origin (x, z), heights absolute. Bridges: BridgeMesh (CONCRETE).
 */
public final class RoadMesh {
    public static final double STEP = 2, TEXTURE = 4, LINE = 0.15, DASH = 3, DASH_PERIOD = 12;
    /** R2: the double centre line's lines start this far from the centreline (0.10 m between them). */
    public static final double DOUBLE_GAP = 0.05;
    private static final double BEVEL_UV = CrossSection.BEVEL_RUN / TEXTURE;
    private static final double[] ACROSS_R4 = makeAcross(CrossSection.R4), ACROSS_R2 = makeAcross(CrossSection.R2);

    /** The lateral breaks of one side's pavement (w outward from the median edge): the shoulders, lanes and texture tiles. */
    private static double[] makeAcross(CrossSection sec) {
        TreeSet<Double> set = new TreeSet<>(List.of(0.0, wInner(sec), wOuter(sec), wEdge(sec)));
        for (double w = TEXTURE; w < wEdge(sec) - 1e-9; w += TEXTURE) set.add(w);
        return set.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] across(CrossSection sec) { return sec == CrossSection.R4 ? ACROSS_R4 : sec == CrossSection.R2 ? ACROSS_R2 : makeAcross(sec); }

    private static double wInner(CrossSection sec) { return sec.inner; }

    private static double wOuter(CrossSection sec) { return sec.inner + sec.lanes * sec.lane; }

    private static double wEdge(CrossSection sec) { return sec.edge - sec.halfMedian; }

    public enum Material { ASPHALT, WHITE, YELLOW, CONCRETE }

    public record Vertex(double x, double y, double z, double u, double v) {
        Vertex lerp(Vertex b, double t) { return new Vertex(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t, u + (b.u - u) * t, v + (b.v - v) * t); }
    }

    /** One triangle, wound counter-clockwise seen from above, with the normal of its unclipped source triangle. */
    public record Triangle(Material material, Vertex a, Vertex b, Vertex c, double nx, double ny, double nz) {}

    private RoadMesh() {}

    /** The triangles of a road inside chunk (cx, cz), relative to (cx * 16, 0, cz * 16). Empty when the road misses it. */
    public static List<Triangle> chunk(RoadDesign d, int cx, int cz) {
        double x0 = cx * 16, z0 = cz * 16;
        CrossSection sec = d.section;
        double[] across = across(sec);
        double wIn = wInner(sec), wOut = wOuter(sec), wEdge = wEdge(sec);
        // the station range whose pavement can reach the chunk: every point of the chunk is within 2.9 m of this grid
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4; i++) for (int j = 0; j <= 4; j++) {
            double[] l = d.local(x0 + i * 4, z0 + j * 4, sec.edge + CrossSection.BEVEL_RUN + 4);
            if (l == null) continue;
            lo = Math.min(lo, l[0]);
            hi = Math.max(hi, l[0]);
        }
        List<Triangle> out = new ArrayList<>();
        if (lo > hi) return out;
        boolean start = lo <= 6, end = hi >= d.length - 6;
        lo = Math.max(0, lo - 6);
        hi = Math.min(d.length, hi + 6);
        List<Triangle> world = new ArrayList<>();
        // only the end bevels can reach a chunk beyond the route's ends
        Double[] st = lo < hi ? stations(d.length, lo, hi) : new Double[0];
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1];
            double vCell = Math.floor((s0 + 1e-9) / TEXTURE) * TEXTURE;
            boolean dash = (s0 + 1e-9) % DASH_PERIOD < DASH;
            boolean noPass = !sec.divided && d.noPassing(s0);
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i + 1 < across.length; i++) {
                    double w0 = across[i], w1 = across[i + 1];
                    double uCell = Math.floor((w0 + 1e-9) / TEXTURE) * TEXTURE;
                    patch(world, d, Material.ASPHALT, side, s0, s1, w0, w1, (w0 - uCell) / TEXTURE, (w1 - uCell) / TEXTURE, vCell);
                }
                if (sec.divided) {
                    line(world, d, Material.YELLOW, side, s0, s1, wIn, vCell);
                } else if (noPass) {
                    // one of the two solid centre lines, on this side of the centreline
                    patch(world, d, Material.YELLOW, side, s0, s1, DOUBLE_GAP, DOUBLE_GAP + LINE, 0, 1, vCell);
                } else if (dash) {
                    // this side's half of the broken centre line
                    patch(world, d, Material.YELLOW, side, s0, s1, 0, LINE / 2, 0.5, 1, vCell);
                }
                line(world, d, Material.WHITE, side, s0, s1, wOut, vCell);
                if (dash) for (int q = 1; q < sec.lanes; q++) line(world, d, Material.WHITE, side, s0, s1, wIn + q * sec.lane, vCell);
                bevel(world, d, side, s0, s1, wEdge, wEdge + CrossSection.BEVEL_RUN, vCell);
                if (sec.divided) bevel(world, d, side, s0, s1, 0, -CrossSection.BEVEL_RUN, vCell);
            }
        }
        for (int side : new int[]{-1, 1}) {
            if (start) endBevel(world, d, side, 0, -CrossSection.BEVEL_RUN);
            if (end) endBevel(world, d, side, d.length, d.length + CrossSection.BEVEL_RUN);
        }
        for (Triangle t : world) clipInto(out, t, x0, z0);
        return out;
    }

    /** The global station lattice restricted to [lo, hi] (whole intervals): 2 m steps, the end, the dash ends. */
    static Double[] stations(double length, double lo, double hi) {
        TreeSet<Double> set = new TreeSet<>();
        double a = Math.max(0, Math.floor(lo / STEP) * STEP), b = Math.min(length, Math.ceil(hi / STEP) * STEP);
        for (double s = a; s <= b + 1e-9 && s < length - 1e-9; s += STEP) set.add(s);
        if (b >= length - 1e-9) set.add(length);
        else set.add(b);
        for (double s = Math.floor(a / DASH_PERIOD) * DASH_PERIOD; s <= b; s += DASH_PERIOD)
            if (s + DASH < length && s + DASH >= a && s + DASH <= b) set.add(s + DASH);
        return set.toArray(new Double[0]);
    }

    private static double lateral(RoadDesign d, int side, double w) { return side * (d.section.halfMedian + w); }

    private static Vertex vertex(RoadDesign d, int side, double s, double w, double tu, double tv, double drop) {
        double[] p = d.point(s, lateral(d, side, w));
        double y = d.pavementY(s, lateral(d, side, Math.max(0, Math.min(wEdge(d.section), w)))) - drop;
        return new Vertex(p[0], y, p[1], tu, tv);
    }

    private static double tv(double s, double vCell) { return Math.max(0, Math.min(1, (s - vCell) / TEXTURE)); }

    private static void patch(List<Triangle> out, RoadDesign d, Material m, int side, double s0, double s1, double w0, double w1,
                              double u0, double u1, double vCell) {
        Vertex a = vertex(d, side, s0, w0, u0, tv(s0, vCell), 0), b = vertex(d, side, s0, w1, u1, tv(s0, vCell), 0),
                c = vertex(d, side, s1, w1, u1, tv(s1, vCell), 0), e = vertex(d, side, s1, w0, u0, tv(s1, vCell), 0);
        quad(out, m, a, b, c, e);
    }

    private static void line(List<Triangle> out, RoadDesign d, Material m, int side, double s0, double s1, double w, double vCell) {
        patch(out, d, m, side, s0, s1, w - LINE / 2, w, 0, 0.5, vCell);
        patch(out, d, m, side, s0, s1, w, w + LINE / 2, 0.5, 1, vCell);
    }

    private static void bevel(List<Triangle> out, RoadDesign d, int side, double s0, double s1, double wTop, double wBottom, double vCell) {
        Vertex a = vertex(d, side, s0, wTop, 0, tv(s0, vCell), 0), b = vertex(d, side, s0, wBottom, BEVEL_UV, tv(s0, vCell), CrossSection.BEVEL_DROP),
                c = vertex(d, side, s1, wBottom, BEVEL_UV, tv(s1, vCell), CrossSection.BEVEL_DROP), e = vertex(d, side, s1, wTop, 0, tv(s1, vCell), 0);
        quad(out, Material.ASPHALT, a, b, c, e);
    }

    private static void endBevel(List<Triangle> out, RoadDesign d, int side, double sTop, double sBottom) {
        double[] across = across(d.section);
        double wEdge = wEdge(d.section);
        for (int i = 0; i + 1 < across.length; i++) {
            double w0 = across[i], w1 = across[i + 1], uCell = Math.floor((w0 + 1e-9) / TEXTURE) * TEXTURE;
            double u0 = (w0 - uCell) / TEXTURE, u1 = (w1 - uCell) / TEXTURE;
            quad(out, Material.ASPHALT, endVertex(d, side, sTop, sBottom, w0, u0, false), endVertex(d, side, sTop, sBottom, w1, u1, false),
                    endVertex(d, side, sTop, sBottom, w1, u1, true), endVertex(d, side, sTop, sBottom, w0, u0, true));
        }
        double[][] corners = d.section.divided ? new double[][]{{0, -CrossSection.BEVEL_RUN}, {wEdge, wEdge + CrossSection.BEVEL_RUN}}
                : new double[][]{{wEdge, wEdge + CrossSection.BEVEL_RUN}};
        for (double[] corner : corners) {
            Vertex a = endVertex(d, side, sTop, sBottom, corner[0], 0, false), e = endVertex(d, side, sTop, sBottom, corner[0], 0, true);
            Vertex b = vertex(d, side, sTop, corner[1], BEVEL_UV, 0, CrossSection.BEVEL_DROP);
            tri(out, Material.ASPHALT, a, b, e);
        }
    }

    private static Vertex endVertex(RoadDesign d, int side, double sTop, double sBottom, double w, double tu, boolean bottom) {
        double u = lateral(d, side, w);
        double[] p = d.point(bottom ? sBottom : sTop, u);
        double y = d.pavementY(sTop, lateral(d, side, Math.max(0, Math.min(wEdge(d.section), w)))) - (bottom ? CrossSection.BEVEL_DROP : 0);
        return new Vertex(p[0], y, p[1], tu, bottom ? BEVEL_UV : 0);
    }

    private static void quad(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c, Vertex e) {
        tri(out, m, a, b, c);
        tri(out, m, a, c, e);
    }

    /** Adds the triangle wound so its normal points up, with that normal; degenerate ones (in plan) are dropped. */
    private static void tri(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        if (Math.abs(ux * vz - uz * vx) < 1e-10) return;
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (ny < 0) { Vertex t = b; b = c; c = t; nx = -nx; ny = -ny; nz = -nz; }
        out.add(new Triangle(m, a, b, c, nx / n, ny / n, nz / n));
    }

    // ------------------------------------------------------------------ clipping

    /** Clip a world-space triangle to [x0, x0 + 16] x [z0, z0 + 16] and add the pieces relative to (x0, z0). */
    public static void clipInto(List<Triangle> out, Triangle t, double x0, double z0) {
        double minX = Math.min(t.a().x(), Math.min(t.b().x(), t.c().x())), maxX = Math.max(t.a().x(), Math.max(t.b().x(), t.c().x()));
        double minZ = Math.min(t.a().z(), Math.min(t.b().z(), t.c().z())), maxZ = Math.max(t.a().z(), Math.max(t.b().z(), t.c().z()));
        // half-open ownership: a face lying in a chunk's boundary plane (a vertical bridge face) belongs to the chunk on its + side
        if (maxX - minX < 1e-9 ? minX < x0 || minX >= x0 + 16 : maxX <= x0 || minX >= x0 + 16) return;
        if (maxZ - minZ < 1e-9 ? minZ < z0 || minZ >= z0 + 16 : maxZ <= z0 || minZ >= z0 + 16) return;
        List<Vertex> p = List.of(t.a(), t.b(), t.c());
        p = plane(p, true, x0, true);
        p = plane(p, true, x0 + 16, false);
        p = plane(p, false, z0, true);
        p = plane(p, false, z0 + 16, false);
        for (int i = 1; i + 1 < p.size(); i++) {
            Vertex a = rel(p.get(0), x0, z0), b = rel(p.get(i), x0, z0), c = rel(p.get(i + 1), x0, z0);
            if (area3(a, b, c) <= 1e-9) continue;
            out.add(new Triangle(t.material(), a, b, c, t.nx(), t.ny(), t.nz()));
        }
    }

    /**
     * A chunk's triangles (chunk-relative) without their parts over the removed block cells (removed[lx | lz << 4]):
     * a triangle touching one is cut into its per-cell pieces and those over removed cells are dropped (a vertical face in
     * a cell boundary belongs to the cell on its + side).
     */
    public static List<Triangle> without(List<Triangle> tris, boolean[] removed) {
        List<Triangle> out = new ArrayList<>(tris.size());
        for (Triangle t : tris) {
            int x0 = cell(Math.min(t.a().x(), Math.min(t.b().x(), t.c().x()))), x1 = Math.max(x0, cell(Math.max(t.a().x(), Math.max(t.b().x(), t.c().x())) - 1e-9));
            int z0 = cell(Math.min(t.a().z(), Math.min(t.b().z(), t.c().z()))), z1 = Math.max(z0, cell(Math.max(t.a().z(), Math.max(t.b().z(), t.c().z())) - 1e-9));
            boolean touches = false;
            for (int z = z0; z <= z1 && !touches; z++) for (int x = x0; x <= x1; x++) if (removed[x | z << 4]) { touches = true; break; }
            if (!touches) { out.add(t); continue; }
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
                if (removed[x | z << 4]) continue;
                List<Vertex> p = List.of(t.a(), t.b(), t.c());
                p = plane(p, true, x, true);
                p = plane(p, true, x + 1, false);
                p = plane(p, false, z, true);
                p = plane(p, false, z + 1, false);
                for (int i = 1; i + 1 < p.size(); i++) {
                    Vertex a = p.get(0), b = p.get(i), c = p.get(i + 1);
                    if (area3(a, b, c) <= 1e-9) continue;
                    out.add(new Triangle(t.material(), a, b, c, t.nx(), t.ny(), t.nz()));
                }
            }
        }
        return out;
    }

    /** Twice the triangle's area in space (vertical faces have none in plan but are kept). */
    static double area3(Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        return Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    private static int cell(double v) { return Math.max(0, Math.min(15, (int) Math.floor(v))); }

    private static Vertex rel(Vertex v, double x0, double z0) { return new Vertex(v.x() - x0, v.y(), v.z() - z0, v.u(), v.v()); }

    private static List<Vertex> plane(List<Vertex> in, boolean x, double edge, boolean lower) {
        if (in.isEmpty()) return in;
        List<Vertex> out = new ArrayList<>(6);
        Vertex a = in.get(in.size() - 1);
        double da = (x ? a.x() : a.z()) - edge;
        boolean ia = lower ? da >= 0 : da <= 0;
        for (Vertex b : in) {
            double db = (x ? b.x() : b.z()) - edge;
            boolean ib = lower ? db >= 0 : db <= 0;
            if (ia != ib) {
                Vertex v = a.lerp(b, da / (da - db));
                out.add(x ? new Vertex(edge, v.y(), v.z(), v.u(), v.v()) : new Vertex(v.x(), v.y(), edge, v.u(), v.v()));
            }
            if (ib) out.add(b);
            a = b;
            da = db;
            ia = ib;
        }
        return out;
    }
}
