package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;
import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

/**
 * C3.3b group 3 offline planning (docs/worldgen/federal_systems_c3_3b.md, 5): the national road network planned again
 * from cached federal C2 terrain plans (the .bin files the dev clients wrote; other checkouts' caches are only read),
 * no world, no chunks. Writes each plan as {@code build/federal-systems/plans/<VERSION>_<seed>_<digest>.json} (the
 * offline checks read that directory) and prints the interstates' corner and join notes, the join angles, the built
 * network's town lines and the first plan's determinism.
 * <pre>
 *   SystemPlans [terrain-plan.bin ...]     (none: the six survey seeds)
 * </pre>
 */
public final class SystemPlans {
    private SystemPlans() {}

    static final String[] DEFAULTS = {
            "build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_4.bin",
            "build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_18151365494840402103.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_10132636411566409605.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_10164326571583364449.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_11823271242869393782.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_16060040187792812410.bin",
    };

    public static void main(String[] args) throws Exception {
        List<String> files = new ArrayList<>(List.of(args.length > 0 ? args : DEFAULTS));
        Path out = Path.of("build/federal-systems/plans");
        Files.createDirectories(out);
        boolean first = true, ok = true;
        for (String f : files) {
            String name = Path.of(f).getFileName().toString();
            String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
            long seed = Long.parseUnsignedLong(unsigned);
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null) { System.out.println("STALE terrain plan " + f); ok = false; continue; }
            FederalMap map = FederalMap.forSeed(seed);
            long t0 = System.nanoTime();
            RoadNetworkPlan plan = RoadNetworkPlan.build(map, new RouteTerrain(s, map));
            long t1 = System.nanoTime();
            String json = plan.toJson(), digest = plan.digest();
            Path file = out.resolve(RoadNetworkPlan.VERSION + "_" + unsigned + "_" + digest + ".json");
            Files.writeString(file, json, StandardCharsets.UTF_8);
            System.out.printf(Locale.ROOT, "== seed %s: planned in %.1f s, %d routes, %d unrouted -> %s%n", unsigned, (t1 - t0) / 1e9, plan.routes().size(),
                    plan.failures().size(), file);
            for (String u : plan.failures()) System.out.println("  UNROUTED " + u);
            if (first) {
                String again = RoadNetworkPlan.build(map, new RouteTerrain(s, map)).digest();
                System.out.println("  determinism: " + (again.equals(digest) ? "identical" : "DIFFERENT " + digest + " / " + again));
                ok &= again.equals(digest);
                first = false;
            }
            double[] f5 = new double[5];
            for (RoadNetworkPlan.Route r : plan.routes()) {
                if (r.roadClass() != RoadClassSpec.INTERSTATE) continue;
                String trace = r.trace() == null ? "" : r.trace();
                int bar = trace.indexOf(" | ");
                System.out.printf(Locale.ROOT, "  %-44s %6.0f m  Rmin %5.0f  grade %4.1f%%%s%n", r.id(), r.curve().length, Math.min(99999, r.design().stats().minRadius()),
                        100 * r.design().stats().maxGrade(), bar >= 0 ? trace.substring(bar) : "");
                if (r.design().stats().minRadius() < r.roadClass().radius) {
                    double worst = 0, at = 0;
                    for (double st = 0; st <= r.curve().length; st += 5) {
                        r.curve().frame(st, f5);
                        if (Math.abs(f5[4]) > worst) { worst = Math.abs(f5[4]); at = st; }
                    }
                    System.out.printf(Locale.ROOT, "    tightest R %.0f at s %.0f of %.0f%n", 1 / worst, at, r.curve().length);
                    double[] cx = r.curve().controlX(), cz = r.curve().controlZ();
                    r.curve().frame(at, f5);
                    StringBuilder sb = new StringBuilder("      control points there (segment m / polyline R):");
                    for (int i = 1; i + 1 < cx.length; i++) {
                        if (Math.hypot(cx[i] - f5[0], cz[i] - f5[1]) > 160) continue;
                        double ax = cx[i] - cx[i - 1], az = cz[i] - cz[i - 1], bx = cx[i + 1] - cx[i], bz = cz[i + 1] - cz[i];
                        double turn = Math.abs(Math.atan2(ax * bz - az * bx, ax * bx + az * bz));
                        sb.append(String.format(Locale.ROOT, " #%d %.0f/%.0f", i, Math.hypot(bx, bz), turn < 1e-9 ? 99999 : 0.5 * (Math.hypot(ax, az) + Math.hypot(bx, bz)) / turn));
                    }
                    System.out.println(sb + " of " + cx.length);
                }
                for (RoadNetworkPlan.Join j : new RoadNetworkPlan.Join[]{r.fromJoin(), r.toJoin()}) {
                    if (j == null) continue;
                    RoadNetworkPlan.Route host = plan.route(j.route());
                    host.curve().frame(j.station(), f5);
                    double[] xs = r.curve().controlX(), zs = r.curve().controlZ();
                    int n = xs.length;
                    double dx = j == r.toJoin() ? xs[n - 1] - xs[n - 2] : xs[1] - xs[0], dz = j == r.toJoin() ? zs[n - 1] - zs[n - 2] : zs[1] - zs[0];
                    double angle = Math.toDegrees(Math.acos(Math.min(1, Math.abs(dx * f5[2] + dz * f5[3]) / Math.hypot(dx, dz))));
                    System.out.printf(Locale.ROOT, "    %s on %s at %.0f m: %.0f deg%s%n", j == r.toJoin() ? "ends" : "starts", j.route(), j.station(), angle,
                            angle < 60 ? "  SHALLOW" : "");
                    if (angle < 60) {
                        // the leg's way off the host, every 500 m back from the join (station on the host, signed offset)
                        StringBuilder sb = new StringBuilder("      off the host:");
                        double len = r.curve().length;
                        for (double back = 500; back <= Math.min(len, 8000); back += 500) {
                            double[] p = r.curve().point(j == r.toJoin() ? len - back : back, 0), l = host.curve().local(p[0], p[1], 8000, 4000);
                            sb.append(l == null ? String.format(Locale.ROOT, " %.0f: -", back) : String.format(Locale.ROOT, " %.0f: s %.0f u %+.0f", back, l[0], l[1]));
                        }
                        System.out.println(sb + String.format(Locale.ROOT, " (host %.0f m)", host.curve().length));
                    }
                }
            }
            RoadNetworkBuild.Result b = RoadNetworkBuild.build(json);
            for (String line : b.report())
                if (line.contains("interstate") && (line.startsWith("town") || line.startsWith("through"))) System.out.println("  build: " + line);
            System.out.println("  build: " + b.conflicts() + " conflict stations");
        }
        System.out.println(ok ? "SYSTEM PLANS: done" : "SYSTEM PLANS: FAILED");
        if (!ok) System.exit(1);
    }
}
