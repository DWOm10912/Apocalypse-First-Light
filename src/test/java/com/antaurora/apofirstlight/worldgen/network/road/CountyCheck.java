package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/**
 * County road G3 check (2026-10-18, docs/worldgen/county_road_g3.md): per seed the full network build with the terrain
 * (RoadNetworkBuild, RoadCounty after the towns), then the county road and the home checked:
 * <ul>
 *   <li>the road's design builds; it crosses no other road's paved band (its hosts' last stretches excepted); its grades
 *   within the class's mountain grade; it keeps RoadCounty.PLOT_HARD off the home's plot;</li>
 *   <li>its end into a town: the town's entry names it, its end on the arm's line, the end beyond the node's box, at the
 *   intersection's datum; an end on a state road: a junction builds on its host; a dead end start: the barrier anchor;</li>
 *   <li>the driveway (the side join, 2026-10-19): from H at the yard's height, RoadCounty.DRIVE_MIN .. DRIVE_MAX long, out
 *   of water and its banks, within DRIVE_GRADE, its last DRIVE_STRAIGHT straight, square to the road (70 .. 90 degrees)
 *   and within DRIVE_END_GRADE, ending on the paved edge; the mouth MOUTH_END from the road's ends and structures, even
 *   and in sight (RoadCounty#even, #sight); the gate and the mailbox where they belong;</li>
 *   <li>the built JSON's home reads back the same (RoadCounty#append / #read);</li>
 *   <li>the works (RoadWorks with the towns and the driveway, as the server builds them): every driveway point's column
 *   its collision at the gravel's height (the last points may be the road's), the road's start and end paved; along the
 *   paved edge across the apron the gravel at the asphalt's edge height and every column there the road's or the
 *   driveway's;</li>
 *   <li>the driveway's mesh (DriveMesh): finite, within its chunk and texture tile, its top on the gravel's height; the
 *   client's copy built from the JSON the server sends (RoadClient#receive) gives the same mesh;</li>
 *   <li>a second build with the first one's remembered site (RoadCounty.Memo) gives the same home and county road.</li>
 * </ul>
 * <pre>
 *   CountyCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS)
 * </pre>
 */
public final class CountyCheck {
    private CountyCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        int fails = 0, seeds = 0;
        List<String> summary = new ArrayList<>();
        for (String f : bins) {
            String name = Path.of(f).getFileName().toString();
            String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
            Path planFile = plans.get(unsigned);
            if (planFile == null) { System.out.println("== seed " + unsigned + ": no plan (run systemPlans)"); continue; }
            long seed = Long.parseUnsignedLong(unsigned);
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null) { System.out.println("== seed " + unsigned + ": STALE terrain plan " + f); continue; }
            seeds++;
            RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
            System.out.println("== seed " + unsigned);
            String[] kept = new String[1];
            RoadCounty.Memo memo = new RoadCounty.Memo() {
                @Override public String load() { return kept[0]; }
                @Override public void store(String site) { kept[0] = site; }
            };
            String planJson = Files.readString(planFile);
            long t0 = System.nanoTime();
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(planJson, terrain, memo);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            long t1 = System.nanoTime();
            RoadNetworkBuild.Result r2 = RoadNetworkBuild.build(planJson, terrain, memo);
            long ms2 = (System.nanoTime() - t1) / 1_000_000;
            for (String l : r.report()) if (l.startsWith("county road")) System.out.println("  " + l);
            List<String> bad = new ArrayList<>();
            RoadCounty.Home h = r.home();
            String line;
            if (h == null) {
                bad.add("no home");
                line = "no home";
            } else {
                String json = RoadCounty.append(TownPlan.append(r.json(), r.towns()), h);
                RoadCounty.Home back = RoadCounty.read(json);
                if (back == null || back.x() != h.x() || back.z() != h.z() || back.rotation() != h.rotation() || back.drive().length != h.drive().length
                        || !h.link().equals(back.link()) || !h.beyond().equals(back.beyond())) bad.add("home JSON does not read back");
                line = check(h, r, terrain, bad);
                if (h.road() != null) line += works(h, r, s, terrain, bad);
            }
            if (!r.json().equals(r2.json())) bad.add("the build with the remembered site differs (" + kept[0] + ")");
            if (h != null && r2.home() != null && !RoadCounty.append("{}", h).equals(RoadCounty.append("{}", r2.home()))) bad.add("the remembered site's home differs");
            for (String l : r2.report()) if (l.startsWith("county road")) System.out.println("  (again) " + l);
            System.out.printf(Locale.ROOT, "  build %d ms, again with the remembered site %d ms; %s%n", ms, ms2, line);
            for (String b : bad) System.out.println("  FAIL " + b);
            fails += bad.isEmpty() ? 0 : 1;
            summary.add(String.format(Locale.ROOT, "%s: %s%s", unsigned, line, bad.isEmpty() ? "" : " FAIL"));
        }
        System.out.println("== summary");
        for (String l : summary) System.out.println("  " + l);
        System.out.println(fails == 0 ? "COUNTY CHECK PASS (" + seeds + " seeds)" : "COUNTY CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        if (fails > 0) System.exit(1);
    }

    /** The works' columns along the driveway and at the road's ends (the class comment). */
    private static String works(RoadCounty.Home h, RoadNetworkBuild.Result r, TerrainPlanSurface s, RouteTerrain t, List<String> bad) {
        RoadWorks.Water water = (x, z) -> {
            int w = t.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        List<RoadDesign> ds = RoadNetworkJson.designs(r.json());
        RoadWorks w = new RoadWorks(ds, water, new com.antaurora.apofirstlight.worldgen.network.town.TownWorks(r.towns()), Driveway.of(h));
        RoadWorks.Ground g = RoadWorks.Ground.of(s);
        double[] dr = h.drive();
        Driveway dw = w.drive();
        int n = dr.length / 3, gravel = 0, road = 0, off = 0;
        String first = null;
        // every centreline point's column: the driveway's collision at the gravel's height (the nine points' mean), the
        // road's pavement at the joint
        for (int i = 1; i < n; i++) {
            int x = (int) Math.floor(dr[3 * i]), z = (int) Math.floor(dr[3 * i + 1]);
            RoadWorks.Column c = w.column(x, z, g);
            double want = dw.height(x + 0.5, z + 0.5);
            if (c.kind() == RoadWorks.Kind.DRIVE && Math.abs(top(c) - want) <= 0.1) gravel++;
            else if (i >= n - 4 && (c.kind() == RoadWorks.Kind.PAVED || c.kind() == RoadWorks.Kind.DRIVE && Double.isNaN(want))) road++;
            else {
                off++;
                if (first == null) first = String.format(Locale.ROOT, "point %d (%d, %d): %s top %.2f, gravel %.2f", i, x, z, c.kind(), top(c), want);
            }
        }
        if (off > 0) bad.add(off + " driveway columns not its collision at the gravel's height, first " + first);
        RoadDesign d = w.road(h.road());
        int paved = 0;
        for (double sx : new double[]{6, d.length - 1}) {
            double[] q = d.point(sx, 0);
            if (w.column((int) Math.floor(q[0]), (int) Math.floor(q[1]), g).kind() == RoadWorks.Kind.PAVED) paved++;
        }
        if (paved < 2) bad.add("the county road's start or end column is not paved");
        // the joint: along the paved edge across the apron the gravel 2 cm short of it at the asphalt's edge height (its
        // cross slope too), every column across the driveway's width half a metre in from the edge the road's or the driveway's
        double seam = 0;
        if (!dw.joinedApron()) bad.add("the driveway is not joined to the county road");
        else {
            // within the gravel 2 cm short of the edge (the fillet's width there)
            double lim = Driveway.width(Driveway.APRON * 0.02 / 8) - 0.05;
            for (double v = -lim; v <= lim + 1e-9; v += 0.2) {
                double[] q = dw.apronPoint(dw.reach(v) - 0.02, v), e = dw.apronPoint(dw.reach(v) + 0.02, v);
                double gy = dw.height(q[0], q[1]), py = RoadWorks.paveAt(d, e[0], e[1]);
                seam = Math.max(seam, Double.isNaN(gy) || Double.isNaN(py) ? 9 : Math.abs(gy - py));
            }
            if (seam > 0.02) bad.add(String.format(Locale.ROOT, "the gravel meets the asphalt %.3f m off", seam));
            // within the driveway's own half width: further out the fillets' outline crosses the columns half a metre in
            // (graded turf at the gravel's edge, as along the whole driveway; the seam above covers the full width)
            for (double v = -Driveway.HALF; v <= Driveway.HALF + 1e-9; v += 0.5) {
                double[] q = dw.apronPoint(dw.reach(v) - 0.5, v);
                RoadWorks.Column c = w.column((int) Math.floor(q[0]), (int) Math.floor(q[1]), g);
                if (c.kind() != RoadWorks.Kind.PAVED && c.kind() != RoadWorks.Kind.DRIVE)
                    bad.add(String.format(Locale.ROOT, "the joint at v %.1f (%d, %d): %s y %d", v, (int) Math.floor(q[0]), (int) Math.floor(q[1]), c.kind(), c.y()));
            }
        }
        // the mesh: every chunk the driveway touches, finite and within its chunk; its top over the gravel's height
        int tris = 0, wrong = 0;
        double worst = 0;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < n; i++)
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int cx = Math.floorDiv((int) Math.floor(dr[3 * i]), 16) + dx, cz = Math.floorDiv((int) Math.floor(dr[3 * i + 1]), 16) + dz;
                if (!seen.add(((long) cx << 32) ^ (cz & 0xffffffffL))) continue;
                for (RoadMesh.Triangle tr : DriveMesh.chunk(dw, cx, cz)) {
                    tris++;
                    for (RoadMesh.Vertex v : new RoadMesh.Vertex[]{tr.a(), tr.b(), tr.c()}) {
                        if (!Double.isFinite(v.y()) || v.x() < -1e-6 || v.x() > 16 + 1e-6 || v.z() < -1e-6 || v.z() > 16 + 1e-6 || v.u() < -1e-6 || v.u() > 1 + 1e-6 || v.v() < -1e-6 || v.v() > 1 + 1e-6) {
                            if (wrong++ == 0) bad.add(String.format(Locale.ROOT, "mesh vertex (%.3f, %.3f, %.3f) uv (%.4f, %.4f) in chunk %d %d, normal y %.2f", v.x(), v.y(), v.z(), v.u(), v.v(), cx, cz, tr.ny()));
                        }
                    }
                    if (tr.ny() > 0.5) {
                        double mx = (tr.a().x() + tr.b().x() + tr.c().x()) / 3 + cx * 16, mz = (tr.a().z() + tr.b().z() + tr.c().z()) / 3 + cz * 16;
                        double my = (tr.a().y() + tr.b().y() + tr.c().y()) / 3, hy = dw.height(mx, mz);
                        if (!Double.isNaN(hy)) worst = Math.max(worst, Math.abs(my - hy));
                    }
                }
            }
        if (tris == 0 || wrong > 0) bad.add(tris + " mesh triangles, " + wrong + " vertices off their chunk or texture tile");
        if (worst > 0.03) bad.add(String.format(Locale.ROOT, "the mesh lies %.3f m off the gravel's height", worst));
        // every driveway collision column wholly under the gravel's mesh or the road's pavement (sampled every 0.1 m): its
        // invisible collision culls the top of the gravel under it, so a corner outside both is a see-through hole
        int bareCols = 0;
        String firstBare = null;
        java.util.Set<Long> cols = new java.util.HashSet<>();
        for (int i = 0; i < n; i++)
            for (int dz = -6; dz <= 6; dz++) for (int dx = -6; dx <= 6; dx++) {
                int x = (int) Math.floor(dr[3 * i]) + dx, z = (int) Math.floor(dr[3 * i + 1]) + dz;
                if (!cols.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
                RoadWorks.Kind kind = w.column(x, z, g).kind();
                if (kind != RoadWorks.Kind.DRIVE && kind != RoadWorks.Kind.PAVED) continue;
                int miss = 0;
                for (int b = 0; b <= 10; b++) for (int a = 0; a <= 10; a++) {
                    double px = x + a * 0.1, pz = z + b * 0.1;
                    if (Double.isNaN(dw.height(px, pz)) && Double.isNaN(RoadWorks.paveAt(d, px, pz))) miss++;
                }
                if (miss > 0 && bareCols++ == 0) firstBare = String.format(Locale.ROOT, "%s (%d, %d) %d of 121 points", kind, x, z, miss);
                if (miss > 0 && System.getenv("COUNTY_DUMP") != null) System.out.printf(Locale.ROOT, "  bare %s (%d, %d): %d of 121%n", kind, x, z, miss);
            }
        if (bareCols > 0) bad.add(bareCols + " collision columns by the driveway partly outside the gravel and the pavement (see-through), first " + firstBare);
        // the pads (Main 2026-10-19): every column the gate's box covers and the mailbox's column and its neighbours (not
        // those by the pavement) a level top at the anchor's whole Y: full turf or full collision at Y - 1, Y clear; the
        // gravel on the gate's pad PAD_LIFT over Y; the turf round them in the shared-corner test below
        double[] gate = h.gate(), mb = h.mailbox();
        int padCols = 0, padBad = 0, byRoad = 0;
        String firstPad = null;
        double gateIn = Double.NaN;
        if (gate == null || mb == null || gate[1] != Math.rint(gate[1]) || mb[1] != Math.rint(mb[1])) bad.add("the gate's or the mailbox's Y is not whole");
        else {
            gateIn = Math.hypot(gate[0] - dr[3 * n - 3], gate[2] - dr[3 * n - 2]);
            double gh = dw.height(gate[0], gate[2]);
            if (Math.abs(gh - Driveway.PAD_LIFT - gate[1]) > 0.002) bad.add(String.format(Locale.ROOT, "the gravel at the gate %.3f, its Y %d", gh, (int) gate[1]));
            java.util.Map<Long, Integer> want = new java.util.LinkedHashMap<>();
            for (double a = -RoadCounty.GATE_ALONG; a <= RoadCounty.GATE_ALONG + 1e-9; a += 0.1)
                for (double v = -RoadCounty.GATE_ACROSS; v <= RoadCounty.GATE_ACROSS + 1e-9; v += 0.1) {
                    double px = gate[0] + gate[3] * a - gate[4] * v, pz = gate[2] + gate[4] * a + gate[3] * v;
                    want.put(((long) (int) Math.floor(px) << 32) ^ ((int) Math.floor(pz) & 0xffffffffL), (int) gate[1]);
                }
            int mx = (int) Math.floor(mb[0]), mz = (int) Math.floor(mb[2]);
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = mx + o[0], z = mz + o[1];
                if (dw.padCell(x, z) == Integer.MIN_VALUE && (o[0] != 0 || o[1] != 0)) { byRoad++; continue; }
                want.put(((long) x << 32) ^ (z & 0xffffffffL), (int) mb[1]);
            }
            for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) cols.add(((long) (mx + dx) << 32) ^ ((mz + dz) & 0xffffffffL));
            for (java.util.Map.Entry<Long, Integer> e : want.entrySet()) {
                int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey(), y = e.getValue();
                RoadWorks.Column c = w.column(x, z, g);
                boolean level = c.kind() == RoadWorks.Kind.TURF && c.y() == y - 1 && (c.data() & 0x1ffff) == 0x8888
                        || c.kind() == RoadWorks.Kind.DRIVE && c.y() == y - 1 && c.layers() == 32 && c.upper() == 0;
                padCols++;
                if (!level && padBad++ == 0)
                    firstPad = String.format(Locale.ROOT, "(%d, %d) %s y %d layers %d data %x, want top %d", x, z, c.kind(), c.y(), c.layers(), c.data() & 0x1ffff, y);
            }
            if (padBad > 0) bad.add(padBad + " of " + padCols + " pad columns not level at their Y, first " + firstPad);
            if (byRoad > 2) bad.add(byRoad + " of the mailbox's neighbours by the pavement");
        }
        // two graded turf blocks side by side cull the faces between them (FLOOR occlusion): by the driveway every pair of
        // turf columns must give the corners they share the same height, else the sky shows through the wedge between
        // them (the driveway's verge and the county road's earthworks once met that way, 2026-10-19); a turf whose low
        // corners graded_turf cannot hold is lifted (RoadWorks#fit), harmless where it stands a layer over its neighbour
        int split = 0;
        String firstSplit = null;
        for (long k : cols) {
            int x = (int) (k >> 32), z = (int) k;
            RoadWorks.Column c = w.column(x, z, g);
            if (c.kind() != RoadWorks.Kind.TURF) continue;
            for (int side = 0; side < 2; side++) {
                RoadWorks.Column o = w.column(x + 1 - side, z + side, g);
                if (o.kind() != RoadWorks.Kind.TURF) continue;
                // east: ne = nw', se = sw'; south: sw = nw', se = ne'
                int[][] pairs = side == 0 ? new int[][]{{1, 0}, {3, 2}} : new int[][]{{2, 0}, {3, 1}};
                for (int[] p : pairs) {
                    double dh = corner(c, p[0]) - corner(o, p[1]), e = Math.abs(dh);
                    if (e < 1e-6) continue;
                    // the higher one's side face is culled only by a block in its own layer (the lower one's turf there,
                    // or its fill under a turf a layer up); over a lower layer it faces air and is drawn (a step of earth)
                    if ((dh > 0 ? o.y() : c.y()) < (dh > 0 ? c.y() : o.y())) continue;
                    if (split++ == 0) firstSplit = String.format(Locale.ROOT, "(%d, %d) and (%d, %d) %.3f m apart", x, z, x + 1 - side, z + side, e);
                    if (System.getenv("COUNTY_DUMP") != null) System.out.printf(Locale.ROOT, "  split (%d, %d) %s: %.3f m%n", x, z, side == 0 ? "east" : "south", e);
                }
            }
        }
        if (split > 0) bad.add(split + " corners shared by two turf columns by the driveway at two heights (see-through wedges), first " + firstSplit);
        // COUNTY_GRID=x0,z0,x1,z1: every column of that box as "x z kind y layers data" (compared with a save offline)
        String grid = System.getenv("COUNTY_GRID");
        if (grid != null) {
            String[] b = grid.split(",");
            for (int z = Integer.parseInt(b[1].trim()); z <= Integer.parseInt(b[3].trim()); z++)
                for (int x = Integer.parseInt(b[0].trim()); x <= Integer.parseInt(b[2].trim()); x++) {
                    RoadWorks.Column c = w.column(x, z, g);
                    System.out.printf(Locale.ROOT, "  grid %d %d %s %d %d %x%n", x, z, c.kind(), c.y(), c.layers(), c.data());
                }
        }
        // COUNTY_COLS=x,z;x,z: those columns as the works give them
        String want = System.getenv("COUNTY_COLS");
        if (want != null)
            for (String p : want.split(";")) {
                String[] xz = p.split(",");
                int x = Integer.parseInt(xz[0].trim()), z = Integer.parseInt(xz[1].trim());
                RoadWorks.Column c = w.column(x, z, g);
                System.out.printf(Locale.ROOT, "  column (%d, %d): %s y %d layers %d data %x pave %.3f..%.3f; ground %.2f; gravel at centre %.3f%n", x, z, c.kind(), c.y(), c.layers(),
                        c.data(), c.paveLo(), c.paveHi(), g.at(x + 0.5, z + 0.5), dw.height(x + 0.5, z + 0.5));
                // its square every 0.05 m (rows z, columns x): g gravel, p pavement, . neither
                for (int bz = 0; bz <= 20; bz++) {
                    StringBuilder row = new StringBuilder("    ");
                    for (int ax = 0; ax <= 20; ax++) {
                        double px = x + ax * 0.05, pz = z + bz * 0.05;
                        row.append(!Double.isNaN(dw.height(px, pz)) ? 'g' : !Double.isNaN(RoadWorks.paveAt(d, px, pz)) ? 'p' : '.');
                    }
                    System.out.println(row);
                }
            }
        // the client's copy (RoadClient#receive) from the JSON the server sends: the same driveway, the same mesh
        String sent = RoadCounty.append(TownPlan.append(r.json(), r.towns()), h);
        RoadWorks client = new RoadWorks(RoadNetworkJson.designs(sent), RoadWorks.Water.NONE,
                new com.antaurora.apofirstlight.worldgen.network.town.TownWorks(TownPlan.read(sent)), Driveway.of(RoadCounty.read(sent)));
        // (its points come through the JSON at 4 decimals, so its triangles split a little differently: compare the
        // surface each chunk's mesh covers, its top seen from above and its skirts)
        double worstArea = 0, serverArea = 0;
        String where = null;
        for (long k : seen) {
            int kx = (int) (k >> 32), kz = (int) k;
            double[] sa = area(DriveMesh.chunk(dw, kx, kz)), ca = area(DriveMesh.chunk(client.drive(), kx, kz));
            serverArea += sa[0];
            double e = Math.max(Math.abs(sa[0] - ca[0]), Math.abs(sa[1] - ca[1]));
            if (e > worstArea) { worstArea = e; where = String.format(Locale.ROOT, "chunk %d %d: top %.4f / %.4f m2, skirts %.4f / %.4f m2", kx, kz, sa[0], ca[0], sa[1], ca[1]); }
        }
        if (serverArea <= 0 || worstArea > 0.02) bad.add("the client's driveway mesh differs from the server's, " + (where == null ? "none drawn" : where));
        // the client's whole chunk mesh (RoadClient#build: roads, pads, towns, the driveway; nothing dug) seen from above
        // over every collision column by the driveway: a point no up-facing triangle covers is a see-through hole (the
        // collision is invisible and culls the top of the block under it)
        List<RoadDesign> all = RoadNetworkJson.designs(sent);
        Map<Long, List<RoadMesh.Triangle>> ups = new java.util.HashMap<>();
        int open = 0;
        String firstOpen = null;
        for (long k : cols) {
            int x = (int) (k >> 32), z = (int) k;
            RoadWorks.Kind kind = w.column(x, z, g).kind();
            if (kind != RoadWorks.Kind.DRIVE && kind != RoadWorks.Kind.PAVED) continue;
            int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
            List<RoadMesh.Triangle> up = ups.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), key -> {
                List<RoadMesh.Triangle> tr = JunctionMesh.pavement(client, all, cx, cz);
                Map<Integer, BridgeMesh.Expect> ex = BridgeMesh.expected(client, cx, cz);
                tr = BridgeMesh.finish(tr, all, client, cx, cz, RoadCells.of(all, client, cx, cz), new java.util.BitSet(), ex, ex.isEmpty() ? Map.of() : null);
                List<RoadMesh.Triangle> u = new ArrayList<>();
                for (RoadMesh.Triangle q : tr) if (q.ny() > 0.2) u.add(q);
                return u;
            });
            int miss = 0;
            for (int b = 0; b <= 10; b++) for (int a = 0; a <= 10; a++) {
                double px = x - cx * 16 + 0.05 + a * 0.09, pz = z - cz * 16 + 0.05 + b * 0.09;
                boolean hit = false;
                for (RoadMesh.Triangle q : up) if (inside(q, px, pz)) { hit = true; break; }
                if (!hit) miss++;
            }
            if (miss > 0) {
                if (open++ == 0) firstOpen = String.format(Locale.ROOT, "%s (%d, %d) %d of 121 points", kind, x, z, miss);
                if (System.getenv("COUNTY_DUMP") != null) System.out.printf(Locale.ROOT, "  open %s (%d, %d): %d of 121%n", kind, x, z, miss);
            }
        }
        if (open > 0) bad.add(open + " collision columns by the driveway not covered by the client's mesh (see-through), first " + firstOpen);
        // the client's paved cells (RoadCells#of: what it compares with the world to find dug cells) hold every collision
        // column by the driveway as the chunk pass put it (a different one reads as dug: a hole cut into the mesh); a turf
        // column beside the driveway's collision that lies open (gone) gets a gravel wall on that side
        Map<Long, Map<Integer, RoadCells.Cell>> cellsBy = new java.util.HashMap<>();
        int cellOff = 0, noWall = 0;
        String firstCell = null, firstWall = null;
        for (long k : cols) {
            int x = (int) (k >> 32), z = (int) k;
            RoadWorks.Column c = w.column(x, z, g);
            int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16), lx = x - cx * 16, lz = z - cz * 16;
            Map<Integer, RoadCells.Cell> cells = cellsBy.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), key -> RoadCells.of(all, client, cx, cz));
            if (c.kind() == RoadWorks.Kind.DRIVE || c.kind() == RoadWorks.Kind.PAVED) {
                RoadCells.Cell cell = cells.get(RoadCells.index(lx, lz));
                if (cell == null || cell.y() != c.y() || cell.layers() != c.layers() || cell.upper() != c.upper()
                        || (c.kind() == RoadWorks.Kind.DRIVE) != (cell.road() == RoadCells.DRIVE))
                    if (cellOff++ == 0) firstCell = String.format(Locale.ROOT, "%s (%d, %d) y %d layers %d, the client's %s", c.kind(), x, z, c.y(), c.layers(),
                            cell == null ? "none" : cell.y() + " / " + cell.layers() + " road " + cell.road());
            } else if (c.kind() == RoadWorks.Kind.TURF) {
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] dv : dirs) {
                    RoadCells.Cell nb = cells.get(RoadCells.index(lx + dv[0], lz + dv[1]));
                    if (nb == null || nb.road() != RoadCells.DRIVE || !RoadCells.inner(nb.index())) continue;
                    java.util.BitSet bits = new java.util.BitSet();
                    bits.set(RoadCells.OPEN + RoadCells.index(lx, lz));
                    boolean wall = false;
                    for (RoadMesh.Triangle q : RoadCells.walls(client, all, cells, bits, cx, cz))
                        wall |= q.material() == RoadMesh.Material.GRAVEL && q.nx() * dv[0] + q.nz() * dv[1] < -0.9;
                    if (!wall && noWall++ == 0) firstWall = String.format(Locale.ROOT, "turf (%d, %d) beside (%d, %d)", x, z, x + dv[0], z + dv[1]);
                }
            }
        }
        if (cellOff > 0) bad.add(cellOff + " collision columns by the driveway the client's paved cells have otherwise, first " + firstCell);
        if (noWall > 0) bad.add(noWall + " open turf sides by the driveway without a gravel wall, first " + firstWall);
        // COUNTY_DUMP: the columns round the driveway's road end (kind letter + top y), the road's pavement over its first metres
        if (System.getenv("COUNTY_DUMP") != null) {
            int ex = (int) Math.floor(dr[3 * (n - 1)]), ez = (int) Math.floor(dr[3 * (n - 1) + 1]);
            System.out.printf(Locale.ROOT, "  dump round (%d, %d), driveway end datum %.2f; x across, z down%n", ex, ez, dr[3 * (n - 1) + 2]);
            for (int z = ez - 8; z <= ez + 8; z++) {
                StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  z %5d ", z));
                for (int x = ex - 8; x <= ex + 8; x++) {
                    RoadWorks.Column c = w.column(x, z, g);
                    char k = switch (c.kind()) {
                        case DRIVE -> 'G';
                        case PAVED -> 'P';
                        case TURF -> 't';
                        case NONE -> '.';
                        default -> '?';
                    };
                    sb.append(String.format(Locale.ROOT, " %c%3d", k, c.kind() == RoadWorks.Kind.NONE ? (int) Math.floor(g.at(x + 0.5, z + 0.5)) : c.y()));
                }
                System.out.println(sb);
            }
            for (int i = n - 6; i < n; i++) System.out.printf(Locale.ROOT, "  drive %d (%.1f, %.1f) datum %.2f ground %.2f%n", i, dr[3 * i], dr[3 * i + 1], dr[3 * i + 2], g.at(dr[3 * i], dr[3 * i + 1]));
        }
        return String.format(Locale.ROOT, "; works: %d driveway columns gravel, %d the road's; mesh %d triangles, %.3f m off, joint %.3f m; gate %.1f m in, %d pad columns level (%d mailbox neighbours by the road)",
                gravel, road, tris, worst, seam, gateIn, padCols - padBad, byRoad);
    }

    /** Whether (x, z) lies in triangle t seen from above (chunk-relative). */
    private static boolean inside(RoadMesh.Triangle t, double x, double z) {
        double ax = t.a().x(), az = t.a().z(), bx = t.b().x(), bz = t.b().z(), cx = t.c().x(), cz = t.c().z();
        double d1 = (x - bx) * (az - bz) - (ax - bx) * (z - bz), d2 = (x - cx) * (bz - cz) - (bx - cx) * (z - cz), d3 = (x - ax) * (cz - az) - (cx - ax) * (z - az);
        boolean neg = d1 < -1e-9 || d2 < -1e-9 || d3 < -1e-9, pos = d1 > 1e-9 || d2 > 1e-9 || d3 > 1e-9;
        return !(neg && pos);
    }

    /** The height of a turf column's corner i (nw, ne, sw, se) as graded_turf draws it. */
    private static double corner(RoadWorks.Column c, int i) {
        return c.y() + (c.data() >> 16 & 1) * 0.5 + (c.data() >> 4 * i & 15) / 8.0;
    }

    /** {area of the up-facing triangles seen from above, area of the others}, m2. */
    private static double[] area(List<RoadMesh.Triangle> ts) {
        double up = 0, other = 0;
        for (RoadMesh.Triangle t : ts) {
            double ux = t.b().x() - t.a().x(), uy = t.b().y() - t.a().y(), uz = t.b().z() - t.a().z();
            double vx = t.c().x() - t.a().x(), vy = t.c().y() - t.a().y(), vz = t.c().z() - t.a().z();
            if (t.ny() > 0.5) up += Math.abs(uz * vx - ux * vz) / 2;
            else other += Math.sqrt(Math.pow(uy * vz - uz * vy, 2) + Math.pow(uz * vx - ux * vz, 2) + Math.pow(ux * vy - uy * vx, 2)) / 2;
        }
        return new double[]{up, other};
    }

    /** A paved or driveway column's collision top. */
    private static double top(RoadWorks.Column c) { return c.y() + (c.layers() + c.upper()) / 32.0; }

    private static String check(RoadCounty.Home h, RoadNetworkBuild.Result r, RouteTerrain t, List<String> bad) {
        if (h.road() == null) return String.format(Locale.ROOT, "site (%d, %d): no road", h.x(), h.z());
        RouteData c = null;
        for (RouteData q : r.routes()) if (q.id().equals(h.road())) c = q;
        if (c == null) { bad.add("the county road is not in the network"); return "?"; }
        RoadDesign d = RoadNetworkJson.design(c);
        double t0 = c.trim()[0], edge = d.section.edge;
        // the road: grades, off the plot
        double gRoad = 0;
        double[] e = c.profile();
        for (int i = 1; i < e.length; i++) gRoad = Math.max(gRoad, Math.abs(e[i] - e[i - 1]) / RoadProfile.DS);
        if (gRoad > RoadClassSpec.COUNTY.gradeMountain + 0.005) bad.add(String.format(Locale.ROOT, "road grade %.1f %%", gRoad * 100));
        com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect plot = com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout.plot(h.reservation());
        double plotMin = Double.POSITIVE_INFINITY;
        for (double s = 0; s <= d.length; s += 2) {
            double[] p = d.point(s, 0);
            plotMin = Math.min(plotMin, plot.distance(p[0], p[1]));
        }
        if (plotMin < RoadCounty.PLOT_HARD - 0.5) bad.add(String.format(Locale.ROOT, "the road comes %.0f m near the home's plot", plotMin));
        // the driveway: from H at the yard's height to the paved edge
        double[] dr = h.drive();
        int n = dr.length / 3;
        if (n < 3) { bad.add("no driveway"); return "?"; }
        if (Math.hypot(dr[0] - h.node()[0], dr[1] - h.node()[2]) > 0.05 || Math.abs(dr[2] - h.node()[1]) > 0.02)
            bad.add(String.format(Locale.ROOT, "the driveway starts at (%.2f, %.2f) Y%.2f, H is (%.2f, %.2f) Y%.2f", dr[0], dr[1], dr[2], h.node()[0], h.node()[2], h.node()[1]));
        double[] along = new double[n];
        for (int i = 1; i < n; i++) along[i] = along[i - 1] + Math.hypot(dr[3 * i] - dr[3 * i - 3], dr[3 * i + 1] - dr[3 * i - 2]);
        double total = along[n - 1];
        if (total < RoadCounty.DRIVE_MIN - 0.5 || total > RoadCounty.DRIVE_MAX + 0.5) bad.add(String.format(Locale.ROOT, "the driveway is %.0f m long", total));
        double ex = dr[3 * n - 3], ez = dr[3 * n - 2];
        double[] le = d.local(ex, ez, edge + 10);
        if (le == null || Math.abs(Math.abs(le[1]) - edge) > 0.05) bad.add("the driveway does not end on the paved edge" + (le == null ? "" : String.format(Locale.ROOT, " (u %.2f)", le[1])));
        double sm = le == null ? 0 : le[0];
        int side = le == null || le[1] >= 0 ? 1 : -1;
        double gDrive = 0, gEnd = 0, bend = 0;
        int wet = 0;
        double[] fp = null;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                double g = Math.abs(dr[3 * i + 2] - dr[3 * i - 1]) / Math.max(1e-6, along[i] - along[i - 1]);
                if (total - along[i] < RoadCounty.DRIVE_STRAIGHT - 1e-6) gEnd = Math.max(gEnd, g);
                else gDrive = Math.max(gDrive, g);
                if (System.getenv("COUNTY_GRADE") != null && g > 0.08)
                    System.out.printf(Locale.ROOT, "  grade %.1f %% at %.1f m from the edge (Y %.2f -> %.2f)%n", g * 100, total - along[i], dr[3 * i - 1], dr[3 * i + 2]);
            }
            for (int k = 0; k < 9; k++) {
                double x = dr[3 * i] + (k % 3 - 1) * 4, z = dr[3 * i + 1] + (k / 3 - 1) * 4;
                if (t.water(x, z) != RouteTerrain.DRY) { wet++; break; }
            }
            if (fp == null && total - along[i] <= RoadCounty.DRIVE_STRAIGHT + 1e-6) fp = new double[]{dr[3 * i], dr[3 * i + 1]};
        }
        if (gDrive > RoadCounty.DRIVE_GRADE + 0.005) bad.add(String.format(Locale.ROOT, "driveway grade %.1f %%", gDrive * 100));
        if (gEnd > RoadCounty.DRIVE_END_GRADE + 0.005) bad.add(String.format(Locale.ROOT, "driveway grade over its last %.0f m %.1f %%", RoadCounty.DRIVE_STRAIGHT, gEnd * 100));
        if (wet > 0) bad.add(wet + " driveway points within 4 m of water");
        // its last stretch straight and square to the road
        double lx = ex - fp[0], lz = ez - fp[1], ll = Math.hypot(lx, lz);
        for (int i = 0; i < n; i++) {
            if (total - along[i] > RoadCounty.DRIVE_STRAIGHT + 1e-6) continue;
            bend = Math.max(bend, Math.abs((dr[3 * i] - fp[0]) * lz - (dr[3 * i + 1] - fp[1]) * lx) / ll);
        }
        if (bend > 0.05) bad.add(String.format(Locale.ROOT, "the driveway's last %.0f m bends %.2f m off straight", RoadCounty.DRIVE_STRAIGHT, bend));
        double[] f = new double[5];
        d.curve.frame(sm, f);
        double angle = Math.toDegrees(Math.acos(Math.min(1, Math.abs(f[2] * lx + f[3] * lz) / ll)));
        if (angle < 70) bad.add(String.format(Locale.ROOT, "the driveway meets the road at %.0f deg", angle));
        // the mouth: the road on both sides of it, clear of the ends and structures, even, in sight
        if (sm < RoadCounty.MOUTH_END - 1 || sm > d.length - RoadCounty.MOUTH_END + 1) bad.add(String.format(Locale.ROOT, "the mouth at s %.0f of %.0f", sm, d.length));
        for (RoadNetworkJson.Structure x : c.structures())
            if (x.s1() >= sm + t0 - RoadCounty.MOUTH_END + 1 && x.s0() <= sm + t0 + RoadCounty.MOUTH_END - 1) bad.add("a " + x.kind() + " near the mouth");
        if (!RoadCounty.even(d, Math.rint(sm / 2) * 2)) bad.add("the road at the mouth is not straight and even");
        if (!RoadCounty.sight(d, Math.rint(sm / 2) * 2, side)) bad.add("no sight from the mouth");
        // the gate on the driveway, the mailbox beside the road
        double[] gate = h.gate(), mb = h.mailbox();
        double gIn = gate == null ? Double.NaN : Math.hypot(gate[0] - ex, gate[2] - ez);
        if (!(gIn >= RoadCounty.GATE_MIN - 0.1 && gIn <= RoadCounty.GATE_MAX + 0.1)) bad.add(String.format(Locale.ROOT, "the gate %.1f m from the edge", gIn));
        double[] lm = mb == null ? null : d.local(mb[0], mb[2], edge + 10);
        if (lm == null || Math.abs(Math.abs(lm[1]) - edge - RoadCounty.MAILBOX_OFF) > 0.05 || lm[1] * side < 0) bad.add("the mailbox is not beside the road by the mouth");
        // crossings: no other road's paved band within the county road's own paved width (its hosts' mouths excepted)
        String hs = c.hosts()[0], he = c.hosts()[1];
        for (RouteData q : r.routes()) {
            if (q == c) continue;
            RoadDesign qd = RoadNetworkJson.design(q);
            double lo = q.id().equals(hs) ? 120 : 0, hi = q.id().equals(he) ? d.length - 120 : d.length;
            for (double s = lo; s <= hi; s += 2) {
                d.curve.frame(s, f);
                double[] l = qd.local(f[0], f[1], qd.section.edge + d.section.edge + 2);
                if (l != null && Math.abs(l[1]) <= qd.section.edge + d.section.edge) {
                    bad.add(String.format(Locale.ROOT, "crosses %s at %.0f m", q.id(), s));
                    break;
                }
            }
        }
        // the start
        String from;
        switch (h.beyond()) {
            case "state" -> {
                RouteData q = null;
                for (RouteData x : r.routes()) if (x.id().equals(hs)) q = x;
                if (q == null) bad.add("the start's host " + hs + " is not in the network");
                else if (Junction.of(RoadNetworkJson.design(q), d, false) == null) bad.add("no junction on " + hs + " at the start");
                from = "T on " + hs;
            }
            case "end" -> {
                if (h.barrier() == null) bad.add("a dead end without its barrier");
                from = "dead end";
            }
            default -> {
                bad.add("the start is " + h.beyond());
                from = h.beyond();
            }
        }
        // the end
        String to;
        if (h.link().equals("town")) {
            TownPlan p = null;
            TownPlan.Entry en = null;
            for (TownPlan q : r.towns()) for (TownPlan.Entry x : q.entries) if (x.road().equals(c.id())) { p = q; en = x; }
            if (en == null) { bad.add("no town entry names the road"); return "town ?"; }
            TownPlan.Node nd = p.nodes.get(en.node());
            double[] pe = d.point(d.length, 0), lt = p.local(pe[0], pe[1]);
            boolean par = en.dir() <= TownPlan.DIR_S_DOWN;
            double at = par ? lt[0] : lt[1], off = par ? lt[1] - nd.v() : lt[0] - nd.s();
            if (Math.abs(at - en.end()) > 0.02) bad.add(String.format(Locale.ROOT, "the road ends at %.2f, the entry says %.2f", at, en.end()));
            if (Math.abs(off) > 0.5) bad.add(String.format(Locale.ROOT, "the road's end is %.2f m off the arm's line", off));
            if (Math.abs(d.datum(d.length) - nd.h()) > 0.02) bad.add(String.format(Locale.ROOT, "the road ends at Y%.2f, the node's datum Y%.2f", d.datum(d.length), nd.h()));
            double[] ta = d.point(d.length - 1, 0), tb = d.point(d.length, 0), u = TownPlan.arm(en.dir());
            double hx = tb[0] - ta[0], hz = tb[1] - ta[1], hl = Math.hypot(hx, hz);
            double ix = -(u[0] * p.ax + u[1] * p.bx), iz = -(u[0] * p.az + u[1] * p.bz);
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (hx * ix + hz * iz) / hl))));
            if (ang > 3) bad.add(String.format(Locale.ROOT, "the road's end heads %.1f deg off the arm", ang));
            to = String.format(Locale.ROOT, "%s node %d arm %d, end %.1f deg off", p.name, en.node(), en.dir(), ang);
        } else {
            RouteData q = null;
            for (RouteData x : r.routes()) if (x.id().equals(he)) q = x;
            if (q == null) { bad.add("the host " + he + " is not in the network"); return "state ?"; }
            if (Junction.of(RoadNetworkJson.design(q), d, true) == null) bad.add("no junction on " + he);
            to = "T on " + he;
        }
        return String.format(Locale.ROOT, "site (%d, %d) r%d: %s .. %s %.0f m, mouth s %.0f (%.0f deg), driveway %.0f m, grade %.1f %% / driveway %.1f %% (last %.0f m %.1f %%), plot %.0f m",
                h.x(), h.z(), h.rotation(), from, to, d.length, sm, angle, total, gRoad * 100, gDrive * 100, RoadCounty.DRIVE_STRAIGHT, gEnd * 100, plotMin);
    }
}
