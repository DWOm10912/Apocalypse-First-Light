package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Triangle;
import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The R4 pavement mesh (docs/worldgen/highway_r4_sample_v1.md, C). Pure Java; triangles in coordinates relative to
 * (originX, 0, originZ), heights absolute.
 * <ul>
 *   <li>"asphalt": both carriageways and their shoulders, the 30-degree safety edges; texture period 4 m both ways, the
 *   lateral period measured outward from the median edge (no seam between lanes);</li>
 *   <li>"yellow": inner edge line (w = 1.5 from the median edge), "white": outer edge line (w = 9.0) and the 3 m / 9 m lane
 *   line (w = 5.25), all 0.15 m wide, coplanar with the asphalt (the renderer offsets them in depth), split on the crown
 *   and shoulder breaks so they follow the creased surface.</li>
 * </ul>
 * Stations: a global 2 m lattice plus the dash ends; never depends on chunks or load order.
 */
public final class R4Mesh {
    public static final double STEP = 2, TEXTURE = 4, LINE = 0.15, DASH = 3, DASH_PERIOD = 12;
    private static final double W_INNER = R4Section.INNER, W_LANE = R4Section.INNER + R4Section.LANE,
            W_OUTER = R4Section.INNER + 2 * R4Section.LANE, W_EDGE = W_OUTER + R4Section.OUTER;

    private R4Mesh() {}

    public static List<Triangle> triangles(R4Design d, double originX, double originZ) {
        var stations = new TreeSet<Double>();
        for (double s = 0; s < d.length - 1e-9; s += STEP) stations.add(s);
        stations.add(d.length);
        for (double s = 0; s < d.length; s += DASH_PERIOD) if (s + DASH < d.length) stations.add(s + DASH);
        Double[] st = stations.toArray(new Double[0]);
        List<Triangle> out = new ArrayList<>();
        double[] across = {0, W_INNER, TEXTURE, 2 * TEXTURE, W_OUTER, 3 * TEXTURE, W_EDGE};
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1];
            double vCell = Math.floor((s0 + 1e-9) / TEXTURE) * TEXTURE;
            boolean dash = (s0 + 1e-9) % DASH_PERIOD < DASH;
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i + 1 < across.length; i++) {
                    double w0 = across[i], w1 = across[i + 1];
                    double uCell = Math.floor((w0 + 1e-9) / TEXTURE) * TEXTURE;
                    patch(out, d, originX, originZ, "asphalt", side, s0, s1, w0, w1, (w0 - uCell) / TEXTURE, (w1 - uCell) / TEXTURE, vCell);
                }
                line(out, d, originX, originZ, "yellow", side, s0, s1, W_INNER, vCell);
                line(out, d, originX, originZ, "white", side, s0, s1, W_OUTER, vCell);
                if (dash) line(out, d, originX, originZ, "white", side, s0, s1, (W_INNER + W_OUTER) / 2, vCell);
                // safety edges: outer edge into the roadside, inner edge into the median
                bevel(out, d, originX, originZ, side, s0, s1, W_EDGE, W_EDGE + R4Section.BEVEL_RUN, vCell);
                bevel(out, d, originX, originZ, side, s0, s1, 0, -R4Section.BEVEL_RUN, vCell);
            }
        }
        // end safety edges across both paved bands
        for (int side : new int[]{-1, 1}) {
            endBevel(out, d, originX, originZ, side, 0, -R4Section.BEVEL_RUN);
            endBevel(out, d, originX, originZ, side, d.length, d.length + R4Section.BEVEL_RUN);
        }
        return List.copyOf(out);
    }

    /** Lateral u of a distance w outward from the median edge on one side. */
    private static double lateral(int side, double w) { return side * (R4Section.HALF_MEDIAN + w); }

    private static Vertex vertex(R4Design d, double ox, double oz, int side, double s, double w, double du, double tu, double tv, double drop) {
        double u = lateral(side, w);
        double[] p = d.point(s, u);
        double y = d.pavementY(s, lateral(side, Math.max(0, Math.min(W_EDGE, w)))) - drop;
        return new Vertex(p[0] - ox, y, p[1] - oz, tu, tv);
    }

    private static double tv(double s, double vCell) { return Math.max(0, Math.min(1, (s - vCell) / TEXTURE)); }

    private static void patch(List<Triangle> out, R4Design d, double ox, double oz, String material, int side,
                              double s0, double s1, double w0, double w1, double u0, double u1, double vCell) {
        Vertex a = vertex(d, ox, oz, side, s0, w0, 0, u0, tv(s0, vCell), 0), b = vertex(d, ox, oz, side, s0, w1, 0, u1, tv(s0, vCell), 0),
                c = vertex(d, ox, oz, side, s1, w1, 0, u1, tv(s1, vCell), 0), e = vertex(d, ox, oz, side, s1, w0, 0, u0, tv(s1, vCell), 0);
        quad(out, material, a, b, c, e);
    }

    /** A 0.15 m line centred on w, split on a slope break at w itself (crown line, shoulder edge). */
    private static void line(List<Triangle> out, R4Design d, double ox, double oz, String material, int side, double s0, double s1, double w, double vCell) {
        double a = w - LINE / 2, b = w + LINE / 2;
        patch(out, d, ox, oz, material, side, s0, s1, a, w, 0, 0.5, vCell);
        patch(out, d, ox, oz, material, side, s0, s1, w, b, 0.5, 1, vCell);
    }

    private static final double BEVEL_UV = R4Section.BEVEL_RUN / TEXTURE;

    private static void bevel(List<Triangle> out, R4Design d, double ox, double oz, int side, double s0, double s1, double wTop, double wBottom, double vCell) {
        Vertex a = vertex(d, ox, oz, side, s0, wTop, 0, 0, tv(s0, vCell), 0), b = vertex(d, ox, oz, side, s0, wBottom, 0, BEVEL_UV, tv(s0, vCell), R4Section.BEVEL_DROP),
                c = vertex(d, ox, oz, side, s1, wBottom, 0, BEVEL_UV, tv(s1, vCell), R4Section.BEVEL_DROP), e = vertex(d, ox, oz, side, s1, wTop, 0, 0, tv(s1, vCell), 0);
        quad(out, "asphalt", a, b, c, e);
    }

    /** End edge of one paved band at station sTop, sloping down to sBottom (outside the segment); split at the texture cells. */
    private static void endBevel(List<Triangle> out, R4Design d, double ox, double oz, int side, double sTop, double sBottom) {
        double[] across = {0, W_INNER, TEXTURE, 2 * TEXTURE, W_OUTER, 3 * TEXTURE, W_EDGE};
        for (int i = 0; i + 1 < across.length; i++) {
            double w0 = across[i], w1 = across[i + 1], uCell = Math.floor((w0 + 1e-9) / TEXTURE) * TEXTURE;
            double u0 = (w0 - uCell) / TEXTURE, u1 = (w1 - uCell) / TEXTURE;
            Vertex a = endVertex(d, ox, oz, side, sTop, sBottom, w0, u0, false), b = endVertex(d, ox, oz, side, sTop, sBottom, w1, u1, false),
                    c = endVertex(d, ox, oz, side, sTop, sBottom, w1, u1, true), e = endVertex(d, ox, oz, side, sTop, sBottom, w0, u0, true);
            quad(out, "asphalt", a, b, c, e);
        }
        // the two band corners: the triangle between the side bevel and the end bevel
        for (double[] corner : new double[][]{{0, -R4Section.BEVEL_RUN}, {W_EDGE, W_EDGE + R4Section.BEVEL_RUN}}) {
            Vertex a = endVertex(d, ox, oz, side, sTop, sBottom, corner[0], 0, false), e = endVertex(d, ox, oz, side, sTop, sBottom, corner[0], 0, true);
            Vertex b = vertex(d, ox, oz, side, sTop, corner[1], 0, BEVEL_UV, 0, R4Section.BEVEL_DROP);
            tri(out, "asphalt", a, b, e);
        }
    }

    private static Vertex endVertex(R4Design d, double ox, double oz, int side, double sTop, double sBottom, double w, double tu, boolean bottom) {
        double s = bottom ? sBottom : sTop, u = lateral(side, w);
        double[] p = d.point(s, u);
        double y = d.pavementY(sTop, u) - (bottom ? R4Section.BEVEL_DROP : 0);
        return new Vertex(p[0] - ox, y, p[1] - oz, tu, bottom ? BEVEL_UV : 0);
    }

    /** Two triangles, each wound so its normal points up (the format's outward side). */
    private static void quad(List<Triangle> out, String material, Vertex a, Vertex b, Vertex c, Vertex e) {
        tri(out, material, a, b, c);
        tri(out, material, a, c, e);
    }

    private static void tri(List<Triangle> out, String material, Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x(), uz = b.z() - a.z(), vx = c.x() - a.x(), vz = c.z() - a.z();
        double ny = uz * vx - ux * vz;   // y of (b - a) x (c - a)
        if (Math.abs(ux * vz - uz * vx) < 1e-10) return;
        out.add(ny > 0 ? new Triangle(material, a, b, c) : new Triangle(material, a, c, b));
    }
}
