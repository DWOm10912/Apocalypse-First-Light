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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/**
 * The roadside turf as the server builds it (RoadWorks#column over the plan's ground, as RoadRockCheck; 2026-10-25,
 * docs/worldgen/road_drainage_v1_design.md 12): per seed, every road at STATION m stations, both sides, a SIDE x SIDE
 * square of columns centred AT m beyond the paved edge (the ditch and the slopes), every graded_turf corner read back.
 * <ul>
 *   <li>FAIL: corners shared by turf columns at heights more than one eighth apart (a torn face: the lifted block's side
 *   step of earth, or a wedge of sky) on more than TEARS of the corners (RoadWorks#agreed leaves a few where a block
 *   must lift twice, at the edge of a steep cut); a corner the works reach standing SPIKE m or more over all four corners
 *   beside it (a lump in a ditch or on a slope).</li>
 *   <li>Report: the corners read, the tears by place (a natural corner, the joint band RoadWorks#joint, inside the
 *   works by section), spikes at natural corners (the natural block there a whole block over its neighbours), the first
 *   few places.</li>
 * </ul>
 * Rock cut faces (RoadWorks.ROCK_CUT: bare stone, a face on purpose), tunnel works and culvert bodies are skipped.
 * <pre>
 *   RoadTurfCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS; plans from systemPlans)
 * </pre>
 */
public final class RoadTurfCheck {
    static final double STATION = 50, AT = 5, SPIKE = 0.3, TEAR = 1.0 / 8 + 1e-9, TEARS = 0.0005;
    static final int SIDE = 12;

    private RoadTurfCheck() {}

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
            for (int i = 0; i < Math.min(bad.size(), 12); i++) System.out.println("  FAIL " + bad.get(i));
            if (bad.size() > 12) System.out.println("  ... " + (bad.size() - 12) + " more");
            fails += bad.isEmpty() ? 0 : 1;
            summary.add(unsigned + ": " + line + (bad.isEmpty() ? "" : " FAIL " + bad.size()));
        }
        System.out.println("== summary");
        for (String l : summary) System.out.println("  " + l);
        System.out.println(fails == 0 ? "ROAD TURF CHECK PASS (" + seeds + " seeds)" : "ROAD TURF CHECK FAIL (" + fails + " of " + seeds + " seeds)");
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
        RoadWorks.Ground ground = RoadWorks.Ground.of(s);
        RoadWorks works = new RoadWorks(RoadNetworkJson.designs(built), water, new TownWorks(b.towns()), home == null ? Driveway.EMPTY : Driveway.of(home), ground);
        // corner point -> {lowest, highest} over the turf columns sharing it
        Map<Long, double[]> corners = new HashMap<>();
        Map<Long, Boolean> seen = new HashMap<>();
        long columns = 0;
        long t0 = System.nanoTime();
        for (RoadDesign d : works.roads()) {
            CrossSection sec = d.section;
            for (double st = 0; st <= d.length; st += STATION)
                for (int side = -1; side <= 1; side += 2) {
                    double[] p = d.point(st, side * (sec.edge + AT));
                    int x0 = (int) Math.floor(p[0]) - SIDE / 2, z0 = (int) Math.floor(p[1]) - SIDE / 2;
                    for (int z = z0; z < z0 + SIDE; z++) for (int x = x0; x < x0 + SIDE; x++) {
                        if (seen.put(key(x, z), Boolean.TRUE) != null) continue;
                        RoadWorks.Column c = works.column(x, z, ground);
                        columns++;
                        if (c.kind() != RoadWorks.Kind.TURF || (c.data() & (RoadWorks.ROCK_CUT | RoadWorks.TUNNEL_OVER | RoadWorks.CULVERT_BODY)) != 0) continue;
                        int base = c.y() * 8 + ((c.data() >> 16 & 1) != 0 ? 4 : 0);
                        for (int k = 0; k < 4; k++) {
                            double h = (base + (c.data() >> 4 * k & 15)) / 8.0;
                            double[] r = corners.computeIfAbsent(key(x + (k & 1), z + (k >> 1)), q -> new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY});
                            r[0] = Math.min(r[0], h);
                            r[1] = Math.max(r[1], h);
                        }
                    }
                }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        int tears = 0, spikes = 0, jointSpikes = 0, tearNatural = 0, tearJoint = 0;
        Map<String, Integer> tearKinds = new java.util.TreeMap<>();
        double worstTear = 0, worstSpike = 0;
        List<String> tearAt = new ArrayList<>(), spikeAt = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : corners.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 32), z = (int) k;
            double[] r = e.getValue();
            if (r[1] - r[0] > TEAR) {
                tears++;
                double g = ground.at(x, z);
                if (Double.isNaN(works.surface(x, z, g))) tearNatural++;
                else if (works.joint(x, z, ground, new double[1]) > 0) tearJoint++;
                else {
                    RoadWorks.Hit h = works.hit(x, z, RoadWorks.REACH);
                    String kind = h == null ? "none" : h.road().section.name + (h.beyond() > 0 ? " beyond" : h.v() > RoadWorks.MAX_HALF_WIDTH - RoadWorks.BLEND ? " blend" : "");
                    tearKinds.merge(kind, 1, Integer::sum);
                }
                worstTear = Math.max(worstTear, r[1] - r[0]);
                if (tearAt.size() < 6) tearAt.add(String.format(Locale.ROOT, "tear at %d %d: %.3f .. %.3f", x, z, r[0], r[1]));
            }
            double over = Double.POSITIVE_INFINITY;
            boolean all = true;
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                double[] n = corners.get(key(x + o[0], z + o[1]));
                if (n == null) { all = false; break; }
                over = Math.min(over, r[0] - n[1]);
            }
            if (all && over >= SPIKE - 1e-9) {
                // a corner the works leave natural: the natural block it meets stands that high (the noise's whole-block
                // step at the joint), reported only
                if (Double.isNaN(works.surface(x, z, ground.at(x, z)))) { jointSpikes++; continue; }
                spikes++;
                worstSpike = Math.max(worstSpike, over);
                if (spikeAt.size() < 6) spikeAt.add(String.format(Locale.ROOT, "spike at %d %d: %.3f, %.3f over the corners beside it", x, z, r[0], over));
            }
        }
        StringBuilder probes = new StringBuilder();
        for (String q : System.getProperty("afl.probe", "").split(";")) {
            if (q.isBlank()) continue;
            String[] v = q.trim().split("\s+");
            int px = Integer.parseInt(v[0]), pz = Integer.parseInt(v[1]);
            for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
                int x = px + i, z = pz + j;
                double g = ground.at(x, z), f = works.surface(x, z, g);
                RoadWorks.Hit h = works.hit(x, z, RoadWorks.REACH);
                probes.append(String.format(Locale.ROOT, "%n  probe point %d %d: ground %.3f, works %.3f, natural tops-joint %.2f, road %s",
                        x, z, g, f, Double.isNaN(f) ? 1 : works.joint(x, z, ground, new double[1]),
                        h == null ? "-" : String.format(Locale.ROOT, "%s s %.1f u %.2f (edge %.2f) %s", h.road().id, h.s(), h.u(), h.road().section.edge, h.road().section.name)));
            }
            for (int j = -1; j <= 0; j++) for (int i = -1; i <= 0; i++) {
                RoadWorks.Column c = works.column(px + i, pz + j, ground);
                int base = c.y() * 8 + ((c.data() >> 16 & 1) != 0 ? 4 : 0);
                probes.append(String.format(Locale.ROOT, "%n  probe column %d %d: %s y %d data %x corners %.3f %.3f %.3f %.3f",
                        px + i, pz + j, c.kind(), c.y(), c.data(), (base + (c.data() & 15)) / 8.0, (base + (c.data() >> 4 & 15)) / 8.0,
                        (base + (c.data() >> 8 & 15)) / 8.0, (base + (c.data() >> 12 & 15)) / 8.0));
            }
        }
        if (tears > corners.size() * TEARS) {
            bad.add(String.format(Locale.ROOT, "%d torn corners, over %.2f%% of %d", tears, 100 * TEARS, corners.size()));
            bad.addAll(tearAt);
        }
        if (spikes > 0) bad.addAll(spikeAt);
        return String.format(Locale.ROOT, "%d roads, %d columns (%.1f us each), %d turf corners: %d torn (worst %.3f m), %d spikes of %.1f m or more (worst %.3f m) in the works, %d at natural corners; tears at natural corners %d, at the joint %d, inside the works %s%s",
                works.roads().size(), columns, ms * 1000 / Math.max(1, columns), corners.size(), tears, worstTear, spikes, SPIKE, worstSpike, jointSpikes, tearNatural, tearJoint, tearKinds, probes);
    }

    private static long key(int x, int z) { return (long) x << 32 | (z & 0xffffffffL); }
}
