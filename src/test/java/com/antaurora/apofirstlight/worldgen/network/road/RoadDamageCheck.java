package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.road.RoadDamage;
import com.antaurora.apofirstlight.road.RoadDamageChunk;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Road Mesh-ification P1 check (docs/worldgen/road_mesh_damage_v1_design.md 4, 5, 7; offline, no world):
 * <ul>
 *   <li>the drawn broken asphalt (RoadDamageMesh) over a flat test chunk with random damage, its neighbours' too: the
 *   pavement left covers exactly the sub-cells still there (plan area by the jittered quads), the floor exactly the gone
 *   ones, a face of the asphalt's thickness along every edge between a gone sub-cell of the chunk and one still there
 *   (across the chunk's edge too), every vertex finite, inside the chunk, its texture coordinates in 0..1; P2: some
 *   emptied columns dug (the earthwork's, cut out by BridgeMesh before), with no floor and no face toward them, a face
 *   along their rim where a broken neighbour's asphalt is still there;</li>
 *   <li>the ground's vertices in a hole (RoadDamageChunk#holes, the water's: P2);</li>
 *   <li>the lattice: a corner on a block edge stays on it, a block corner does not move, no quad folds;</li>
 *   <li>the collision (RoadDamage#shapeOf): the base slab and the sub-cells left, by volume;</li>
 *   <li>the strikes (RoadDamage#piece): each piece its size (or all asphalt in reach), connected, within reach; how many
 *   strikes clear a county road's and an interstate's square metre.</li>
 * </ul>
 * <pre>
 *   RoadDamageCheck
 * </pre>
 */
public final class RoadDamageCheck {
    private RoadDamageCheck() {}

    static final int N = RoadDamageChunk.N;
    static final double TOP = 100.0, DEPTH = 0.10;

    public static void main(String[] args) {
        List<String> fails = new ArrayList<>();
        lattice(fails);
        mesh(fails, 1, 0, 0);
        mesh(fails, 2, -3, 5);
        mesh(fails, 3, 7, -2);
        shapes(fails);
        holes(fails);
        pieces(fails);
        for (String f : fails) System.out.println("FAIL " + f);
        System.out.println(fails.isEmpty() ? "ROAD DAMAGE CHECK PASS" : "ROAD DAMAGE CHECK FAIL (" + fails.size() + ")");
        if (!fails.isEmpty()) System.exit(1);
    }

    static void lattice(List<String> fails) {
        int bad = 0;
        for (int j = -40; j <= 40; j++) for (int i = -40; i <= 40; i++) {
            double[] p = RoadDamageMesh.lattice(i, j);
            if (Math.floorMod(i, N) == 0 && Math.abs(p[0] - (double) i / N) > 1e-12) bad++;
            if (Math.floorMod(j, N) == 0 && Math.abs(p[1] - (double) j / N) > 1e-12) bad++;
            if (Math.abs(p[0] - (double) i / N) > RoadDamageMesh.JITTER + 1e-12 || Math.abs(p[1] - (double) j / N) > RoadDamageMesh.JITTER + 1e-12) bad++;
            if (quadArea(i, j) <= 0) bad++;
        }
        System.out.println("lattice: " + bad + " bad corners or folded quads of 6561");
        if (bad > 0) fails.add("lattice: " + bad + " bad");
    }

    /** The plan area of the jittered quad of sub-cell (i, j) (positive: counter-clockwise in x right, z down: a, b, e, d). */
    static double quadArea(int i, int j) {
        double[][] q = {RoadDamageMesh.lattice(i, j), RoadDamageMesh.lattice(i + 1, j), RoadDamageMesh.lattice(i + 1, j + 1), RoadDamageMesh.lattice(i, j + 1)};
        double a = 0;
        for (int k = 0; k < 4; k++) {
            double[] u = q[k], v = q[(k + 1) % 4];
            a += u[0] * v[1] - v[0] * u[1];
        }
        return a / 2;
    }

    static void mesh(List<String> fails, long seed, int cx, int cz) {
        Random rnd = new Random(seed);
        Map<Long, RoadDamageChunk.Col> cols = new HashMap<>();
        java.util.Set<Long> dug = new java.util.HashSet<>();
        // damage in the chunk and its ring: a third of the columns, each with a random blob or all gone, half of those dug
        for (int z = cz * 16 - 1; z <= cz * 16 + 16; z++) for (int x = cx * 16 - 1; x <= cx * 16 + 16; x++) {
            if (rnd.nextInt(3) != 0) continue;
            long gone = rnd.nextInt(8) == 0 ? RoadDamageChunk.ALL : rnd.nextLong() & rnd.nextLong();
            if (gone == RoadDamageChunk.ALL && rnd.nextBoolean()) dug.add(key(x, z));
            else if (gone != 0) cols.put(key(x, z), new RoadDamageChunk.Col(gone, (float) TOP, (float) DEPTH));
        }
        RoadDamageMesh.Damage dmg = new RoadDamageMesh.Damage() {
            @Override public RoadDamageChunk.Col col(int x, int z) { return cols.get(key(x, z)); }

            @Override public double[] asphalt(double x, double z, double yHint) { return new double[]{TOP, DEPTH}; }

            @Override public boolean dug(int x, int z) { return dug.contains(key(x, z)); }
        };
        // flat pavement over the chunk, one quad per block, none over a dug one (BridgeMesh#finish cut it out; chunk-relative)
        List<RoadMesh.Triangle> tris = new ArrayList<>();
        for (int tz = 0; tz < 16; tz++) for (int tx = 0; tx < 16; tx++) {
            if (dug.contains(key(cx * 16 + tx, cz * 16 + tz))) continue;
            double u = (tx & 3) / 4.0, v = (tz & 3) / 4.0;
            RoadMesh.Vertex a = new RoadMesh.Vertex(tx, TOP, tz, u, v), b = new RoadMesh.Vertex(tx + 1, TOP, tz, u + 0.25, v),
                    c = new RoadMesh.Vertex(tx + 1, TOP, tz + 1, u + 0.25, v + 0.25), d = new RoadMesh.Vertex(tx, TOP, tz + 1, u, v + 0.25);
            RoadMesh.tri(tris, RoadMesh.Material.ASPHALT, a, b, c);
            RoadMesh.tri(tris, RoadMesh.Material.ASPHALT, a, c, d);
        }
        List<RoadMesh.Triangle> out = RoadDamageMesh.apply(tris, dmg, cx, cz);
        double top = 0, floor = 0, face = 0;
        int badV = 0;
        for (RoadMesh.Triangle t : out) {
            for (RoadMesh.Vertex v : new RoadMesh.Vertex[]{t.a(), t.b(), t.c()}) {
                if (!Double.isFinite(v.x() + v.y() + v.z() + v.u() + v.v()) || v.x() < -1e-9 || v.x() > 16 + 1e-9 || v.z() < -1e-9 || v.z() > 16 + 1e-9
                        || v.u() < -1e-9 || v.u() > 1 + 1e-9 || v.v() < -1e-9 || v.v() > 1 + 1e-9) badV++;
            }
            double area = RoadMesh.area3(t.a(), t.b(), t.c()) / 2;
            if (t.ny() > 0.5 && t.material() == RoadMesh.Material.ASPHALT) top += area;
            else if (t.ny() > 0.5 && t.material() == RoadMesh.Material.GRAVEL) floor += area;
            else if (Math.abs(t.ny()) < 1e-9) face += area;
        }
        double wantTop = 0, wantFloor = 0, wantFace = 0;
        int rim = 0;
        for (int z = cz * 16; z < cz * 16 + 16; z++) for (int x = cx * 16; x < cx * 16 + 16; x++) {
            boolean dugHere = dug.contains(key(x, z));
            for (int sz = 0; sz < N; sz++) for (int sx = 0; sx < N; sx++) {
                int i = x * N + sx, j = z * N + sz;
                double a = quadArea(i, j);
                // the neighbour across each edge, and the edge: x-, x+, z-, z+
                int[][] e = {{i - 1, j, i, j, i, j + 1}, {i + 1, j, i + 1, j, i + 1, j + 1}, {i, j - 1, i, j, i + 1, j}, {i, j + 1, i, j + 1, i + 1, j + 1}};
                if (dugHere) {
                    // the rim: toward a broken neighbour's asphalt still there (a whole one is RoadCells#walls')
                    for (int[] q : e) {
                        if (cols.get(key(Math.floorDiv(q[0], N), Math.floorDiv(q[1], N))) == null || gone(cols, q[0], q[1])) continue;
                        wantFace += len(q[2], q[3], q[4], q[5]) * DEPTH;
                        rim++;
                    }
                    continue;
                }
                if (!gone(cols, i, j)) { wantTop += a; continue; }
                wantFloor += a;
                // edges to sub-cells still there (not gone, not dug)
                for (int[] q : e)
                    if (!gone(cols, q[0], q[1]) && !dug.contains(key(Math.floorDiv(q[0], N), Math.floorDiv(q[1], N)))) wantFace += len(q[2], q[3], q[4], q[5]) * DEPTH;
            }
        }
        System.out.printf("mesh chunk (%d, %d): %d triangles, %d dug columns (%d rim faces); pavement %.6f / %.6f m², floor %.6f / %.6f m², faces %.6f / %.6f m², %d bad vertices%n",
                cx, cz, out.size(), dug.size(), rim, top, wantTop, floor, wantFloor, face, wantFace, badV);
        if (Math.abs(top - wantTop) > 1e-6) fails.add(String.format("mesh (%d, %d): pavement %.6f, want %.6f", cx, cz, top, wantTop));
        if (Math.abs(floor - wantFloor) > 1e-6) fails.add(String.format("mesh (%d, %d): floor %.6f, want %.6f", cx, cz, floor, wantFloor));
        if (Math.abs(face - wantFace) > 1e-6) fails.add(String.format("mesh (%d, %d): faces %.6f, want %.6f", cx, cz, face, wantFace));
        if (badV > 0) fails.add("mesh (" + cx + ", " + cz + "): " + badV + " bad vertices");
    }

    static double len(int i0, int j0, int i1, int j1) {
        double[] a = RoadDamageMesh.lattice(i0, j0), b = RoadDamageMesh.lattice(i1, j1);
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    static boolean gone(Map<Long, RoadDamageChunk.Col> cols, int i, int j) {
        RoadDamageChunk.Col c = cols.get(key(Math.floorDiv(i, N), Math.floorDiv(j, N)));
        return c != null && RoadDamageChunk.gone(c.gone(), Math.floorMod(i, N), Math.floorMod(j, N));
    }

    static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    /** RoadDamageChunk#holes: a vertex goes down to the base only when every sub-cell of the column round it is gone. */
    static void holes(List<String> fails) {
        Random rnd = new Random(11);
        int bad = 0, lowered = 0;
        for (int run = 0; run < 2000; run++) {
            long gone = run == 0 ? RoadDamageChunk.ALL : rnd.nextLong() | rnd.nextLong();
            RoadDamageChunk.Col c = new RoadDamageChunk.Col(gone, (float) TOP, (float) DEPTH);
            double[] rel = new double[25];
            java.util.Arrays.fill(rel, TOP - 99);
            RoadDamageChunk.holes(c, 99, rel);
            for (int j = 0; j <= 4; j++) for (int i = 0; i <= 4; i++) {
                boolean all = true;
                for (int sz = 2 * j - 1; sz <= 2 * j; sz++) for (int sx = 2 * i - 1; sx <= 2 * i; sx++)
                    if (sx >= 0 && sz >= 0 && sx < N && sz < N && !RoadDamageChunk.gone(gone, sx, sz)) all = false;
                double want = all ? TOP - DEPTH - 99 : TOP - 99;
                if (Math.abs(rel[j * 5 + i] - want) > 1e-4) bad++;   // the entry holds floats
                if (all) lowered++;
            }
        }
        System.out.println("holes: " + lowered + " of 50000 vertices on the base, " + bad + " wrong");
        if (bad > 0) fails.add("holes: " + bad + " wrong");
    }

    static void shapes(List<String> fails) {
        Random rnd = new Random(7);
        int bad = 0;
        for (int n = 0; n < 500; n++) {
            long gone = rnd.nextLong() & rnd.nextLong();
            double base = rnd.nextInt(4) == 0 ? 0 : rnd.nextDouble() * 0.8, top = Math.min(1, base + rnd.nextDouble() * 0.3);
            VoxelShape s = RoadDamage.shapeOf(gone, base, top);
            double vol = 0;
            for (AABB b : s.toAabbs()) vol += b.getXsize() * b.getYsize() * b.getZsize();
            double qb = Math.round(base * 1024) / 1024.0, qt = Math.round(top * 1024) / 1024.0;
            double want = qb + (64 - Long.bitCount(gone)) / 64.0 * Math.max(0, qt - qb);
            if (Math.abs(vol - want) > 1e-9) bad++;
        }
        System.out.println("shapes: " + bad + " of 500 off by volume");
        if (bad > 0) fails.add("shapes: " + bad + " off");
    }

    static void pieces(List<String> fails) {
        Random rnd = new Random(11);
        int bad = 0;
        // an open field with a hole (sub-cells 0..15 x 0..15 gone), strikes near its rim
        RoadDamage.Asphalt field = (i, j) -> !(i >= 0 && i < 16 && j >= 0 && j < 16);
        for (int n = 0; n < 300; n++) {
            int si = rnd.nextInt(30) - 7, sj = rnd.nextInt(30) - 7;
            if (!field.at(si, sj)) continue;
            int count = 2 + rnd.nextInt(10);
            LongOpenHashSet p = RoadDamage.piece(si, sj, count, rnd.nextLong(), field);
            if (p.size() != count || !connected(p, si, sj)) bad++;
            for (long s : p) {
                int i = (int) (s >> 32), j = (int) s;
                if (!field.at(i, j) || Math.abs(i - si) > 5 || Math.abs(j - sj) > 5) bad++;
            }
        }
        System.out.println("pieces: " + bad + " bad of 300");
        if (bad > 0) fails.add("pieces: " + bad + " bad");
        clear(fails, "county", 0.10);
        clear(fails, "state", 0.15);
        clear(fails, "interstate", 0.25);
    }

    static boolean connected(LongOpenHashSet p, int si, int sj) {
        LongOpenHashSet seen = new LongOpenHashSet();
        List<long[]> stack = new ArrayList<>();
        stack.add(new long[]{si, sj});
        seen.add(RoadDamage.sub(si, sj));
        while (!stack.isEmpty()) {
            long[] c = stack.remove(stack.size() - 1);
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int i = (int) c[0] + d[0], j = (int) c[1] + d[1];
                long k = RoadDamage.sub(i, j);
                if (p.contains(k) && seen.add(k)) stack.add(new long[]{i, j});
            }
        }
        return seen.size() == p.size();
    }

    /** Strikes at random points of one block of asphalt (thickness depth) until it is all gone, as RoadDamage#strike sizes them. */
    static void clear(List<String> fails, String what, double depth) {
        Random rnd = new Random(23);
        int total = 0, runs = 50;
        for (int run = 0; run < runs; run++) {
            LongOpenHashSet gone = new LongOpenHashSet();
            RoadDamage.Asphalt block = (i, j) -> i >= 0 && i < N && j >= 0 && j < N && !gone.contains(RoadDamage.sub(i, j));
            int strikes = 0;
            while (gone.size() < N * N && strikes < 200) {
                int si = rnd.nextInt(N), sj = rnd.nextInt(N);
                // the struck sub-cell, else the nearest one still there (RoadDamage.SEEK)
                int[] s = nearest(block, si, sj);
                if (s == null) { strikes++; continue; }
                double want = RoadDamage.STRIKE_LITRES / (depth * RoadDamage.SUB_AREA * 1000);
                int count = Math.max(1, (int) want + (rnd.nextDouble() < want - (int) want ? 1 : 0));
                gone.addAll(RoadDamage.piece(s[0], s[1], count, rnd.nextLong(), block));
                strikes++;
            }
            total += strikes;
        }
        double avg = total / (double) runs, ideal = N * N * depth * RoadDamage.SUB_AREA * 1000 / RoadDamage.STRIKE_LITRES;
        System.out.printf("%s (%.2f m asphalt): %.1f strikes on average clear a block (%.1f if no strike were wasted)%n", what, depth, avg, ideal);
        // the last strikes find little left: allowed up to twice the ideal
        if (avg > 2 * ideal + 1) fails.add(what + ": " + avg + " strikes, ideal " + ideal);
    }

    static int[] nearest(RoadDamage.Asphalt a, int i0, int j0) {
        for (int r = 0; r <= 3; r++) for (int dj = -r; dj <= r; dj++) for (int di = -r; di <= r; di++)
            if (Math.max(Math.abs(di), Math.abs(dj)) == r && a.at(i0 + di, j0 + dj)) return new int[]{i0 + di, j0 + dj};
        return null;
    }
}
