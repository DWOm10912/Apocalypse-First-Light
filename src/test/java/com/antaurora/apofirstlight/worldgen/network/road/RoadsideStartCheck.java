package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;
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
 * Offline check of the roadside start's anchor (RoadsideStart, C3.4; docs/worldgen/main_c3_interface_contract_v1.md 9.1):
 * per seed the works as the server builds them (as ForestHomeStartCheck), then the anchor of POINTS points nearest the
 * home site (the world origin without one) and nearest the origin.
 * <ul>
 *   <li>FAIL: no anchor in a world with state highways; a point not on its road's outer shoulder (the hit is another
 *   road's, or its lateral off the shoulder); its column not paved at the point's height; points not STEP apart or not
 *   facing along the road; the run not clear by RoadsideStart#clear; a second computation different; an empty network
 *   giving an anchor.</li>
 * </ul>
 * <pre>
 *   RoadsideStartCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS; plans from systemPlans)
 * </pre>
 */
public final class RoadsideStartCheck {
    static final int POINTS = 8;

    private RoadsideStartCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        int fails = 0, seeds = 0;
        List<String> summary = new ArrayList<>();
        if (RoadsideStart.find(new RoadWorks(List.of(), (x, z) -> false), 0, 0, 3) != null) {
            System.out.println("  FAIL an empty network gives an anchor");
            fails++;
        }
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
            System.out.println("== seed " + unsigned);
            List<String> bad = new ArrayList<>();
            String line = check(seed, s, Files.readString(planFile), bad);
            System.out.println("  " + line);
            for (String b : bad) System.out.println("  FAIL " + b);
            fails += bad.isEmpty() ? 0 : 1;
            summary.add(unsigned + ": " + line + (bad.isEmpty() ? "" : " FAIL"));
        }
        System.out.println("== summary");
        for (String l : summary) System.out.println("  " + l);
        System.out.println(fails == 0 ? "ROADSIDE START CHECK PASS (" + seeds + " seeds)" : "ROADSIDE START CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        if (fails > 0) System.exit(1);
    }

    private static String check(long seed, TerrainPlanSurface s, String planJson, List<String> bad) {
        RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
        String[] kept = new String[1];
        RoadCounty.Memo memo = new RoadCounty.Memo() {
            @Override public String load() { return kept[0]; }
            @Override public void store(String site) { kept[0] = site; }
        };
        // as RoadNetworkRuntime#get builds it
        RoadNetworkBuild.Result b = RoadNetworkBuild.build(planJson, terrain, memo);
        RoadCounty.Home home = b.home();
        String built = TownPlan.append(RoadStructures.attach(b.json(), terrain).json(), b.towns());
        if (home != null) built = RoadCounty.append(built, home);
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        RoadWorks works = new RoadWorks(RoadNetworkJson.designs(built), water, new TownWorks(b.towns()), home == null ? Driveway.EMPTY : Driveway.of(home), RoadWorks.Ground.of(s));
        RoadWorks.Ground ground = RoadWorks.Ground.of(s);
        long states = works.roads().stream().filter(RoadsideStart::state).count();
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%d state highways", states));
        double[][] refs = home == null ? new double[][]{{0, 0}} : new double[][]{{home.x(), home.z()}, {0, 0}};
        for (double[] ref : refs) {
            long t0 = System.nanoTime();
            RoadsideStart.Anchor a = RoadsideStart.find(works, ref[0], ref[1], POINTS);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String where = String.format(Locale.ROOT, "from %.0f %.0f", ref[0], ref[1]);
            if (a == null) {
                if (states > 0) bad.add("no anchor " + where);
                out.append("; ").append(where).append(": none");
                continue;
            }
            if (!a.equals(RoadsideStart.find(works, ref[0], ref[1], POINTS))) bad.add("a second computation differs " + where);
            RoadDesign d = works.road(a.road());
            if (d == null || !RoadsideStart.state(d)) { bad.add("anchor road " + a.road() + " not a state highway"); continue; }
            if (a.points().size() != POINTS) bad.add(a.points().size() + " points " + where);
            double lo = d.section.lane, hi = d.section.lane + d.section.outer;
            for (int i = 0; i < a.points().size(); i++) {
                RoadsideStart.Point p = a.points().get(i);
                RoadWorks.Hit h = works.hit(p.x(), p.z(), d.section.edge + 1);
                if (h == null || h.road() != d) bad.add("point " + i + " " + where + " is " + (h == null ? "off the roads" : h.road().id + "'s"));
                else if (h.v() < lo + 0.3 || h.v() > hi - 0.3 || Math.signum(h.u()) != a.side())
                    bad.add(String.format(Locale.ROOT, "point %d %s at lateral %.2f, off the outer shoulder", i, where, h.u()));
                RoadWorks.Column c = works.touches((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4)
                        ? works.column((int) Math.floor(p.x()), (int) Math.floor(p.z()), ground) : null;
                if (c == null || c.kind() != RoadWorks.Kind.PAVED || p.y() < c.paveLo() - 0.02 || p.y() > c.paveHi() + 0.02)
                    bad.add(String.format(Locale.ROOT, "point %d %s: y %.2f, column %s", i, where, p.y(),
                            c == null ? "off the works" : c.kind() + String.format(Locale.ROOT, " %.2f..%.2f", c.paveLo(), c.paveHi())));
                if (i > 0) {
                    RoadsideStart.Point q = a.points().get(i - 1);
                    double dx = p.x() - q.x(), dz = p.z() - q.z(), l = Math.hypot(dx, dz);
                    if (Math.abs(l - RoadsideStart.STEP) > 0.2) bad.add(String.format(Locale.ROOT, "points %d-%d %s %.2f m apart", i - 1, i, where, l));
                    // facing the way the points run (the traffic's way): yaw 0 faces +z, 90 faces -x
                    double fx = -Math.sin(Math.toRadians(p.yaw())), fz = Math.cos(Math.toRadians(p.yaw()));
                    if ((fx * dx + fz * dz) / l < 0.98) bad.add(String.format(Locale.ROOT, "point %d %s yaw %.0f off the road", i, where, p.yaw()));
                }
            }
            double sa = a.side() > 0 ? a.s() : a.s() - (POINTS - 1) * RoadsideStart.STEP;
            if (!RoadsideStart.clear(works, d, sa, sa + (POINTS - 1) * RoadsideStart.STEP, a.side())) bad.add("the run " + where + " not clear");
            RoadsideStart.Point p0 = a.points().get(0);
            out.append(String.format(Locale.ROOT, "; %s: %s s %.0f side %+d, point 0 %.1f %.2f %.1f yaw %.0f, %.0f m away (%d ms)", where, a.road(), a.s(),
                    a.side(), p0.x(), p0.y(), p0.z(), p0.yaw(), Math.hypot(p0.x() - ref[0], p0.z() - ref[1]), ms));
        }
        return out.toString();
    }
}
