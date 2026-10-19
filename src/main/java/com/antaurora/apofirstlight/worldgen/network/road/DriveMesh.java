package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The forest home's gravel driveway as a mesh (County road G3, 2026-10-18, docs/worldgen/county_road_g3.md 6; user: "车道改成
 * 和县道一样的平滑路面（碎石贴图），边缘不要锯齿"): a ribbon along Driveway's centreline to F (the apron's start), its stations
 * every STEP metres and on every texture tile line, across it lines every LANE_STEP metres (the edge follows the width
 * exactly, no saw); then the apron (the side join, 2026-10-19): rows across it by the way t still to the paved edge (every
 * APRON_LINE over the straight part, by FILLET_ROWS angles over the fillets), APRON_COLS columns each spread over the
 * row's width, the gravel run on UNDER beneath the asphalt; every vertex at Driveway's own height (#height there: the
 * collision and the vehicles' query use the same function). A SKIRT down from both edges (and the home's end; the road's
 * end too when not joined) hides the turf's 1/8 steps beside it. Material GRAVEL, a tile every RoadMesh.TEXTURE metres
 * along and across (across measured from TILE_ORIGIN; the apron's triangles cut on the tile lines). Pure Java; the client
 * draws it with the roads' chunk meshes (JunctionMesh#pavement).
 */
public final class DriveMesh {
    private DriveMesh() {}

    static final double STEP = 1, LANE_STEP = 0.5, SKIRT = 0.25, APRON_LINE = 0.5;
    static final int FILLET_ROWS = 12, APRON_COLS = 30;
    /** The lateral offset where a texture tile starts (tiles at -2 .. 2, 2 .. 6, ...), m. */
    static final double TILE_ORIGIN = -2;
    private static final double TINY = 1e-5;

    /** A station: position, unit left normal, datum, half width, distance from the road end, distance from the home end. */
    private record Station(double x, double z, double nx, double nz, double y, double w, double toEnd, double along) {
        double[] at(double u) { return new double[]{x + nx * u, z + nz * u}; }

        double y(double u) { return y - Driveway.CROWN * Math.abs(u); }
    }

    /** The driveway's triangles inside chunk (cx, cz), relative to (cx * 16, 0, cz * 16). */
    public static List<RoadMesh.Triangle> chunk(Driveway dw, int cx, int cz) {
        List<RoadMesh.Triangle> out = new ArrayList<>();
        if (dw.isEmpty() || !dw.touches(cx, cz)) return out;
        double x0 = cx * 16, z0 = cz * 16;
        List<Station> st = stations(dw);
        double reach = Driveway.HALF + 0.5;
        List<RoadMesh.Triangle> world = new ArrayList<>();
        for (int i = 0; i + 1 < st.size(); i++) {
            Station a = st.get(i), b = st.get(i + 1);
            if (Math.max(a.x, b.x) + reach < x0 || Math.min(a.x, b.x) - reach > x0 + 16 || Math.max(a.z, b.z) + reach < z0 || Math.min(a.z, b.z) - reach > z0 + 16) continue;
            strip(world, a, b);
            edge(world, a, b, 1);
            edge(world, a, b, -1);
        }
        end(world, st.get(0), -1);
        if (dw.joinedApron()) {
            double[] f = dw.apronPoint(Driveway.APRON / 2, 0);
            double r = Driveway.APRON + Driveway.HALF + Driveway.FILLET + 2;
            if (f[0] + r >= x0 && f[0] - r <= x0 + 16 && f[1] + r >= z0 && f[1] - r <= z0 + 16) apron(world, dw);
        } else end(world, st.get(st.size() - 1), 1);
        for (RoadMesh.Triangle t : world) RoadMesh.clipInto(out, t, x0, z0);
        return out;
    }

    /** The ribbon's stations from the home end to its road end (F when joined). */
    private static List<Station> stations(Driveway dw) {
        int n = dw.size();
        double total = dw.toEnd(0);
        // unit left normals of the segments, then at the points (the bisector of the two segments meeting there)
        double[][] seg = new double[n - 1][];
        for (int i = 0; i + 1 < n; i++) {
            double dx = dw.x(i + 1) - dw.x(i), dz = dw.z(i + 1) - dw.z(i), l = Math.hypot(dx, dz);
            seg[i] = l < 1e-9 ? new double[]{0, 0} : new double[]{-dz / l, dx / l};
        }
        double[][] pn = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] a = seg[Math.max(0, i - 1)], b = seg[Math.min(n - 2, i)];
            pn[i] = unit(a[0] + b[0], a[1] + b[1]);
        }
        // the ribbon ends on F's line, square to the way in (Driveway#endWay)
        double[] way = dw.endWay();
        if (way != null) pn[n - 1] = new double[]{-way[1], way[0]};
        List<Station> out = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) {
            double a0 = total - dw.toEnd(i), a1 = total - dw.toEnd(i + 1), len = a1 - a0;
            if (len < 1e-9) continue;
            TreeSet<Double> ts = new TreeSet<>();
            int k = Math.max(1, (int) Math.ceil(len / STEP - 1e-9));
            for (int j = 0; j < k; j++) ts.add((double) j / k);
            // the texture's tile lines along
            for (double t = Math.ceil(a0 / RoadMesh.TEXTURE) * RoadMesh.TEXTURE; t < a1 - 1e-6; t += RoadMesh.TEXTURE)
                if (t > a0 + 1e-6) ts.add((t - a0) / len);
            for (double t : ts) out.add(station(dw, pn, i, t, a0 + len * t));
        }
        out.add(station(dw, pn, n - 2, 1, total - dw.toEnd(n - 1)));
        return out;
    }

    private static Station station(Driveway dw, double[][] pn, int i, double t, double along) {
        double x = dw.x(i) + (dw.x(i + 1) - dw.x(i)) * t, z = dw.z(i) + (dw.z(i + 1) - dw.z(i)) * t;
        double y = dw.y(i) + (dw.y(i + 1) - dw.y(i)) * t, toEnd = dw.toEnd(i) + (dw.toEnd(i + 1) - dw.toEnd(i)) * t;
        double[] nv = unit(pn[i][0] + (pn[i + 1][0] - pn[i][0]) * t, pn[i][1] + (pn[i + 1][1] - pn[i][1]) * t);
        return new Station(x, z, nv[0], nv[1], y, Driveway.HALF, toEnd, along);
    }

    private static double[] unit(double x, double z) {
        double l = Math.hypot(x, z);
        return l < 1e-12 ? new double[]{0, 0} : new double[]{x / l, z / l};
    }

    /**
     * A vertex of station s at lateral u, its texture across at tu (u kept within its strip: a strip pinched to the edge
     * at a narrower station keeps its own tile) from the tile at tu0, along from the tile at tv0.
     */
    private static RoadMesh.Vertex vertex(Station s, double u, double tu, double tu0, double tv0) {
        double[] p = s.at(u);
        return new RoadMesh.Vertex(p[0], s.y(u), p[1], (tu - TILE_ORIGIN - tu0) / RoadMesh.TEXTURE, (s.along - tv0) / RoadMesh.TEXTURE);
    }

    /** The surface between stations a and b: a strip between each pair of lateral lines, clamped to each station's width. */
    private static void strip(List<RoadMesh.Triangle> out, Station a, Station b) {
        double wmax = Math.max(a.w, b.w);
        int m = (int) Math.ceil(wmax / LANE_STEP - 1e-9);
        double tv0 = Math.floor((a.along + b.along) / 2 / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
        for (int k = -m; k < m; k++) {
            double l0 = k * LANE_STEP, l1 = (k + 1) * LANE_STEP;
            double tu0 = Math.floor(((l0 + l1) / 2 - TILE_ORIGIN) / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
            double ua0 = clamp(l0, a.w), ua1 = clamp(l1, a.w), ub0 = clamp(l0, b.w), ub1 = clamp(l1, b.w);
            if (ua1 - ua0 < 1e-9 && ub1 - ub0 < 1e-9) continue;
            RoadMesh.Vertex p0 = vertex(a, ua0, within(ua0, l0, l1), tu0, tv0), p1 = vertex(a, ua1, within(ua1, l0, l1), tu0, tv0),
                    q0 = vertex(b, ub0, within(ub0, l0, l1), tu0, tv0), q1 = vertex(b, ub1, within(ub1, l0, l1), tu0, tv0);
            top(out, p0, p1, q1);
            top(out, p0, q1, q0);
        }
    }

    private static double clamp(double u, double w) { return Math.max(-w, Math.min(w, u)); }

    private static double within(double u, double lo, double hi) { return Math.max(lo, Math.min(hi, u)); }

    /** The skirt down from the edge on side (+1 left, -1 right) between stations a and b. */
    private static void edge(List<RoadMesh.Triangle> out, Station a, Station b, int side) {
        double ua = side * a.w, ub = side * b.w;
        double[] pa = a.at(ua), pb = b.at(ub);
        double tv0 = Math.floor((a.along + b.along) / 2 / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
        double[] nv = unit(side * (a.nx + b.nx), side * (a.nz + b.nz));
        skirt(out, pa[0], pa[1], a.y(ua), pb[0], pb[1], b.y(ub), (a.along - tv0) / RoadMesh.TEXTURE, (b.along - tv0) / RoadMesh.TEXTURE, nv[0], nv[1]);
    }

    /** A skirt SKIRT down from the top edge (x0, y0, z0) .. (x1, y1, z1), its texture along v0 .. v1, facing (nx, nz). */
    private static void skirt(List<RoadMesh.Triangle> out, double x0, double z0, double y0, double x1, double z1, double y1, double v0, double v1, double nx, double nz) {
        double d = SKIRT / RoadMesh.TEXTURE;
        RoadMesh.Vertex ta = new RoadMesh.Vertex(x0, y0, z0, v0, 0), tb = new RoadMesh.Vertex(x1, y1, z1, v1, 0),
                ba = new RoadMesh.Vertex(x0, y0 - SKIRT, z0, v0, d), bb = new RoadMesh.Vertex(x1, y1 - SKIRT, z1, v1, d);
        side(out, ta, tb, bb, nx, nz);
        side(out, ta, bb, ba, nx, nz);
    }

    /** The skirt down from the end at station s, facing dir (-1: back toward the home, +1: on toward the road). */
    private static void end(List<RoadMesh.Triangle> out, Station s, int dir) {
        // the way along: the left normal turned clockwise
        double fx = s.nz * dir, fz = -s.nx * dir;
        int m = (int) Math.ceil(s.w / LANE_STEP - 1e-9);
        for (int k = -m; k < m; k++) {
            double u0 = clamp(k * LANE_STEP, s.w), u1 = clamp((k + 1) * LANE_STEP, s.w);
            if (u1 - u0 < 1e-9) continue;
            double[] p0 = s.at(u0), p1 = s.at(u1);
            double c0 = (u0 - TILE_ORIGIN) / RoadMesh.TEXTURE, c1 = (u1 - TILE_ORIGIN) / RoadMesh.TEXTURE, t0 = Math.floor((c0 + c1) / 2);
            skirt(out, p0[0], p0[1], s.y(u0), p1[0], p1[1], s.y(u1), c0 - t0, c1 - t0, fx, fz);
        }
    }

    // ------------------------------------------------------------------ the apron (the side join)

    /** The apron's surface, the gravel tucked under the asphalt, the skirts down from its fillets (world coordinates). */
    private static void apron(List<RoadMesh.Triangle> out, Driveway dw) {
        List<Double> ts = new ArrayList<>();
        for (double t = Driveway.APRON; t > Driveway.FILLET + 1e-9; t -= APRON_LINE) ts.add(t);
        for (int k = 0; k <= FILLET_ROWS; k++) ts.add(Driveway.FILLET * (1 - Math.cos(Math.PI / 2 * (1 - (double) k / FILLET_ROWS))));
        int rows = ts.size();
        double[][][] g = new double[rows + 2][APRON_COLS + 1][];
        for (int r = 0; r < rows; r++) {
            double t = ts.get(r), hw = Driveway.width(t);
            for (int c = 0; c <= APRON_COLS; c++) {
                double v = hw * (-1 + 2.0 * c / APRON_COLS), a = (1 - t / Driveway.APRON) * dw.reach(v);
                g[r][c] = new double[]{v, a, dw.apronY(a, v)};
            }
        }
        // under the asphalt: from the paved edge on UNDER, UNDER_DROP below the pavement
        for (int c = 0; c <= APRON_COLS; c++) {
            double v = g[rows - 1][c][0];
            for (int e = 0; e < 2; e++) {
                double a = dw.reach(v) + e * Driveway.UNDER;
                double[] p = dw.apronPoint(a, v);
                g[rows + e][c] = new double[]{v, a, dw.underY(p[0], p[1])};
            }
        }
        for (int r = 0; r + 1 < rows + 2; r++) {
            if (r == rows - 1) continue;
            for (int c = 0; c < APRON_COLS; c++) {
                tiles(out, dw, g[r][c], g[r][c + 1], g[r + 1][c + 1]);
                tiles(out, dw, g[r][c], g[r + 1][c + 1], g[r + 1][c]);
            }
        }
        // the skirts down from both fillets
        double[] o = dw.apronPoint(0, 0), l = dw.apronPoint(0, 1);
        double lx = l[0] - o[0], lz = l[1] - o[1];
        for (int side = -1; side <= 1; side += 2) {
            int c = side < 0 ? 0 : APRON_COLS;
            for (int r = 0; r + 1 < rows; r++) {
                double[] a = g[r][c], b = g[r + 1][c];
                double[] pa = dw.apronPoint(a[1], a[0]), pb = dw.apronPoint(b[1], b[0]);
                double dx = pb[0] - pa[0], dz = pb[1] - pa[1], len = Math.hypot(dx, dz);
                if (len < 1e-6) continue;
                double nx = dz / len, nz = -dx / len;
                if ((nx * lx + nz * lz) * side < 0) { nx = -nx; nz = -nz; }
                double along = dw.alongF() + (a[1] + b[1]) / 2, tv0 = Math.floor(along / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
                double v0 = clamp01((dw.alongF() + a[1] - tv0) / RoadMesh.TEXTURE), v1 = clamp01((dw.alongF() + b[1] - tv0) / RoadMesh.TEXTURE);
                skirt(out, pa[0], pa[1], a[2], pb[0], pb[1], b[2], v0, v1, nx, nz);
            }
        }
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /**
     * The triangle {v, a, y} x3 of the apron cut on the texture's tile lines (across at TILE_ORIGIN + k TEXTURE, along at
     * k TEXTURE from the home end), each piece with its own tile's coordinates.
     */
    private static void tiles(List<RoadMesh.Triangle> out, Driveway dw, double[] p, double[] q, double[] r) {
        double base = dw.alongF(), tex = RoadMesh.TEXTURE;
        double[][] tri = {{p[0], base + p[1], p[2]}, {q[0], base + q[1], q[2]}, {r[0], base + r[1], r[2]}};
        double u0 = Math.min(p[0], Math.min(q[0], r[0])), u1 = Math.max(p[0], Math.max(q[0], r[0]));
        double a0 = base + Math.min(p[1], Math.min(q[1], r[1])), a1 = base + Math.max(p[1], Math.max(q[1], r[1]));
        int iu0 = (int) Math.floor((u0 - TILE_ORIGIN) / tex), iu1 = (int) Math.floor((u1 - TILE_ORIGIN - 1e-9) / tex);
        int ia0 = (int) Math.floor(a0 / tex), ia1 = (int) Math.floor((a1 - 1e-9) / tex);
        for (int iu = iu0; iu <= Math.max(iu0, iu1); iu++)
            for (int ia = ia0; ia <= Math.max(ia0, ia1); ia++) {
                double lu = TILE_ORIGIN + iu * tex, la = ia * tex;
                List<double[]> poly = List.of(tri);
                poly = cut(poly, 0, lu, true);
                poly = cut(poly, 0, lu + tex, false);
                poly = cut(poly, 1, la, true);
                poly = cut(poly, 1, la + tex, false);
                for (int i = 1; i + 1 < poly.size(); i++)
                    top(out, apronVertex(dw, poly.get(0), lu, la), apronVertex(dw, poly.get(i), lu, la), apronVertex(dw, poly.get(i + 1), lu, la));
            }
    }

    /** The polygon's part on the kept side of coordinate k = line (keep greater: at or above it). */
    private static List<double[]> cut(List<double[]> poly, int k, double line, boolean greater) {
        List<double[]> out = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double da = greater ? a[k] - line : line - a[k], db = greater ? b[k] - line : line - b[k];
            if (da >= 0) out.add(a);
            if (da >= 0 != db >= 0) {
                double f = da / (da - db);
                out.add(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f});
            }
        }
        return out;
    }

    /** An apron vertex {v, along from the home end, y} in its tile (lu across, la along). */
    private static RoadMesh.Vertex apronVertex(Driveway dw, double[] p, double lu, double la) {
        double[] w = dw.apronPoint(p[1] - dw.alongF(), p[0]);
        return new RoadMesh.Vertex(w[0], p[2], w[1], (p[0] - lu) / RoadMesh.TEXTURE, (p[1] - la) / RoadMesh.TEXTURE);
    }

    /** A top triangle wound counter-clockwise from above, its normal up; none when (nearly) without area. */
    private static void top(List<RoadMesh.Triangle> out, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 2 * TINY) return;
        if (ny < 0) { RoadMesh.Vertex t = b; b = c; c = t; nx = -nx; ny = -ny; nz = -nz; }
        out.add(new RoadMesh.Triangle(RoadMesh.Material.GRAVEL, a, b, c, nx / n, ny / n, nz / n));
    }

    /** A side triangle wound counter-clockwise seen from where its normal (nx, 0, nz) points. */
    private static void side(List<RoadMesh.Triangle> out, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c, double nx, double nz) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        if (Math.sqrt(cx * cx + cy * cy + cz * cz) < 2 * TINY) return;
        if (cx * nx + cz * nz < 0) { RoadMesh.Vertex t = b; b = c; c = t; }
        out.add(new RoadMesh.Triangle(RoadMesh.Material.GRAVEL, a, b, c, nx, 0, nz));
    }
}
