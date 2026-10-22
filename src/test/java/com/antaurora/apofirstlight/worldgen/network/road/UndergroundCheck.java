package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.underground.GeoProtect;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel.Column;
import com.antaurora.apofirstlight.worldgen.underground.RockType;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Anchor;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Capsule;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Cave;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.CaveLink;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Ellipsoid;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Entrance;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.EntranceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Exit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.FacilityPart;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Kit;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreBody;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pillar;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Pitch;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Place;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Need;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Step;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.PlaceKind;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Platform;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Shape;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.Succession;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlanner;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundRoutes;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.InflaterInputStream;

/**
 * U1 offline check of the underground plan (docs/worldgen/underground_geology_caves_v0_design.md 14.1, 19.10): per
 * seed the full network build (as StartMapCheck), then the geology model at minY -256, the C works' protection and the
 * underground plan, and:
 * <ul>
 *   <li>the geology by state (bedrock shares; geology follows landform, not borders), the map and six sections;</li>
 *   <li>the four layers' air by Monte Carlo against the design targets, layer 1 clear of everything but throats;</li>
 *   <li>counts of systems, giant caves, canyons, entrances by kind, ore districts, mines, the facility;</li>
 *   <li>the connectivity graph, the routes in and out for every kit (no traps), least kit to every target;</li>
 *   <li>every rope pitch's two ends standable and anchored in competent rock;</li>
 *   <li>no shape or entrance breaking a road, town, home or facility rule;</li>
 *   <li>every succession candidate with at least one reliable exit;</li>
 *   <li>determinism (two builds' digests, queries in two orders) and the planning times, plus minY -192 and -64 counts.</li>
 * </ul>
 * Writes build/underground/&lt;seed&gt;_*.png and build/underground/report.txt. Never touches a world or a save.
 * <pre>
 *   UndergroundCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS)
 * </pre>
 */
public final class UndergroundCheck {
    private UndergroundCheck() {}

    static final File OUT = new File("build/underground");
    static final StringBuilder REPORT = new StringBuilder();

    static void out(String fmt, Object... a) {
        String s = a.length == 0 ? fmt : String.format(Locale.ROOT, fmt, a);
        System.out.println(s);
        REPORT.append(s).append('\n');
    }

    public static void main(String[] args) throws Exception {
        OUT.mkdirs();
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        int fails = 0, seeds = 0;
        List<String> summary = new ArrayList<>();
        for (String f : bins) {
            String name = Path.of(f).getFileName().toString();
            String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
            Path planFile = plans.get(unsigned);
            if (planFile == null) { out("== seed " + unsigned + ": no plan (run systemPlans)"); continue; }
            long seed = Long.parseUnsignedLong(unsigned);
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null) { out("== seed " + unsigned + ": STALE terrain plan " + f); continue; }
            seeds++;
            out("== seed " + unsigned);
            FederalMap map = FederalMap.forSeed(seed);
            RouteTerrain terrain = new RouteTerrain(s, map);
            String[] kept = new String[1];
            RoadCounty.Memo memo = new RoadCounty.Memo() {
                @Override public String load() { return kept[0]; }
                @Override public void store(String site) { kept[0] = site; }
            };
            long tr = System.nanoTime();
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(Files.readString(planFile), terrain, memo);
            RoadCounty.Home h = r.home();
            List<RoadDesign> roads = RoadNetworkJson.designs(h == null ? TownPlan.append(r.json(), r.towns()) : RoadCounty.append(TownPlan.append(r.json(), r.towns()), h));
            out("  roads/towns/home built in %d ms (%d roads, %d towns, home %s)", (System.nanoTime() - tr) / 1_000_000, roads.size(), r.towns().size(), h == null ? "none" : "yes");
            Result res = check(unsigned, seed, s, map, roads, r.towns(), h);
            summary.add(unsigned + ": " + res.line + (res.bad.isEmpty() ? "" : " FAIL " + res.bad));
            for (String b : res.bad) out("  FAIL " + b);
            fails += res.bad.isEmpty() ? 0 : 1;
        }
        out("== summary");
        for (String l : summary) out("  " + l);
        out(fails == 0 ? "UNDERGROUND CHECK PASS (" + seeds + " seeds)" : "UNDERGROUND CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        Files.writeString(new File(OUT, "report.txt").toPath(), REPORT.toString());
        if (fails > 0) System.exit(1);
    }

    record Result(String line, List<String> bad) {}

    static Result check(String tag, long seed, TerrainPlanSurface s, FederalMap map, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) throws Exception {
        List<String> bad = new ArrayList<>();
        long t0 = System.nanoTime();
        GeologyModel g = new GeologyModel(seed, s, map, -256);
        long t1 = System.nanoTime();
        GeoProtect p = new GeoProtect(s, roads, towns, h);
        long t2 = System.nanoTime();
        UndergroundPlan u = UndergroundPlanner.build(g, p);
        long t3 = System.nanoTime();
        long geoMs = (t1 - t0) / 1_000_000, protMs = (t2 - t1) / 1_000_000, planMs = (t3 - t2) / 1_000_000;
        out("  times: geology %d ms, protection %d ms, plan %d ms %s", geoMs, protMs, planMs, u.times);
        out("  planner notes: %s", u.notes);
        if (geoMs + planMs > 3000) out("  NOTE planning over the 3 s target (geology + plan %d ms)", geoMs + planMs);

        // ---- geology by state (10, 4): bedrock shares on a 256 m grid
        Map<FederalStateId, EnumMap<RockType, Integer>> byState = new TreeMap<>();
        Map<FederalStateId, Integer> stateN = new TreeMap<>();
        double size = s.gridN * TerrainPlanSurface.CELL;
        for (double z = s.gridOrigin + 128; z < s.gridOrigin + size; z += 256) for (double x = s.gridOrigin + 128; x < s.gridOrigin + size; x += 256) {
            if (s.waterAt(x, z) || !map.isLand(x, z)) continue;
            FederalStateId st = map.stateAt(x, z);
            if (st == null) continue;
            RockType b = g.bedrock(g.column(x, z));
            byState.computeIfAbsent(st, q -> new EnumMap<>(RockType.class)).merge(b, 1, Integer::sum);
            stateN.merge(st, 1, Integer::sum);
        }
        out("  bedrock by state (share of land columns, 256 m grid):");
        for (var e : byState.entrySet()) {
            StringBuilder sb = new StringBuilder();
            int n = stateN.get(e.getKey());
            e.getValue().entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                    .forEach(q -> sb.append(String.format(Locale.ROOT, " %s %.0f%%", q.getKey().id, 100.0 * q.getValue() / n)));
            out("    %-14s n=%4d%s", e.getKey(), n, sb);
        }
        out("  faults: %d (%s)", g.faultList().size(), faultText(g));
        {
            int landN = 0, road = 0, town = 0, home = 0, wet = 0, river = 0, ok = 0;
            for (double z = s.gridOrigin + 128; z < s.gridOrigin + size; z += 256) for (double x = s.gridOrigin + 128; x < s.gridOrigin + size; x += 256) {
                if (s.waterAt(x, z) || !map.isLand(x, z)) continue;
                landN++;
                if (p.roadDistance(x, z) < GeoProtect.ENTRANCE_ROAD) road++;
                if (p.townDistance(x, z) < GeoProtect.ENTRANCE_TOWN) town++;
                if (p.homeDistance(x, z) < GeoProtect.ENTRANCE_HOME) home++;
                if (p.wet(x, z)) wet++;
                if (s.rivers != null && s.rivers.nearestEdge(x, z, 31, new double[1]) < GeoProtect.ENTRANCE_RIVER) river++;
                if (p.entranceAllowed(EntranceKind.SHAFT, x, z)) ok++;
            }
            out("  entrance rules on %d land cells: road %d, town %d, home %d, wet %d, river %d; allowed %d", landN, road, town, home, wet, river, ok);
        }

        // ---- counts
        EnumMap<CaveKind, Integer> kinds = new EnumMap<>(CaveKind.class);
        double passKarst = 0;
        for (Cave c : u.caves) if (!c.label.equals("removed")) kinds.merge(c.kind, 1, Integer::sum);
        for (Cave c : u.caves) if (c.kind == CaveKind.KARST_SYSTEM) passKarst += c.passageLength;
        int karst = kinds.getOrDefault(CaveKind.KARST_SYSTEM, 0), frac = kinds.getOrDefault(CaveKind.FRACTURE_SYSTEM, 0);
        int giants = kinds.getOrDefault(CaveKind.GIANT, 0), canyons = kinds.getOrDefault(CaveKind.CANYON, 0);
        out("  caves: %s; karst passage %.1f km (%.0f m a system)", kinds, passKarst / 1000, karst == 0 ? 0 : passKarst / karst);
        EnumMap<EntranceKind, int[]> ents = new EnumMap<>(EntranceKind.class);
        for (Entrance e : u.entrances) {
            int[] c = ents.computeIfAbsent(e.kind(), q -> new int[2]);
            c[e.connected() ? 0 : 1]++;
        }
        StringBuilder es = new StringBuilder();
        for (var e : ents.entrySet()) es.append(String.format(Locale.ROOT, " %s %d+%d", e.getKey().name().toLowerCase(Locale.ROOT), e.getValue()[0], e.getValue()[1]));
        out("  entrances (connected+dead):%s; total %d", es, u.entrances.size());
        EnumMap<UndergroundPlan.OreKind, int[]> ores = new EnumMap<>(UndergroundPlan.OreKind.class);
        for (UndergroundPlan.District d : u.districts) ores.computeIfAbsent(d.kind(), q -> new int[2])[0]++;
        for (OreBody o : u.ores) ores.computeIfAbsent(o.kind(), q -> new int[2])[1]++;
        StringBuilder os = new StringBuilder();
        for (var e : ores.entrySet()) os.append(String.format(Locale.ROOT, " %s %d/%d", e.getKey().name().toLowerCase(Locale.ROOT), e.getValue()[0], e.getValue()[1]));
        out("  ore districts/bodies:%s; mines %d", os, u.mines.size());
        range(bad, "karst systems", karst, 50, 120);
        range(bad, "fracture systems", frac, 20, 40);
        range(bad, "giant caves", giants, 3, 6);
        range(bad, "canyons", canyons, 1, 2);
        rangeNote("collapse pits", count(ents, EntranceKind.COLLAPSE_PIT), 300, 800);
        rangeNote("cover sinkholes", count(ents, EntranceKind.COVER_SINKHOLE), 40, 100);
        range(bad, "great sinkholes", count(ents, EntranceKind.GREAT_SINKHOLE), 4, 8);
        rangeNote("hillside mouths", count(ents, EntranceKind.HILLSIDE_MOUTH), 30, 80);
        rangeNote("fissures + shafts", count(ents, EntranceKind.FISSURE) + count(ents, EntranceKind.SHAFT), 50, 150);
        // the states' own ways in (21.10)
        out("  state entrances: spring mouths %d, karst windows %d, cliff mouths %d, wave-cut caves %d",
                count(ents, EntranceKind.SPRING_MOUTH), count(ents, EntranceKind.KARST_WINDOW), count(ents, EntranceKind.CLIFF_MOUTH), count(ents, EntranceKind.WAVE_CUT));
        if (u.facility == null) bad.add("no research facility candidate");

        // ---- layer air by Monte Carlo (7.2), depth histogram
        double[][] mc = monteCarlo(g, u, 400_000, seed);
        String[] ln = {"", "L1", "L2", "L3", "L4"};
        double[] target = {0, 0, 0.01, 0.04, 0};
        for (int L = 1; L <= 4; L++) {
            out("  %s: rock %.2f km3, air %.4f%% (karst %.3f%%, non-karst %.3f%%), water share of air %.0f%%", ln[L], mc[L][0] / 1e9,
                    100 * mc[L][1] / Math.max(1, mc[L][0]), 100 * mc[L][3] / Math.max(1, mc[L][2]), 100 * (mc[L][1] - mc[L][3]) / Math.max(1, mc[L][0] - mc[L][2]),
                    100 * mc[L][4] / Math.max(1, mc[L][1]));
        }
        if (mc[2][3] / Math.max(1, mc[2][2]) > target[2] * 1.5) bad.add("layer 2 karst air over 1.5 % of the 1 % target");
        int l1 = layer1Breaks(g, u);
        out("  layer 1 (no caves): %d non-throat shapes reach it", l1);
        if (l1 > 0) bad.add(l1 + " shapes in layer 1");
        depthPng(new File(OUT, tag + "_depth.png"), mc[5], mc[6]);

        // ---- protection (12.2): every shape against a fresh protection (roads, towns, home), the facility separately
        GeoProtect p0 = new GeoProtect(s, roads, towns, h);
        int viol = 0;
        int facOwner = u.facility == null ? -1 : u.facility.id(), facLink = u.facility == null ? -1 : u.facility.link();
        Map<String, Integer> why = new TreeMap<>();
        for (int i = 0; i < u.shapes.size(); i++) {
            Shape sh = u.shapes.get(i);
            if (sh.bounds()[1] < -1e5) continue;
            Cave c = u.caves.get(sh.owner());
            if (sh instanceof Pillar) continue;
            String v = p0.violation(sh, c.layer);
            if (v == null && sh.owner() != facOwner && sh.owner() != facLink && p.facilityHit(sh)) v = "facility";
            if (v != null && c.kind == CaveKind.BUILT && c.label.startsWith("mine")) v = p0.violation(sh, 3);
            if (v != null) { viol++; why.merge(v.split(" ")[0] + " " + c.kind, 1, Integer::sum); if (viol <= 5) out("    violation: cave %d %s %s", c.id, c.kind, v); }
        }
        int entBad = 0;
        double nearRoad = 1e9, nearTown = 1e9, nearHome = 1e9;
        for (Entrance e : u.entrances) {
            if (e.kind() == EntranceKind.FACILITY) continue;
            double rd = p0.roadDistance(e.x(), e.z()), td = p0.townDistance(e.x(), e.z()), hd = p0.homeDistance(e.x(), e.z());
            nearRoad = Math.min(nearRoad, rd);
            nearTown = Math.min(nearTown, td);
            nearHome = Math.min(nearHome, hd);
            if (!p0.entranceAllowed(e.kind(), e.x(), e.z())) entBad++;
        }
        out("  protection: %d shape violations %s, %d entrances break a rule; nearest entrance to a road %.0f m, a town %.0f m, the home %.0f m",
                viol, why, entBad, nearRoad, nearTown, nearHome);
        if (viol > 0) bad.add(viol + " shapes break protection");
        if (entBad > 0) bad.add(entBad + " entrances break protection");

        // ---- facility (19.5)
        if (u.facility != null) {
            var fc = u.facility;
            StringBuilder fs = new StringBuilder();
            for (FacilityPart fp : fc.parts()) fs.append(' ').append(fp.role());
            Place lab = null, portal = null;
            for (FacilityPart fp : fc.parts()) {
                if (fp.role().equals("laboratory")) lab = u.places.get(fp.place());
                if (fp.role().equals("hidden_entrance")) portal = u.places.get(fp.place());
            }
            double cover = lab == null ? 0 : s.heightAt(lab.x(), lab.z()) - (lab.y() + 12);
            double sr = fc.serviceRoad().length < 6 ? 0 : Math.hypot(fc.serviceRoad()[3] - fc.serviceRoad()[0], fc.serviceRoad()[5] - fc.serviceRoad()[2]);
            out("  facility at (%.0f, %.0f) in %s: parts%s; lab cover %.0f m; portal to road %.0f m, town %.0f m, home %.0f m; service road %.0f m at %.1f%%; rock passage %s",
                    fc.x(), fc.z(), fc.host() == null ? "-" : fc.host().id, fs, cover,
                    portal == null ? -1 : p0.roadDistance(portal.x(), portal.z()), lab == null ? -1 : p0.townDistance(lab.x(), lab.z()), lab == null ? -1 : p0.homeDistance(lab.x(), lab.z()),
                    sr, 100 * fc.serviceGrade(), fc.link() >= 0 ? "yes" : "no");
            if (cover < 60) bad.add("facility lab cover " + Math.round(cover) + " m");
            if (lab != null && p0.townDistance(lab.x(), lab.z()) < 3000) bad.add("facility within 3 km of a town");
        }

        // ---- connectivity (7.4)
        int[] comp = components(u);
        Map<Integer, Integer> compSize = new HashMap<>();
        for (Cave c : u.caves) if (!c.label.equals("removed")) compSize.merge(comp[c.id], 1, Integer::sum);
        int sysCon = 0, sysLinked = 0, sysEnt = 0, gLinked = 0, karstN = 0, fracN = 0, karstCon = 0, fracCon = 0, karstEnt = 0, fracEnt = 0;
        BitSet hasEnt = new BitSet();
        for (Entrance e : u.entrances) if (e.connected()) hasEnt.set(e.target());
        BitSet compEnt = new BitSet();
        for (Cave c : u.caves) if (hasEnt.get(c.id)) compEnt.set(comp[c.id]);
        for (Cave c : u.caves) {
            if (c.system()) {
                boolean con = false, lk = false;
                for (CaveLink l : u.links) {
                    if (l.a() == c.id && l.kind().equals("connector")) con = true;
                    if ((l.a() == c.id || l.b() == c.id) && l.kind().equals("karst_link")) lk = true;
                }
                boolean en = compEnt.get(comp[c.id]);
                if (c.kind == CaveKind.KARST_SYSTEM) { karstN++; if (con) karstCon++; if (en) karstEnt++; } else { fracN++; if (con) fracCon++; if (en) fracEnt++; }
                if (lk) sysLinked++;
            }
            if (c.kind == CaveKind.GIANT) for (CaveLink l : u.links) if (l.b() == c.id) { gLinked++; break; }
        }
        int largest = compSize.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        out("  connectivity: karst with connector %d/%d (%.0f%%, target 60), fracture %d/%d (%.0f%%, target 20); karst linked to a neighbour %d; giants linked %d/%d (target 50%%);",
                karstCon, karstN, pct(karstCon, karstN), fracCon, fracN, pct(fracCon, fracN), sysLinked, gLinked, giants);
        out("    reachable from the surface: karst %d/%d (%.0f%%, target 40), fracture %d/%d (%.0f%%, target 15); %d links; largest network %d caves",
                karstEnt, karstN, pct(karstEnt, karstN), fracEnt, fracN, pct(fracEnt, fracN), u.links.size(), largest);

        // ---- routes (19.3)
        List<BitSet> in = new ArrayList<>();
        StringBuilder rs = new StringBuilder();
        int trapsAll = 0;
        for (Kit k : UndergroundPlan.KITS) {
            BitSet ri = UndergroundRoutes.reachIn(u, k);
            in.add(ri);
            BitSet tr = UndergroundRoutes.traps(u, k);
            trapsAll += tr.cardinality();
            rs.append(String.format(Locale.ROOT, " %s in %d traps %d;", k.name(), ri.cardinality(), tr.cardinality()));
            if (tr.cardinality() > 0) {
                int t = tr.nextSetBit(0);
                Place q = u.places.get(t);
                out("    trap with %s: place %d %s at (%.0f, %.0f, %.0f) cave %d", k.name(), t, q.kind(), q.x(), q.y(), q.z(), q.cave());
            }
        }
        out("  routes:%s", rs);
        {
            // where the widest kit stops: steps leaving its reach towards places the step graph alone would reach
            BitSet full = in.get(in.size() - 1);
            Kit top = UndergroundPlan.KITS.get(UndergroundPlan.KITS.size() - 1);
            Map<String, Integer> stop = new TreeMap<>();
            for (Step st : u.steps) for (int side = 0; side < 2; side++) {
                int from = side == 0 ? st.a() : st.b(), to = side == 0 ? st.b() : st.a();
                if (!full.get(from) || full.get(to)) continue;
                Need nd = side == 0 ? st.ab() : st.ba();
                String stopWhy = nd == null ? "none" : nd.dive() ? "dive" : nd.rope() > top.rope() ? "rope>" + top.rope() : nd.anchors() > top.anchors() ? "anchors" : "other";
                stop.merge(st.kind().name().toLowerCase(Locale.ROOT) + ":" + stopWhy, 1, Integer::sum);
            }
            out("    widest kit stops at: %s", stop);
        }
        if (trapsAll > 0) bad.add(trapsAll + " trap places (in, not out)");
        String cut = cutCheck(g, u, seed);
        if (cut != null) bad.add(cut);
        StringBuilder gs = new StringBuilder();
        int giantReach = 0;
        for (Cave c : u.caves) if (c.kind == CaveKind.GIANT || c.kind == CaveKind.CANYON) {
            int k = UndergroundRoutes.leastKitIn(u, c.places.get(0), in);
            if (c.kind == CaveKind.GIANT && k >= 0) giantReach++;
            gs.append(String.format(Locale.ROOT, " %s %s depth %.0f m kit %s;", c.kind == CaveKind.GIANT ? c.label : "canyon", c.host == null ? "-" : c.host.id,
                    g.hs(c.x, c.z) - u.places.get(c.places.get(0)).y(), k < 0 ? "none" : UndergroundPlan.KITS.get(k).name()));
        }
        out("  deep targets:%s", gs);
        if (giantReach < 1) bad.add("no giant cave reachable from the surface");
        int[] kitHist = new int[UndergroundPlan.KITS.size() + 1];
        for (Cave c : u.caves) if (c.system()) {
            int best = -1;
            for (int pl : c.places) { int k = UndergroundRoutes.leastKitIn(u, pl, in); if (k >= 0 && (best < 0 || k < best)) best = k; }
            kitHist[best < 0 ? kitHist.length - 1 : best]++;
        }
        out("  systems by least kit to reach any place: %s (last = not reachable)", Arrays.toString(kitHist));
        int walkSys = 0, sysN = 0;
        for (int i = 0; i + 1 < kitHist.length; i++) walkSys += kitHist[i];
        for (int v : kitHist) sysN += v;
        // linked to an entrance is not walkable: a sump on the way needs diving (V3+); the walkable share is the honest figure
        out("  systems walkable in from the surface (no diving): %d/%d (%.0f%%)", walkSys, sysN, pct(walkSys, sysN));
        rangeNote("percent of systems walkable in", (int) Math.round(pct(walkSys, sysN)), 15, 60);
        // systems by state (C3 per-state caves): depth below the ground, water table below the ground, dry places
        {
            Map<FederalStateId, double[]> st = new TreeMap<>();
            double nearDry = 1e9, nearAny = 1e9;
            for (Cave c : u.caves) if (c.system() || c.kind == CaveKind.GIANT) {
                FederalStateId sid = map.stateAt(c.x, c.z);
                if (sid == null) continue;
                GeologyModel.Column col = g.column(c.x, c.z);
                int dry = 0;
                for (int pl : c.places) if (u.places.get(pl).y() + 1 >= c.waterY) dry++;
                double[] a = st.computeIfAbsent(sid, q -> new double[6]);
                a[0]++; a[1] += col.h - c.y; a[2] += col.h - c.waterY; a[3] += c.places.isEmpty() ? 0 : (double) dry / c.places.size();
                if (dry * 2 >= Math.max(1, c.places.size())) a[4]++;
                if (c.label.contains("vadose")) a[5]++;
                double hd = p.homeDistance(c.x, c.z);
                nearAny = Math.min(nearAny, hd);
                if (dry * 2 >= Math.max(1, c.places.size())) nearDry = Math.min(nearDry, hd);
            }
            out("  systems by state (n, mean depth below ground, water table below ground, dry share of places, mostly dry, vadose):");
            for (var e : st.entrySet()) {
                double[] a = e.getValue();
                out("    %-14s n=%3.0f depth %4.0f m, water table %4.0f m, dry places %3.0f%%, mostly dry %2.0f, vadose %2.0f", e.getKey(), a[0], a[1] / a[0], a[2] / a[0], 100 * a[3] / a[0], a[4], a[5]);
            }
            out("  nearest system to the home %.0f m, nearest mostly dry one %.0f m", nearAny, nearDry);
            // the plan forms by state (21.10): strike S1, branchwork S2, maze S3, small S4, conduit S5
            Map<FederalStateId, Map<String, Integer>> fm = new TreeMap<>();
            for (Cave c : u.caves) if (c.system()) {
                FederalStateId sid = map.stateAt(c.x, c.z);
                if (sid == null) continue;
                String f = "plain";
                for (String q : new String[]{"strike", "branchwork", "maze", "small", "conduit"}) if (c.label.endsWith(q) || c.label.contains(q + " ")) f = q;
                fm.computeIfAbsent(sid, q -> new TreeMap<>()).merge(f, 1, Integer::sum);
            }
            out("  plan forms by state: %s", fm);
        }
        // the start dry cave (21.6): a mostly dry natural system within 3 km of the home that a light reaches from the surface
        {
            BitSet lit = UndergroundRoutes.reachIn(u, UndergroundPlan.KITS.get(1));
            double best = 1e9;
            int bestId = -1, bestIn = 0;
            for (Cave c : u.caves) {
                if (!c.system() && c.kind != CaveKind.GIANT) continue;
                int dry = 0, inLit = 0;
                for (int pl : c.places) {
                    if (u.places.get(pl).y() + 1 >= c.waterY) dry++;
                    if (lit.get(pl)) inLit++;
                }
                if (inLit == 0 || dry * 2 < Math.max(1, c.places.size())) continue;
                double hd = p.homeDistance(c.x, c.z);
                if (hd < best) { best = hd; bestId = c.id; bestIn = inLit; }
            }
            if (bestId < 0) out("  start dry cave: none reached with a light");
            else {
                Cave c = u.caves.get(bestId);
                out("  start dry cave: cave %d %s (%s) %.0f m from the home, %d of %d places reached with a light, %.0f m of passage%s",
                        bestId, c.kind, c.label, best, bestIn, c.places.size(), c.passageLength, u.startCave == bestId ? "" : " (planner's start cave " + u.startCave + ")");
            }
            if (best > 3000) bad.add("no light-only dry cave within 3 km of the home");
        }
        int entUnreach = 0;
        for (Entrance e : u.entrances) if (e.connected() && e.place() >= 0 && UndergroundRoutes.leastKitIn(u, e.place(), in) < 0) entUnreach++;
        if (entUnreach > 0) bad.add(entUnreach + " connected entrances whose rim is not a surface place");

        // ---- pitches, platforms and anchors (19.2)
        int pitchN = 0, platBad = 0, anchorBad = 0, anchorWeak = 0, noAnchor = 0;
        double maxH = 0;
        Map<String, Integer> fx = new TreeMap<>();
        for (Pitch pc : u.pitches) {
            pitchN++;
            maxH = Math.max(maxH, pc.height());
            fx.merge(pc.fixture().name().toLowerCase(Locale.ROOT) + (pc.slopeAlt() >= 0 ? "+slope" : ""), 1, Integer::sum);
            for (int i = 0; i < pc.rigs().size(); i++) {
                Place q = u.places.get(pc.rigs().get(i));
                String why2 = standable(u, s, q, pc);
                if (why2 != null) {
                    platBad++;
                    if (platBad <= 6) {
                        out("    platform: pitch %d rig %d %s %s at (%.1f, %.1f, %.1f) cave %d %s", pc.id(), i, q.kind(), why2, q.x(), q.y(), q.z(), pc.owner(), u.caves.get(pc.owner()).kind);
                        // the shape under the platform, to tell a later shape from the pitch's own
                        for (int si : u.shapesNear(q.x(), q.z())) if (u.shapes.get(si).inside(q.x(), q.y() - 0.6, q.z()))
                            out("      under it: shape %d %s of cave %d, y %.0f..%.0f, x %.0f..%.0f", si, u.shapes.get(si).getClass().getSimpleName(), u.shapes.get(si).owner(), u.shapes.get(si).bounds()[1], u.shapes.get(si).bounds()[4], u.shapes.get(si).bounds()[0], u.shapes.get(si).bounds()[3]);
                    }
                }
                if (pc.rigs().get(i) == pc.bottom()) continue;
                int[] a = i < pc.anchorsAt().size() ? pc.anchorsAt().get(i) : new int[0];
                if (a.length < 2) { noAnchor++; continue; }
                for (int ai : a) {
                    Anchor an = u.anchors.get(ai);
                    if (u.openAt(an.x(), an.y(), an.z()) || an.rock() == null || an.rock().soil) {
                        anchorBad++;
                        if (anchorBad <= 4) out("    anchor: pitch %d rig %d %s at (%.1f, %.1f, %.1f) %s, place y %.1f ground %.1f, pit r %.1f", pc.id(), i, q.kind(), an.x(), an.y(), an.z(),
                                u.openAt(an.x(), an.y(), an.z()) ? "open" : an.rock() == null ? "no rock" : an.rock().id, q.y(), s.heightAt(an.x(), an.z()), pc.rh());
                    }
                    else if (!an.good()) anchorWeak++;
                }
            }
        }
        out("  pitches %d (max %.0f m) %s; anchors %d: %d not in rock, %d in weak rock, %d rigs without; %d rig platforms not standable",
                pitchN, maxH, fx, u.anchors.size(), anchorBad, anchorWeak, noAnchor, platBad);
        if (platBad > pitchN / 50 + 2) bad.add(platBad + " rope platforms not standable");
        if (anchorBad > 0 || noAnchor > 0) bad.add((anchorBad + noAnchor) + " rope anchors missing or not in rock");

        // ---- successions (19.7)
        int succ = 0, first = 0, noExit = 0;
        EnumMap<UndergroundPlan.Env, Integer> envs = new EnumMap<>(UndergroundPlan.Env.class);
        for (Succession c : u.successions) {
            envs.merge(c.env(), 1, Integer::sum);
            if (c.succession()) succ++;
            if (c.firstStart()) first++;
            boolean rel = false;
            for (Exit e : c.exits()) if (e.reliable()) rel = true;
            if (c.succession() && !rel) noExit++;
        }
        out("  succession candidates %d %s: %d suit succession (reliable exit, hazard <= 3), %d a first start; %d marked without a reliable exit",
                u.successions.size(), envs, succ, first, noExit);
        for (int i = 0; i < Math.min(6, u.successions.size()); i++) {
            Succession c = u.successions.get(i);
            Exit e = c.exits().isEmpty() ? null : c.exits().get(0);
            out("    #%d %s at (%.0f, %.0f, %.0f) %s hazard %d kit %s exit %s bg %s", c.id(), c.env(), c.x(), c.y(), c.z(), c.host(), c.hazard(),
                    c.kit() == null ? "none" : c.kit().name(), e == null ? "none" : String.format(Locale.ROOT, "%.0f m, up %.0f m, %d pitches, needs %s", e.length(), e.climbUp(), e.pitches(), e.need().text()), c.backgrounds());
        }
        if (noExit > 0) bad.add(noExit + " succession candidates without a reliable exit");
        Map<String, Integer> docs = new TreeMap<>();
        for (UndergroundPlan.DocumentSite d : u.documents) docs.merge(d.kind().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        out("  document points %d %s", u.documents.size(), docs);

        // ---- determinism: a second build, and queries in two orders
        GeologyModel g2 = new GeologyModel(seed, s, map, -256);
        UndergroundPlan u2 = UndergroundPlanner.build(g2, new GeoProtect(s, roads, towns, h));
        String d1 = digest(u), d2 = digest(u2);
        boolean qOk = queryOrder(g, g2, u, u2, seed);
        out("  determinism: digest %s / %s %s; queries in shuffled order %s", d1.substring(0, 12), d2.substring(0, 12), d1.equals(d2) ? "same" : "DIFFERENT", qOk ? "same" : "DIFFERENT");
        if (!d1.equals(d2)) bad.add("two builds differ");
        if (!qOk) bad.add("query order changes answers");

        // ---- sampling cost a chunk (U2 input) and the other heights
        double chunkMs = chunkCost(g, u, seed);
        out("  sampling a full 16x16 chunk column (rock + cave at every y): %.1f ms", chunkMs);
        for (int minY : new int[]{-192, -64}) {
            long a = System.nanoTime();
            GeologyModel gm = new GeologyModel(seed, s, map, minY);
            UndergroundPlan um = UndergroundPlanner.build(gm, new GeoProtect(s, roads, towns, h));
            EnumMap<CaveKind, Integer> km = new EnumMap<>(CaveKind.class);
            for (Cave c : um.caves) if (!c.label.equals("removed")) km.merge(c.kind, 1, Integer::sum);
            out("  minY %d: %s, entrances %d, %d ms", minY, km, um.entrances.size(), (System.nanoTime() - a) / 1_000_000);
        }

        // ---- pictures
        geologyPng(new File(OUT, tag + "_geology.png"), g, s, map, roads, towns, h);
        mapPng(new File(OUT, tag + "_map.png"), g, s, u, roads, towns, h);
        graphPng(new File(OUT, tag + "_graph.png"), s, u, comp, in);
        sections(tag, g, s, u, p0);

        String line = String.format(Locale.ROOT, "systems %d+%d, giants %d, canyons %d, entrances %d (%d connected), mines %d, facility %s, successions %d, plan %d ms",
                karst, frac, giants, canyons, u.entrances.size(), u.entrances.stream().filter(Entrance::connected).count(), u.mines.size(), u.facility != null ? "yes" : "no",
                u.successions.size(), geoMs + planMs);
        out("  " + line);
        return new Result(line, bad);
    }

    // ------------------------------------------------------------------ helpers

    static double pct(int a, int b) { return b == 0 ? 0 : 100.0 * a / b; }

    static int count(EnumMap<EntranceKind, int[]> m, EntranceKind k) { int[] c = m.get(k); return c == null ? 0 : c[0] + c[1]; }

    static void range(List<String> bad, String what, int n, int lo, int hi) {
        if (n < lo || n > hi) bad.add(what + " " + n + " outside " + lo + "-" + hi);
    }

    static void rangeNote(String what, int n, int lo, int hi) {
        if (n < lo || n > hi) out("  NOTE %s %d outside the design's %d-%d", what, n, lo, hi);
    }

    static String faultText(GeologyModel g) {
        StringBuilder sb = new StringBuilder();
        for (GeologyModel.Fault f : g.faultList()) sb.append(String.format(Locale.ROOT, "%s%.1f km/%.0f m", sb.length() == 0 ? "" : ", ", f.length() / 1000, f.throwM()));
        return sb.toString();
    }

    /**
     * Monte Carlo over land columns: per layer {rock m3, air m3, karst rock m3, karst air m3, water m3}, then rows 5/6:
     * the air sample counts by depth below Hs (5 m bins) and by layer (row 6 holds the layer of each bin's majority).
     */
    static double[][] monteCarlo(GeologyModel g, UndergroundPlan u, int n, long seed) {
        TerrainPlanSurface s = g.surface;
        double size = s.gridN * TerrainPlanSurface.CELL;
        SplittableRandom rnd = new SplittableRandom(seed ^ 0x5EED);
        double[][] r = new double[7][];
        for (int i = 0; i < 5; i++) r[i] = new double[5];
        r[5] = new double[4 * 120];
        r[6] = new double[0];
        int land = 0, tries = 0;
        // the volume one sample stands for: land area x column height / samples (each sample a random land column, random y)
        double area = 0;
        List<double[]> samples = new ArrayList<>();
        while (land < n && tries < n * 4) {
            tries++;
            double x = s.gridOrigin + rnd.nextDouble() * size, z = s.gridOrigin + rnd.nextDouble() * size;
            if (s.waterAt(x, z) || !g.map.isLand(x, z)) continue;
            land++;
            samples.add(new double[]{x, z, rnd.nextDouble()});
        }
        area = size * size * land / tries;
        for (double[] sm : samples) {
            Column c = g.column(sm[0], sm[1]);
            double y = g.minY + sm[2] * (c.h - g.minY);
            double vol = area * (c.h - g.minY) / land;
            int L = Math.max(1, Math.min(4, g.layer(c, y)));
            if (g.layer(c, y) == 5) L = 4;
            boolean kar = g.karst(c, y) >= 0.5;
            r[L][0] += vol;
            if (kar) r[L][2] += vol;
            int cave = u.caveAt(sm[0], y, sm[1]);
            if (cave >= 0) {
                r[L][1] += vol;
                if (kar) r[L][3] += vol;
                if (u.waterAt(sm[0], y, sm[1])) r[L][4] += vol;
                int bin = (int) Math.min(119, Math.max(0, (c.hs - y) / 5));
                r[5][(L - 1) * 120 + bin] += vol;
            }
        }
        return r;
    }

    /** Non-throat shapes whose roof comes above the bottom of layer 1 (Hs - T1) anywhere on a 3x3 footprint. */
    static int layer1Breaks(GeologyModel g, UndergroundPlan u) {
        int n = 0;
        for (int i = 0; i < u.shapes.size(); i++) {
            if (u.throats.get(i)) continue;
            Shape sh = u.shapes.get(i);
            if (sh instanceof Pillar) continue;
            double[] b = sh.bounds();
            if (b[1] < -1e5) continue;
            boolean br = false;
            for (int a = 0; a <= 2 && !br; a++) for (int c = 0; c <= 2 && !br; c++) {
                double x = b[0] + (b[3] - b[0]) * a / 2, z = b[2] + (b[5] - b[2]) * c / 2;
                double top = b[4];
                if (sh instanceof Ellipsoid e) { top = e.roofAt(x, z); if (Double.isNaN(top)) continue; }
                if (sh instanceof Capsule cp && !(a == 1 && c == 1) && !cp.vertical()) continue;
                Column col = g.column(x, z);
                if (top > col.hs - col.t1 + 0.5) br = true;
            }
            if (br) { n++; if (n <= 3) out("    layer 1: shape %d %s of cave %d %s", i, sh.getClass().getSimpleName(), sh.owner(), u.caves.get(sh.owner()).kind); }
        }
        return n;
    }

    /** Why a rigging place is not standable, or null: open at +0.5 and +1.7, solid at -0.6, not over the shaft. */
    static String standable(UndergroundPlan u, TerrainPlanSurface s, Place q, Pitch pc) {
        Platform pf = q.platform();
        if (pf == null) return "no platform";
        if (q.kind() == PlaceKind.SURFACE || Math.abs(q.y() - s.heightAt(q.x(), q.z())) < 1.5 && !u.openAt(q.x(), q.y() + 0.5, q.z())) {
            double gy = s.heightAt(q.x(), q.z());
            if (Math.abs(gy - q.y()) > 2) return String.format(Locale.ROOT, "%.1f m off the ground", q.y() - gy);
            if (u.openAt(q.x(), gy - 0.6, q.z()) && Math.hypot(q.x() - pc.ax(), q.z() - pc.az()) <= pc.rh() + 0.2) return "over the shaft";
            return null;
        }
        if (!u.openAt(q.x(), q.y() + 0.5, q.z())) return "solid at +0.5";
        if (!u.openAt(q.x(), q.y() + 1.7, q.z())) return "solid at +1.7";
        if (u.openAt(q.x(), q.y() - 0.6, q.z())) {
            if (q.id() != pc.bottom() || u.caveAt(q.x(), q.y() - 3, q.z()) >= 0) return "open below";
        }
        return null;
    }

    static int[] components(UndergroundPlan u) {
        int[] p = new int[u.caves.size()];
        for (int i = 0; i < p.length; i++) p[i] = i;
        for (CaveLink l : u.links) union(p, l.a(), l.b());
        for (int i = 0; i < p.length; i++) p[i] = find(p, i);
        return p;
    }

    static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }

    static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

    static String digest(UndergroundPlan u) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (Shape sh : u.shapes) sb.append(sh).append('\n');
        for (Entrance e : u.entrances) sb.append(e).append('\n');
        for (Place q : u.places) sb.append(q).append('\n');
        for (UndergroundPlan.Step st : u.steps) sb.append(st).append('\n');
        for (OreBody o : u.ores) sb.append(o).append('\n');
        for (Anchor a : u.anchors) sb.append(a).append('\n');
        for (Succession c : u.successions) sb.append(c.id()).append(c.env()).append(c.place()).append(c.hazard()).append(c.exits().size()).append('\n');
        sb.append(u.facility == null ? "-" : u.facility.x() + "," + u.facility.z());
        byte[] d = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hx = new StringBuilder();
        for (byte b : d) hx.append(String.format("%02x", b));
        return hx.toString();
    }

    /** The same points asked of plan 1 in order and of plan 2 shuffled (a query never depends on what was asked before). */
    static boolean queryOrder(GeologyModel g1, GeologyModel g2, UndergroundPlan u1, UndergroundPlan u2, long seed) {
        TerrainPlanSurface s = g1.surface;
        double size = s.gridN * TerrainPlanSurface.CELL;
        SplittableRandom rnd = new SplittableRandom(seed ^ 0xC0FFEE);
        int n = 20000;
        double[][] pts = new double[n][];
        for (int i = 0; i < n; i++) {
            double x = s.gridOrigin + rnd.nextDouble() * size, z = s.gridOrigin + rnd.nextDouble() * size;
            pts[i] = new double[]{x, g1.minY + rnd.nextDouble() * (s.heightAt(x, z) - g1.minY), z};
        }
        // bias half of them to cave shapes so the cave answers are exercised
        for (int i = 0; i < n / 2 && !u1.shapes.isEmpty(); i++) {
            double[] b = u1.shapes.get(rnd.nextInt(u1.shapes.size())).bounds();
            if (b[1] < -1e5) continue;
            pts[i] = new double[]{b[0] + rnd.nextDouble() * (b[3] - b[0]), b[1] + rnd.nextDouble() * (b[4] - b[1]), b[2] + rnd.nextDouble() * (b[5] - b[2])};
        }
        String[] a1 = new String[n], a2 = new String[n];
        for (int i = 0; i < n; i++) a1[i] = g1.rockAt(pts[i][0], pts[i][1], pts[i][2]) + ":" + u1.caveAt(pts[i][0], pts[i][1], pts[i][2]);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = n - 1 - i;
        for (int i = n - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = order[i]; order[i] = order[j]; order[j] = t; }
        for (int i : order) a2[i] = g2.rockAt(pts[i][0], pts[i][1], pts[i][2]) + ":" + u2.caveAt(pts[i][0], pts[i][1], pts[i][2]);
        return Arrays.equals(a1, a2);
    }

    /** ms to answer rock and cave at every block of a 16x16 column from minY to the ground (average of 12 land chunks). */
    /**
     * U2: the chunk cut (NoiseChunkUndergroundMixin: one shapesInBox per chunk, caveAmong per block) answers exactly
     * caveAt, block for block, in 24 chunks through cave shapes (every 2nd block of the column, the cave's height range);
     * prints the cut's cost per chunk. Null = same everywhere.
     */
    static String cutCheck(GeologyModel g, UndergroundPlan u, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed ^ 0x52C);
        long diff = 0, cells = 0, inCave = 0, ns = 0;
        int chunks = 0;
        for (int tries = 0; chunks < 24 && tries < 2000 && !u.shapes.isEmpty(); tries++) {
            UndergroundPlan.Shape sh = u.shapes.get(rnd.nextInt(u.shapes.size()));
            double[] b = sh.bounds();
            double cx = b[0] + rnd.nextDouble() * (b[3] - b[0]), cz = b[2] + rnd.nextDouble() * (b[5] - b[2]);
            double x0 = Math.floor(cx / 16) * 16, z0 = Math.floor(cz / 16) * 16;
            int y0 = (int) Math.floor(b[1]) - 2, y1 = (int) Math.ceil(b[4]) + 2;
            long a = System.nanoTime();
            int[] ids = u.shapesInBox(x0, z0, x0 + 16, z0 + 16);
            int[] got = new int[16 * 16 * ((y1 - y0) / 2 + 1)];
            int k = 0;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++)
                for (int y = y0; y <= y1; y += 2) got[k++] = u.caveAmong(ids, x0 + dx + 0.5, y + 0.5, z0 + dz + 0.5);
            ns += System.nanoTime() - a;
            k = 0;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++)
                for (int y = y0; y <= y1; y += 2) {
                    int want = u.caveAt(x0 + dx + 0.5, y + 0.5, z0 + dz + 0.5);
                    if (want != got[k++]) diff++;
                    if (want >= 0) inCave++;
                    cells++;
                }
            chunks++;
        }
        out("  U2 cut: %d chunks, %d cells (%d in caves), %d differ from caveAt; cut %.2f ms per chunk column range",
                chunks, cells, inCave, diff, chunks == 0 ? 0 : ns / 1e6 / chunks);
        return diff == 0 ? null : diff + " cut cells differ from caveAt";
    }

    static double chunkCost(GeologyModel g, UndergroundPlan u, long seed) {
        TerrainPlanSurface s = g.surface;
        SplittableRandom rnd = new SplittableRandom(seed ^ 0xC4);
        double size = s.gridN * TerrainPlanSurface.CELL;
        int done = 0;
        long sink = 0, ns = 0;
        while (done < 12) {
            double x0 = Math.floor((s.gridOrigin + rnd.nextDouble() * size) / 16) * 16, z0 = Math.floor((s.gridOrigin + rnd.nextDouble() * size) / 16) * 16;
            if (s.waterAt(x0, z0) || !g.map.isLand(x0, z0)) continue;
            long a = System.nanoTime();
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                Column c = g.column(x0 + dx + 0.5, z0 + dz + 0.5);
                for (int y = g.minY; y < c.h; y++) {
                    RockType r = g.rockAt(c, y + 0.5);
                    sink += r == null ? 0 : r.ordinal();
                    sink += u.caveAt(x0 + dx + 0.5, y + 0.5, z0 + dz + 0.5);
                }
            }
            ns += System.nanoTime() - a;
            done++;
        }
        if (sink == Long.MIN_VALUE) System.out.println();
        return ns / 1e6 / done;
    }

    // ------------------------------------------------------------------ pictures

    static final double MPP = 32;

    static int px(TerrainPlanSurface s, double x) { return (int) ((x - s.gridOrigin) / MPP); }

    static Color rgb(int c) { return new Color(c); }

    static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f)), g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f)), b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static BufferedImage base(GeologyModel g, TerrainPlanSurface s, FederalMap map, double dim) {
        int w = (int) (s.gridN * TerrainPlanSurface.CELL / MPP);
        BufferedImage im = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        FederalStateId[] prev = new FederalStateId[w];
        for (int j = 0; j < w; j++) {
            FederalStateId left = null;
            for (int i = 0; i < w; i++) {
                double x = s.gridOrigin + (i + 0.5) * MPP, z = s.gridOrigin + (j + 0.5) * MPP;
                int c;
                FederalStateId st = map.stateAt(x, z);
                if (s.waterAt(x, z) || !map.isLand(x, z)) c = 0x3A5F8A;
                else {
                    RockType b = g.bedrock(g.column(x, z));
                    double hs = g.hs(x + MPP, z + MPP) - g.hs(x - MPP, z - MPP);
                    c = shade(b == null ? 0x808080 : b.rgb, (0.85 + Math.max(-0.25, Math.min(0.25, hs / 60))) * dim);
                }
                if (st != null && ((left != null && left != st) || (prev[i] != null && prev[i] != st))) c = 0x101010;
                im.setRGB(i, j, c);
                left = st;
                prev[i] = st;
            }
        }
        return im;
    }

    static void works(Graphics2D gr, TerrainPlanSurface s, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) {
        gr.setColor(new Color(250, 250, 250, 200));
        gr.setStroke(new BasicStroke(1.2f));
        for (RoadDesign r : roads) {
            double[] prev = null;
            for (double d = 0; d <= r.length; d += 64) {
                double[] q = r.point(Math.min(d, r.length), 0);
                if (prev != null) gr.draw(new Line2D.Double(px(s, prev[0]), px(s, prev[1]), px(s, q[0]), px(s, q[1])));
                prev = q;
            }
        }
        gr.setColor(new Color(255, 230, 60));
        for (TownPlan t : towns) {
            double[][] cs = {{t.limit[0], t.limit[2]}, {t.limit[1], t.limit[2]}, {t.limit[1], t.limit[3]}, {t.limit[0], t.limit[3]}};
            for (int i = 0; i < 4; i++) {
                double[] a = t.world(cs[i][0], cs[i][1]), b = t.world(cs[(i + 1) % 4][0], cs[(i + 1) % 4][1]);
                gr.draw(new Line2D.Double(px(s, a[0]), px(s, a[1]), px(s, b[0]), px(s, b[1])));
            }
        }
        if (h != null) {
            gr.setColor(Color.RED);
            gr.fill(new Ellipse2D.Double(px(s, h.x()) - 4, px(s, h.z()) - 4, 8, 8));
            gr.draw(new Ellipse2D.Double(px(s, h.x()) - 300 / MPP, px(s, h.z()) - 300 / MPP, 600 / MPP, 600 / MPP));
        }
    }

    static void legend(Graphics2D gr, int x, int y, String[] names, int[] colors) {
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < names.length; i++) {
            gr.setColor(new Color(colors[i]));
            gr.fillRect(x, y + i * 16, 12, 12);
            gr.setColor(Color.WHITE);
            gr.drawString(names[i], x + 16, y + i * 16 + 11);
        }
    }

    static void geologyPng(File f, GeologyModel g, TerrainPlanSurface s, FederalMap map, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) throws Exception {
        BufferedImage im = base(g, s, map, 1);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setColor(new Color(200, 20, 20));
        gr.setStroke(new BasicStroke(2f));
        for (GeologyModel.Fault fl : g.faultList()) gr.draw(new Line2D.Double(px(s, fl.ax()), px(s, fl.az()), px(s, fl.bx()), px(s, fl.bz())));
        works(gr, s, roads, towns, h);
        RockType[] rs = RockType.values();
        String[] names = new String[rs.length];
        int[] cols = new int[rs.length];
        for (int i = 0; i < rs.length; i++) { names[i] = rs[i].id; cols[i] = rs[i].rgb; }
        gr.setColor(new Color(0, 0, 0, 160));
        gr.fillRect(6, 6, 110, rs.length * 16 + 8);
        legend(gr, 10, 10, names, cols);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    static final Map<CaveKind, Integer> CAVE_RGB = Map.of(CaveKind.SMALL_KARST, 0x9AD0FF, CaveKind.FISSURE, 0xC0C0C0, CaveKind.KARST_SYSTEM, 0x2EE6FF,
            CaveKind.FRACTURE_SYSTEM, 0xFFB347, CaveKind.CONNECTOR, 0x7CFF7C, CaveKind.GIANT, 0xFF40FF, CaveKind.CANYON, 0xFF4040,
            CaveKind.SINKHOLE, 0xFFFFFF, CaveKind.BUILT, 0xFFE040);

    static void mapPng(File f, GeologyModel g, TerrainPlanSurface s, UndergroundPlan u, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) throws Exception {
        BufferedImage im = base(g, s, g.map, 0.45);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        works(gr, s, roads, towns, h);
        for (int i = 0; i < u.shapes.size(); i++) {
            Shape sh = u.shapes.get(i);
            double[] b = sh.bounds();
            if (b[1] < -1e5 || sh instanceof Pillar) continue;
            Cave c = u.caves.get(sh.owner());
            gr.setColor(rgb(CAVE_RGB.get(c.kind)));
            if (sh instanceof Capsule cp) {
                gr.setStroke(new BasicStroke((float) Math.max(1, 2 * cp.rh() / MPP)));
                gr.draw(new Line2D.Double(px(s, cp.ax()), px(s, cp.az()), px(s, cp.bx()), px(s, cp.bz())));
            } else {
                double w = Math.max(2, (b[3] - b[0]) / MPP), hh = Math.max(2, (b[5] - b[2]) / MPP);
                gr.fill(new Ellipse2D.Double(px(s, (b[0] + b[3]) / 2) - w / 2, px(s, (b[2] + b[5]) / 2) - hh / 2, w, hh));
            }
        }
        for (OreBody o : u.ores) {
            gr.setColor(new Color(255, 120, 200, 140));
            double w = Math.max(3, 2 * o.rx() / MPP);
            gr.fill(new Ellipse2D.Double(px(s, o.x()) - w / 2, px(s, o.z()) - w / 2, w, w));
        }
        for (Entrance e : u.entrances) {
            int col = switch (e.kind()) {
                case COLLAPSE_PIT -> 0xB0B0B0;
                case COVER_SINKHOLE -> 0x80E0A0;
                case GREAT_SINKHOLE -> 0xFFFFFF;
                case FISSURE -> 0xE0A060;
                case SHAFT -> 0x60A0FF;
                case HILLSIDE_MOUTH -> 0x00FFC0;
                case DRY_SPRING -> 0x4080FF;
                case MINE_ADIT, MINE_SHAFT -> 0xFFE040;
                case FACILITY -> 0xFF2020;
                case SPRING_MOUTH -> 0x40C0FF;
                case KARST_WINDOW -> 0xA0FFFF;
                case CLIFF_MOUTH -> 0xFF80C0;
                case WAVE_CUT -> 0xC0A0FF;
            };
            gr.setColor(e.connected() ? rgb(col) : new Color(col & 0x7F7F7F));
            double r = e.kind() == EntranceKind.GREAT_SINKHOLE ? Math.max(5, 2 * e.radius() / MPP) : e.connected() ? 4 : 2;
            gr.fill(new Ellipse2D.Double(px(s, e.x()) - r / 2, px(s, e.z()) - r / 2, r, r));
        }
        gr.setColor(Color.WHITE);
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (Cave c : u.caves) if (c.kind == CaveKind.GIANT) gr.drawString(c.label, px(s, c.x) + 8, px(s, c.z));
        for (Succession c : u.successions) {
            gr.setColor(c.firstStart() ? Color.GREEN : c.succession() ? Color.ORANGE : Color.GRAY);
            gr.draw(new Ellipse2D.Double(px(s, c.x()) - 5, px(s, c.z()) - 5, 10, 10));
        }
        if (u.facility != null) {
            gr.setColor(Color.RED);
            gr.setStroke(new BasicStroke(2));
            gr.draw(new Ellipse2D.Double(px(s, u.facility.x()) - 12, px(s, u.facility.z()) - 12, 24, 24));
            gr.drawString("facility", px(s, u.facility.x()) + 14, px(s, u.facility.z()) + 4);
        }
        String[] names = {"karst system", "fracture system", "connector (L2)", "small cave / fissure", "giant cave", "canyon", "mine / facility", "ore body", "succession: first / succ / no"};
        int[] cols = {0x2EE6FF, 0xFFB347, 0x7CFF7C, 0x9AD0FF, 0xFF40FF, 0xFF4040, 0xFFE040, 0xFF78C8, 0x00FF00};
        gr.setColor(new Color(0, 0, 0, 170));
        gr.fillRect(6, 6, 200, names.length * 16 + 8);
        legend(gr, 10, 10, names, cols);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    static void graphPng(File f, TerrainPlanSurface s, UndergroundPlan u, int[] comp, List<BitSet> in) throws Exception {
        int w = (int) (s.gridN * TerrainPlanSurface.CELL / MPP);
        BufferedImage im = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setColor(new Color(16, 18, 22));
        gr.fillRect(0, 0, w, w);
        BitSet any = in.get(in.size() - 1);
        gr.setStroke(new BasicStroke(1.5f));
        for (CaveLink l : u.links) {
            Cave a = u.caves.get(l.a()), b = u.caves.get(l.b());
            gr.setColor(switch (l.kind()) {
                case "connector" -> new Color(120, 255, 120);
                case "karst_link" -> new Color(46, 230, 255);
                case "system_giant" -> new Color(255, 64, 255);
                case "system_canyon" -> new Color(255, 64, 64);
                default -> new Color(255, 224, 64);
            });
            gr.draw(new Line2D.Double(px(s, a.x), px(s, a.z), px(s, b.x), px(s, b.z)));
        }
        gr.setColor(new Color(255, 255, 255, 110));
        for (Entrance e : u.entrances) if (e.connected()) {
            Cave c = u.caves.get(e.target());
            gr.draw(new Line2D.Double(px(s, e.x()), px(s, e.z()), px(s, c.x), px(s, c.z)));
        }
        for (Cave c : u.caves) {
            if (c.label.equals("removed") || c.places.isEmpty()) continue;
            if (c.kind == CaveKind.SMALL_KARST || c.kind == CaveKind.FISSURE) {
                boolean reach = false;
                for (int pl : c.places) if (any.get(pl)) reach = true;
                if (!reach) continue;
            }
            boolean reach = false;
            for (int pl : c.places) if (any.get(pl)) reach = true;
            double r = switch (c.kind) { case GIANT -> 14; case CANYON -> 11; case KARST_SYSTEM, FRACTURE_SYSTEM -> 7; case BUILT -> 8; default -> 4; };
            gr.setColor(rgb(CAVE_RGB.get(c.kind)));
            Ellipse2D.Double el = new Ellipse2D.Double(px(s, c.x) - r / 2, px(s, c.z) - r / 2, r, r);
            if (reach) gr.fill(el); else gr.draw(el);
        }
        String[] names = {"filled: reachable from the surface", "ring: closed (no route)", "connector link", "karst link", "system - giant", "system - canyon", "entrance (white line)"};
        int[] cols = {0xFFFFFF, 0x888888, 0x78FF78, 0x2EE6FF, 0xFF40FF, 0xFF4040, 0xFFFFFF};
        legend(gr, 10, 10, names, cols);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    /** Three E-W and three N-S sections (8 m a pixel across, 1 m a pixel up) and two 1 m detail sections. */
    static void sections(String tag, GeologyModel g, TerrainPlanSurface s, UndergroundPlan u, GeoProtect p) throws Exception {
        List<Cave> gi = new ArrayList<>();
        for (Cave c : u.caves) if (c.kind == CaveKind.GIANT) gi.add(c);
        double size = s.gridN * TerrainPlanSurface.CELL;
        double[] at = {-size / 5, 0, size / 5};
        for (int i = 0; i < 3; i++) {
            double z = i < gi.size() ? gi.get(i).z : at[i];
            section(new File(OUT, tag + "_section_ew_" + (i + 1) + ".png"), g, s, u, s.gridOrigin, z, s.gridOrigin + size, z, 8, String.format(Locale.ROOT, "E-W at z %.0f", z));
            double x = i < gi.size() ? gi.get(gi.size() - 1 - i).x : at[i];
            if (i == 0 && u.facility != null) x = u.facility.x();
            section(new File(OUT, tag + "_section_ns_" + (i + 1) + ".png"), g, s, u, x, s.gridOrigin, x, s.gridOrigin + size, 8, String.format(Locale.ROOT, "N-S at x %.0f", x));
        }
        if (!gi.isEmpty()) {
            Cave c = gi.get(0);
            section(new File(OUT, tag + "_detail_giant.png"), g, s, u, c.x - 600, c.z, c.x + 600, c.z, 1, "detail E-W through " + c.label);
        }
        if (u.facility != null) {
            var fc = u.facility;
            double ux = Math.cos(fc.yaw()), uz = Math.sin(fc.yaw());
            section(new File(OUT, tag + "_detail_facility.png"), g, s, u, fc.x() - ux * 700, fc.z() - uz * 700, fc.x() + ux * 300, fc.z() + uz * 300, 1, "detail along the facility's haul tunnel");
        }
        // the deepest pitch of a karst system (vertical exploration)
        Pitch deep = null;
        for (Pitch pc : u.pitches) if (u.caves.get(pc.owner()).kind != CaveKind.BUILT && (deep == null || pc.height() > deep.height())) deep = pc;
        if (deep != null) section(new File(OUT, tag + "_detail_pitch.png"), g, s, u, deep.ax() - 150, deep.az(), deep.ax() + 150, deep.az(), 0.5,
                String.format(Locale.ROOT, "detail through the deepest natural pitch (%.0f m, %s)", deep.height(), deep.fixture()));
    }

    static void section(File f, GeologyModel g, TerrainPlanSurface s, UndergroundPlan u, double ax, double az, double bx, double bz, double mpp, String title) throws Exception {
        double len = Math.hypot(bx - ax, bz - az);
        int w = (int) Math.ceil(len / mpp);
        double yTop = 320, yBot = g.minY;
        double ypp = mpp >= 8 ? 1 : mpp;
        int hgt = (int) ((yTop - yBot) / ypp);
        BufferedImage im = new BufferedImage(w, hgt + 20, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w; i++) {
            double t = (i + 0.5) / w;
            double x = ax + (bx - ax) * t, z = az + (bz - az) * t;
            boolean land = !s.waterAt(x, z) && g.map.isLand(x, z);
            Column c = g.column(x, z);
            double[] bands = g.bands(c);
            double wl = s.waterAt(x, z) ? Math.max(c.h, 64) : c.h;
            for (int j = 0; j < hgt; j++) {
                double y = yTop - (j + 0.5) * ypp;
                int col;
                if (y > c.h) col = y <= wl ? 0x3A5F8A : 0xE8EEF4;
                else {
                    int cave = u.caveAt(x, y, z);
                    if (cave >= 0) {
                        Cave cv = u.caves.get(cave);
                        col = u.waterAt(x, y, z) ? 0x2050C0 : cv.kind == CaveKind.BUILT ? 0xFFB020 : 0x000000;
                    } else {
                        RockType r = g.rockAt(c, y);
                        col = r == null ? 0x808080 : r.rgb;
                        for (OreBody o : u.ores) {
                            if (Math.abs(o.x() - x) > o.rx() + o.rz() || Math.abs(o.z() - z) > o.rx() + o.rz()) continue;
                            double dx = x - o.x(), dz = z - o.z(), cy = Math.cos(o.yaw()), sy = Math.sin(o.yaw());
                            double lx = dx * cy + dz * sy, lz = -dx * sy + dz * cy, ly = y - o.y();
                            if (lx * lx / (o.rx() * o.rx()) + ly * ly / (o.ry() * o.ry()) + lz * lz / (o.rz() * o.rz()) <= 1) { col = 0xFF60C0; break; }
                        }
                    }
                }
                if (land) for (int b = 1; b <= 4; b++) if (Math.abs(y - bands[b]) < ypp * 0.5 && y < c.h) col = b == 1 ? 0xFFFF00 : b == 2 ? 0x00FF00 : b == 3 ? 0x00C0FF : 0xFF00FF;
                im.setRGB(i, j, col);
            }
        }
        Graphics2D gr = im.createGraphics();
        gr.setColor(Color.BLACK);
        gr.fillRect(0, hgt, w, 20);
        gr.setColor(Color.WHITE);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gr.drawString(String.format(Locale.ROOT, "%s, %.0f m wide, Y %d..%d; lines: yellow L1 bottom, green L2 bottom, cyan L3 bottom, magenta floor keep; black cave air, blue water, orange built, pink ore", title, len, (int) yBot, (int) yTop), 4, hgt + 14);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }

    static void depthPng(File f, double[] bins, double[] unused) throws Exception {
        int bw = 4, nb = 120, w = nb * bw + 80, hgt = 4 * 90 + 40;
        BufferedImage im = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = im.createGraphics();
        gr.setColor(new Color(20, 22, 26));
        gr.fillRect(0, 0, w, hgt);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int[] cols = {0xFFFF00, 0x00FF00, 0x00C0FF, 0xFF40FF};
        String[] names = {"layer 1", "layer 2", "layer 3", "layer 4"};
        for (int L = 0; L < 4; L++) {
            double max = 1;
            for (int b = 0; b < nb; b++) max = Math.max(max, bins[L * nb + b]);
            int y0 = 30 + L * 90 + 80;
            gr.setColor(new Color(cols[L]));
            for (int b = 0; b < nb; b++) {
                int hh = (int) Math.round(75 * bins[L * nb + b] / max);
                gr.fillRect(60 + b * bw, y0 - hh, bw - 1, hh);
            }
            gr.drawString(names[L], 4, y0 - 30);
        }
        gr.setColor(Color.WHITE);
        gr.drawString("cave air volume by depth below Hs (5 m bins, 0-600 m), each layer scaled to its own peak", 60, 16);
        for (int d = 0; d <= 600; d += 100) gr.drawString(d + " m", 60 + d / 5 * bw - 8, hgt - 4);
        gr.dispose();
        ImageIO.write(im, "png", f);
    }
}
