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
 * Offline check of the rock kept under a road's works (RoadWorks#ROCK, RoadWorksRef#rock / #springBlocked, 2026-10-19;
 * docs/worldgen/federal_highways_c3.md): per seed the works as the server builds them (as RoadsideStartCheck), then every
 * road at STATION m stations, across its whole works width every LATERAL m.
 * <ul>
 *   <li>FAIL: a column the works reach (RoadWorks#densitySurface not NaN) without rock; away from tunnels and towns, rock
 *   not from ROCK under the works' surface up; a spring at the works' surface there not blocked; a column with no works
 *   within SPRING_CLEAR getting rock; a road's own cut roadside template (CrossSection#roadside with the ground where
 *   the works start blending) dipping outward by more than DIP beyond the ditch (a hollow, the rock cut's blend).</li>
 *   <li>Report: cut walls (the roadside WALL m or more under the ground where the blend starts), the rock face columns
 *   (RoadWorks.ROCK_CUT) at mid-roadside.</li>
 * </ul>
 * Probes (-Dafl.probe="x y z;x y z"): whether a spring there is blocked, and the rock of its column (the reported cut).
 * <pre>
 *   RoadRockCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS; plans from systemPlans)
 * </pre>
 */
public final class RoadRockCheck {
    static final double STATION = 25, LATERAL = 2, WALL = 4, DIP = 0.5;

    private RoadRockCheck() {}

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
            List<String> bad = new ArrayList<>();
            String line = check(seed, s, Files.readString(planFile), bad);
            System.out.println("  " + line);
            for (int i = 0; i < Math.min(bad.size(), 20); i++) System.out.println("  FAIL " + bad.get(i));
            if (bad.size() > 20) System.out.println("  ... " + (bad.size() - 20) + " more");
            fails += bad.isEmpty() ? 0 : 1;
            summary.add(unsigned + ": " + line + (bad.isEmpty() ? "" : " FAIL " + bad.size()));
        }
        System.out.println("== summary");
        for (String l : summary) System.out.println("  " + l);
        System.out.println(fails == 0 ? "ROAD ROCK CHECK PASS (" + seeds + " seeds)" : "ROAD ROCK CHECK FAIL (" + fails + " of " + seeds + " seeds)");
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
        RoadWorksRef ref = new RoadWorksRef();
        ref.set(works);
        long cols = 0, cut = 0, own = 0, off = 0;
        double deepest = 0;
        for (RoadDesign d : works.roads()) {
            for (double st = 0; st <= d.length; st += STATION) {
                for (double u = -RoadWorks.REACH; u <= RoadWorks.REACH; u += LATERAL) {
                    double[] p = d.point(st, u);
                    int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                    double g = s.heightAt(x, z), fs = works.densitySurface(x + 0.5, z + 0.5, g);
                    double[] r = ref.rock(x, z, s);
                    if (Double.isNaN(fs)) {
                        if (r != null && TunnelWorks.rock(works, x, z) == null && works.towns().rock(x, z) == null)
                            bad.add(String.format(Locale.ROOT, "%s s %.0f: rock at %d %d off the works", d.id, st, x, z));
                        off++;
                        continue;
                    }
                    cols++;
                    if (g - fs > 0.5) { cut++; deepest = Math.max(deepest, g - fs); }
                    if (r == null) { bad.add(String.format(Locale.ROOT, "%s s %.0f: no rock at %d %d in the works", d.id, st, x, z)); continue; }
                    if (TunnelWorks.rock(works, x, z) != null || works.towns().rock(x, z) != null) continue;
                    own++;
                    if (Math.abs(r[0] - (fs - RoadWorks.ROCK)) > 1e-9 || r[1] != Double.POSITIVE_INFINITY)
                        bad.add(String.format(Locale.ROOT, "%s s %.0f: rock %.2f..%.2f at %d %d, surface %.2f", d.id, st, r[0], r[1], x, z, fs));
                    if (!ref.springBlocked(x, (int) Math.floor(fs), z, s))
                        bad.add(String.format(Locale.ROOT, "%s s %.0f: a spring at %d %.0f %d not blocked", d.id, st, x, fs, z));
                }
            }
        }
        // report: a cut whose roadside has not reached the ground where the blend starts (MAX_HALF_WIDTH - BLEND) ends in a
        // wall over the last BLEND m (sh_hills 3421 -1700 in the user's world 2026-10-19: 35 m)
        int walls = 0, stations = 0;
        double worst = 0;
        String worstAt = "-";
        for (RoadDesign d : works.roads()) {
            for (double st = 0; st <= d.length; st += STATION) {
                for (int side = -1; side <= 1; side += 2) {
                    stations++;
                    double[] a = d.point(st, side * (RoadWorks.MAX_HALF_WIDTH - RoadWorks.BLEND)), e = d.point(st, side * RoadWorks.MAX_HALF_WIDTH);
                    double fa = works.densitySurface(a[0], a[1], s.heightAt(a[0], a[1])), ge = s.heightAt(e[0], e[1]);
                    if (Double.isNaN(fa)) continue;
                    double wall = ge - fa;
                    if (wall > WALL) walls++;
                    if (wall > worst) { worst = wall; worstAt = String.format(Locale.ROOT, "%s s %.0f %.0f %.0f", d.id, st, e[0], e[1]); }
                }
            }
        }
        // the rock cut (CrossSection#rockCut): the roadside never dips going outward in a cut (a hollow would hold water),
        // and the rock face columns (RoadWorks.ROCK_CUT) on the sampled columns
        RoadWorks.Ground ground = RoadWorks.Ground.of(s);
        int dips = 0, faces = 0, sampled = 0;
        double worstDip = 0;
        String dipAt = "-";
        for (RoadDesign d : works.roads()) {
            CrossSection sec = d.section;
            for (double st = 0; st <= d.length; st += STATION) {
                for (int side = -1; side <= 1; side += 2) {
                    // this road's own roadside template over the plan's ground (other roads' works, pads and ramps aside)
                    double prev = Double.NaN, peak = Double.NEGATIVE_INFINITY;
                    double h0 = d.formationY(Math.max(0, Math.min(d.length, st)), side * sec.edge);
                    double[] e = d.point(st, side * (RoadWorks.MAX_HALF_WIDTH - RoadWorks.BLEND));
                    double need = s.heightAt(e[0], e[1]) - h0;
                    for (double v = sec.edge + 1; v <= RoadWorks.MAX_HALF_WIDTH - RoadWorks.BLEND; v += 1) {
                        double[] p = d.point(st, side * v);
                        double g = s.heightAt(p[0], p[1]), f = h0 + sec.roadside(v - sec.edge, g - h0, need);
                        if (f > g - 0.5) { prev = Double.NaN; peak = Double.NEGATIVE_INFINITY; continue; }
                        if (!Double.isNaN(prev) && peak - f > worstDip) { worstDip = peak - f; dipAt = String.format(Locale.ROOT, "%s s %.0f %.0f %.0f", d.id, st, p[0], p[1]); }
                        if (!Double.isNaN(prev) && peak - f > DIP && v > sec.edge + sec.ditchDepth / CrossSection.FORESLOPE + sec.ditchBottom) dips++;
                        peak = Math.max(peak, f);
                        prev = f;
                    }
                }
                double[] p = d.point(st, (sec.edge + RoadWorks.MAX_HALF_WIDTH) / 2);
                int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                if (works.touches(x >> 4, z >> 4)) {
                    sampled++;
                    if ((works.column(x, z, ground).data() & RoadWorks.ROCK_CUT) != 0) faces++;
                }
            }
        }
        if (dips > 0) bad.add(String.format(Locale.ROOT, "%d cut roadside templates dip outward by over %.1f m, deepest %.2f m at %s", dips, DIP, worstDip, dipAt));
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "%d works columns (%d own rock, %d in a cut, deepest cut %.1f m), %d off the works; cut walls over %.0f m at %d of %d roadside stations, highest %.1f m at %s; rock face at %d of %d mid-roadside columns; deepest outward dip %.2f m at %s",
                cols, own, cut, deepest, off, WALL, walls, stations, worst, worstAt, faces, sampled, worstDip, dipAt));
        String probes = System.getProperty("afl.probe", "");
        for (String q : probes.split(";")) {
            if (q.isBlank()) continue;
            String[] v = q.trim().split("\\s+");
            int x = Integer.parseInt(v[0]), y = Integer.parseInt(v[1]), z = Integer.parseInt(v[2]);
            double g = s.heightAt(x, z), fs = works.densitySurface(x + 0.5, z + 0.5, g);
            double[] r = ref.rock(x, z, s);
            RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
            out.append(String.format(Locale.ROOT, "%n  probe %d %d %d: spring blocked %s, ground %.1f, works %.1f, rock from %s, road %s",
                    x, y, z, ref.springBlocked(x, y, z, s), g, fs, r == null ? "-" : String.format(Locale.ROOT, "%.1f", r[0]),
                    h == null ? "-" : String.format(Locale.ROOT, "%s u %.1f", h.road().id, h.u())));
        }
        return out.toString();
    }
}
