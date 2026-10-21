package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
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
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

/**
 * Offline check of the framework towns (Town Framework V1, docs/worldgen/federal_towns_v1.md): every survey seed's
 * newest plan (build/federal-systems/plans, SystemPlans) built with its cached C2 terrain plan, the towns laid out; prints
 * the exit interchange, the crossroad's profile over the ground, and the town plan's numbers.
 * <pre>
 *   TownCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS)
 * </pre>
 */
public final class TownCheck {
    private TownCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = newestPlans();
        boolean ok = true;
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
            if (s == null) { System.out.println("== seed " + unsigned + ": STALE terrain plan " + f); ok = false; continue; }
            RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
            System.out.println("== seed " + unsigned + " (" + planFile.getFileName() + ")");
            long t0 = System.nanoTime();
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(Files.readString(planFile), terrain);
            System.out.printf(Locale.ROOT, "  build %d ms, conflicts %d%n", (System.nanoTime() - t0) / 1_000_000, r.conflicts());
            for (String l : r.report())
                if (l.contains("town_") || l.startsWith("town ") || l.startsWith("CONFLICT")) System.out.println("  " + l);
            List<RoadDesign> roads = RoadNetworkJson.designs(r.json());
            for (RoadDesign d : roads) {
                if (!d.id.startsWith("town_")) continue;
                RoadDesign host = null;
                for (RoadDesign q : roads) if (q.section == CrossSection.R4 && q.local(d.point(d.length / 2, 0)[0], d.point(d.length / 2, 0)[1], 400) != null) host = q;
                StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  crossroad %s %.0f m, host %s%n", d.id, d.length, host == null ? "-" : host.id));
                for (double t = 0; t <= d.length + 1e-9; t += 40) {
                    double[] p = d.point(t, 0), l = host == null ? null : host.local(p[0], p[1], 2000);
                    double e = d.profile.at(t), g = terrain.height(p[0], p[1]);
                    sb.append(String.format(Locale.ROOT, "    t %4.0f u %+6.0f  E %6.2f  g %6.2f  E-g %+5.2f%s%n", t, l == null ? Double.NaN : l[1], e, g, e - g,
                            d.span(t) != null ? "  span" : ""));
                }
                System.out.print(sb);
            }
            // the towns: their numbers, the round trip through the JSON, the picture
            String withTowns = com.antaurora.apofirstlight.worldgen.network.town.TownPlan.append(r.json(), r.towns());
            List<com.antaurora.apofirstlight.worldgen.network.town.TownPlan> back = com.antaurora.apofirstlight.worldgen.network.town.TownPlan.read(withTowns);
            if (back.size() != r.towns().size()) { System.out.println("  TOWN JSON ROUND TRIP FAILED"); ok = false; }
            RoadWorks.Water water = (x, z) -> {
                int w = terrain.water(x, z);
                return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
            };
            RoadWorks works = new RoadWorks(RoadNetworkJson.designs(withTowns), water, new com.antaurora.apofirstlight.worldgen.network.town.TownWorks(back),
                    Driveway.EMPTY, RoadWorks.Ground.of(terrain.plan));
            for (com.antaurora.apofirstlight.worldgen.network.town.TownPlan p : back) {
                double hmin = Double.POSITIVE_INFINITY, hmax = Double.NEGATIVE_INFINITY, steep = 0;
                for (com.antaurora.apofirstlight.worldgen.network.town.TownPlan.Node n : p.nodes) { hmin = Math.min(hmin, n.h()); hmax = Math.max(hmax, n.h()); }
                for (com.antaurora.apofirstlight.worldgen.network.town.TownPlan.Street st : p.streets) {
                    var a = p.nodes.get(st.a()); var b = p.nodes.get(st.b());
                    steep = Math.max(steep, Math.abs(a.h() - b.h()) / Math.hypot(a.s() - b.s(), a.v() - b.v()));
                }
                // pads against the ground: the earthwork the town asks for
                double cut = 0, fill = 0, worst = 0;
                int n = 0;
                for (com.antaurora.apofirstlight.worldgen.network.town.TownPlan.Lot l : p.lots) {
                    double[] w = p.world((l.s0() + l.s1()) / 2, (l.v0() + l.v1()) / 2);
                    double d = l.pad() - terrain.height(w[0], w[1]);
                    if (d > 0) fill += d; else cut -= d;
                    worst = Math.max(worst, Math.abs(d));
                    n++;
                }
                // where it is in the world (to send someone there): the limit's corners and its middle
                double[] c0 = p.world(p.limit[0], p.limit[2]), c1 = p.world(p.limit[1], p.limit[3]);
                double[] mid = p.world((p.limit[0] + p.limit[1]) / 2, (p.limit[2] + p.limit[3]) / 2);
                System.out.printf(Locale.ROOT, "  town %s in the world: middle %.0f %.0f, limit corners %.0f %.0f / %.0f %.0f%n", p.name, mid[0], mid[1], c0[0], c0[1], c1[0], c1[1]);
                for (double[] e : p.exitEnds) {
                    double[] w = p.world(p.nodes.get((int) e[0]).s(), e[1]);
                    System.out.printf(Locale.ROOT, "  town %s exit crossroad's end at %.0f %.0f%n", p.name, w[0], w[1]);
                }
                System.out.printf(Locale.ROOT, "  town %s: intersections %.1f .. %.1f m, steepest street %.1f %%; lots %d, mean fill %.2f m, mean cut %.2f m, worst %.1f m%n",
                        p.town, hmin, hmax, steep * 100, n, fill / Math.max(1, n), cut / Math.max(1, n), worst);
                String dir = System.getProperty("afl.townPng");
                if (dir != null) {
                    java.io.File pic = new java.io.File(dir, p.town + "_" + unsigned + ".png");
                    TownPng.draw(p, roads, terrain, pic);
                    System.out.println("  picture " + pic);
                }
                if (!TownWorksCheck.check(p, works, RoadWorks.Ground.of(s), terrain, dir == null ? null : new java.io.File(dir, p.town + "_" + unsigned + "_works.png"))) ok = false;
                if (!TownBuildingsCheck.check(p, works.towns())) ok = false;
                // the pavement mesh (TownMesh): its tops no steeper than a street can be (2026-10-17: the curb returns'
                // corners read the asphalt's height, a saw of sloped curb tops in game)
                int tops = 0, steepTops = 0, slivers = 0, sliverFaces = 0, twice = 0, paintTris = 0;
                double topArea = 0, paintArea = 0;
                java.util.Set<Long> faces = new java.util.HashSet<>();
                double worstTop = 0, wx = 0, wz = 0, a1Top = 0;
                int cx0 = Integer.MAX_VALUE, cx1 = Integer.MIN_VALUE, cz0 = Integer.MAX_VALUE, cz1 = Integer.MIN_VALUE;
                for (double[] c : new double[][]{{p.limit[0], p.limit[2]}, {p.limit[1], p.limit[2]}, {p.limit[0], p.limit[3]}, {p.limit[1], p.limit[3]}}) {
                    double[] w = p.world(c[0], c[1]);
                    cx0 = Math.min(cx0, Math.floorDiv((int) Math.floor(w[0]), 16)); cx1 = Math.max(cx1, Math.floorDiv((int) Math.floor(w[0]), 16));
                    cz0 = Math.min(cz0, Math.floorDiv((int) Math.floor(w[1]), 16)); cz1 = Math.max(cz1, Math.floorDiv((int) Math.floor(w[1]), 16));
                }
                for (int cx = cx0; cx <= cx1; cx++)
                    for (int cz = cz0; cz <= cz1; cz++)
                        for (RoadMesh.Triangle t : TownMesh.chunk(works.towns(), cx, cz)) {
                            double ux = t.b().x() - t.a().x(), uy = t.b().y() - t.a().y(), uz = t.b().z() - t.a().z();
                            double vx = t.c().x() - t.a().x(), vy = t.c().y() - t.a().y(), vz = t.c().z() - t.a().z();
                            double ar = Math.sqrt(Math.pow(uy * vz - uz * vy, 2) + Math.pow(uz * vx - ux * vz, 2) + Math.pow(ux * vy - uy * vx, 2)) / 2;
                            if (!t.material().solid()) {
                                // the markings (TownControl): their painted area against the marks' (2026-10-19)
                                paintTris++;
                                paintArea += ar * t.ny();
                                continue;
                            }
                            double lmax = Math.max(Math.sqrt(ux * ux + uy * uy + uz * uz), Math.max(Math.sqrt(vx * vx + vy * vy + vz * vz),
                                    Math.sqrt(Math.pow(t.c().x() - t.b().x(), 2) + Math.pow(t.c().y() - t.b().y(), 2) + Math.pow(t.c().z() - t.b().z(), 2))));
                            // a sliver: long and thinner than 1 mm (2026-10-17: only where RoadMesh.clipInto cuts the triangles at
                            // the chunk borders, which run askew to the town's grid; the next chunk holds the rest; reported only)
                            if (lmax > 0.05 && 2 * ar / lmax < 1e-3) {
                                if (t.ny() < 0.5) sliverFaces++; else slivers++;
                            }
                            if (t.ny() < 0.5) {
                                // a vertical face: its three corners rounded to 1 mm, sorted; the same face twice z-fights
                                long[] h = new long[3];
                                int hi = 0;
                                for (RoadMesh.Vertex q : new RoadMesh.Vertex[]{t.a(), t.b(), t.c()})
                                    h[hi++] = Math.round((cx * 16 + q.x()) * 1000) * 73856093L ^ Math.round(q.y() * 1000) * 19349663L ^ Math.round((cz * 16 + q.z()) * 1000) * 83492791L;
                                java.util.Arrays.sort(h);
                                if (!faces.add(h[0] * 31 * 31 + h[1] * 31 + h[2])) twice++;
                                continue;
                            }
                            tops++;
                            topArea += ar * t.ny();
                            double slope = Math.hypot(t.nx(), t.nz()) / t.ny();
                            // Fuel Stop A1's aprons (2026-10-20): apart, at most 25 %
                            if (works.towns().onFuelStopApron(cx * 16 + (t.a().x() + t.b().x() + t.c().x()) / 3, cz * 16 + (t.a().z() + t.b().z() + t.c().z()) / 3)) {
                                a1Top = Math.max(a1Top, slope);
                                continue;
                            }
                            if (slope > 0.15) steepTops++;
                            if (slope > worstTop) { worstTop = slope; wx = cx * 16 + t.a().x(); wz = cz * 16 + t.a().z(); }
                        }
                System.out.printf(Locale.ROOT, "  pavement mesh: %d tops (%.0f m2), %d steeper than 15 %% (worst %.0f %% at %.0f %.0f), slivers (over 5 cm long, under 1 mm wide) %d on top, %d in the faces, %d faces drawn twice; Fuel Stop A1's aprons at most %.0f %%%n",
                        tops, topArea, steepTops, worstTop * 100, wx, wz, slivers, sliverFaces, twice, a1Top * 100);
                if (steepTops > 0) {
                    // where the worst one lies: town coordinates, the nearest intersection and whether a county road enters there
                    double[] l = p.local(wx, wz);
                    int near = -1;
                    double nd = Double.POSITIVE_INFINITY;
                    for (int i = 0; i < p.nodes.size(); i++) {
                        double d = Math.hypot(p.nodes.get(i).s() - l[0], p.nodes.get(i).v() - l[1]);
                        if (d < nd) { nd = d; near = i; }
                    }
                    boolean entry = false;
                    for (var e : p.entries) entry |= e.node() == near;
                    var sp = works.towns().spot(wx, wz);
                    System.out.printf(Locale.ROOT, "    worst top at s %.1f v %.1f (%s), %.1f m from node %d%s%n", l[0], l[1], sp == null ? "off the town" : sp.part(),
                            nd, near, entry ? " (a county road's mouth)" : "");
                    var nq = p.nodes.get(near);
                    System.out.printf(Locale.ROOT, "    node %d at s %.1f v %.1f h %.2f; around (s across, v down, 0.5 m):%n", near, nq.s(), nq.v(), nq.h());
                    for (double dv = -2; dv <= 2.001; dv += 0.5) {
                        StringBuilder row = new StringBuilder("     ");
                        for (double ds = -2; ds <= 2.001; ds += 0.5) {
                            double[] w = p.world(l[0] + ds, l[1] + dv);
                            var q = works.towns().spot(w[0], w[1]);
                            row.append(q == null ? "    --    " : String.format(Locale.ROOT, " %7.3f%c", q.h(), q.part().name().charAt(0)));
                        }
                        System.out.println(row);
                    }
                }
                if (steepTops > 0 || twice > 0 || a1Top > 0.25) ok = false;
                // the control and markings (TownControl, 2026-10-19): every mark on the asphalt, painted whole
                var ctl = works.towns().control(p);
                int white = 0, yellow = 0, offAsphalt = 0, allWay = 0;
                double markArea = 0;
                for (var m : ctl.marks()) {
                    if (m.yellow()) yellow++; else white++;
                    markArea += (m.s1() - m.s0()) * (m.v1() - m.v0());
                    for (double[] c : new double[][]{{m.s0(), m.v0()}, {m.s1(), m.v0()}, {m.s1(), m.v1()}, {m.s0(), m.v1()}, {(m.s0() + m.s1()) / 2, (m.v0() + m.v1()) / 2}}) {
                        double[] w = p.world(c[0], c[1]);
                        var sp = works.towns().spot(w[0], w[1]);
                        if (sp == null || sp.part() != com.antaurora.apofirstlight.worldgen.network.town.TownWorks.Part.ASPHALT) {
                            if (offAsphalt++ < 3) System.out.printf(Locale.ROOT, "  MARK OFF THE ASPHALT at s %.2f v %.2f (%s)%n", c[0], c[1], sp == null ? "none" : sp.part());
                        }
                    }
                }
                for (var st : ctl.stops()) if (st.allWay()) allWay++;
                System.out.printf(Locale.ROOT, "  markings: %d white (stop bars, crosswalk lines), %d yellow (centre lines), %.0f m2; painted %d triangles, %.0f m2; corners off the asphalt %d; stops %d (%d all-way)%n",
                        white, yellow, markArea, paintTris, paintArea, offAsphalt, ctl.stops().size(), allWay);
                if (offAsphalt > 0 || Math.abs(paintArea - markArea) > 0.01 * markArea + 1) ok = false;
            }
            // the towns' signs (TownSigns): a limit sign per exit end, the blades on the sidewalk at its height
            if (!back.isEmpty()) {
                int limits = 0, blades = 0, off = 0, ends = 0, meshFailed = 0, stopSigns = 0, stopsWanted = 0, stopOff = 0, speeds = 0, numbered = 0;
                int overlapChecked = 0, overlaps = 0;
                for (var p : back) {
                    ends += p.exitEnds.size();
                    stopsWanted += works.towns().control(p).stops().size();
                }
                // the client's sign mesh (GuideSignParts.build, package-private) for every town sign: a one-post blade
                // crashed it in game on 2026-10-17 (feet()[1])
                java.lang.reflect.Method mesh = Class.forName("com.antaurora.apofirstlight.client.road.GuideSignParts").getDeclaredMethod("build", String.class, float[].class);
                mesh.setAccessible(true);
                for (TunnelMesh.Placement q : TownSigns.all(works, RoadWorks.Ground.of(s))) {
                    // the STOP signs at the town's 7 ft (on the utility strip or the sidewalk, at its height) and the speed limits
                    if (q.mesh().equals("stop_sign")) {
                        stopSigns++;
                        var sp = works.towns().spot(q.x(), q.z());
                        boolean strip = sp != null && (sp.part() == com.antaurora.apofirstlight.worldgen.network.town.TownWorks.Part.UTILITY
                                || sp.part() == com.antaurora.apofirstlight.worldgen.network.town.TownWorks.Part.SIDEWALK);
                        if (!strip || Math.abs(sp.h() - q.y()) > 0.01 || !("town".equals(q.bone()) || "all_way".equals(q.bone()))) stopOff++;
                        continue;
                    }
                    if (q.mesh().equals("speed_limit_sign")) { speeds++; continue; }
                    if (GuideSigns.Spec.parse(q.bone()).type() == GuideSigns.Type.STREET && !GuideSigns.Spec.parse(q.bone()).exit().isEmpty()) numbered++;
                    if (overlapChecked++ < 4)
                        for (String finding : com.antaurora.apofirstlight.client.road.SignOverlap.check(q.bone())) {
                            System.out.println("  SIGN FACES OVERLAP " + q.bone() + ": " + finding);
                            overlaps++;
                        }
                    try {
                        mesh.invoke(null, q.bone(), new float[]{0, 1, 0, 1});
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        if (meshFailed++ == 0) System.out.println("  SIGN MESH FAILED for " + q.bone() + ": " + e.getCause());
                    }
                    if (q.bone().startsWith(GuideSigns.Type.LIMIT.key + "|")) limits++;
                    else {
                        blades++;
                        var sp = works.towns().spot(q.x(), q.z());
                        if (sp == null || sp.part() != com.antaurora.apofirstlight.worldgen.network.town.TownWorks.Part.SIDEWALK || Math.abs(sp.h() - q.y()) > 0.01) off++;
                    }
                }
                System.out.printf(Locale.ROOT, "  signs: limit %d of %d exit ends, speed limit %d, street blades %d (%d with the hundred block, %d off the sidewalk), STOP %d of %d stops (%d off the strip), mesh failed %d%n",
                        limits, ends, speeds, blades, numbered, off, stopSigns, stopsWanted, stopOff, meshFailed);
                if (limits < ends || off > 0 || meshFailed > 0 || overlaps > 0 || stopSigns < stopsWanted || stopOff > 0 || speeds < 1) ok = false;
            }
        }
        System.out.println(ok ? "TOWN CHECK: done" : "TOWN CHECK: FAILED");
        if (!ok) System.exit(1);
    }

    /** The newest plan file per seed (unsigned decimal) in build/federal-systems/plans. */
    static Map<String, Path> newestPlans() throws Exception {
        Map<String, Path> bySeed = new TreeMap<>();
        Path d = Path.of(SystemCheck.PLANS);
        if (!Files.isDirectory(d)) return bySeed;
        try (Stream<Path> list = Files.list(d)) {
            for (Path p : (Iterable<Path>) list.filter(p -> p.getFileName().toString().matches(com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.VERSION + "_\\d+_[0-9a-f]+\\.json"))::iterator) {
                String n = p.getFileName().toString(), seed = n.substring((com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.VERSION + "_").length(), n.lastIndexOf('_'));
                Path old = bySeed.get(seed);
                if (old == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(old)) > 0) bySeed.put(seed, p);
            }
        }
        return bySeed;
    }
}
