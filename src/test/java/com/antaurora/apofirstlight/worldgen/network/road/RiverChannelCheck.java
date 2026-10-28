package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.vegetation.VegetationPlan;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.HydroQuery;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverBed;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverChannels;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;
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
 * River / lake V1 R2 (docs/worldgen/river_lake_v1_design.md 13) on the survey seeds' real plans and road works, as
 * RiverChannels binds them: the wider channel's build time, its widths, pools, deep holes and fish water per km, and
 * where it holds the planned width.
 * <ul>
 *   <li>FAIL: a river vertex on a road's formation, in a town, by the driveway or the forest home not at the planned width; the channel or
 *   its graded banks (RiverBed's section) cutting the forest home's graded ring below the home's ground; a build over 10 s.</li>
 *   <li>The wetted bed's plants (vegetation v7, VegetationPlan.Area#riverAllows): the share of the bed under water where a
 *   wetland plant may still stand (shallow, at the margin; before v7: all of it).</li>
 * </ul>
 * <pre>
 *   RiverChannelCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS; plans from systemPlans)
 * </pre>
 */
public final class RiverChannelCheck {
    private RiverChannelCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        int fails = 0, seeds = 0;
        for (String f : bins) {
            String name = Path.of(f).getFileName().toString();
            String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
            Path planFile = plans.get(unsigned);
            if (planFile == null || !Files.isRegularFile(Path.of(f))) { System.out.println("== seed " + unsigned + ": no plan (run systemPlans)"); continue; }
            long seed = Long.parseUnsignedLong(unsigned);
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null || s.rivers == null) { System.out.println("== seed " + unsigned + ": STALE terrain plan or no rivers " + f); continue; }
            seeds++;
            System.out.println("== seed " + unsigned);
            List<String> bad = new ArrayList<>();
            check(seed, s, Files.readString(planFile), bad);
            for (String b : bad) System.out.println("  FAIL " + b);
            fails += bad.isEmpty() ? 0 : 1;
        }
        System.out.println(fails == 0 ? "RIVER CHANNEL CHECK PASS (" + seeds + " seeds)" : "RIVER CHANNEL CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        if (fails > 0) System.exit(1);
    }

    private static void check(long seed, TerrainPlanSurface s, String planJson, List<String> bad) {
        RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
        String[] kept = new String[1];
        RoadCounty.Memo memo = new RoadCounty.Memo() {
            @Override public String load() { return kept[0]; }
            @Override public void store(String site) { kept[0] = site; }
        };
        RoadNetworkBuild.Result b = RoadNetworkBuild.build(planJson, terrain, memo);
        RoadCounty.Home home = b.home();
        String built = TownPlan.append(RoadStructures.attach(b.json(), terrain).json(), b.towns());
        if (home != null) built = RoadCounty.append(built, home);
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        RoadWorks works = new RoadWorks(RoadNetworkJson.designs(built), water, new TownWorks(b.towns()),
                home == null ? Driveway.EMPTY : Driveway.of(home), RoadWorks.Ground.of(s));
        RiverNetwork r = s.rivers;
        long t0 = System.nanoTime();
        HydroQuery.Narrow narrow = RiverChannels.narrow(works, home);
        HydroQuery h = HydroQuery.of(r, narrow);
        double ms = (System.nanoTime() - t0) / 1e6;
        if (ms > 10_000) bad.add(String.format(Locale.ROOT, "the channel took %.0f ms", ms));

        int held = 0, wrong = 0, past = 0, pools = 0, holes = 0, fish = 0, segs = 0;
        double length = 0, wideSum = 0, plannedSum = 0, maxExtra = 0;
        for (int line = 0; line < r.lines(); line++) {
            boolean in = false;
            double holeMax = 0;
            for (int i = r.lineStart(line); i < r.lineEnd(line); i++) {
                double w = h.leftAt(i) + h.rightAt(i), pw = r.vertexWidth(i);
                segs++;
                length += 2;
                wideSum += w;
                plannedSum += pw;
                double extra = Math.max(h.leftAt(i), h.rightAt(i)) - pw / 2;
                maxExtra = Math.max(maxExtra, extra);
                if (extra > RiverNetwork.BANK_REACH) past++;
                if (h.widenAt(i) < 0.5) held++;
                if (narrow.near(r.vertexX(i), r.vertexZ(i), 0) && h.widenAt(i) > 1e-6) wrong++;
                double pl = h.poolAtVertex(i);
                if (pl >= HydroQuery.FISH_POOL) fish++;
                if (!in && pl > 0.7) { in = true; pools++; holeMax = 0; }
                if (in) holeMax = Math.max(holeMax, h.holeAt(i));
                if (in && pl < 0.3) { in = false; if (holeMax > 2.4) holes++; }
            }
        }
        if (wrong > 0) bad.add(wrong + " river vertices on a road's formation, in a town, by the driveway or the forest home wider than planned");
        System.out.printf(Locale.ROOT, "  channel built in %.0f ms: %.0f km of river, mean width %.1f m (planned %.1f), widest extra %.1f m over the planned half width%n",
                ms, length / 1000, wideSum / segs, plannedSum / segs, maxExtra);
        System.out.printf(Locale.ROOT, "  held near roads, towns, the driveway and the home: %.1f %% of the length; past the network's bank reach: %.1f %%%n",
                100.0 * held / segs, 100.0 * past / segs);
        System.out.printf(Locale.ROOT, "  %.1f pools a km (%d), %.0f %% of them deep holes; fish water %.0f %% of the length%n",
                pools / (length / 1000), pools, pools == 0 ? 0 : 100.0 * holes / pools, 100.0 * fish / segs);

        // the wetted bed's plants: cross-sections every 10 m, a sample a metre, the bed as RiverBed lays it on the plan's ground
        {
            HydroQuery.Point p = new HydroQuery.Point();
            RiverBed.Out out = new RiverBed.Out();
            int wet = 0, margin = 0;
            for (int line = 0; line < r.lines(); line++)
                for (int i = r.lineStart(line); i + 1 < r.lineEnd(line); i += 5) {
                    double vx = r.vertexX(i), vz = r.vertexZ(i), dx = r.vertexX(i + 1) - vx, dz = r.vertexZ(i + 1) - vz, l = Math.hypot(dx, dz);
                    if (l < 1e-9) continue;
                    double nx = -dz / l, nz = dx / l, reach = Math.max(h.leftAt(i), h.rightAt(i)) + 1;
                    for (double o = -reach; o <= reach; o += 1) {
                        double x = vx + nx * o, z = vz + nz * o;
                        if (!h.near(x, z, 0, p)) continue;
                        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                        RiverBed.plan(p, bx, bz, (int) Math.round(s.heightAt(bx, bz)) - 1, out);
                        double under = p.eta - (out.top + 1);
                        if (under < VegetationPlan.RIVER_DRY) continue;
                        wet++;
                        boolean may = under <= VegetationPlan.RIVER_WET_DEPTH && p.edge >= -VegetationPlan.RIVER_MARGIN;
                        if (may) margin++;
                    }
                }
            System.out.printf(Locale.ROOT, "  wetted bed: %d samples, a wetland plant may stand on %.1f %% of them (water <= %.1f m within %.0f m of the edge); before v7 on all%n",
                    wet, wet == 0 ? 0 : 100.0 * margin / wet, VegetationPlan.RIVER_WET_DEPTH, VegetationPlan.RIVER_MARGIN);
        }

        if (home != null) {
            RotatedRect plot = ForestHomeLayout.plot(home.reservation()), keep = plot.grow(ForestHomeLayout.GRADE_MARGIN);
            HydroQuery.Point p = new HydroQuery.Point();
            RiverBed.Out out = new RiverBed.Out();
            int into = 0, cut = 0;
            double nearest = Double.MAX_VALUE;
            double[] bb = keep.bounds();
            for (double x = bb[0]; x <= bb[2]; x += 1)
                for (double z = bb[1]; z <= bb[3]; z += 1) {
                    if (keep.distance(x, z) > 0) continue;
                    if (h.near(x, z, HydroQuery.MAX_REACH, p)) {
                        nearest = Math.min(nearest, p.edge);
                        if (p.edge < RiverBed.REACH) {
                            into++;
                            // the channel's section at the ring's graded ground (the home's top block at the plot,
                            // easing to the plan's ground at the ring's edge): cut below it?
                            double f = Math.min(1, Math.max(0, plot.distance(x, z) / ForestHomeLayout.GRADE_MARGIN));
                            int top = (int) Math.round((home.groundY() - 1) * (1 - f) + (s.heightAt((int) Math.floor(x), (int) Math.floor(z)) - 1) * f);
                            RiverBed.plan(p, (int) Math.floor(x), (int) Math.floor(z), top, out);
                            if (out.top < top) cut++;
                        }
                    }
                }
            System.out.printf(Locale.ROOT, "  forest home at %d %d: nearest channel edge %s%n", home.x(), home.z(),
                    nearest == Double.MAX_VALUE ? "beyond " + HydroQuery.MAX_REACH + " m of the graded ring" : String.format(Locale.ROOT, "%.1f m", nearest));
            if (into > 0) System.out.printf(Locale.ROOT, "  %d cells of the graded ring within the channel's bank reach, %d cut below its graded ground (home Y %d)%n", into, cut, home.groundY());
            if (cut > 0) bad.add(cut + " cells of the forest home's graded ring cut below their graded ground by the channel");
        }
    }
}
