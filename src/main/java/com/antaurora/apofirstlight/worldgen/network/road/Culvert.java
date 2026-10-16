package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Triangle;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A box culvert under a built road (C3.3a group D, 2026-10-15; docs/worldgen/federal_highways_c3.md 11.13): a concrete
 * trough laid along the stream, roofed where the embankment passes over it, the embankment continuous above. Pure Java;
 * the geometry is decided on the server from the terrain (RoadStructures) and carried in the built network JSON, so
 * the server's works and chunk pass and the client's mesh and collision read the same numbers.
 * <p>
 * Frame: a along the stream's axis (pointing to the road's +u side), b across it, u the road's lateral offset at the
 * crossing (u = a cos + b sin, the road taken straight over the culvert's length). The trough: |b| <= {@link #out}, a in
 * tip0 .. tip1:
 * <ul>
 *   <li>the channel |b| < width / 2 over a gravel bed (the bed block is bed - 1, the stream's water level kept);</li>
 *   <li>the walls width / 2 .. out, WALL thick, from the bed up: under the roof to the slab's top, in the mouths to a
 *   straight coping that falls from the headwall's top to the tip, COPING above the embankment behind it;</li>
 *   <li>the roof between the two portals, the road-parallel lines u = -u0 and u = u1: the slab soffit .. slabTop, and
 *   over the last WALL before each portal the headwall up to its top (the embankment there plus COPING); the earth over
 *   the slab elsewhere is the road's own finish (pavement or turf);</li>
 *   <li>the mouths: beyond the portals, the open trough between its walls, to the tips.</li>
 * </ul>
 * Columns: a cell whose square crosses the headwall's back line (or lies wholly behind it) keeps the road's finish on
 * top and has the culvert's body below it; the other cells of the trough belong to the culvert (Kind.CULVERT), except
 * those that cross a wall's outer face in a mouth, which are the embankment's. Every wall exposed with open water or air
 * on one side and earth on the other is WALL = 1.5 m thick, more than a block's diagonal, so no block straddles both
 * faces: a cell across the inner face is invisible structure (waterlogged below the water), a cell across the outer face
 * is earth whose top lies under the coping.
 */
public final class Culvert {
    /** Wall and headwall thickness, m. */
    public static final double WALL = 1.5;
    /** Roof slab depth, and a coping's height over the earth it retains, m. */
    public static final double SLAB = 0.3, COPING = 0.15;
    /** Wet points between the tips within this much of the trough are filled (the stream goes through the culvert), m. */
    public static final double FILL_REACH = 30;

    public final double s, ox, oz, ax, az, nx, nz, width, soffit, u0, u1, head0, head1, tip0, tip1;
    public final int bed, level;
    /** Coping at the tips: [end * 2 + (side > 0 ? 1 : 0)]. */
    private final double[] tipTop;
    /** Across the stream (b), the slab's top, half the clear span, the trough's half width, a . N and b . N. */
    public final double bx, bz, slabTop, half, out;
    private final double cosA, bn;

    public Culvert(double s, double ox, double oz, double ax, double az, double nx, double nz, double width, int bed, int level,
                   double soffit, double u0, double u1, double head0, double head1, double tip0, double tip1, double[] tipTop) {
        double la = Math.hypot(ax, az), ln = Math.hypot(nx, nz);
        this.s = s;
        this.ox = ox;
        this.oz = oz;
        this.ax = ax / la;
        this.az = az / la;
        this.nx = nx / ln;
        this.nz = nz / ln;
        this.width = width;
        this.bed = bed;
        this.level = level;
        this.soffit = soffit;
        this.u0 = u0;
        this.u1 = u1;
        this.head0 = head0;
        this.head1 = head1;
        this.tip0 = tip0;
        this.tip1 = tip1;
        this.tipTop = tipTop.clone();
        bx = -this.az;
        bz = this.ax;
        slabTop = soffit + SLAB;
        half = width / 2;
        out = half + WALL;
        cosA = this.ax * this.nx + this.az * this.nz;
        bn = bx * this.nx + bz * this.nz;
        if (cosA < 0.2) throw new IllegalArgumentException("culvert axis along the road at " + s);
    }

    // ------------------------------------------------------------------ frame

    public double a(double x, double z) { return (x - ox) * ax + (z - oz) * az; }

    public double b(double x, double z) { return (x - ox) * bx + (z - oz) * bz; }

    public double u(double a, double b) { return a * cosA + b * bn; }

    double wx(double a, double b) { return ox + a * ax + b * bx; }

    double wz(double a, double b) { return oz + a * az + b * bz; }

    /** The a of the point at road lateral u and stream lateral b. */
    public double aAt(double u, double b) { return (u - b * bn) / cosA; }

    /** The a where the portal of an end (0: u = -u0, 1: u = u1) crosses lateral position b. */
    public double aPortal(int end, double b) { return aAt(end == 1 ? u1 : -u0, b); }

    /** The a where the headwall's back line (WALL inside the portal) crosses b. */
    double aBack(int end, double b) { return ((end == 1 ? u1 - WALL : -u0 + WALL) - b * bn) / cosA; }

    public double head(int end) { return end == 1 ? head1 : head0; }

    public double tip(int end) { return end == 1 ? tip1 : tip0; }

    public double tipTop(int end, int side) { return tipTop[end * 2 + (side > 0 ? 1 : 0)]; }

    /** The end a point belongs to (by its u: the nearer portal). */
    int end(double u) { return u >= (u1 - u0) * 0.5 ? 1 : 0; }

    /** Distance inside the portal of an end, towards the road (negative in the mouth). */
    double q(int end, double u) { return end == 1 ? u1 - u : u + u0; }

    /**
     * The a where a mouth wall's coping starts falling: the portal's outermost point across the wall (a skewed portal
     * crosses the wall obliquely; flat at the head's top until here, so the coping meets the headwall's top level).
     */
    double aStart(int end, int side) {
        double p = aPortal(end, side * half), q = aPortal(end, side * out);
        return end == 1 ? Math.max(p, q) : Math.min(p, q);
    }

    /** A mouth wall's coping at a: straight from the headwall's top at the portal to the tip's, never above the head. */
    public double wallTop(int end, int side, double a) {
        double ap = aStart(end, side), tip = tip(end), h = head(end);
        double t = (a - ap) / (tip - ap);
        return Math.min(h, h + (tipTop(end, side) - h) * t);
    }

    /** Bounding box in world xz: {x0, z0, x1, z1}. */
    public double[] bounds(double margin) {
        double r = out + margin;
        double[] xs = {wx(tip0 - margin, -r), wx(tip0 - margin, r), wx(tip1 + margin, -r), wx(tip1 + margin, r)};
        double[] zs = {wz(tip0 - margin, -r), wz(tip0 - margin, r), wz(tip1 + margin, -r), wz(tip1 + margin, r)};
        double x0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) { x0 = Math.min(x0, xs[i]); x1 = Math.max(x1, xs[i]); z0 = Math.min(z0, zs[i]); z1 = Math.max(z1, zs[i]); }
        return new double[]{x0, z0, x1, z1};
    }

    /**
     * Whether a wet point is filled by the embankment (between the tips, near the trough): the stream is led straight
     * through the culvert from tip to tip, its old meanders under the embankment filled.
     */
    public boolean fills(double x, double z) {
        double a = a(x, z);
        return a >= tip0 && a <= tip1 && Math.abs(b(x, z)) <= out + FILL_REACH;
    }

    // ------------------------------------------------------------------ solid

    /**
     * The concrete at world point (x, z): {bottom, top} ranges (bottom -infinity: down to the bed), or null. The roof over
     * the channel between the portals (the headwall's top over its last WALL), the walls from the bed to the slab, the
     * headwall's top or the mouth's coping.
     */
    public double[] solid(double x, double z) {
        double a = a(x, z), b = b(x, z);
        if (a < tip0 || a > tip1) return null;
        double vb = Math.abs(b);
        if (vb > out) return null;
        double u = u(a, b);
        int e = end(u);
        boolean roofed = u >= -u0 && u <= u1;
        double roof = q(e, u) <= WALL ? head(e) : slabTop;
        if (vb < half) return roofed ? new double[]{soffit, roof} : null;
        return new double[]{Double.NEGATIVE_INFINITY, roofed ? roof : wallTop(e, b < 0 ? -1 : 1, a)};
    }

    // ------------------------------------------------------------------ cells

    /**
     * One block column of the culvert. own: the culvert's (Kind.CULVERT), else the body under the road's finish. channel,
     * wall, roof: the square reaches the channel, a wall, the roofed channel; roofLo: the block holding the soffit; top,
     * layers: the highest concrete block and its layers (top = level, layers 0: water only); keepGround: a cell across a
     * tip, off the channel, which keeps the natural ground under its concrete.
     */
    public record Cell(Culvert culvert, boolean own, boolean channel, boolean wall, boolean roof, boolean keepGround, int roofLo, int top, int layers) {
        /** Whether block y of the column holds concrete. */
        public boolean concrete(int y) {
            if (y > top || layers == 0) return false;
            return wall && y >= culvert.bed || roof && y >= roofLo;
        }

        public boolean water(int y) { return channel && y >= culvert.bed && y <= culvert.level; }
    }

    /** The culvert's cell at block column (x, z), or null where the column is not the culvert's (earth, or off it). */
    public Cell cell(int x, int z) {
        double amin = Double.POSITIVE_INFINITY, amax = Double.NEGATIVE_INFINITY, bmin = amin, bmax = amax, umin = amin, umax = amax;
        for (int k = 0; k < 4; k++) {
            double px = x + (k & 1), pz = z + (k >> 1);
            double a = a(px, pz), b = b(px, pz), u = u(a, b);
            amin = Math.min(amin, a); amax = Math.max(amax, a);
            bmin = Math.min(bmin, b); bmax = Math.max(bmax, b);
            umin = Math.min(umin, u); umax = Math.max(umax, u);
        }
        double ac = a(x + 0.5, z + 0.5), uc = u(ac, b(x + 0.5, z + 0.5));
        if (ac < tip0 || ac > tip1) return null;
        double vmin = bmin <= 0 && bmax >= 0 ? 0 : Math.min(Math.abs(bmin), Math.abs(bmax)), vmax = Math.max(Math.abs(bmin), Math.abs(bmax));
        if (vmin >= out) return null;
        int e = end(uc);
        double qmax = Math.max(q(e, umin), q(e, umax));
        boolean body = qmax > WALL;
        if (!body && vmax > out + 1e-9) return null;   // across a mouth wall's outer face: the embankment's
        boolean channel = vmin < half, wall = vmax > half - 1e-9;
        boolean roof = channel && umax > -u0 && umin < u1;
        // the highest concrete over the square (sampled; the corners included); a body cell holds the slab and the walls
        // under it, never a headwall's top (the turf there rises under its coping: RoadWorks.column)
        double top = Double.NEGATIVE_INFINITY;
        for (int j = 0; j <= 8; j++) for (int i = 0; i <= 8; i++) {
            double[] r = solid(x + i / 8.0, z + j / 8.0);
            if (r != null) top = Math.max(top, body ? Math.min(r[1], slabTop) : r[1]);
        }
        int topBlock = level, layers = 0;
        if (top > Double.NEGATIVE_INFINITY && (wall || roof)) {
            topBlock = (int) Math.ceil(top - 1e-9) - 1;
            layers = Math.max(1, Math.min(32, (int) Math.round((top - topBlock) * 32)));
        } else {
            wall = roof = false;
        }
        boolean keep = !body && !channel && (amin < tip0 || amax > tip1);
        return new Cell(this, !body, channel, wall, roof, keep, (int) Math.floor(soffit), topBlock, layers);
    }

    // ------------------------------------------------------------------ mesh

    /**
     * The culvert's concrete in world space (BridgeMesh clips it to chunks): the walls' inner faces (from a block into the
     * bed), the soffit, the headwalls (front over the opening, top, back), the mouth walls' copings, back faces and tip
     * ends. Faces inside the earth are drawn down to the bed (hidden there).
     */
    public void mesh(List<Triangle> tris) {
        double foot = bed - 1;
        for (int side : new int[]{-1, 1}) {
            double bi = side * half, bo = side * out;
            double p0 = aPortal(0, bi), p1 = aPortal(1, bi), s0 = aStart(0, side), s1 = aStart(1, side);
            // inner face: mouth 0 (falling, then flat at the head), roofed, mouth 1 (flat, then falling)
            double[][] runs = {{tip0, Math.min(s0, p0), 0}, {Math.min(s0, p0), p0, 0}, {p0, p1, -1}, {p1, Math.max(s1, p1), 1}, {Math.max(s1, p1), tip1, 1}};
            for (double[] r : runs) {
                double a0 = r[0], a1 = r[1];
                if (a1 - a0 < 1e-6) continue;
                int e = (int) r[2];
                double t0 = e < 0 ? soffit : wallTop(e, side, a0), t1 = e < 0 ? soffit : wallTop(e, side, a1);
                vertical(tris, a0, bi, a1, bi, foot, t0, t1, a0, a1, -side * bx, -side * bz);
            }
            // mouth copings, back faces, tip ends
            for (int e = 0; e < 2; e++) {
                int sg = e == 1 ? 1 : -1;
                double tip = tip(e), h = head(e), ap = aStart(e, side);
                // coping: the strip between the portal and the tip, flat at the head until ap, then falling
                List<double[]> strip = new ArrayList<>(List.of(new double[]{aPortal(e, bi), bi}, new double[]{tip, bi},
                        new double[]{tip, bo}, new double[]{aPortal(e, bo), bo}));
                final int end = e, sd = side;
                flat(tris, strip, ap, (a, b) -> wallTop(end, sd, a), 1);
                // back face from the headwall's back line to the tip (the headwall's lateral end, then the mouth wall)
                double ab = aBack(e, bo);
                vertical(tris, ab, bo, ap, bo, foot, h, h, ab, ap, side * bx, side * bz);
                vertical(tris, ap, bo, tip, bo, foot, h, tipTop(e, side), ap, tip, side * bx, side * bz);
                // tip end
                vertical(tris, tip, bi, tip, bo, foot, tipTop(e, side), tipTop(e, side), half, out, sg * ax, sg * az);
            }
        }
        for (int e = 0; e < 2; e++) {
            int sg = e == 1 ? 1 : -1;
            double h = head(e);
            // headwall front over the opening, its top, its back (down into the slab)
            vertical(tris, aPortal(e, -half), -half, aPortal(e, half), half, soffit, h, h, -half, half, sg * nx, sg * nz);
            List<double[]> top = List.of(new double[]{aPortal(e, -out), -out}, new double[]{aPortal(e, out), out},
                    new double[]{aBack(e, out), out}, new double[]{aBack(e, -out), -out});
            flat(tris, new ArrayList<>(top), Double.NaN, (a, b) -> h, 1);
            vertical(tris, aBack(e, -out), -out, aBack(e, out), out, slabTop - 0.5, h, h, -out, out, -sg * nx, -sg * nz);
        }
        // soffit between the portals
        List<double[]> soff = List.of(new double[]{aPortal(0, -half), -half}, new double[]{aPortal(0, half), half},
                new double[]{aPortal(1, half), half}, new double[]{aPortal(1, -half), -half});
        flat(tris, new ArrayList<>(soff), Double.NaN, (a, b) -> soffit, -1);
    }

    /**
     * A vertical face over the line (a0, b0) -> (a1, b1) from the bottom up to tops t0 / t1, normal (nx, nz); texture
     * coordinate tu0 .. tu1 along the line (the frame's a or b, so the tiles line up along a wall).
     */
    private void vertical(List<Triangle> out, double a0, double b0, double a1, double b1, double bottom, double t0, double t1,
                          double tu0, double tu1, double nx, double nz) {
        if (Math.hypot(a1 - a0, b1 - b0) < 1e-6) return;
        BridgeMesh.wall(out, wx(a0, b0), wz(a0, b0), wx(a1, b1), wz(a1, b1), bottom, bottom, t0, t1, tu0, tu1, nx, 0, nz);
    }

    /**
     * A horizontal or sloped face over a polygon in (a, b), split into the frame's 1 m tiles (and at a = split when that
     * is a number), heights y(a, b) per vertex; ny: 1 up, -1 down.
     */
    private void flat(List<Triangle> out, List<double[]> poly, double split, java.util.function.DoubleBinaryOperator y, int ny) {
        double amin = Double.POSITIVE_INFINITY, amax = Double.NEGATIVE_INFINITY, bmin = amin, bmax = amax;
        for (double[] p : poly) { amin = Math.min(amin, p[0]); amax = Math.max(amax, p[0]); bmin = Math.min(bmin, p[1]); bmax = Math.max(bmax, p[1]); }
        List<Double> as = new ArrayList<>();
        for (double a = Math.floor(amin); a < amax; a++) as.add(a);
        as.add(Math.ceil(amax));
        for (int i = 0; i + 1 < as.size(); i++) {
            double ia = as.get(i), ib = as.get(i + 1);
            List<double[]> col = clip(clip(poly, 0, ia, false), 0, ib, true);
            if (col.size() < 3) continue;
            List<List<double[]>> parts = new ArrayList<>();
            if (!Double.isNaN(split) && split > ia && split < ib) { parts.add(clip(col, 0, split, true)); parts.add(clip(col, 0, split, false)); }
            else parts.add(col);
            for (List<double[]> part : parts)
                for (double jb = Math.floor(bmin); jb < bmax; jb++) {
                    List<double[]> cell = clip(clip(part, 1, jb, false), 1, jb + 1, true);
                    if (cell.size() < 3) continue;
                    List<Vertex> v = new ArrayList<>(cell.size());
                    for (double[] p : cell)
                        v.add(new Vertex(wx(p[0], p[1]), y.applyAsDouble(p[0], p[1]), wz(p[0], p[1]), clamp(p[0] - ia), clamp(p[1] - jb)));
                    BridgeMesh.polygon(out, v, 0, ny, 0, true);
                }
        }
    }

    /** Clip a polygon of (a, b) to component k <= edge (lower) or >= edge. */
    private static List<double[]> clip(List<double[]> in, int k, double edge, boolean lower) {
        List<double[]> res = new ArrayList<>(in.size() + 2);
        if (in.size() < 3) return res;
        double[] p = in.get(in.size() - 1);
        double dp = p[k] - edge;
        boolean ip = lower ? dp <= 1e-9 : dp >= -1e-9;
        for (double[] c : in) {
            double dc = c[k] - edge;
            boolean ic = lower ? dc <= 1e-9 : dc >= -1e-9;
            if (ip != ic) {
                double t = dp / (dp - dc);
                double[] m = {p[0] + (c[0] - p[0]) * t, p[1] + (c[1] - p[1]) * t};
                m[k] = edge;
                res.add(m);
            }
            if (ic) res.add(c);
            p = c;
            dp = dc;
            ip = ic;
        }
        return res;
    }

    private static double clamp(double t) { return Math.max(0, Math.min(1, t)); }

    // ------------------------------------------------------------------ JSON

    /** This culvert as a built-network JSON object (stations and heights at 2 decimals, directions at 5). */
    public String toJson() {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"s\":").append(RoadNetworkJson.fmt(s)).append(",\"x\":").append(RoadNetworkJson.fmt(ox)).append(",\"z\":").append(RoadNetworkJson.fmt(oz))
                .append(",\"ax\":").append(dir(ax)).append(",\"az\":").append(dir(az)).append(",\"nx\":").append(dir(nx)).append(",\"nz\":").append(dir(nz))
                .append(",\"w\":").append(RoadNetworkJson.fmt(width)).append(",\"bed\":").append(bed).append(",\"level\":").append(level)
                .append(",\"soffit\":").append(RoadNetworkJson.fmt(soffit)).append(",\"u0\":").append(RoadNetworkJson.fmt(u0)).append(",\"u1\":").append(RoadNetworkJson.fmt(u1))
                .append(",\"head0\":").append(RoadNetworkJson.fmt(head0)).append(",\"head1\":").append(RoadNetworkJson.fmt(head1))
                .append(",\"tip0\":").append(RoadNetworkJson.fmt(tip0)).append(",\"tip1\":").append(RoadNetworkJson.fmt(tip1)).append(",\"tops\":[");
        for (int i = 0; i < 4; i++) b.append(i > 0 ? "," : "").append(RoadNetworkJson.fmt(tipTop[i]));
        return b.append("]}").toString();
    }

    private static String dir(double v) { return String.format(Locale.ROOT, "%.5f", v); }

    @SuppressWarnings("unchecked")
    static Culvert of(Map<String, Object> m) {
        List<Object> t = (List<Object>) m.get("tops");
        double[] tops = new double[4];
        for (int i = 0; i < 4; i++) tops[i] = (Double) t.get(i);
        return new Culvert(d(m, "s"), d(m, "x"), d(m, "z"), d(m, "ax"), d(m, "az"), d(m, "nx"), d(m, "nz"), d(m, "w"),
                (int) Math.round(d(m, "bed")), (int) Math.round(d(m, "level")), d(m, "soffit"), d(m, "u0"), d(m, "u1"),
                d(m, "head0"), d(m, "head1"), d(m, "tip0"), d(m, "tip1"), tops);
    }

    private static double d(Map<String, Object> m, String k) { return (Double) m.get(k); }

    /** The same culvert as a reader of its JSON gets it (what the server builds from, so both sides agree to the bit). */
    public Culvert rounded() { return of(asMap(new RoadNetworkJson.Reader(toJson()).value())); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }
}
