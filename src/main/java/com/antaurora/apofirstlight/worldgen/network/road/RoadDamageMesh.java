package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.road.RoadDamageChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The drawn broken asphalt of a chunk (road Mesh-ification P1, docs/worldgen/road_mesh_damage_v1_design.md 5): the
 * pavement's triangles cut away over every gone 12.5 cm sub-cell (RoadDamageChunk), a vertical asphalt face where a gone
 * sub-cell meets asphalt still there (the wearing course's thickness, facing into the hole), and the hole's floor, the
 * aggregate base's top (gravel) at the asphalt's underside. A column whose last asphalt went is the earthwork's (P2,
 * RoadDamage#dig): its collision blocks gone, the road's mesh cuts it out whole (BridgeMesh#finish, a dug cell) and the
 * earthwork draws its ground; here only the faces of a broken neighbour's asphalt toward it (#dug: RoadCells#walls lines
 * the whole ones).
 * <p>
 * The sub-cells' outline runs on a jittered lattice (#lattice: every corner moved up to JITTER by a hash of its place, a
 * corner on a block edge only along that edge, a block corner not at all), so a hole's edge is ragged rather than a 12.5 cm
 * staircase, every block's sub-cells still tile it exactly, and neighbouring chunks agree on every shared edge. Collision
 * keeps the plain squares (RoadDamage#shape). Pure Java.
 */
public final class RoadDamageMesh {
    private RoadDamageMesh() {}

    static final int N = RoadDamageChunk.N;
    static final double SUB = 1.0 / N;
    /** How far a lattice corner moves off its square, m (design 5: ragged, not a staircase). */
    public static final double JITTER = 0.03;
    /** A pavement triangle in a broken column is cut only within this of the column's top (a deck over the road stays), m. */
    static final double BELOW = 1.2, ABOVE = 0.5;

    /** The broken columns (null: whole) and the asphalt everywhere: {pavement top, thickness} at a point near yHint, or null. */
    public interface Damage {
        RoadDamageChunk.Col col(int x, int z);

        double[] asphalt(double x, double z, double yHint);

        /** Whether paved column (x, z) has lost its collision blocks (dug out: the earthwork's after P2, a hole otherwise). */
        default boolean dug(int x, int z) { return false; }
    }

    /** Damage over the client's or a check's copies and the works' asphalt (RoadSurfaceQuery#asphalt). */
    public static Damage of(RoadWorks works, java.util.function.BiFunction<Integer, Integer, RoadDamageChunk> chunks) {
        return of(works, chunks, null, 0, 0);
    }

    /** #of with the dug cells of chunk (cx, cz) and its ring (RoadCells indices; RoadClient#dug), null: none. */
    public static Damage of(RoadWorks works, java.util.function.BiFunction<Integer, Integer, RoadDamageChunk> chunks, java.util.BitSet dug, int cx, int cz) {
        return new Damage() {
            @Override
            public boolean dug(int x, int z) {
                int lx = x - cx * 16, lz = z - cz * 16;
                return dug != null && lx >= -1 && lx <= 16 && lz >= -1 && lz <= 16 && dug.get(RoadCells.index(lx, lz));
            }

            @Override
            public RoadDamageChunk.Col col(int x, int z) {
                RoadDamageChunk c = chunks.apply(x >> 4, z >> 4);
                return c == null ? null : c.get(x, z);
            }

            @Override
            public double[] asphalt(double x, double z, double yHint) { return RoadSurfaceQuery.asphalt(works, x, z, yHint); }
        };
    }

    /** Lattice corner (i, j) (global sub-cell corner indices) in world x, z. */
    public static double[] lattice(int i, int j) {
        double x = (double) i / N, z = (double) j / N;
        long h = i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double jx = ((h & 0xFFFF) / 65535.0 * 2 - 1) * JITTER, jz = ((h >>> 16 & 0xFFFF) / 65535.0 * 2 - 1) * JITTER;
        if (Math.floorMod(i, N) == 0) jx = 0;
        if (Math.floorMod(j, N) == 0) jz = 0;
        return new double[]{x + jx, z + jz};
    }

    /** Whether the asphalt sub-cell (i, j) is still there: a broken column's bit clear, or a whole column with asphalt. */
    static boolean intact(Damage dmg, Map<Long, Boolean> paved, int i, int j, double yHint) {
        int x = Math.floorDiv(i, N), z = Math.floorDiv(j, N);
        RoadDamageChunk.Col c = dmg.col(x, z);
        if (c != null) return !RoadDamageChunk.gone(c.gone(), Math.floorMod(i, N), Math.floorMod(j, N));
        if (dmg.dug(x, z)) return false;
        return paved.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> dmg.asphalt(x + 0.5, z + 0.5, yHint) != null);
    }

    /** A chunk's triangles (chunk-relative) with its broken asphalt cut out, faced and floored. */
    public static List<RoadMesh.Triangle> apply(List<RoadMesh.Triangle> tris, Damage dmg, int cx, int cz) {
        RoadDamageChunk.Col[] cols = new RoadDamageChunk.Col[256];
        boolean any = false;
        // dug columns of the chunk beside a broken one (its faces toward them are this chunk's)
        List<int[]> dug = new ArrayList<>();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = cx * 16 + lx, z = cz * 16 + lz;
            RoadDamageChunk.Col c = dmg.col(x, z);
            if (c != null && c.gone() != 0) { cols[lx | lz << 4] = c; any = true; }
            else if (c == null && dmg.dug(x, z)) {
                for (int[] d : SIDES) if (dmg.col(x + d[0], z + d[1]) != null) { dug.add(new int[]{x, z}); break; }
            }
        }
        if (!any && dug.isEmpty()) return tris;
        double ox = cx * 16, oz = cz * 16;
        List<RoadMesh.Triangle> out = new ArrayList<>(tris.size() + 256);
        if (any) for (RoadMesh.Triangle t : tris) cut(out, t, cols, cx, cz);
        else out.addAll(tris);
        Map<Long, double[]> tops = new HashMap<>();
        Map<Long, Boolean> paved = new HashMap<>();
        for (int k = 0; k < 256; k++) {
            RoadDamageChunk.Col c = cols[k];
            if (c == null) continue;
            int bx = cx * 16 + (k & 15), bz = cz * 16 + (k >> 4);
            for (int sz = 0; sz < N; sz++) for (int sx = 0; sx < N; sx++) {
                if (!RoadDamageChunk.gone(c.gone(), sx, sz)) continue;
                int i = bx * N + sx, j = bz * N + sz;
                double[] p00 = top(dmg, tops, i, j, c), p10 = top(dmg, tops, i + 1, j, c), p11 = top(dmg, tops, i + 1, j + 1, c), p01 = top(dmg, tops, i, j + 1, c);
                floor(out, p00, p10, p11, p01, c.depth(), ox, oz);
                // the faces toward asphalt still there: x-, x+, z-, z+
                if (intact(dmg, paved, i - 1, j, c.top())) face(out, p01, p00, depth(dmg, i - 1, j, c), ox, oz);
                if (intact(dmg, paved, i + 1, j, c.top())) face(out, p10, p11, depth(dmg, i + 1, j, c), ox, oz);
                if (intact(dmg, paved, i, j - 1, c.top())) face(out, p00, p10, depth(dmg, i, j - 1, c), ox, oz);
                if (intact(dmg, paved, i, j + 1, c.top())) face(out, p11, p01, depth(dmg, i, j + 1, c), ox, oz);
            }
        }
        // a dug column's rim: the faces of a broken neighbour's asphalt still there, at that neighbour's top and thickness
        for (int[] d : dug) for (int s = 0; s < 4; s++) {
            RoadDamageChunk.Col nb = dmg.col(d[0] + SIDES[s][0], d[1] + SIDES[s][1]);
            if (nb == null) continue;
            for (int k = 0; k < N; k++) {
                // the rim's sub-cell (i, j) of the dug column, its neighbour across side s
                int i = d[0] * N + (s == 0 ? N - 1 : s == 1 ? 0 : k), j = d[1] * N + (s == 2 ? N - 1 : s == 3 ? 0 : k);
                int ni = i + SIDES[s][0], nj = j + SIDES[s][1];
                if (RoadDamageChunk.gone(nb.gone(), Math.floorMod(ni, N), Math.floorMod(nj, N))) continue;
                double[] p00 = top(dmg, tops, i, j, nb), p10 = top(dmg, tops, i + 1, j, nb), p11 = top(dmg, tops, i + 1, j + 1, nb), p01 = top(dmg, tops, i, j + 1, nb);
                switch (s) {
                    case 0 -> face(out, p10, p11, nb.depth(), ox, oz);
                    case 1 -> face(out, p01, p00, nb.depth(), ox, oz);
                    case 2 -> face(out, p11, p01, nb.depth(), ox, oz);
                    default -> face(out, p00, p10, nb.depth(), ox, oz);
                }
            }
        }
        return out;
    }

    /** x+, x-, z+, z- */
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The asphalt's thickness of the column holding sub-cell (i, j): its entry's, else the works' (else the broken one's). */
    private static double depth(Damage dmg, int i, int j, RoadDamageChunk.Col near) {
        int x = Math.floorDiv(i, N), z = Math.floorDiv(j, N);
        RoadDamageChunk.Col c = dmg.col(x, z);
        if (c != null) return c.depth();
        double[] a = dmg.asphalt(x + 0.5, z + 0.5, near.top());
        return a == null ? near.depth() : a[1];
    }

    /** Lattice corner (i, j): world {x, z, pavement top} (the drawn top; the column's collision top where no pavement is found). */
    private static double[] top(Damage dmg, Map<Long, double[]> tops, int i, int j, RoadDamageChunk.Col c) {
        return tops.computeIfAbsent(((long) i << 32) ^ (j & 0xffffffffL), k -> {
            double[] p = lattice(i, j);
            double[] a = dmg.asphalt(p[0], p[1], c.top());
            return new double[]{p[0], p[1], a == null ? c.top() : a[0]};
        });
    }

    /** The hole's floor over a gone sub-cell: the asphalt's underside, gravel, wound up. */
    private static void floor(List<RoadMesh.Triangle> out, double[] a, double[] b, double[] c, double[] d, double depth, double ox, double oz) {
        double u0 = Math.floor(Math.min(a[0], d[0]) / RoadMesh.TEXTURE) * RoadMesh.TEXTURE, v0 = Math.floor(Math.min(a[1], b[1]) / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
        RoadMesh.Vertex va = floorVertex(a, depth, u0, v0, ox, oz), vb = floorVertex(b, depth, u0, v0, ox, oz),
                vc = floorVertex(c, depth, u0, v0, ox, oz), vd = floorVertex(d, depth, u0, v0, ox, oz);
        RoadMesh.tri(out, RoadMesh.Material.GRAVEL, va, vb, vc);
        RoadMesh.tri(out, RoadMesh.Material.GRAVEL, va, vc, vd);
    }

    private static RoadMesh.Vertex floorVertex(double[] p, double depth, double u0, double v0, double ox, double oz) {
        double u = Math.max(0, Math.min(1, (p[0] - u0) / RoadMesh.TEXTURE)), v = Math.max(0, Math.min(1, (p[1] - v0) / RoadMesh.TEXTURE));
        return new RoadMesh.Vertex(p[0] - ox, p[2] - depth, p[1] - oz, u, v);
    }

    /**
     * A face of asphalt still there along the lattice edge a - b, the gone sub-cell on its left seen from above (a to b
     * counter-clockwise round the gone one): from the pavement down the thickness, facing into the hole.
     */
    private static void face(List<RoadMesh.Triangle> out, double[] a, double[] b, double depth, double ox, double oz) {
        double ex = b[0] - a[0], ez = b[1] - a[1], len = Math.hypot(ex, ez);
        if (len < 1e-6) return;
        // into the hole: the edge's left normal (counter-clockwise round the gone sub-cell, its inside on the left)
        double nx = -ez / len, nz = ex / len;
        double t = RoadMesh.TEXTURE, along = Math.floorMod((long) Math.floor((a[0] + a[1]) * N), (long) (t * N)) / (double) N;
        double ua = along / t, ub = Math.min(1, (along + len) / t);
        RoadMesh.Vertex p0 = new RoadMesh.Vertex(a[0] - ox, a[2] - depth, a[1] - oz, ua, depth / t),
                p1 = new RoadMesh.Vertex(b[0] - ox, b[2] - depth, b[1] - oz, ub, depth / t),
                p2 = new RoadMesh.Vertex(b[0] - ox, b[2], b[1] - oz, ub, 0),
                p3 = new RoadMesh.Vertex(a[0] - ox, a[2], a[1] - oz, ua, 0);
        wall(out, p0, p1, p2, nx, nz);
        wall(out, p0, p2, p3, nx, nz);
    }

    private static void wall(List<RoadMesh.Triangle> out, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c, double nx, double nz) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double px = uy * vz - uz * vy, pz = ux * vy - uy * vx;
        if (Math.abs(px) + Math.abs(pz) < 1e-12) return;
        if (px * nx + pz * nz < 0) { RoadMesh.Vertex s = b; b = c; c = s; }
        out.add(new RoadMesh.Triangle(RoadMesh.Material.ASPHALT, a, b, c, nx, 0, nz));
    }

    // ------------------------------------------------------------------ cutting

    /**
     * One triangle into out: as it is when it touches no broken column within its height band; else cut into its per-block
     * pieces, a broken block's piece further into the pieces over its sub-cells still there (the jittered quads).
     */
    private static void cut(List<RoadMesh.Triangle> out, RoadMesh.Triangle t, RoadDamageChunk.Col[] cols, int cx, int cz) {
        int x0 = cell(Math.min(t.a().x(), Math.min(t.b().x(), t.c().x()))), x1 = Math.max(x0, cell(Math.max(t.a().x(), Math.max(t.b().x(), t.c().x())) - 1e-9));
        int z0 = cell(Math.min(t.a().z(), Math.min(t.b().z(), t.c().z()))), z1 = Math.max(z0, cell(Math.max(t.a().z(), Math.max(t.b().z(), t.c().z())) - 1e-9));
        double y0 = Math.min(t.a().y(), Math.min(t.b().y(), t.c().y())), y1 = Math.max(t.a().y(), Math.max(t.b().y(), t.c().y()));
        boolean touches = false;
        for (int z = z0; z <= z1 && !touches; z++) for (int x = x0; x <= x1; x++) if (inBand(cols[x | z << 4], y0, y1)) { touches = true; break; }
        if (!touches) { out.add(t); return; }
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            List<RoadMesh.Vertex> p = List.of(t.a(), t.b(), t.c());
            p = half(p, 1, 0, x);
            p = half(p, -1, 0, -(x + 1));
            p = half(p, 0, 1, z);
            p = half(p, 0, -1, -(z + 1));
            if (p.size() < 3) continue;
            RoadDamageChunk.Col c = cols[x | z << 4];
            if (!inBand(c, y0, y1)) { fan(out, t, p); continue; }
            int bi = (cx * 16 + x) * N, bj = (cz * 16 + z) * N;
            double ox = cx * 16, oz = cz * 16;
            for (int sz = 0; sz < N; sz++) for (int sx = 0; sx < N; sx++) {
                if (RoadDamageChunk.gone(c.gone(), sx, sz)) continue;
                double[] a = lattice(bi + sx, bj + sz), b = lattice(bi + sx + 1, bj + sz), e = lattice(bi + sx + 1, bj + sz + 1), d = lattice(bi + sx, bj + sz + 1);
                List<RoadMesh.Vertex> q = p;
                q = edge(q, a, b, ox, oz);
                q = edge(q, b, e, ox, oz);
                q = edge(q, e, d, ox, oz);
                q = edge(q, d, a, ox, oz);
                fan(out, t, q);
            }
        }
    }

    private static boolean inBand(RoadDamageChunk.Col c, double y0, double y1) {
        return c != null && c.gone() != 0 && y1 >= c.top() - BELOW && y0 <= c.top() + ABOVE;
    }

    private static void fan(List<RoadMesh.Triangle> out, RoadMesh.Triangle t, List<RoadMesh.Vertex> p) {
        for (int i = 1; i + 1 < p.size(); i++) {
            RoadMesh.Vertex a = p.get(0), b = p.get(i), c = p.get(i + 1);
            if (RoadMesh.area3(a, b, c) <= 1e-10) continue;
            out.add(new RoadMesh.Triangle(t.material(), a, b, c, t.nx(), t.ny(), t.nz()));
        }
    }

    /**
     * Keep the part of polygon in (chunk-relative) on the inner side of the lattice edge a - b (world), the quad's corners
     * taken (i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1): inside where ex (pz - az) - ez (px - ax) >= 0.
     */
    private static List<RoadMesh.Vertex> edge(List<RoadMesh.Vertex> in, double[] a, double[] b, double ox, double oz) {
        double ex = b[0] - a[0], ez = b[1] - a[1];
        return half(in, -ez, ex, -ez * (a[0] - ox) + ex * (a[1] - oz));
    }

    private static int cell(double v) { return Math.max(0, Math.min(15, (int) Math.floor(v))); }

    /** Keep the part of polygon in where nx x + nz z >= c (linear in y, u, v along each edge). */
    static List<RoadMesh.Vertex> half(List<RoadMesh.Vertex> in, double nx, double nz, double c) {
        if (in.isEmpty()) return in;
        List<RoadMesh.Vertex> out = new ArrayList<>(in.size() + 2);
        RoadMesh.Vertex a = in.get(in.size() - 1);
        double da = nx * a.x() + nz * a.z() - c;
        for (RoadMesh.Vertex b : in) {
            double db = nx * b.x() + nz * b.z() - c;
            if (db >= 0) {
                if (da < 0) out.add(a.lerp(b, da / (da - db)));
                out.add(b);
            } else if (da >= 0) out.add(a.lerp(b, da / (da - db)));
            a = b;
            da = db;
        }
        return out;
    }
}
