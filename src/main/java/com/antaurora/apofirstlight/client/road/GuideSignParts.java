package com.antaurora.apofirstlight.client.road;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.network.road.GuideSignAtlas;
import com.antaurora.apofirstlight.worldgen.network.road.GuideSigns;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * An interchange guide sign's geometry (C3.3b group 2 package B, 2026-10-17; laid out by
 * worldgen/network/road/GuideSigns) in its own frame, in RoadClient's vertex layout, on the sheet block/road/guide_sign
 * (GuideSignAtlas, tools/build-guide-signs-v1.mjs). Two streams:
 * <ul>
 *   <li>solid: the green face of the panel and its tab (rounded convex outlines, fanned), their aluminium backs and
 *   edges, the galvanised posts sunk under their feet and the two stringers between them and the panel;</li>
 *   <li>overlay (the marking stream: its polygon offset, and FACE in front of the face): the white border rings, the
 *   legend's character cells and the shield and arrow tiles, each cell opaque white on the same green.</li>
 * </ul>
 * The face and the overlay take one light, of the block in front of the panel's centre (anchor), so the cells' green
 * never shows against the panel's in uneven light. Uniform faces sample the middle half of their material tile (UVs
 * spread with the face: never degenerate, a shader pack's tangents stay defined).
 */
final class GuideSignParts {
    static final ResourceLocation SHEET = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/road/guide_sign");
    /** Segments per rounded corner. */
    private static final int ARC = 6;

    private GuideSignParts() {}

    /** A sign in its frame: the solid stream (its first front vertices the face), the overlay, the light anchor (part-local). */
    record Local(float[] solid, int front, float[] overlay, double[] anchor) {}

    /** A growing vertex array. */
    private static final class Buf {
        float[] v = new float[1024];
        int n;

        void put(double x, double y, double z, float u, float w, double nx, double ny, double nz) {
            if (n + RoadClient.STRIDE > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
            v[n++] = (float) x; v[n++] = (float) y; v[n++] = (float) z; v[n++] = u; v[n++] = w;
            v[n++] = (float) nx; v[n++] = (float) ny; v[n++] = (float) nz;
        }

        int vertices() { return n / RoadClient.STRIDE; }

        float[] array() { return java.util.Arrays.copyOf(v, n); }
    }

    /** UV mapping onto the sheet: the sprite's {u0, u1, v0, v1}. */
    private record Sheet(float[] s) {
        float u(double px) { return s[0] + (s[1] - s[0]) * (float) (px / GuideSignAtlas.WIDTH); }

        float v(double py) { return s[2] + (s[3] - s[2]) * (float) (py / GuideSignAtlas.HEIGHT); }
    }

    /** A uniform material's middle half on the sheet {px0, py0, px1, py1}. */
    private static double[] material(String name) {
        int[] r = GuideSignAtlas.tileRect(GuideSignAtlas.tile(name));
        return new double[]{r[0] + r[2] / 4.0, r[1] + r[3] / 4.0, r[0] + 3 * r[2] / 4.0, r[1] + 3 * r[3] / 4.0};
    }

    static Local build(String bone, float[] sprite) {
        Sheet sh = new Sheet(sprite);
        GuideSigns.Layout l = GuideSigns.layout(bone);
        double[] p = l.panel(), t = l.tab();
        double r = GuideSigns.RADIUS, rt = GuideSigns.TAB_RADIUS;
        boolean tab = t != null, full = tab && t[0] <= p[0] + 1e-6;
        // the panel's corners (bottom left, bottom right, top right, top left): square under the tab
        double[] pr = {r, r, tab ? 0 : r, full ? 0 : r};
        List<double[]> panel = outline(p[0], p[1], p[2], p[3], pr, tab && !full ? t[0] : Double.NaN);
        List<double[]> tabLine = tab ? outline(t[0], t[1], t[2], t[3], new double[]{0, 0, rt, rt}, Double.NaN) : null;
        // the sign's extent for the uniform faces' UVs
        double bx0 = p[0], by0 = Math.min(p[1], Math.min(l.feet()[0], l.feet()[1]) - GuideSigns.POST_SINK), bx1 = p[2], by1 = tab ? t[3] : p[3];
        double[] box = {bx0, by0, bx1, by1};

        Buf solid = new Buf();
        double[] green = material("green"), back = material("back"), post = material("post"), white = material("white");
        fan(solid, sh, panel, 0, 1, green, box);
        if (tab) fan(solid, sh, tabLine, 0, 1, green, box);
        int front = solid.vertices();
        fan(solid, sh, panel, -GuideSigns.PANEL, -1, back, box);
        if (tab) fan(solid, sh, tabLine, -GuideSigns.PANEL, -1, back, box);
        edges(solid, sh, panel, tab ? t : null, back, box);
        if (tab) edges(solid, sh, tabLine, t, back, box);
        for (int i = 0; i < 2; i++) {
            double x = l.posts()[i];
            // the faces but the bottom (under the ground)
            box(solid, sh, post, x - GuideSigns.POST / 2, l.feet()[i] - GuideSigns.POST_SINK, GuideSigns.POST_BACK,
                    x + GuideSigns.POST / 2, p[3] - GuideSigns.POST_TOP, GuideSigns.POST_FRONT, 0b111111 & ~(1 << 2));
        }
        // the stringers: no face against the panel's back
        for (double y : GuideSigns.stringers(p))
            box(solid, sh, post, p[0] + 0.1, y, GuideSigns.POST_FRONT, p[2] - 0.1, y + GuideSigns.STRINGER, -GuideSigns.PANEL, 0b111111 & ~(1 << 5));

        Buf over = new Buf();
        double z = GuideSigns.FACE, in = GuideSigns.BORDER_INSET, bw = GuideSigns.BORDER;
        ring(over, sh, outline(p[0] + in, p[1] + in, p[2] - in, p[3] - in, inset(pr, in), Double.NaN),
                outline(p[0] + in + bw, p[1] + in + bw, p[2] - in - bw, p[3] - in - bw, inset(pr, in + bw), Double.NaN), true, z, white, box);
        if (tab) {
            // the tab's border down to the panel's: its sides and top (the panel's top border runs under it)
            double ty0 = p[3] - in;
            ring(over, sh, outline(t[0] + in, ty0, t[2] - in, t[3] - in, new double[]{0, 0, rt - in, rt - in}, Double.NaN),
                    outline(t[0] + in + bw, ty0, t[2] - in - bw, t[3] - in - bw, new double[]{0, 0, rt - in - bw, rt - in - bw}, Double.NaN),
                    false, z, white, box);
        }
        for (GuideSigns.Piece q : l.pieces()) {
            int[] rect;
            if (q.tile() != null) {
                int ti = GuideSignAtlas.tile(q.tile());
                if (ti < 0) continue;
                rect = GuideSignAtlas.tileRect(ti);
            } else {
                int ci = GuideSignAtlas.index(q.ch());
                if (ci < 0) continue;
                rect = GuideSignAtlas.cell(ci);
            }
            float u0 = sh.u(rect[0]), u1 = sh.u(rect[0] + rect[2]), v0 = sh.v(rect[1]), v1 = sh.v(rect[1] + rect[3]);
            // counter-clockwise from the front; the sheet's top (v0) at the piece's top
            over.put(q.x0(), q.y0(), z, u0, v1, 0, 0, 1);
            over.put(q.x1(), q.y0(), z, u1, v1, 0, 0, 1);
            over.put(q.x1(), q.y1(), z, u1, v0, 0, 0, 1);
            over.put(q.x0(), q.y1(), z, u0, v0, 0, 0, 1);
        }
        return new Local(solid.array(), front, over.array(), new double[]{0, (p[1] + p[3]) / 2, 0.5});
    }

    private static double[] inset(double[] radii, double d) {
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) out[i] = radii[i] > 0 ? Math.max(0.005, radii[i] - d) : 0;
        return out;
    }

    /**
     * A rounded rectangle's outline counter-clockwise from the front, from its bottom left corner: each corner (bottom
     * left, bottom right, top right, top left) an arc of radius radii[k] (0: square, one point); split: an extra point on
     * the top edge at that x (NaN: none).
     */
    private static List<double[]> outline(double x0, double y0, double x1, double y1, double[] radii, double split) {
        List<double[]> out = new ArrayList<>();
        double[][] centre = {{x0 + radii[0], y0 + radii[0]}, {x1 - radii[1], y0 + radii[1]}, {x1 - radii[2], y1 - radii[2]}, {x0 + radii[3], y1 - radii[3]}};
        double[] start = {Math.PI, 1.5 * Math.PI, 0, 0.5 * Math.PI};
        for (int k = 0; k < 4; k++) {
            if (k == 3 && !Double.isNaN(split)) out.add(new double[]{split, y1});
            if (radii[k] <= 0) {
                out.add(new double[]{centre[k][0], centre[k][1]});
                continue;
            }
            for (int s = 0; s <= ARC; s++) {
                double a = start[k] + 0.5 * Math.PI * s / ARC;
                out.add(new double[]{centre[k][0] + radii[k] * Math.cos(a), centre[k][1] + radii[k] * Math.sin(a)});
            }
        }
        return out;
    }

    /** A convex outline's face at z, fanned from its centroid (four vertices a triangle: the last repeats); side 1: front, -1: back. */
    private static void fan(Buf b, Sheet sh, List<double[]> line, double z, int side, double[] mat, double[] box) {
        double cx = 0, cy = 0;
        for (double[] q : line) { cx += q[0]; cy += q[1]; }
        cx /= line.size();
        cy /= line.size();
        int n = line.size();
        for (int i = 0; i < n; i++) {
            double[] a = line.get(i), c = line.get((i + 1) % n);
            if (Math.abs(a[0] - c[0]) + Math.abs(a[1] - c[1]) < 1e-7) continue;
            double[] first = side > 0 ? a : c, second = side > 0 ? c : a;
            flat(b, sh, cx, cy, z, side, mat, box);
            flat(b, sh, first[0], first[1], z, side, mat, box);
            flat(b, sh, second[0], second[1], z, side, mat, box);
            flat(b, sh, second[0], second[1], z, side, mat, box);
        }
    }

    /** A vertex of a face across the sign (normal along z), its UV the material's middle half by its place on the sign. */
    private static void flat(Buf b, Sheet sh, double x, double y, double z, int side, double[] mat, double[] box) {
        double fu = (x - box[0]) / Math.max(1e-6, box[2] - box[0]), fv = (box[3] - y) / Math.max(1e-6, box[3] - box[1]);
        b.put(x, y, z, sh.u(mat[0] + (mat[2] - mat[0]) * fu), sh.v(mat[1] + (mat[3] - mat[1]) * fv), 0, 0, side);
    }

    /**
     * An outline's edge faces from the back (z -PANEL) to the face (z 0), but those along the tab's foot (inside the
     * sign); u along the outline's length (no edge degenerate), v across the panel's thickness.
     */
    private static void edges(Buf b, Sheet sh, List<double[]> line, double[] tab, double[] mat, double[] box) {
        int n = line.size();
        double zb = -GuideSigns.PANEL, total = 0;
        for (int i = 0; i < n; i++) total += Math.hypot(line.get((i + 1) % n)[0] - line.get(i)[0], line.get((i + 1) % n)[1] - line.get(i)[1]);
        double at = 0;
        float v0 = sh.v(mat[1]), v1 = sh.v(mat[1] + (mat[3] - mat[1]) * 0.1);
        for (int i = 0; i < n; i++) {
            double[] a = line.get(i), c = line.get((i + 1) % n);
            double dx = c[0] - a[0], dy = c[1] - a[1], len = Math.hypot(dx, dy);
            double from = at;
            at += len;
            if (len < 1e-7) continue;
            if (tab != null && Math.abs(a[1] - tab[1]) < 1e-6 && Math.abs(c[1] - tab[1]) < 1e-6
                    && Math.min(a[0], c[0]) >= tab[0] - 1e-6 && Math.max(a[0], c[0]) <= tab[2] + 1e-6) continue;
            double nx = dy / len, ny = -dx / len;
            float ua = sh.u(mat[0] + (mat[2] - mat[0]) * from / Math.max(1e-6, total)), uc = sh.u(mat[0] + (mat[2] - mat[0]) * at / Math.max(1e-6, total));
            b.put(a[0], a[1], 0, ua, v0, nx, ny, 0);
            b.put(a[0], a[1], zb, ua, v1, nx, ny, 0);
            b.put(c[0], c[1], zb, uc, v1, nx, ny, 0);
            b.put(c[0], c[1], 0, uc, v0, nx, ny, 0);
        }
    }

    /**
     * The band between outer and inner (outlines with the same corners: the same number of points) at z, facing front;
     * closed: around (else from the first point to the last, an open band: inner and outer as returned by outline, its
     * first corner square at the bottom left, walked up the left side: the band's ends are the outlines' bottom corners).
     */
    private static void ring(Buf b, Sheet sh, List<double[]> outer, List<double[]> inner, boolean closed, double z, double[] mat, double[] box) {
        int n = outer.size();
        if (closed) {
            for (int i = 0; i < n; i++) band(b, sh, outer.get(i), outer.get((i + 1) % n), inner.get((i + 1) % n), inner.get(i), z, mat, box);
            return;
        }
        // the open band: from the bottom left corner (index 0) backwards round the top to the bottom right corner (index 1)
        for (int i = 0; i > -(n - 1); i--) {
            int a = Math.floorMod(i, n), c = Math.floorMod(i - 1, n);
            // walked clockwise: inner first keeps the quad counter-clockwise from the front
            band(b, sh, inner.get(a), inner.get(c), outer.get(c), outer.get(a), z, mat, box);
        }
    }

    /** A quad a, b, c, d (counter-clockwise from the front) at z, skipped where it has no area. */
    private static void band(Buf b, Sheet sh, double[] a, double[] c1, double[] c2, double[] d, double z, double[] mat, double[] box) {
        double area = (c1[0] - a[0]) * (c2[1] - a[1]) - (c2[0] - a[0]) * (c1[1] - a[1]) + (c2[0] - a[0]) * (d[1] - a[1]) - (d[0] - a[0]) * (c2[1] - a[1]);
        if (Math.abs(area) < 1e-9) return;
        flat(b, sh, a[0], a[1], z, 1, mat, box);
        flat(b, sh, c1[0], c1[1], z, 1, mat, box);
        flat(b, sh, c2[0], c2[1], z, 1, mat, box);
        flat(b, sh, d[0], d[1], z, 1, mat, box);
    }

    /**
     * An axis box's faces (bit 2k: -axis k, bit 2k + 1: +axis k), counter-clockwise from outside; UVs the material's
     * middle half spread over the face.
     */
    private static void box(Buf out, Sheet sh, double[] mat, double x0, double y0, double z0, double x1, double y1, double z1, int faces) {
        double[] lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int k = 0; k < 3; k++)
            for (int side = 0; side < 2; side++) {
                if ((faces & 1 << (2 * k + side)) == 0) continue;
                int a = (k + 1) % 3, c = (k + 2) % 3;
                double[][] q = new double[4][3];
                double[][] ac = {{lo[a], lo[c]}, {hi[a], lo[c]}, {hi[a], hi[c]}, {lo[a], hi[c]}};
                for (int i = 0; i < 4; i++) {
                    q[i][k] = side == 0 ? lo[k] : hi[k];
                    q[i][a] = ac[i][0];
                    q[i][c] = ac[i][1];
                }
                double[] nrm = new double[3];
                nrm[k] = side == 0 ? -1 : 1;
                // (a, c, k) is a right-handed cycle: the corners run counter-clockwise seen from +k
                int[] order = side == 1 ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};
                for (int i : order) {
                    double fa = (q[i][a] - lo[a]) / Math.max(1e-6, hi[a] - lo[a]), fc = (q[i][c] - lo[c]) / Math.max(1e-6, hi[c] - lo[c]);
                    out.put(q[i][0], q[i][1], q[i][2], sh.u(mat[0] + (mat[2] - mat[0]) * fa), sh.v(mat[1] + (mat[3] - mat[1]) * fc), nrm[0], nrm[1], nrm[2]);
                }
            }
    }
}
