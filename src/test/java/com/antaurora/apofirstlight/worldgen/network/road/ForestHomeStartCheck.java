package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeRuntime;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeSavedData;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
 * Offline check of the forest-home start on the county road G3's site (Main, M4 round 2; docs/worldgen/
 * forest_home_b_m4_placement.md 9): per seed the network as the server builds it (RoadNetworkRuntime#get without the
 * cache), then the reservation, the driveway's farm gate and mailbox (ForestHomeRuntime#gate / #mailbox) and the three
 * start spots (ForestHomeLayout#startSpot).
 * <ul>
 *   <li>FAIL: no home; a fixture missing; a gate or mailbox cell inside the house's graded ring, or the gate over
 *   pavement (its bottom cells' columns must be the driveway's gravel or turf), the mailbox not on turf; a start spot
 *   off the plot; the facing more than 45 degrees off the anchor's; the fixtures not the same on a second computation.</li>
 *   <li>WARN: the gate's four bottom cells or the mailbox's neighbours not level (C's pads leave cells within 0.3 m of the paving edge as they are).</li>
 * </ul>
 * <pre>
 *   ForestHomeStartCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS; plans from systemPlans)
 * </pre>
 */
public final class ForestHomeStartCheck {
    private ForestHomeStartCheck() {}

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
            System.out.println("== seed " + unsigned);
            List<String> bad = new ArrayList<>(), warn = new ArrayList<>();
            String line = check(seed, s, Files.readString(planFile), bad, warn);
            System.out.println("  " + line);
            for (String w : warn) System.out.println("  WARN " + w);
            for (String b : bad) System.out.println("  FAIL " + b);
            fails += bad.isEmpty() ? 0 : 1;
            summary.add(String.format(Locale.ROOT, "%s: %s%s%s", unsigned, line, warn.isEmpty() ? "" : " (" + warn.size() + " warn)", bad.isEmpty() ? "" : " FAIL"));
        }
        System.out.println("== summary");
        for (String l : summary) System.out.println("  " + l);
        System.out.println(fails == 0 ? "FOREST HOME START CHECK PASS (" + seeds + " seeds)" : "FOREST HOME START CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        if (fails > 0) System.exit(1);
    }

    private static String check(long seed, TerrainPlanSurface s, String planJson, List<String> bad, List<String> warn) {
        RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
        String[] kept = new String[1];
        RoadCounty.Memo memo = new RoadCounty.Memo() {
            @Override public String load() { return kept[0]; }
            @Override public void store(String site) { kept[0] = site; }
        };
        // as RoadNetworkRuntime#get builds it: the network, the box culverts, the towns, the home, the works
        RoadNetworkBuild.Result b = RoadNetworkBuild.build(planJson, terrain, memo);
        RoadCounty.Home home = b.home();
        if (home == null) { bad.add("no home"); return "no home"; }
        String built = RoadCounty.append(TownPlan.append(RoadStructures.attach(b.json(), terrain).json(), b.towns()), home);
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        RoadWorks works = new RoadWorks(RoadNetworkJson.designs(built), water, new TownWorks(b.towns()), Driveway.of(home));
        RoadNetworkRuntime.Network n = new RoadNetworkRuntime.Network(planJson, "check", built, works, s, home);
        StructureReservation r = home.reservation();
        RotatedRect keep = ForestHomeLayout.plot(r).grow(ForestHomeLayout.GRADE_MARGIN);
        ForestHomeSavedData.Fixture gate = ForestHomeRuntime.gate(n, home), mailbox = ForestHomeRuntime.mailbox(n, home);
        if (gate == null) bad.add("no gate");
        if (mailbox == null) bad.add("no mailbox");
        ForestHomeSavedData.Fixture gate2 = ForestHomeRuntime.gate(n, home), mailbox2 = ForestHomeRuntime.mailbox(n, home);
        if (gate != null && !gate.equals(gate2) || mailbox != null && !mailbox.equals(mailbox2)) bad.add("fixtures differ on a second computation");
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "site %d %d r%d ground %d", home.x(), home.z(), home.rotation(), home.groundY()));
        if (gate != null) {
            double[] g = home.gate();
            Direction cw = gate.facing().getClockWise();
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int c = 0; c < 4; c++) {
                int x = gate.origin().getX() + c * cw.getStepX(), z = gate.origin().getZ() + c * cw.getStepZ();
                RoadWorks.Column col = column(n, x, z);
                int free = ForestHomeRuntime.firstFree(n, x, z, g[1]);
                lo = Math.min(lo, free);
                hi = Math.max(hi, free);
                if (col == null || col.kind() != RoadWorks.Kind.DRIVE && col.kind() != RoadWorks.Kind.TURF)
                    bad.add("gate column " + c + " at " + x + " " + z + " is " + (col == null ? "off the works" : col.kind()));
                if (keep.distance(x + 0.5, z + 0.5) <= 0) bad.add("gate cell " + x + " " + z + " in the house's graded ring");
            }
            if (hi > lo) warn.add("gate bottom cells not level: first free " + lo + ".." + hi);
            double off = angle(g[3], g[4], gate.facing());
            if (off > 45.01) bad.add(String.format(Locale.ROOT, "gate facing %s %.0f deg off the anchor", gate.facing().getName(), off));
            out.append(String.format(Locale.ROOT, "; gate %s %s (%.0f deg off the driveway, anchor y %.2f)", gate.origin().toShortString(),
                    gate.facing().getName(), off, g[1]));
        }
        if (mailbox != null) {
            double[] m = home.mailbox();
            BlockPos p = mailbox.origin();
            RoadWorks.Column col = column(n, p.getX(), p.getZ());
            if (col == null || col.kind() != RoadWorks.Kind.TURF) bad.add("mailbox column is " + (col == null ? "off the works" : col.kind()));
            if (keep.distance(p.getX() + 0.5, p.getZ() + 0.5) <= 0) bad.add("mailbox in the house's graded ring");
            StringBuilder uneven = new StringBuilder();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                RoadWorks.Column c = column(n, p.getX() + d.getStepX(), p.getZ() + d.getStepZ());
                if (c == null || c.kind() != RoadWorks.Kind.TURF || c.top() + 1 == p.getY()) continue;
                uneven.append(' ').append(d.getName()).append(d == mailbox.facing() ? " (road side)" : "").append(' ').append(c.top() + 1);
            }
            if (!uneven.isEmpty()) warn.add("mailbox neighbours not level with its Y " + p.getY() + ":" + uneven);
            double off = angle(m[3], m[4], mailbox.facing());
            if (off > 45.01) bad.add(String.format(Locale.ROOT, "mailbox facing %s %.0f deg off the anchor", mailbox.facing().getName(), off));
            out.append(String.format(Locale.ROOT, "; mailbox %s %s (anchor y %.2f)", p.toShortString(), mailbox.facing().getName(), m[1]));
        }
        RotatedRect plot = ForestHomeLayout.plot(r);
        for (int i = 0; i < ForestHomeLayout.START_SPOTS; i++) {
            double[] sp = ForestHomeLayout.startSpot(r, i);
            if (plot.distance(sp[0], sp[1]) > 0) bad.add("start spot " + i + " off the plot");
        }
        out.append(String.format(Locale.ROOT, "; spot 0 %.1f %d %.1f yaw %.0f", ForestHomeLayout.startSpot(r, 0)[0], home.groundY(),
                ForestHomeLayout.startSpot(r, 0)[1], ForestHomeLayout.facingHouse(r.rotation())));
        return out.toString();
    }

    private static RoadWorks.Column column(RoadNetworkRuntime.Network n, int x, int z) {
        return n.works().touches(x >> 4, z >> 4) ? n.works().column(x, z, RoadWorks.Ground.of(n.plan())) : null;
    }

    /** Degrees between a direction (x, z) and a block facing. */
    private static double angle(double x, double z, Direction d) {
        double l = Math.hypot(x, z);
        if (l == 0) return 0;
        double c = (x * d.getStepX() + z * d.getStepZ()) / l;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }
}
