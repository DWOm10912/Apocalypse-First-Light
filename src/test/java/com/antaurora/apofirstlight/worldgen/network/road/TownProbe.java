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
import java.util.List;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

/**
 * Debug: the ground across the interstate at a framework town (Town Framework V1). Prints, every DS_ALONG along the
 * interstate round the town point, the planned ground every 40 m across it (left negative), and the interstate's datum.
 * <pre>
 *   TownProbe terrain-plan.bin [town]
 * </pre>
 */
public final class TownProbe {
    private TownProbe() {}

    public static void main(String[] args) throws Exception {
        String f = args[0], town = args.length > 1 ? args[1] : "valley_town";
        String name = Path.of(f).getFileName().toString();
        String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
        long seed = Long.parseUnsignedLong(unsigned);
        TerrainPlanSurface s;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
            s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
        }
        RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
        Path plan = TownCheck.newestPlans().get(unsigned);
        String json = Files.readString(plan);
        List<RoadNetworkJson.RouteData> routes = RoadNetworkJson.routes(json);
        RoadNetworkJson.RouteData a = null;
        for (RoadNetworkJson.RouteData r : routes) if (r.roadClass().equals("interstate") && town.equals(r.to()) && r.corridor().equals("interstate_ew")) a = r;
        int n = a.xs().length;
        double tx = a.xs()[n - 1], tz = a.zs()[n - 1];
        RoadNetworkBuild.Result b = RoadNetworkBuild.build(json);
        RoadDesign host = null;
        for (RoadDesign d : RoadNetworkJson.designs(b.json()))
            if (d.section == CrossSection.R4 && d.local(tx, tz, 100) != null) host = d;
        double[] l = host.local(tx, tz, 100);
        System.out.printf(Locale.ROOT, "town %s point %.0f %.0f on %s s %.0f%n", town, tx, tz, host.id, l[0]);
        StringBuilder head = new StringBuilder("   ds  datum |");
        for (double u = -600; u <= 600; u += 40) head.append(String.format(Locale.ROOT, "%5.0f", u));
        System.out.println(head);
        for (double ds = -1000; ds <= 1000; ds += 100) {
            double st = l[0] + ds;
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%5.0f %6.1f |", ds, host.profile.at(st)));
            for (double u = -600; u <= 600; u += 40) {
                double[] p = host.point(st, u);
                int w = terrain.water(p[0], p[1]);
                sb.append(w != RouteTerrain.DRY ? "    ~" : String.format(Locale.ROOT, "%5.0f", terrain.height(p[0], p[1])));
            }
            System.out.println(sb);
        }
    }
}
