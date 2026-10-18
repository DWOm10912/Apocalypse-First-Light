package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * The paved block cells of a chunk and its one-cell ring (C3.2; docs/worldgen/federal_highways_c3.md, 10.5): the
 * collision blocks the chunk pass put there (the same pure RoadWorks.paved), which the client compares with the world
 * to cut a hole into the pavement mesh where they were dug out, and the asphalt walls of such a hole; not the squares a
 * tunnel's works fill with road_structure (Tunnel V1). Pure Java.
 */
public final class RoadCells {
    private RoadCells() {}

    /** Cells per side of the window: the chunk's 16 plus one on each side. */
    public static final int SIDE = 18;

    /** Index of the cell (lx, lz), lx and lz in -1..16 (chunk-relative). */
    public static int index(int lx, int lz) { return (lx + 1) + (lz + 1) * SIDE; }

    public static int lx(int index) { return index % SIDE - 1; }

    public static int lz(int index) { return index / SIDE - 1; }

    public static boolean inner(int index) {
        int x = lx(index), z = lz(index);
        return x >= 0 && x < 16 && z >= 0 && z < 16;
    }

    /** One paved cell: its collision blocks (y, layers; y + 1, upper when upper > 0) and the road it belongs to. */
    public record Cell(int index, int y, int layers, int upper, int road) {}

    /** The paved cells of chunk (cx, cz) and its ring, by index. */
    public static Map<Integer, Cell> of(List<RoadDesign> roads, RoadWorks works, int cx, int cz) {
        Map<Integer, Cell> out = new java.util.HashMap<>();
        for (int lz = -1; lz <= 16; lz++) for (int lx = -1; lx <= 16; lx++) {
            int x = cx * 16 + lx, z = cz * 16 + lz;
            RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, CrossSection.R4.edge + 2);
            // near a junction pad (C3.3b) the pad and the roads pave the square together
            boolean pad = works.padsNear(x + 0.5, z + 0.5) != null;
            if (h == null && !pad) continue;
            // the works' own rule: a pad and the roads (C3.3b), an attached ramp and its host, not an overpass's deck
            double[] pave = works.pavedRange(x, z);
            if (pave == null) continue;
            // a tunnel's works filling the pavement's cell (in a bore beside its pavement): road_structure, not a
            // road_surface the client could find dug (Tunnel V1); works standing on the finish (ON_GROUND: walls,
            // lead-ins, a portal's front) and a bore's pavement keep their road_surface
            RoadDesign.Tunnel tn = h == null ? null : TunnelWorks.near(h);
            if (tn != null) {
                RoadWorks.Column t = TunnelWorks.column(h, tn, x, z, pave);
                if (t != null && t.kind() != RoadWorks.Kind.PAVED && (t.data() & TunnelWorks.ON_GROUND) == 0) continue;
            }
            RoadWorks.Column c = RoadWorks.paved(RoadWorks.Kind.PAVED, pave, 0);
            out.put(index(lx, lz), new Cell(index(lx, lz), c.y(), c.layers(), c.upper(), h == null ? -1 : roads.indexOf(h.road())));
        }
        return out;
    }

    /**
     * Vertical asphalt faces (chunk-relative) where a dug cell of this chunk meets a paved cell that is not dug: from
     * that neighbour's collision block bottom (its asphalt top) up to the pavement along the shared edge, facing the hole.
     */
    public static List<RoadMesh.Triangle> walls(List<RoadDesign> roads, Map<Integer, Cell> cells, BitSet dug, int cx, int cz) {
        return walls(null, roads, cells, dug, cx, cz);
    }

    /** #walls with the works: the pavement's height there from a junction pad too (RoadWorks#paveTop). */
    public static List<RoadMesh.Triangle> walls(RoadWorks works, List<RoadDesign> roads, Map<Integer, Cell> cells, BitSet dug, int cx, int cz) {
        List<RoadMesh.Triangle> out = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = dug.nextSetBit(0); i >= 0; i = dug.nextSetBit(i + 1)) {
            if (!inner(i) || !cells.containsKey(i)) continue;
            int lx = lx(i), lz = lz(i);
            for (int[] dir : dirs) {
                int n = index(lx + dir[0], lz + dir[1]);
                Cell nb = cells.get(n);
                if (nb == null || dug.get(n)) continue;
                // the shared edge, from (ax, az) to (bx, bz), chunk-relative
                double ax, az, bx, bz;
                if (dir[0] != 0) { ax = bx = dir[0] > 0 ? lx + 1 : lx; az = lz; bz = lz + 1; }
                else { az = bz = dir[1] > 0 ? lz + 1 : lz; ax = lx; bx = lx + 1; }
                RoadDesign d = nb.road() < 0 ? null : roads.get(nb.road());
                double ta, tb;
                if (works != null) {
                    ta = works.paveTop(cx * 16 + ax, cz * 16 + az, d);
                    tb = works.paveTop(cx * 16 + bx, cz * 16 + bz, d);
                } else if (d == null) continue;
                else {
                    ta = top(d, cx * 16 + ax, cz * 16 + az);
                    tb = top(d, cx * 16 + bx, cz * 16 + bz);
                }
                if (Double.isNaN(ta) || Double.isNaN(tb)) continue;
                double y = nb.y();
                if (ta <= y && tb <= y) continue;
                double t = RoadMesh.TEXTURE;
                RoadMesh.Vertex p0 = new RoadMesh.Vertex(ax, y, az, 0, (Math.max(ta, y) - y) / t),
                        p1 = new RoadMesh.Vertex(bx, y, bz, 1 / t, (Math.max(tb, y) - y) / t),
                        p2 = new RoadMesh.Vertex(bx, Math.max(tb, y), bz, 1 / t, 0),
                        p3 = new RoadMesh.Vertex(ax, Math.max(ta, y), az, 0, 0);
                // facing into the hole: away from the neighbour
                double nx = -dir[0], nz = -dir[1];
                wall(out, p0, p1, p2, nx, nz);
                wall(out, p0, p2, p3, nx, nz);
            }
        }
        return out;
    }

    private static double top(RoadDesign d, double x, double z) {
        double[] l = d.local(x, z, d.section.edge + 4);
        return l == null ? Double.NaN : d.pavementY(l[0], l[1]);
    }

    /** A wall triangle wound counter-clockwise seen from the side its normal (nx, 0, nz) points to. */
    private static void wall(List<RoadMesh.Triangle> out, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c, double nx, double nz) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double cx = uy * vz - uz * vy, cz = ux * vy - uy * vx;
        if (Math.abs(cx) + Math.abs(cz) < 1e-12) return;
        if (cx * nx + cz * nz < 0) { RoadMesh.Vertex t = b; b = c; c = t; }
        out.add(new RoadMesh.Triangle(RoadMesh.Material.ASPHALT, a, b, c, nx, 0, nz));
    }
}
