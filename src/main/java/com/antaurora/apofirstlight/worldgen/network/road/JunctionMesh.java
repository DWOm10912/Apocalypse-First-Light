package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;

/**
 * The mesh of a T junction's pad for one chunk (C3.3b; docs/worldgen/federal_junctions_c3_3b.md 3, Junction). Pure
 * Java; the client draws it with the roads (RoadClient), the server's ray hits use it (RoadStructureHits).
 * <ul>
 *   <li>"asphalt": the pad's triangles cut to a 0.5 m grid (CELL), every corner at the pad's height (Junction#height; on
 *   the outline linear between its points, so the pad meets the host's and the minor's meshes exactly); world-aligned
 *   4 m texture tiles;</li>
 *   <li>the 30-degree safety edge along the free edges (the two arcs and the minor's edges beyond its end line), as the
 *   roads' own (CrossSection.BEVEL_RUN / BEVEL_DROP), meeting the host's side bevel at T1 and the minor's at its end;</li>
 *   <li>"white" stop bar: Junction.STOP_NEAR to STOP_FAR from the host's paved edge (parallel to it), across the
 *   approach lane from Junction.STOP_IN off the centreline to the lane's edge;</li>
 *   <li>"yellow" double centre line (two solid lines, RoadMesh.DOUBLE_GAP and LINE) from the end line, where the minor's
 *   own no-passing lines end, to the stop bar;</li>
 *   <li>an interchange ramp's terminal (C3.3b group 2, CrossSection#oneWay): an exit ramp's stop bar across its whole lane
 *   and no centre line; an entrance ramp (its traffic leaves the crossroad) neither.</li>
 * </ul>
 * Lines are coplanar with the pad (the renderer offsets them in depth). Coordinates are relative to the chunk's origin,
 * heights absolute.
 */
public final class JunctionMesh {
    /** Marking pieces across and along; the pad's grid cells (its height bends: at most about 5 mm off within one), m. */
    private static final double PIECE = 0.5, ALONG = 1, CELL = 0.5;
    private static final double BEVEL_UV = CrossSection.BEVEL_RUN / RoadMesh.TEXTURE;

    private JunctionMesh() {}

    /** A chunk's pavement: every road's mesh (with the junctions it takes part in) and every junction pad's. */
    public static List<RoadMesh.Triangle> pavement(RoadWorks works, List<RoadDesign> roads, int cx, int cz) {
        List<RoadMesh.Triangle> out = new ArrayList<>();
        for (RoadDesign d : roads) out.addAll(RoadMesh.chunk(d, cx, cz, works.junctionsOf(d), works.marksOf(d)));
        Junction[] pads = works.padsNear(cx * 16 + 8, cz * 16 + 8);
        if (pads != null) for (Junction j : pads) out.addAll(chunk(j, cx, cz));
        return out;
    }

    /** The pad's triangles inside chunk (cx, cz), relative to (cx * 16, 0, cz * 16). */
    public static List<RoadMesh.Triangle> chunk(Junction j, int cx, int cz) {
        double x0 = cx * 16, z0 = cz * 16;
        List<RoadMesh.Triangle> out = new ArrayList<>();
        double[] b = j.bounds();
        double r = CrossSection.BEVEL_RUN + 0.1;
        if (b[2] + r < x0 || b[0] - r > x0 + 16 || b[3] + r < z0 || b[1] - r > z0 + 16) return out;
        double[] px = j.outlineX(), pz = j.outlineZ();
        int n = px.length;
        double[] py = new double[n];
        for (int i = 0; i < n; i++) py[i] = j.height(px[i], pz[i]);
        int[] t = j.triangles();
        for (int k = 0; k + 2 < t.length; k += 3)
            pad(out, j, new RoadMesh.Vertex(px[t[k]], py[t[k]], pz[t[k]], 0, 0), new RoadMesh.Vertex(px[t[k + 1]], py[t[k + 1]], pz[t[k + 1]], 0, 0),
                    new RoadMesh.Vertex(px[t[k + 2]], py[t[k + 2]], pz[t[k + 2]], 0, 0), x0, z0);
        List<RoadMesh.Triangle> world = new ArrayList<>();
        bevels(world, j, px, pz, py);
        markings(world, j);
        for (RoadMesh.Triangle w : world) RoadMesh.clipInto(out, w, x0, z0);
        return out;
    }

    // ------------------------------------------------------------------ the pad

    /** One outline triangle cut to the chunk's CELL grid; corners off the outline take the pad's height. */
    private static void pad(List<RoadMesh.Triangle> out, Junction j, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c, double x0, double z0) {
        double minX = Math.min(a.x(), Math.min(b.x(), c.x())), maxX = Math.max(a.x(), Math.max(b.x(), c.x()));
        double minZ = Math.min(a.z(), Math.min(b.z(), c.z())), maxZ = Math.max(a.z(), Math.max(b.z(), c.z()));
        int n = (int) Math.round(1 / CELL);
        int ix0 = (int) Math.max(x0 * n, Math.floor(minX * n)), ix1 = (int) Math.min((x0 + 16) * n, Math.ceil(maxX * n));
        int iz0 = (int) Math.max(z0 * n, Math.floor(minZ * n)), iz1 = (int) Math.min((z0 + 16) * n, Math.ceil(maxZ * n));
        for (int iz = iz0; iz < iz1; iz++) for (int ix = ix0; ix < ix1; ix++) {
            double cx = ix * CELL, cz = iz * CELL;
            List<RoadMesh.Vertex> p = List.of(a, b, c);
            p = plane(p, true, cx, true);
            p = plane(p, true, cx + CELL, false);
            p = plane(p, false, cz, true);
            p = plane(p, false, cz + CELL, false);
            if (p.size() < 3) continue;
            double tu = Math.floor(cx / RoadMesh.TEXTURE) * RoadMesh.TEXTURE, tv = Math.floor(cz / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
            List<RoadMesh.Vertex> q = new ArrayList<>(p.size());
            double sx = 0, sz = 0;
            for (RoadMesh.Vertex v : p) {
                // on the outline: linear along it (the neighbour meshes' edges); inside: the pad's own height
                double y = j.nearest(v.x(), v.z())[0] < 1e-4 ? v.y() : j.height(v.x(), v.z());
                q.add(new RoadMesh.Vertex(v.x() - x0, y, v.z() - z0, (v.x() - tu) / RoadMesh.TEXTURE, (v.z() - tv) / RoadMesh.TEXTURE));
                sx += v.x();
                sz += v.z();
            }
            double[] nm = j.normal(sx / p.size(), sz / p.size());
            for (int i = 1; i + 1 < q.size(); i++) {
                RoadMesh.Vertex va = q.get(0), vb = q.get(i), vc = q.get(i + 1);
                if (RoadMesh.area3(va, vb, vc) <= 1e-9) continue;
                // counter-clockwise seen from above
                double cr = (vb.z() - va.z()) * (vc.x() - va.x()) - (vb.x() - va.x()) * (vc.z() - va.z());
                if (cr < 0) { RoadMesh.Vertex s = vb; vb = vc; vc = s; }
                out.add(new RoadMesh.Triangle(RoadMesh.Material.ASPHALT, va, vb, vc, nm[0], nm[1], nm[2]));
            }
        }
    }

    // ------------------------------------------------------------------ bevels

    /**
     * The safety edge along every free outline edge: from the pad's edge out BEVEL_RUN and down BEVEL_DROP. Between two
     * free edges the corner direction is their mitre; where a free edge runs on tangent from a fixed one (an arc at T1)
     * the fixed edge's normal (the host's own bevel there); at a corner (the end line) the free edge's normal.
     */
    private static void bevels(List<RoadMesh.Triangle> out, Junction j, double[] px, double[] pz, double[] py) {
        int n = px.length;
        double area = 0;
        for (int i = 0; i < n; i++) { int k = (i + 1) % n; area += px[i] * pz[k] - px[k] * pz[i]; }
        double sg = area > 0 ? 1 : -1;
        // outward unit normals of the edges i -> i + 1
        double[] ex = new double[n], ez = new double[n];
        for (int i = 0; i < n; i++) {
            int k = (i + 1) % n;
            double dx = px[k] - px[i], dz = pz[k] - pz[i], l = Math.hypot(dx, dz);
            if (l < 1e-9) continue;
            ex[i] = sg * dz / l;
            ez[i] = -sg * dx / l;
        }
        double[] vx = new double[n], vz = new double[n];
        for (int i = 0; i < n; i++) {
            int p = (i + n - 1) % n;
            boolean fp = j.free(p), fi = j.free(i);
            if (fp && fi) {
                double mx = ex[p] + ex[i], mz = ez[p] + ez[i], m = Math.hypot(mx, mz);
                if (m < 1e-9) { vx[i] = ex[i]; vz[i] = ez[i]; continue; }
                mx /= m;
                mz /= m;
                double cos = Math.max(0.5, mx * ex[i] + mz * ez[i]);
                vx[i] = mx / cos;
                vz[i] = mz / cos;
            } else if (fp || fi) {
                int f = fi ? i : p, o = fi ? p : i;
                // tangent: the two edges' normals nearly agree
                if (ex[f] * ex[o] + ez[f] * ez[o] > 0.95) { vx[i] = ex[o]; vz[i] = ez[o]; }
                else { vx[i] = ex[f]; vz[i] = ez[f]; }
            }
        }
        double run = CrossSection.BEVEL_RUN, drop = CrossSection.BEVEL_DROP;
        for (int i = 0; i < n; i++) {
            if (!j.free(i)) continue;
            int k = (i + 1) % n;
            double l = Math.hypot(px[k] - px[i], pz[k] - pz[i]) / RoadMesh.TEXTURE;
            if (l < 1e-9) continue;
            RoadMesh.Vertex a = new RoadMesh.Vertex(px[i], py[i], pz[i], 0, 0), e = new RoadMesh.Vertex(px[k], py[k], pz[k], 0, Math.min(1, l));
            RoadMesh.Vertex b = new RoadMesh.Vertex(px[i] + vx[i] * run, py[i] - drop, pz[i] + vz[i] * run, BEVEL_UV, 0),
                    c = new RoadMesh.Vertex(px[k] + vx[k] * run, py[k] - drop, pz[k] + vz[k] * run, BEVEL_UV, Math.min(1, l));
            tri(out, RoadMesh.Material.ASPHALT, a, b, c);
            tri(out, RoadMesh.Material.ASPHALT, a, c, e);
        }
    }

    // ------------------------------------------------------------------ markings

    private static void markings(List<RoadMesh.Triangle> out, Junction j) {
        RoadDesign m = j.minor;
        // a one-way ramp (its traffic runs +s): an exit's stop bar across the lane, an entrance nothing
        boolean ramp = m.section.oneWay;
        if (ramp && !j.atEnd) return;
        // the stop bar across the approach lane (on the right of travel toward the host: u has the sign of dir)
        double u0 = ramp ? -m.section.lanesOut : Junction.STOP_IN, u1 = m.section.lanesOut;
        int pieces = Math.max(1, (int) Math.ceil((u1 - u0) / PIECE));
        RoadMesh.Vertex pn = null, pf = null;
        for (int i = 0; i <= pieces; i++) {
            double u = j.dir * (u0 + (u1 - u0) * i / pieces);
            double sn = j.stationAt(u, Junction.STOP_NEAR), sf = j.stationAt(u, Junction.STOP_FAR);
            if (Double.isNaN(sn) || Double.isNaN(sf)) { pn = pf = null; continue; }
            RoadMesh.Vertex qn = at(j, sn, u, (double) i / pieces, 0), qf = at(j, sf, u, (double) i / pieces, 1);
            if (pn != null) {
                tri(out, RoadMesh.Material.WHITE, pn, qn, qf);
                tri(out, RoadMesh.Material.WHITE, pn, qf, pf);
            }
            pn = qn;
            pf = qf;
        }
        if (ramp) return;
        // the double centre line from the end line to the stop bar
        for (int side : new int[]{-1, 1}) {
            double ua = side * RoadMesh.DOUBLE_GAP, ub = side * (RoadMesh.DOUBLE_GAP + RoadMesh.LINE);
            double la = j.dir * (j.stationAt(ua, Junction.STOP_FAR) - j.sEnd), lb = j.dir * (j.stationAt(ub, Junction.STOP_FAR) - j.sEnd);
            if (Double.isNaN(la) || Double.isNaN(lb) || la <= 0 || lb <= 0) continue;
            int steps = Math.max(1, (int) Math.ceil(Math.max(la, lb) / ALONG));
            double tv = Math.max(la, lb) / steps / RoadMesh.TEXTURE;
            for (int i = 0; i < steps; i++) {
                double f0 = (double) i / steps, f1 = (double) (i + 1) / steps;
                RoadMesh.Vertex a0 = at(j, j.sEnd + j.dir * la * f0, ua, 0, 0), b0 = at(j, j.sEnd + j.dir * lb * f0, ub, 1, 0),
                        a1 = at(j, j.sEnd + j.dir * la * f1, ua, 0, tv), b1 = at(j, j.sEnd + j.dir * lb * f1, ub, 1, tv);
                tri(out, RoadMesh.Material.YELLOW, a0, b0, b1);
                tri(out, RoadMesh.Material.YELLOW, a0, b1, a1);
            }
        }
    }

    private static RoadMesh.Vertex at(Junction j, double s, double u, double tu, double tv) {
        double[] p = j.minor.point(s, u);
        return new RoadMesh.Vertex(p[0], j.height(p[0], p[1]), p[1], tu, tv);
    }

    // ------------------------------------------------------------------ helpers

    /** Adds the triangle wound so its normal points up, with that normal; degenerate ones (in plan) are dropped. */
    private static void tri(List<RoadMesh.Triangle> out, RoadMesh.Material m, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        if (Math.abs(ux * vz - uz * vx) < 1e-10) return;
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (ny < 0) { RoadMesh.Vertex t = b; b = c; c = t; nx = -nx; ny = -ny; nz = -nz; }
        out.add(new RoadMesh.Triangle(m, a, b, c, nx / n, ny / n, nz / n));
    }

    /** Sutherland-Hodgman against one axis plane (x or z = edge), keeping the side at or above (lower) or below it. */
    private static List<RoadMesh.Vertex> plane(List<RoadMesh.Vertex> in, boolean x, double edge, boolean lower) {
        if (in.isEmpty()) return in;
        List<RoadMesh.Vertex> out = new ArrayList<>(6);
        RoadMesh.Vertex a = in.get(in.size() - 1);
        double da = (x ? a.x() : a.z()) - edge;
        boolean ia = lower ? da >= 0 : da <= 0;
        for (RoadMesh.Vertex b : in) {
            double db = (x ? b.x() : b.z()) - edge;
            boolean ib = lower ? db >= 0 : db <= 0;
            if (ia != ib) {
                RoadMesh.Vertex v = a.lerp(b, da / (da - db));
                out.add(x ? new RoadMesh.Vertex(edge, v.y(), v.z(), v.u(), v.v()) : new RoadMesh.Vertex(v.x(), v.y(), edge, v.u(), v.v()));
            }
            if (ib) out.add(b);
            a = b;
            da = db;
            ia = ib;
        }
        return out;
    }
}
