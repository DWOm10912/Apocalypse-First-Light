package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.ground.GroundCoverage;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.underground.GeoProtect;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlanner;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/**
 * Terrain rework R3 survey on every seed's planned ground (GroundCoverage; docs/worldgen/terrain_rework_v1_design.md 14):
 * the network, geology and underground plan built as UndergroundCheck builds them, then 8 entrance patches, 24 random land patches and 8 shore
 * patches per seed counted. Writes build/underground/coverage.txt. Read only, never a world or a save.
 * <pre>
 *   gradlew groundCoverageSeeds [-PterrainPlans=a.bin;b.bin]
 * </pre>
 */
public final class GroundCoverageCheck {
    private GroundCoverageCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        List<String> out = new ArrayList<>();
        GroundCoverage.Tally all = new GroundCoverage.Tally();
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
            FederalMap map = FederalMap.forSeed(seed);
            String[] kept = new String[1];
            RoadCounty.Memo memo = new RoadCounty.Memo() {
                @Override public String load() { return kept[0]; }
                @Override public void store(String site) { kept[0] = site; }
            };
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(Files.readString(planFile), new RouteTerrain(s, map), memo);
            RoadCounty.Home h = r.home();
            List<RoadDesign> roads = RoadNetworkJson.designs(h == null ? TownPlan.append(r.json(), r.towns()) : RoadCounty.append(TownPlan.append(r.json(), r.towns()), h));
            GeologyModel g = new GeologyModel(seed, s, map, -256);
            UndergroundPlan u = UndergroundPlanner.build(g, new GeoProtect(s, roads, r.towns(), h));
            double span = s.gridN * TerrainPlanSurface.CELL;
            GroundCoverage.Tally t = new GroundCoverage.Tally();
            GroundCoverage.seedPatches(seed, g, u, (x, z) -> s.waterAt(x, z), map::isLand,
                    new double[]{s.gridOrigin + span * 0.1, s.gridOrigin + span * 0.9}, 24, t);
            List<String> rep = t.report("seed " + unsigned);
            rep.forEach(System.out::println);
            out.addAll(rep);
            all.add(t);
        }
        List<String> rep = all.report("all seeds");
        rep.forEach(System.out::println);
        out.addAll(rep);
        new File("build/underground").mkdirs();
        Files.write(Path.of("build/underground/coverage.txt"), out);
    }
}
