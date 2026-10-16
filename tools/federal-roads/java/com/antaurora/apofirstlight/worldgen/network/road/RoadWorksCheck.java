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
 * C3.2 / C3.3a offline check of the terrain works and the pavement mesh of the built roads
 * (docs/worldgen/federal_highways_c3.md, 10.6 and 11): the same pure classes the server's chunk pass and the client use,
 * on the cached federal plan; since C3.3a the whole built network (RoadNetworkBuild).
 * <ul>
 *   <li>the built network: roads, through towns, trims, remaining conflicts, the build time;</li>
 *   <li>determinism: two independent builds (network build, JSON parse, designs, works) give the same column digest
 *   (on every 8th chunk);</li>
 *   <li>collision: every paved / deck column's collision top against the pavement over its square;</li>
 *   <li>turf under the asphalt: no turf corner above the pavement within the paved band and its bevels;</li>
 *   <li>mesh: random points on the pavement are covered by exactly one asphalt triangle of exactly one chunk (no gap,
 *   no overlap across chunk borders), and the mesh height there matches the design;</li>
 *   <li>water: no works on planned water outside the formation; bridges where the route crosses water;</li>
 *   <li>bridges (10.11): the client's expected structure cells against the server's columns, the parapet collision
 *   against the parapet mesh, the deck collision inside the deck mesh, the concrete mesh itself (finite, unit normals,
 *   uv in the tile), and a damage run (cells broken, cut, repaired);</li>
 *   <li>group D: the box culverts and the wing reaches (RoadStructures), the culverts' cells against the client's
 *   expectations, their slab under the road's finish, the wet columns their embankment fills, their collision inside
 *   their mesh (with the bridges'), the determinism of the structured JSON;</li>
 *   <li>timing per chunk (columns, mesh) and the field calls the density makes.</li>
 * </ul>
 */
public final class RoadWorksCheck {
    private RoadWorksCheck() {}


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
        long t2 = System.nanoTime();
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(json);
        long t3 = System.nanoTime();
        say(log, "built network (%s): %d roads in %.2f s, json %d bytes (the client packet), remaining conflict stations %d",
                RoadNetworkBuild.STAGE, built.routes().size(), (t3 - t2) / 1e9, built.json().length(), built.conflicts());
        for (String line : built.report()) say(log, "  %s", line);
        long t4 = System.nanoTime();
        RoadStructures.Result st = RoadStructures.attach(built.json(), terrain);
        say(log, "structures (group D) in %.2f s, json %d bytes:", (System.nanoTime() - t4) / 1e9, st.json().length());
        for (String line : st.report()) say(log, "  %s", line);
        Files.writeString(new File(out, "built.json").toPath(), st.json(), StandardCharsets.UTF_8);
        RoadWorks.Ground ground = plan::heightAt;
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        RoadWorks w1 = new RoadWorks(RoadNetworkJson.designs(st.json()), water);
        String st2 = RoadStructures.attach(RoadNetworkBuild.build(json).json(), terrain).json();
        say(log, "structured JSON, independent build: %s", st2.equals(st.json()) ? "IDENTICAL" : "DIFFERENT");
        RoadWorks w2 = new RoadWorks(RoadNetworkJson.designs(st2), water);
        List<RoadDesign> roads = w1.roads();
        double total = 0;
        for (RoadDesign d : roads) {
            total += d.length;
            int np = 0, run = 0;
            double noPass = 0;
            for (double s = 0; s < d.length; s += 2) if (d.noPassing(s)) noPass += 2;
            StringBuilder sp = new StringBuilder();
            for (RoadDesign.Span s : d.spans) {
                sp.append(String.format(Locale.ROOT, " %.0f..%.0f", s.s0(), s.s1()));
                if (s.depth() == RoadNetworkJson.CULVERT_DEPTH) sp.append("(culvert)");
                if (s.piers().length > 0) sp.append("(").append(s.piers().length).append(" piers)");
            }
            for (Culvert c : d.culverts) sp.append(String.format(Locale.ROOT, " box %.0f", c.s));
            say(log, "road %-48s %s %7.0f m  no-passing %4.0f %%  spans%s", d.id, d.section, d.length, d.section.divided ? 0 : 100 * noPass / d.length,
                    sp.length() == 0 ? " -" : sp);
        }
        // wing reach (group D): per span end and side, against the old fixed reach
        int ends = 0, full = 0;
        double sumReach = 0, maxReach = 0;
        for (RoadDesign d : roads)
            for (RoadDesign.Span s : d.spans)
                for (double r0 : s.reach()) {
                    ends++;
                    sumReach += r0;
                    maxReach = Math.max(maxReach, r0);
                    if (r0 >= BridgeMesh.WALL_REACH - 1e-9) full++;
                }
        say(log, "wing reach: %d span end sides, mean %.1f m, max %.1f m, at the old full %.0f m: %d", ends, sumReach / Math.max(1, ends), maxReach, BridgeMesh.WALL_REACH, full);
        say(log, "built length %.1f km", total / 1000);
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
        if ("1".equals(System.getenv("AFL_BRIDGES_ONLY"))) {   // the bridge checks alone (a quick rerun)
            markings(log, roads);
            bridges(log, roads, w1, ground);
            return;
        }

        // chunks the works touch
        TreeSet<Long> chunks = new TreeSet<>();
        for (RoadDesign d : roads)
            for (double s = -RoadDesign.END; s <= d.length + RoadDesign.END; s += 4)
                for (double u = -RoadWorks.REACH; u <= RoadWorks.REACH; u += 4) {
                    double[] p = d.point(s, u);
                    chunks.add(RoadWorks.key(Math.floorDiv((int) Math.floor(p[0]), 16), Math.floorDiv((int) Math.floor(p[1]), 16)));
                }
        say(log, "chunks with works: %d", chunks.size());

        MessageDigest h1 = MessageDigest.getInstance("SHA-256"), h2 = MessageDigest.getInstance("SHA-256");
        int[] kinds = new int[RoadWorks.Kind.values().length];
        double maxCollision = 0, sumCollision = 0, maxCut = 0, maxFill = 0;
        int nCollision = 0, turfAbove = 0, clamped = 0, wetWorks = 0, wetFilled = 0, bodyCells = 0, slabHigh = 0;
        List<String> slabAt = new ArrayList<>();
        double worstTurf = 0;
        int[] cutAt = new int[2], fillAt = new int[2];
        int fill10 = 0;
        List<String> wetAt = new ArrayList<>();
        long colNanos = 0, meshNanos = 0, worksNanos = 0;
        List<String> turfAt = new ArrayList<>();
        int meshTris = 0, meshChunks = 0, yellow = 0;
        List<Long> paved = new ArrayList<>();
        // plants on turf (group B): bare columns, and the plant-bearing squares (grown by PLANT_CLEAR) of every 8th chunk
        // against the road mesh of every chunk they reach
        int bare = 0, plantChecked = 0, plantOnMesh = 0;
        List<String> plantAt = new ArrayList<>();
        java.util.Map<Long, List<RoadMesh.Triangle>> plantMesh = new java.util.HashMap<>();
        int ci = 0;
        for (long key : chunks) {
            int cx = (int) (key >> 32), cz = (int) key;
            boolean det = ci++ % 8 == 0;
            List<int[]> plants = new ArrayList<>();
            long b = System.nanoTime();
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                int x = cx * 16 + dx, z = cz * 16 + dz;
                long cb = System.nanoTime();
                RoadWorks.Column c = w1.column(x, z, ground);
                worksNanos += System.nanoTime() - cb;
                kinds[c.kind().ordinal()]++;
                double g = plan.heightAt(x + 0.5, z + 0.5);
                switch (c.kind()) {
                    case PAVED, DECK, ABUTMENT -> {
                        double top = c.y() + (c.layers() + c.upper()) / 32.0;
                        double err = Math.max(Math.abs(top - c.paveLo()), Math.abs(top - c.paveHi()));
                        maxCollision = Math.max(maxCollision, err);
                        sumCollision += err;
                        nCollision++;
                        if (c.kind() == RoadWorks.Kind.PAVED) {
                            if (g - top > maxCut) { maxCut = g - top; cutAt = new int[]{x, z}; }
                            if (top - g > maxFill) { maxFill = top - g; fillAt = new int[]{x, z}; }
                            if (top - g > 10) fill10++;
                        }
                    }
                    case TURF -> {
                        int[] q = {c.data() & 15, c.data() >> 4 & 15, c.data() >> 8 & 15, c.data() >> 12 & 15};
                        int shift = c.data() >> 16 & 1;
                        double base = c.y() + shift * 0.5;
                        double[][] corners = {{x, z}, {x + 1, z}, {x, z + 1}, {x + 1, z + 1}};
                        for (int k = 0; k < 4; k++) {
                            RoadWorks.Hit hit = w1.hit(corners[k][0], corners[k][1], CrossSection.R4.edge + 1);
                            if (hit == null || hit.beyond() > 0 || !hit.road().section.paved(hit.v())) continue;
                            double over = base + q[k] / 8.0 - hit.road().pavementY(hit.sc(), hit.u());
                            worstTurf = Math.max(worstTurf, over);
                            if (over > 1e-6) {
                                turfAbove++;
                                if (turfAt.size() < 6) turfAt.add(String.format(Locale.ROOT, "%d %d corner %d %+.4f%s", x, z, k, over,
                                        (c.data() & RoadWorks.CULVERT_BODY) != 0 ? " culvert body" : ""));
                            }
                        }
                        double f = base + Math.max(Math.max(q[0], q[1]), Math.max(q[2], q[3])) / 8.0;
                        if (g - f > maxCut) { maxCut = g - f; cutAt = new int[]{x, z}; }
                        if (f - g > maxFill) { maxFill = f - g; fillAt = new int[]{x, z}; }
                        if ((c.data() & RoadWorks.BARE) != 0) bare++;
                        else if (det) plants.add(new int[]{x, z});
                    }
                    default -> { }
                }
                if ((c.data() & RoadWorks.CULVERT_BODY) != 0 && (c.kind() == RoadWorks.Kind.PAVED || c.kind() == RoadWorks.Kind.TURF)) {
                    // the culvert's slab under the road's finish: two blocks of base under a pavement, under a turf block
                    Culvert.Cell cell = w1.culvertCell(x, z);
                    bodyCells++;
                    int cap = c.kind() == RoadWorks.Kind.PAVED ? c.y() - 2 : c.y() - 1;
                    if (cell != null && cell.layers() > 0 && cell.top() > cap) {
                        slabHigh++;
                        if (slabAt.size() < 6) slabAt.add(String.format(Locale.ROOT, "%d %d %s y %d culvert top %d", x, z, c.kind(), c.y(), cell.top()));
                    }
                }
                if (c.kind() == RoadWorks.Kind.TURF && water.wet(x + 0.5, z + 0.5) && w1.filled(x + 0.5, z + 0.5)) wetFilled++;
                else if (c.kind() == RoadWorks.Kind.TURF && water.wet(x + 0.5, z + 0.5)) {
                    RoadWorks.Hit hit = w1.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                    if (hit != null && (hit.v() > hit.road().section.edge + 0.5 || hit.beyond() > 0)) {
                        wetWorks++;
                        if (wetAt.size() < 6) wetAt.add(String.format(Locale.ROOT, "%d %d (%s s %.0f v %.1f)", x, z, hit.road().id, hit.sc(), hit.v()));
                    }
                }
                if (det) { feed(h1, c); feed(h2, w2.column(x, z, ground)); }
            }
            colNanos += System.nanoTime() - b;
            b = System.nanoTime();
            List<RoadMesh.Triangle> m = mesh(roads, cx, cz);
            meshNanos += System.nanoTime() - b;
            if (!m.isEmpty()) {
                meshChunks++;
                meshTris += m.size();
                paved.add(key);
                for (RoadMesh.Triangle t : m) if (t.material() == RoadMesh.Material.YELLOW) yellow++;
            }
            for (int[] q : plants) {
                plantChecked++;
                double x0 = q[0] - RoadWorks.PLANT_CLEAR, z0 = q[1] - RoadWorks.PLANT_CLEAR, x1 = q[0] + 1 + RoadWorks.PLANT_CLEAR, z1 = q[1] + 1 + RoadWorks.PLANT_CLEAR;
                boolean hit = false;
                for (int ncx = Math.floorDiv((int) Math.floor(x0), 16); ncx <= Math.floorDiv((int) Math.floor(x1), 16) && !hit; ncx++)
                    for (int ncz = Math.floorDiv((int) Math.floor(z0), 16); ncz <= Math.floorDiv((int) Math.floor(z1), 16) && !hit; ncz++) {
                        int fx = ncx, fz = ncz;
                        if (plantMesh.size() > 64) plantMesh.clear();
                        List<RoadMesh.Triangle> nm = fx == cx && fz == cz ? m : plantMesh.computeIfAbsent(RoadWorks.key(fx, fz), k -> mesh(roads, fx, fz));
                        for (RoadMesh.Triangle t : nm)
                            if (overlaps(t, x0 - fx * 16, z0 - fz * 16, x1 - fx * 16, z1 - fz * 16)) { hit = true; break; }
                    }
                if (hit) {
                    plantOnMesh++;
                    if (plantAt.size() < 6) plantAt.add(q[0] + " " + q[1]);
                }
            }
        }
        String d1 = hex(h1.digest()), d2 = hex(h2.digest());
        say(log, "column digest (every 8th chunk) %s, independent build %s  %s", d1, d2, d1.equals(d2) ? "IDENTICAL" : "DIFFERENT");
        for (RoadWorks.Kind k : RoadWorks.Kind.values()) say(log, "  %-9s %8d columns", k, kinds[k.ordinal()]);
        say(log, "collision: %d columns, max error %.4f m, mean %.4f m (1/32 block = %.4f)", nCollision, maxCollision, sumCollision / Math.max(1, nCollision), 1 / 32.0);
        say(log, "turf corners above the pavement: %d (worst %+.4f m) %s", turfAbove, worstTurf, turfAt);
        say(log, "works on planned water outside the formation: %d columns %s; wet columns the embankment fills over a box culvert: %d", wetWorks, wetAt, wetFilled);
        say(log, "box culvert body under the road's finish: %d columns, slab over the finish's base %d %s", bodyCells, slabHigh, slabAt);
        say(log, "turf without plants (beside the pavement): %d columns; plant-bearing squares (+%.2f m) on the road mesh: %d of %d checked %s",
                bare, RoadWorks.PLANT_CLEAR, plantOnMesh, plantChecked, plantAt);
        say(log, "max cut %.2f m at %d %d %s, max fill %.2f m at %d %d %s (finished top against the planned ground, paved and turf columns); pavement filled over 10 m: %d columns",
                maxCut, cutAt[0], cutAt[1], near(w1, cutAt), maxFill, fillAt[0], fillAt[1], near(w1, fillAt), fill10);
        say(log, "works columns alone (RoadWorks.column): %.2f ms per chunk", worksNanos / 1e6 / chunks.size());
        say(log, "columns: %.2f ms per chunk (%d chunks); mesh: %.2f ms per chunk, %d chunks with pavement, %d triangles (%.0f per chunk)",
                colNanos / 1e6 / chunks.size(), chunks.size(), meshNanos / 1e6 / chunks.size(), meshChunks, meshTris, meshTris / (double) Math.max(1, meshChunks));
        say(log, "yellow line triangles %d", yellow);
        markings(log, roads);

        // mesh coverage: random points on the paved band of random roads (all roads' meshes of the chunk: no overlap between roads either)
        Random r = new Random(12345);
        int samples = 40000, gaps = 0, overlaps = 0, crossChunk = 0;
        double worstMesh = 0;
        java.util.Map<Long, List<RoadMesh.Triangle>> meshCache = new java.util.HashMap<>();
        for (int i = 0; i < samples; i++) {
            RoadDesign d = roads.get(r.nextInt(roads.size()));
            CrossSection sec = d.section;
            double s = r.nextDouble() * d.length, side = r.nextBoolean() ? 1 : -1;
            double u = side * (sec.halfMedian + 0.01 + r.nextDouble() * (sec.edge - sec.halfMedian - 0.02));
            double[] p = d.point(s, u);
            int cx = Math.floorDiv((int) Math.floor(p[0]), 16), cz = Math.floorDiv((int) Math.floor(p[1]), 16);
            if (meshCache.size() > 4000) meshCache.clear();
            List<RoadMesh.Triangle> chunkMesh = meshCache.computeIfAbsent(RoadWorks.key(cx, cz), k -> mesh(roads, cx, cz));
            int cover = 0;
            double y = Double.NaN;
            for (RoadMesh.Triangle t : chunkMesh) {
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
        for (int i = 0; i < paved.size(); i += Math.max(1, paved.size() / 2000)) {
            int cx = (int) (paved.get(i) >> 32), cz = (int) (long) paved.get(i);
            List<RoadMesh.Triangle> chunkMesh = mesh(roads, cx, cz);
            java.util.Map<Integer, RoadCells.Cell> cells = RoadCells.of(roads, w1, cx, cz);
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
            List<RoadMesh.Triangle> cut = RoadMesh.without(chunkMesh, removed), rest = RoadMesh.without(chunkMesh, kept);
            double split = Math.abs(area(cut) + area(rest) - area(chunkMesh));
            worstSplit = Math.max(worstSplit, split);
            if (split > 1e-6) cutBad++;
            for (RoadMesh.Triangle t : cut) {
                double mx = (t.a().x() + t.b().x() + t.c().x()) / 3, mz = (t.a().z() + t.b().z() + t.c().z()) / 3;
                if (removed[(int) Math.floor(mx) | (int) Math.floor(mz) << 4]) inHole++;
            }
            for (RoadMesh.Triangle t : RoadCells.walls(roads, cells, dug, cx, cz)) {
                walls++;
                double lo = Math.min(t.a().y(), Math.min(t.b().y(), t.c().y())), hi = Math.max(t.a().y(), Math.max(t.b().y(), t.c().y()));
                if (hi - lo > 2.5 || Math.abs(t.ny()) > 1e-9) wallBad++;
            }
        }
        say(log, "dug cells: predicted collision blocks %d, different from the chunk pass %d; %d chunks dug (every 7th paved cell): cut not a partition %d (worst %.2e m2), pieces left in a hole %d, wall triangles %d (bad %d)",
                cellsChecked, cellMismatch, cellChunks, cutBad, worstSplit, inHole, walls, wallBad);

        bridges(log, roads, w1, ground);

        // field calls: what the density pays per column (surface over the planned ground), on every 8th chunk
        long b = System.nanoTime();
        int calls = 0, cj = 0;
        double sink = 0;
        for (long key : chunks) {
            if (cj++ % 8 != 0) continue;
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

    /** All roads' pavement mesh of one chunk. */
    static List<RoadMesh.Triangle> mesh(List<RoadDesign> roads, int cx, int cz) {
        List<RoadMesh.Triangle> out = new ArrayList<>();
        for (RoadDesign d : roads) out.addAll(RoadMesh.chunk(d, cx, cz));
        return out;
    }

    /** The R2 centre line: no-passing zones on curves, crests and bridges, their lengths and the passing zones'. */
    /** The road nearest a column, with its station. */
    static String near(RoadWorks w, int[] xz) {
        RoadWorks.Hit hit = w.hit(xz[0] + 0.5, xz[1] + 0.5, RoadWorks.REACH);
        return hit == null ? "(no road)" : String.format(Locale.ROOT, "(%s s %.0f)", hit.road().id, hit.sc());
    }

    /** A curve this tight is reported where passing stays allowed on it (informative), m. */
    static final double TIGHT = 300;

    static void markings(PrintStream log, List<RoadDesign> roads) {
        int zones = 0, shortPass = 0, shortNo = 0, spanOpen = 0, tightOpen = 0;
        for (RoadDesign d : roads) {
            if (d.section.divided) continue;
            boolean prev = d.noPassing(0);
            double start = 0;
            for (double s = 2; s <= d.length + 1e-9; s += 2) {
                boolean v = d.noPassing(Math.min(d.length, s));
                if (v != prev || s >= d.length - 1e-9) {
                    double len = s - start;
                    if (start > 0 && s < d.length - 1e-9) { if (prev && len < RoadDesign.MIN_NO_PASS - 2) shortNo++; if (!prev && len < RoadDesign.MIN_PASS - 2) shortPass++; }
                    if (prev) zones++;
                    start = s;
                    prev = v;
                }
            }
            for (double s = 0; s <= d.length; s += 2) {
                if (!d.noPassing(s) && d.span(s) != null) spanOpen++;
                if (!d.noPassing(s) && Math.abs(d.curvature(s)) * TIGHT > 1) tightOpen++;
            }
        }
        say(log, "R2 centre line (sight %.0f m): %d no-passing zones; short passing zones %d, short no-passing zones %d (inside the route); passing allowed on a bridge %d / on a curve under %.0f m %d stations (short curves keep the sight)",
                RoadDesign.NO_PASS_SIGHT, zones, shortPass, shortNo, spanOpen, TIGHT, tightOpen);
    }

    /** The bridge checks: expectations, collision against the mesh, the mesh, a damage run. */
    static void bridges(PrintStream log, List<RoadDesign> roads, RoadWorks w, RoadWorks.Ground ground) {
        // the chunks near a span
        TreeSet<Long> chunks = new TreeSet<>();
        for (RoadDesign d : roads)
            for (RoadDesign.Span sp : d.spans)
                for (double s = sp.s0() - 8; s <= sp.s1() + 8; s += 2)
                    for (double u = -BridgeMesh.WALL_REACH - 4; u <= BridgeMesh.WALL_REACH + 4; u += 4) {
                        double[] p = d.point(s, u);
                        chunks.add(RoadWorks.key(Math.floorDiv((int) Math.floor(p[0]), 16), Math.floorDiv((int) Math.floor(p[1]), 16)));
                    }
        for (Culvert c : w.culverts()) {
            double[] b = c.bounds(2);
            for (int cx = Math.floorDiv((int) Math.floor(b[0]), 16); cx <= Math.floorDiv((int) Math.floor(b[2]), 16); cx++)
                for (int cz = Math.floorDiv((int) Math.floor(b[1]), 16); cz <= Math.floorDiv((int) Math.floor(b[3]), 16); cz++)
                    chunks.add(RoadWorks.key(cx, cz));
        }
        int culvertCells = 0, culvertMismatch = 0, probes = 0;
        int pierCells = 0;
        double worstProtrude = 0;
        int chunksNear = 0, expected = 0, serverOnly = 0, mismatch = 0, missing = 0, wings = 0, notPresent = 0, deckOut = 0, parapets = 0;
        double worstParapet = 0;
        int tris = 0, vertical = 0, badTri = 0, damagedChunks = 0, damaged = 0, brokenSeen = 0, leftInCell = 0, repairTris = 0;
        int solidPoints = 0, solidNone = 0, solidUncovered = 0;
        double worstTopDiff = 0;
        List<String> uncoveredExamples = new ArrayList<>(), topOffExamples = new ArrayList<>(), badExamples = new ArrayList<>();
        int topOff = 0, parapetOverPaved = 0, onEdge = 0;
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
                if (e != null && e.probe()) {
                    // a ground probe finds the ground as the noise leaves it (about the planned ground's block)
                    int g = (int) Math.floor(ground.at(x + 0.5, z + 0.5));
                    found.put(i, new BridgeMesh.Found(g, false, 0, g + 1));
                    probes++;
                    continue;
                }
                Culvert.Cell cell = w.culvertCell(x, z);
                if (cell != null && cell.layers() > 0 || e != null && e.kind() == RoadWorks.Kind.CULVERT) {
                    // a box culvert's cell: the chunk pass writes its concrete from the bed (walls) or the soffit (roof)
                    culvertCells++;
                    if (e == null || cell == null || e.kind() != RoadWorks.Kind.CULVERT || e.top() != cell.top() || e.layers() != cell.layers()
                            || cell.own() != (c.kind() == RoadWorks.Kind.CULVERT)) { culvertMismatch++; continue; }
                    found.put(i, new BridgeMesh.Found(cell.top(), false, cell.layers(), cell.wall() ? cell.culvert().bed : cell.roofLo()));
                    if (RoadCells.inner(i)) expected++;
                    continue;
                }
                if (e != null) expected++;
                if (c.kind() == RoadWorks.Kind.ABUTMENT) {
                    RoadWorks.Hit ph = w.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                    RoadDesign.Span psp = ph == null ? null : ph.road().span(ph.sc());
                    if (psp != null && RoadCells.inner(i) && !RoadWorks.band(ph.road(), psp, x, z)) pierCells++;
                }
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
                    CrossSection sec = h.road().section;
                    double edge = h.v() > sec.edge ? sec.edge : sec.halfMedian;
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
                if (bad) {
                    badTri++;
                    if (badExamples.size() < 4) badExamples.add(String.format(java.util.Locale.ROOT, "chunk %d %d n %.3f %.3f %.3f a %.3f %.3f %.3f uv %.3f %.3f b %.3f %.3f %.3f uv %.3f %.3f c %.3f %.3f %.3f uv %.3f %.3f",
                            cx, cz, t.nx(), t.ny(), t.nz(), t.a().x(), t.a().y(), t.a().z(), t.a().u(), t.a().v(), t.b().x(), t.b().y(), t.b().z(), t.b().u(), t.b().v(),
                            t.c().x(), t.c().y(), t.c().z(), t.c().u(), t.c().v()));
                }
            }
            // collision inside the mesh: on 8 x 8 points of every intact structure cell, where BridgeMesh.solid (the
            // collision) has a top, an upward face of the mesh (bridge or pavement) lies there at that height
            List<RoadMesh.Triangle> all = new ArrayList<>(mesh);
            all.addAll(mesh(roads, cx, cz));
            for (BridgeMesh.Expect e : exp.values()) {
                if (!RoadCells.inner(e.index()) || !BridgeMesh.intact(e, found.get(e.index()))) continue;
                int lx = RoadCells.lx(e.index()), lz = RoadCells.lz(e.index());
                // a culvert's body under the road's finish is buried: its slab's top is not drawn
                Culvert.Cell own = w.culvertCell(cx * 16 + lx, cz * 16 + lz);
                if (e.kind() == RoadWorks.Kind.CULVERT && own != null && !own.own()) continue;
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
                    if (Double.isNaN(best) && upAt(all, px + 0.002, pz) | upAt(all, px - 0.002, pz) | upAt(all, px, pz + 0.002) | upAt(all, px, pz - 0.002)) {
                        // the point lies on the mesh's edge (a wall face at t = 1 of an integer span end, a parapet's
                        // outer edge on an axis-aligned road): covered within 2 mm
                        onEdge++;
                        continue;
                    }
                    if (Double.isNaN(best)) {
                        solidUncovered++;
                        if (uncoveredExamples.size() < 6) {
                            RoadWorks.Hit hh = w.hit(cx * 16 + px, cz * 16 + pz, RoadWorks.REACH);
                            uncoveredExamples.add(String.format(java.util.Locale.ROOT, "%.2f %.2f %s wing %s top %d (%s)", cx * 16 + px, cz * 16 + pz, e.kind(), e.wing(), e.top(),
                                    hh == null ? "no road" : String.format(java.util.Locale.ROOT, "%s s %.1f u %.2f beyond %.1f", hh.road().id, hh.sc(), hh.u(), hh.beyond())));
                        }
                    }
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
                if (!RoadCells.inner(e.index()) || f == null || e.probe() || k++ % 5 != 0) continue;
                if (f.top() > f.foot()) hit.put(e.index(), new BridgeMesh.Found(f.top() - 1, false, 32, f.foot()));
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
            repairTris += BridgeMesh.repairs(exp, hit, RoadCells.of(roads, w, cx, cz)).size();
        }
        say(log, "bridges: %d chunks near a span; %d expected structure cells (client), built by the server but not expected %d, expected deck / parapet cells the server does not build %d, different top / layers / kind %d; pier cells %d",
                chunksNear, expected, serverOnly, missing, mismatch, pierCells);
        say(log, "  box culverts: %d cells with concrete, expectation / column / cell disagree %d; ground probes %d", culvertCells, culvertMismatch, probes);
        say(log, "  wing cells built %d; undamaged cells not intact %d; parapet collision against the parapet mesh: %d cells, worst %.4f m; deck structure blocks below the mesh underside %d (the collision block itself under the 0.3 m slab: worst %.2f m)",
                wings, notPresent, parapets, worstParapet, deckOut, worstProtrude);
        say(log, "  concrete mesh: %d triangles (%d vertical), bad (normal, uv, NaN, outside the chunk) %d %s; expectations + mesh %.2f ms per near chunk",
                tris, vertical, badTri, badExamples, nanos / 1e6 / Math.max(1, chunksNear));
        say(log, "  damage: %d chunks, %d cells broken (every 5th), seen broken %d, mesh left over a broken cell %d, repair triangles %d",
                damagedChunks, damaged, brokenSeen, leftInCell, repairTris);
        say(log, "  collision inside the mesh: %d points of intact cells with collision (%d without), no mesh above %d %s (on the mesh's edge, covered within 2 mm: %d); mesh top vs collision top worst %.3f m",
                solidPoints, solidNone, solidUncovered, uncoveredExamples, onEdge, worstTopDiff);
        say(log, "    structure-topped points more than 0.1 m off: %d %s; paved-cell points under a parapet (no collision above the pavement) %d",
                topOff, topOffExamples, parapetOverPaved);
    }

    static double area(List<RoadMesh.Triangle> tris) {
        double s = 0;
        for (RoadMesh.Triangle t : tris)
            s += Math.abs((t.b().x() - t.a().x()) * (t.c().z() - t.a().z()) - (t.b().z() - t.a().z()) * (t.c().x() - t.a().x())) / 2;
        return s;
    }

    /** An upward face of the mesh lies over (x, z). */
    static boolean upAt(List<RoadMesh.Triangle> all, double x, double z) {
        for (RoadMesh.Triangle t : all) if (t.ny() >= 0.15 && bary(t, x, z) != null) return true;
        return false;
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

    /** Whether triangle t overlaps the open square (x0, x1) x (z0, z1) in plan (separating axes; touching is not). */
    static boolean overlaps(RoadMesh.Triangle t, double x0, double z0, double x1, double z1) {
        double[] xs = {t.a().x(), t.b().x(), t.c().x()}, zs = {t.a().z(), t.b().z(), t.c().z()};
        double e = 1e-6;
        if (Math.max(xs[0], Math.max(xs[1], xs[2])) <= x0 + e || Math.min(xs[0], Math.min(xs[1], xs[2])) >= x1 - e
                || Math.max(zs[0], Math.max(zs[1], zs[2])) <= z0 + e || Math.min(zs[0], Math.min(zs[1], zs[2])) >= z1 - e) return false;
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3, k = (i + 2) % 3;
            double nx = zs[j] - zs[i], nz = xs[i] - xs[j];
            double edge = nx * xs[i] + nz * zs[i], apex = nx * xs[k] + nz * zs[k];
            double lo = Math.min(nx * x0, nx * x1) + Math.min(nz * z0, nz * z1), hi = Math.max(nx * x0, nx * x1) + Math.max(nz * z0, nz * z1);
            double len = Math.hypot(nx, nz);
            if (len < 1e-12) continue;
            if (apex > edge && hi <= edge + e * len || apex < edge && lo >= edge - e * len) return false;
        }
        return true;
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
