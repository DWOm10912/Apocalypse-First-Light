package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.InflaterInputStream;

/**
 * C3.2 offline check of the terrain works and the pavement mesh of the built routes (docs/worldgen/federal_highways_c3.md,
 * 10.6): the same pure classes the server's chunk pass and the client use, on the cached federal plan.
 * <ul>
 *   <li>determinism: two independent builds (JSON parse, designs, works) give the same column digest;</li>
 *   <li>collision: every paved / deck column's collision top against the pavement over its square;</li>
 *   <li>turf under the asphalt: no turf corner above the pavement within the paved band and its bevels;</li>
 *   <li>mesh: random points on the pavement are covered by exactly one asphalt triangle of exactly one chunk (no gap,
 *   no overlap across chunk borders), and the mesh height there matches the design;</li>
 *   <li>water: no works on planned water outside the formation; bridges where the route crosses water;</li>
 *   <li>bridges (10.11): the client's expected structure cells against the server's columns, the parapet collision
 *   against the parapet mesh, the deck collision inside the deck mesh, the concrete mesh itself (finite, unit normals,
 *   uv in the tile), and a damage run (cells broken, cut, repaired);</li>
 *   <li>timing per chunk (columns, mesh) and the field calls the density makes.</li>
 * </ul>
 */
public final class RoadWorksCheck {
    private RoadWorksCheck() {}

    static final Set<String> BUILT = Set.of("interstate_ew:county_seat-valley_town");

    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        File cache = new File(a[1]), out = new File(a[2]);
        out.mkdirs();
        PrintStream log = new PrintStream(new FileOutputStream(new File(out, "c3_works_check.txt")), true, StandardCharsets.UTF_8);
        TerrainPlanSurface plan;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(cache)), 1 << 16))) {
            plan = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
        }
        if (plan == null) throw new IllegalStateException("stale or foreign plan cache " + cache);
        FederalMap map = FederalMap.forSeed(seed);
        RouteTerrain terrain = new RouteTerrain(plan, map);
        long t0 = System.nanoTime();
        String json = RoadNetworkPlan.build(map, terrain).toJson();
        long t1 = System.nanoTime();
        Files.writeString(new File(out, "network.json").toPath(), json, StandardCharsets.UTF_8);
        say(log, "network build %.1f s, json %d bytes", (t1 - t0) / 1e9, json.length());
        String sub = RoadNetworkJson.subset(json, BUILT);
        say(log, "built subset json %d bytes (the client packet)", sub.length());
        RoadWorks.Ground ground = plan::heightAt;
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        RoadWorks w1 = new RoadWorks(RoadNetworkJson.designs(json, BUILT), water);
        RoadWorks w2 = new RoadWorks(RoadNetworkJson.designs(sub, BUILT), water);
        RoadDesign d = w1.roads().get(0);
        say(log, "route %s length %.1f m, spans %s", d.id, d.length, d.spans);
        String probe = System.getenv("AFL_PROBE");
        if (probe != null && !probe.isEmpty()) {
            for (String p : probe.split(";")) {
                String[] q = p.split(",");
                int x = Integer.parseInt(q[0].trim()), z = Integer.parseInt(q[1].trim());
                RoadWorks.Column c = w1.column(x, z, ground);
                RoadWorks.Hit h = w1.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                System.out.printf(java.util.Locale.ROOT, "PROBE %d %d kind %s y %d top %d bottom %d layers %d data %d ground %.2f s %s u %s beyond %s%n",
                        x, z, c.kind(), c.y(), c.top(), c.bottom(), c.layers(), c.data(), ground.at(x + 0.5, z + 0.5),
                        h == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", h.sc()), h == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", h.u()),
                        h == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", h.beyond()));
            }
            return;
        }

        // chunks the works touch
        TreeSet<Long> chunks = new TreeSet<>();
        for (double s = -RoadDesign.END; s <= d.length + RoadDesign.END; s += 4)
            for (double u = -RoadWorks.REACH; u <= RoadWorks.REACH; u += 4) {
                double[] p = d.point(s, u);
                chunks.add(RoadWorks.key(Math.floorDiv((int) Math.floor(p[0]), 16), Math.floorDiv((int) Math.floor(p[1]), 16)));
            }
        say(log, "chunks with works: %d", chunks.size());

        MessageDigest h1 = MessageDigest.getInstance("SHA-256"), h2 = MessageDigest.getInstance("SHA-256");
        int[] kinds = new int[RoadWorks.Kind.values().length];
        double maxCollision = 0, sumCollision = 0, maxCut = 0, maxFill = 0;
        int nCollision = 0, turfAbove = 0, clamped = 0, wetWorks = 0;
        double worstTurf = 0;
        long colNanos = 0, meshNanos = 0;
        int meshTris = 0, meshChunks = 0;
        List<RoadMesh.Triangle>[] meshes = new List[chunks.size()];
        long[] keys = new long[chunks.size()];
        int ci = 0;
        for (long key : chunks) {
            int cx = (int) (key >> 32), cz = (int) key;
            long b = System.nanoTime();
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                int x = cx * 16 + dx, z = cz * 16 + dz;
                RoadWorks.Column c = w1.column(x, z, ground);
                feed(h1, c);
                kinds[c.kind().ordinal()]++;
                double g = plan.heightAt(x + 0.5, z + 0.5);
                switch (c.kind()) {
                    case PAVED, DECK, ABUTMENT -> {
                        double top = c.y() + (c.layers() + c.upper()) / 32.0;
                        double err = Math.max(Math.abs(top - c.paveLo()), Math.abs(top - c.paveHi()));
                        maxCollision = Math.max(maxCollision, err);
                        sumCollision += err;
                        nCollision++;
                        if (c.kind() == RoadWorks.Kind.PAVED) { maxCut = Math.max(maxCut, g - top); maxFill = Math.max(maxFill, top - g); }
                    }
                    case TURF -> {
                        int[] q = {c.data() & 15, c.data() >> 4 & 15, c.data() >> 8 & 15, c.data() >> 12 & 15};
                        int shift = c.data() >> 16 & 1;
                        double base = c.y() + shift * 0.5;
                        double[][] corners = {{x, z}, {x + 1, z}, {x, z + 1}, {x + 1, z + 1}};
                        for (int k = 0; k < 4; k++) {
                            RoadWorks.Hit hit = w1.hit(corners[k][0], corners[k][1], R4CrossSection.EDGE + 1);
                            if (hit == null || hit.beyond() > 0 || hit.v() < R4CrossSection.HALF_MEDIAN || hit.v() > R4CrossSection.EDGE) continue;
                            double over = base + q[k] / 8.0 - hit.road().pavementY(hit.sc(), hit.u());
                            worstTurf = Math.max(worstTurf, over);
                            if (over > 1e-6) turfAbove++;
                        }
                        double f = base + Math.max(Math.max(q[0], q[1]), Math.max(q[2], q[3])) / 8.0;
                        maxCut = Math.max(maxCut, g - f);
                        maxFill = Math.max(maxFill, f - g);
                    }
                    default -> { }
                }
                if (c.kind() == RoadWorks.Kind.TURF && water.wet(x + 0.5, z + 0.5)) {
                    RoadWorks.Hit hit = w1.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                    if (hit != null && (hit.v() > R4CrossSection.EDGE + 0.5 || hit.beyond() > 0)) wetWorks++;
                }
                feed(h2, w2.column(x, z, ground));
            }
            colNanos += System.nanoTime() - b;
            b = System.nanoTime();
            List<RoadMesh.Triangle> m = RoadMesh.chunk(d, cx, cz);
            meshNanos += System.nanoTime() - b;
            meshes[ci] = m;
            keys[ci++] = key;
            if (!m.isEmpty()) { meshChunks++; meshTris += m.size(); }
        }
        String d1 = hex(h1.digest()), d2 = hex(h2.digest());
        say(log, "column digest %s, independent build %s  %s", d1, d2, d1.equals(d2) ? "IDENTICAL" : "DIFFERENT");
        for (RoadWorks.Kind k : RoadWorks.Kind.values()) say(log, "  %-9s %8d columns", k, kinds[k.ordinal()]);
        say(log, "collision: %d columns, max error %.4f m, mean %.4f m (1/32 block = %.4f)", nCollision, maxCollision, sumCollision / Math.max(1, nCollision), 1 / 32.0);
        say(log, "turf corners above the pavement: %d (worst %+.4f m)", turfAbove, worstTurf);
        say(log, "works on planned water outside the formation: %d columns", wetWorks);
        say(log, "max cut %.2f m, max fill %.2f m (finished top against the planned ground, paved and turf columns)", maxCut, maxFill);
        say(log, "columns: %.2f ms per chunk (%d chunks); mesh: %.2f ms per chunk, %d chunks with pavement, %d triangles (%.0f per chunk)",
                colNanos / 1e6 / chunks.size(), chunks.size(), meshNanos / 1e6 / chunks.size(), meshChunks, meshTris, meshTris / (double) Math.max(1, meshChunks));

        // mesh coverage: random points on the paved band
        Random r = new Random(12345);
        int samples = 20000, gaps = 0, overlaps = 0, crossChunk = 0;
        double worstMesh = 0;
        for (int i = 0; i < samples; i++) {
            double s = r.nextDouble() * d.length, side = r.nextBoolean() ? 1 : -1;
            double u = side * (R4CrossSection.HALF_MEDIAN + 0.01 + r.nextDouble() * (R4CrossSection.EDGE - R4CrossSection.HALF_MEDIAN - 0.02));
            double[] p = d.point(s, u);
            int cx = Math.floorDiv((int) Math.floor(p[0]), 16), cz = Math.floorDiv((int) Math.floor(p[1]), 16);
            int idx = java.util.Arrays.binarySearch(keys, RoadWorks.key(cx, cz));
            if (idx < 0) { gaps++; continue; }
            int cover = 0;
            double y = Double.NaN;
            for (RoadMesh.Triangle t : meshes[idx]) {
                if (t.material() != RoadMesh.Material.ASPHALT) continue;
                double[] bc = bary(t, p[0] - cx * 16, p[1] - cz * 16);
                if (bc == null) continue;
                cover++;
                y = bc[0] * t.a().y() + bc[1] * t.b().y() + bc[2] * t.c().y();
            }
            if (cover == 0) gaps++;
            else if (cover > 1) overlaps++;
            else worstMesh = Math.max(worstMesh, Math.abs(y - d.pavementY(s, u)));
            if ((p[0] - cx * 16 < 0.05 || p[0] - cx * 16 > 15.95 || p[1] - cz * 16 < 0.05 || p[1] - cz * 16 > 15.95)) crossChunk++;
        }
        say(log, "mesh coverage: %d points, gaps %d, overlaps %d, near a chunk border %d, worst height vs design %.4f m", samples, gaps, overlaps, crossChunk, worstMesh);

        // dug cells (client): predicted collision blocks against the chunk pass, mesh cut, hole walls
        int cellChunks = 0, cellsChecked = 0, cellMismatch = 0, cutBad = 0, inHole = 0, walls = 0, wallBad = 0;
        double worstSplit = 0;
        for (int i = 0; i < meshes.length; i++) {
            if (meshes[i].isEmpty()) continue;
            int cx = (int) (keys[i] >> 32), cz = (int) keys[i];
            java.util.Map<Integer, RoadCells.Cell> cells = RoadCells.of(List.of(d), w1, cx, cz);
            for (RoadCells.Cell c : cells.values()) {
                RoadWorks.Column col = w1.column(cx * 16 + RoadCells.lx(c.index()), cz * 16 + RoadCells.lz(c.index()), ground);
                cellsChecked++;
                if (col.kind() == RoadWorks.Kind.TURF || col.kind() == RoadWorks.Kind.NONE || col.y() != c.y() || col.layers() != c.layers() || col.upper() != c.upper())
                    cellMismatch++;
            }
            if (cellChunks >= 300) continue;
            // dig every 7th inner paved cell
            java.util.BitSet dug = new java.util.BitSet();
            boolean[] removed = new boolean[256], kept = new boolean[256];
            int n = 0;
            for (RoadCells.Cell c : cells.values())
                if (RoadCells.inner(c.index()) && n++ % 7 == 0) { dug.set(c.index()); removed[RoadCells.lx(c.index()) | RoadCells.lz(c.index()) << 4] = true; }
            if (dug.isEmpty()) continue;
            cellChunks++;
            for (int k = 0; k < 256; k++) kept[k] = !removed[k];
            List<RoadMesh.Triangle> cut = RoadMesh.without(meshes[i], removed), rest = RoadMesh.without(meshes[i], kept);
            double split = Math.abs(area(cut) + area(rest) - area(meshes[i]));
            worstSplit = Math.max(worstSplit, split);
            if (split > 1e-6) cutBad++;
            for (RoadMesh.Triangle t : cut) {
                double mx = (t.a().x() + t.b().x() + t.c().x()) / 3, mz = (t.a().z() + t.b().z() + t.c().z()) / 3;
                if (removed[(int) Math.floor(mx) | (int) Math.floor(mz) << 4]) inHole++;
            }
            for (RoadMesh.Triangle t : RoadCells.walls(List.of(d), cells, dug, cx, cz)) {
                walls++;
                double lo = Math.min(t.a().y(), Math.min(t.b().y(), t.c().y())), hi = Math.max(t.a().y(), Math.max(t.b().y(), t.c().y()));
                if (hi - lo > 2.5 || Math.abs(t.ny()) > 1e-9) wallBad++;
            }
        }
        say(log, "dug cells: predicted collision blocks %d, different from the chunk pass %d; %d chunks dug (every 7th paved cell): cut not a partition %d (worst %.2e m2), pieces left in a hole %d, wall triangles %d (bad %d)",
                cellsChecked, cellMismatch, cellChunks, cutBad, worstSplit, inHole, walls, wallBad);

        bridges(log, d, w1, ground, chunks);

        // field calls: what the density pays per column (surface over the planned ground)
        long b = System.nanoTime();
        int calls = 0;
        double sink = 0;
        for (long key : chunks) {
            int cx = (int) (key >> 32), cz = (int) key;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                double g = plan.heightAt(cx * 16 + dx, cz * 16 + dz);
                double f = w1.surface(cx * 16 + dx + 0.5, cz * 16 + dz + 0.5, g);
                if (!Double.isNaN(f)) sink += f;
                calls++;
            }
        }
        say(log, "works surface: %.2f us per column (%d columns, incl. the planned ground) [%s]", (System.nanoTime() - b) / 1e3 / calls, calls, sink > 0 ? "ok" : "-");
        say(log, "output: %s", out);
        log.close();
    }

    /** The bridge checks: expectations, collision against the mesh, the mesh, a damage run. */
    static void bridges(PrintStream log, RoadDesign d, RoadWorks w, RoadWorks.Ground ground, TreeSet<Long> chunks) {
        double worstProtrude = 0;
        int chunksNear = 0, expected = 0, serverOnly = 0, mismatch = 0, missing = 0, wings = 0, notPresent = 0, deckOut = 0, parapets = 0;
        double worstParapet = 0;
        int tris = 0, vertical = 0, badTri = 0, damagedChunks = 0, damaged = 0, brokenSeen = 0, leftInCell = 0, repairTris = 0;
        int solidPoints = 0, solidNone = 0, solidUncovered = 0;
        double worstTopDiff = 0;
        List<String> uncoveredExamples = new ArrayList<>(), topOffExamples = new ArrayList<>();
        int topOff = 0, parapetOverPaved = 0;
        long nanos = 0;
        for (long key : chunks) {
            int cx = (int) (key >> 32), cz = (int) key;
            long b = System.nanoTime();
            java.util.Map<Integer, BridgeMesh.Expect> exp = BridgeMesh.expected(w, cx, cz);
            nanos += System.nanoTime() - b;
            if (exp.isEmpty()) continue;
            chunksNear++;
            // the world as the chunk pass builds it: the found cells of an undamaged bridge
            java.util.Map<Integer, BridgeMesh.Found> found = new java.util.HashMap<>();
            for (int lz = -1; lz <= 16; lz++) for (int lx = -1; lx <= 16; lx++) {
                int x = cx * 16 + lx, z = cz * 16 + lz, i = RoadCells.index(lx, lz);
                RoadWorks.Column c = w.column(x, z, ground);
                BridgeMesh.Expect e = exp.get(i);
                boolean structure = c.kind() == RoadWorks.Kind.DECK || c.kind() == RoadWorks.Kind.ABUTMENT || c.kind() == RoadWorks.Kind.BARRIER;
                if (e != null) expected++;
                if (!structure) {
                    if (e != null && !e.wing()) missing++;
                    continue;
                }
                if (e == null) { serverOnly++; continue; }
                boolean surface = c.kind() != RoadWorks.Kind.BARRIER;
                int top = surface ? c.top() : c.y(), layers = surface ? (c.upper() > 0 ? c.upper() : c.layers()) : c.layers();
                if (e.kind() != c.kind() || e.top() != top || e.surface() != surface || e.layers() != layers || e.wing() != (c.data() == BridgeMesh.WING))
                    mismatch++;
                int g = (int) Math.floor(ground.at(x + 0.5, z + 0.5));
                int foot = c.bottom() <= g + 1 ? Math.min(c.bottom(), g + 1) : c.bottom();
                found.put(i, new BridgeMesh.Found(top, surface, layers, foot));
                if (!RoadCells.inner(i)) continue;
                if (e.wing()) wings++;
                RoadWorks.Hit h = w.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                if (c.kind() == RoadWorks.Kind.BARRIER && !e.wing()) {
                    double edge = h.v() > R4CrossSection.EDGE ? R4CrossSection.EDGE : R4CrossSection.HALF_MEDIAN;
                    double design = h.road().pavementY(h.sc(), Math.copySign(edge, h.u())) + BridgeMesh.PARAPET_HEIGHT;
                    worstParapet = Math.max(worstParapet, Math.abs(c.y() + c.layers() / 32.0 - design));
                    parapets++;
                }
                if (c.kind() == RoadWorks.Kind.DECK && !Double.isNaN(e.meshBottom()) && c.bottom() < e.meshBottom() - 1e-9) {
                    // the collision block itself may reach under a thin slab; a structure block below it must not
                    if (c.bottom() < c.y()) deckOut++;
                    else worstProtrude = Math.max(worstProtrude, e.meshBottom() - c.bottom());
                }
            }
            for (BridgeMesh.Expect e : exp.values()) if (BridgeMesh.state(e, found.get(e.index())) == BridgeMesh.State.BROKEN) notPresent++;
            b = System.nanoTime();
            List<RoadMesh.Triangle> mesh = BridgeMesh.chunk(w, cx, cz, exp, found);
            nanos += System.nanoTime() - b;
            for (RoadMesh.Triangle t : mesh) {
                tris++;
                if (Math.abs(t.ny()) < 0.5) vertical++;
                double n = Math.sqrt(t.nx() * t.nx() + t.ny() * t.ny() + t.nz() * t.nz());
                boolean bad = Math.abs(n - 1) > 1e-6;
                for (RoadMesh.Vertex v : new RoadMesh.Vertex[]{t.a(), t.b(), t.c()})
                    bad |= !Double.isFinite(v.x() + v.y() + v.z()) || v.u() < -1e-6 || v.u() > 1 + 1e-6 || v.v() < -1e-6 || v.v() > 1 + 1e-6
                            || v.x() < -1e-6 || v.x() > 16 + 1e-6 || v.z() < -1e-6 || v.z() > 16 + 1e-6;
                if (bad) badTri++;
            }
            // collision inside the mesh: on 8 x 8 points of every intact structure cell, where BridgeMesh.solid (the
            // collision) has a top, an upward face of the mesh (bridge or pavement) lies there at that height
            List<RoadMesh.Triangle> all = new ArrayList<>(mesh);
            all.addAll(RoadMesh.chunk(d, cx, cz));
            for (BridgeMesh.Expect e : exp.values()) {
                if (!RoadCells.inner(e.index()) || BridgeMesh.state(e, found.get(e.index())) != BridgeMesh.State.PRESENT) continue;
                int lx = RoadCells.lx(e.index()), lz = RoadCells.lz(e.index());
                for (int a = 0; a < 8; a++) for (int c = 0; c < 8; c++) {
                    double px = lx + (a + 0.5) / 8, pz = lz + (c + 0.5) / 8;
                    double[] sol = BridgeMesh.solid(w, cx * 16 + px, cz * 16 + pz);
                    if (sol == null) { solidNone++; continue; }
                    double cap = e.top() + e.layers() / 32.0, top = Math.min(sol[1], cap);
                    solidPoints++;
                    double best = Double.NaN;
                    for (RoadMesh.Triangle t : all) {
                        if (t.ny() < 0.15) continue;
                        double[] b3 = bary(t, px, pz);
                        if (b3 == null) continue;
                        double y = b3[0] * t.a().y() + b3[1] * t.b().y() + b3[2] * t.c().y();
                        if (Double.isNaN(best) || Math.abs(y - top) < Math.abs(best - top)) best = y;
                    }
                    if (Double.isNaN(best)) { solidUncovered++; if (uncoveredExamples.size() < 6) uncoveredExamples.add(String.format(java.util.Locale.ROOT, "%.2f %.2f %s", cx * 16 + px, cz * 16 + pz, e.kind())); }
                    else if (e.surface()) {
                        // a paved cell's top block is road_surface (its layers' box, not the mesh's solid): a parapet
                        // reaching over it has no collision above the pavement there
                        if (best - top > 0.1) parapetOverPaved++;
                    } else {
                        worstTopDiff = Math.max(worstTopDiff, Math.abs(best - top));
                        if (Math.abs(best - top) > 0.1) {
                            topOff++;
                            if (topOffExamples.size() < 8) {
                                RoadWorks.Hit hh = w.hit(cx * 16 + px, cz * 16 + pz, RoadWorks.REACH);
                                topOffExamples.add(String.format(java.util.Locale.ROOT, "%s s %.2f u %.2f mesh %.2f col %.2f", e.kind(), hh.s(), hh.u(), best, top));
                            }
                        }
                    }
                }
            }
            // damage: every 5th inner structure cell loses its top block (or all of it when it is one block)
            if (damagedChunks >= 40) continue;
            java.util.Map<Integer, BridgeMesh.Found> hit = new java.util.HashMap<>(found);
            boolean[] want = new boolean[256];
            int k = 0, nd = 0;
            for (BridgeMesh.Expect e : exp.values()) {
                BridgeMesh.Found f = found.get(e.index());
                if (!RoadCells.inner(e.index()) || f == null || k++ % 5 != 0) continue;
                if (f.top() > f.foot()) hit.put(e.index(), new BridgeMesh.Found(f.top() - 1, false, 32, f.foot()));
                else if (e.wing()) continue;   // a wing dug out completely is absent, not broken
                else hit.remove(e.index());
                want[RoadCells.lx(e.index()) | RoadCells.lz(e.index()) << 4] = true;
                nd++;
            }
            if (nd == 0) continue;
            damagedChunks++;
            damaged += nd;
            boolean[] broken = BridgeMesh.broken(exp, hit);
            for (int c = 0; c < 256; c++) if (broken[c]) { brokenSeen++; if (!want[c]) mismatch++; }
            List<RoadMesh.Triangle> cut = RoadMesh.without(BridgeMesh.chunk(w, cx, cz, exp, hit), broken);
            for (RoadMesh.Triangle t : cut) {
                double mx = (t.a().x() + t.b().x() + t.c().x()) / 3, mz = (t.a().z() + t.b().z() + t.c().z()) / 3;
                int c = Math.min(15, (int) Math.floor(mx)) | Math.min(15, (int) Math.floor(mz)) << 4;
                if (broken[c] && mx - Math.floor(mx) > 1e-6 && mz - Math.floor(mz) > 1e-6) leftInCell++;
            }
            repairTris += BridgeMesh.repairs(exp, hit, RoadCells.of(List.of(d), w, cx, cz)).size();
        }
        say(log, "bridges: %d chunks near a span; %d expected structure cells (client), built by the server but not expected %d, expected deck / parapet cells the server does not build %d, different top / layers / kind %d",
                chunksNear, expected, serverOnly, missing, mismatch);
        say(log, "  wing cells built %d; undamaged cells not intact %d; parapet collision against the parapet mesh: %d cells, worst %.4f m; deck structure blocks below the mesh underside %d (the collision block itself under the 0.3 m slab: worst %.2f m)",
                wings, notPresent, parapets, worstParapet, deckOut, worstProtrude);
        say(log, "  concrete mesh: %d triangles (%d vertical), bad (normal, uv, NaN, outside the chunk) %d; expectations + mesh %.2f ms per near chunk",
                tris, vertical, badTri, nanos / 1e6 / Math.max(1, chunksNear));
        say(log, "  damage: %d chunks, %d cells broken (every 5th), seen broken %d, mesh left over a broken cell %d, repair triangles %d",
                damagedChunks, damaged, brokenSeen, leftInCell, repairTris);
        say(log, "  collision inside the mesh: %d points of intact cells with collision (%d without), no mesh above %d %s; mesh top vs collision top worst %.3f m",
                solidPoints, solidNone, solidUncovered, uncoveredExamples, worstTopDiff);
        say(log, "    structure-topped points more than 0.1 m off: %d %s; paved-cell points under a parapet (no collision above the pavement) %d",
                topOff, topOffExamples, parapetOverPaved);
    }

    static double area(List<RoadMesh.Triangle> tris) {
        double s = 0;
        for (RoadMesh.Triangle t : tris)
            s += Math.abs((t.b().x() - t.a().x()) * (t.c().z() - t.a().z()) - (t.b().z() - t.a().z()) * (t.c().x() - t.a().x())) / 2;
        return s;
    }

    static double[] bary(RoadMesh.Triangle t, double x, double z) {
        var a = t.a();
        var b = t.b();
        var c = t.c();
        double d = (b.z() - c.z()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.z() - c.z());
        if (Math.abs(d) < 1e-12) return null;
        double l = ((b.z() - c.z()) * (x - c.x()) + (c.x() - b.x()) * (z - c.z())) / d;
        double m = ((c.z() - a.z()) * (x - c.x()) + (a.x() - c.x()) * (z - c.z())) / d;
        return l >= -1e-9 && m >= -1e-9 && l + m <= 1 + 1e-9 ? new double[]{l, m, 1 - l - m} : null;
    }

    static void feed(MessageDigest h, RoadWorks.Column c) {
        h.update((byte) c.kind().ordinal());
        for (int v : new int[]{c.y(), c.layers(), c.upper(), c.data(), c.bottom()}) {
            h.update((byte) v); h.update((byte) (v >> 8)); h.update((byte) (v >> 16)); h.update((byte) (v >> 24));
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 8; i++) s.append(String.format(Locale.ROOT, "%02x", b[i]));
        return s.toString();
    }

    static void say(PrintStream log, String f, Object... args) {
        String s = String.format(Locale.ROOT, f, args);
        System.out.println(s);
        log.println(s);
    }
}
