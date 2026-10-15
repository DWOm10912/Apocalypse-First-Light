package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalEcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The plan side of the C2 probe (dev only): which plan the world runs on, where the probe sites are, what the plan says there. */
final class C2Plan {
    private C2Plan() {}

    static TerrainPlanSurface surface(ServerLevel level) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        return access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
    }

    static String describe(TerrainPlanSurface s) {
        return s.version + " rivers " + (s.rivers == null ? 0 : s.rivers.lines());
    }

    /** Fixed sites from the spawn and from the plan (searched on a 64 m lattice within 6 km of the spawn). */
    static List<C2Probe.Site> sites(ServerLevel level, TerrainPlanSurface s, BlockPos spawn) {
        if (s.federal()) return federalSites(s, spawn);
        List<C2Probe.Site> out = new ArrayList<>();
        int sx = spawn.getX(), sz = spawn.getZ();
        out.add(new C2Probe.Site("ne1536", sx + 1536, sz - 1536, 2));
        out.add(new C2Probe.Site("se1536", sx + 1536, sz + 1536, 2));
        out.add(new C2Probe.Site("sw1536", sx - 1536, sz + 1536, 2));
        out.add(new C2Probe.Site("nw1536", sx - 1536, sz - 1536, 2));
        int[] high = null, coast = null, marsh = null, river = null;
        double hb = -1e9, dc = 1e18, dm = 1e18, dr = 1e18;
        for (int dz = -6144; dz <= 6144; dz += 64) for (int dx = -6144; dx <= 6144; dx += 64) {
            int x = sx + dx, z = sz + dz;
            double d = (double) dx * dx + (double) dz * dz;
            int wc = s.waterClass(x, z);
            double h = s.heightAt(x, z);
            if (wc == 0 && h > hb) { hb = h; high = new int[]{x, z}; }
            if (wc == 0 && d < dc && s.shoreDistance(x, z) <= 8 && s.waterClass(x + 32, z) == 1) { dc = d; coast = new int[]{x, z}; }
            if (wc == 3 && d < dm) { dm = d; marsh = new int[]{x, z}; }
            if (wc == 0 && d < dr && s.rivers != null && s.rivers.edgeDistance(x, z) < 1) { dr = d; river = new int[]{x, z}; }
        }
        if (high != null) out.add(new C2Probe.Site("highest", high[0], high[1], 2));
        if (coast != null) out.add(new C2Probe.Site("coast", coast[0], coast[1], 2));
        if (marsh != null) out.add(new C2Probe.Site("marsh", marsh[0], marsh[1], 2));
        if (river != null) out.add(new C2Probe.Site("river", river[0], river[1], 2));
        return out;
    }

    /**
     * Federal sites (C2): each state's land centre, then the landforms the states are about, each the nearest lattice
     * point (64 m, the whole country) to its state's centre: S4 lake shore, S3 snow peak and treeline, S6 canyon and
     * steppe, S5 swamp and tidal marsh, S2 open plain, and four state borders (S1|S2, S2|S6, S3|S4, S1|S5).
     */
    static List<C2Probe.Site> federalSites(TerrainPlanSurface s, BlockPos spawn) {
        List<C2Probe.Site> out = new ArrayList<>();
        FederalMap map = FederalMap.forSeed(s.seed);
        FederalMap.Bounds b = map.landBounds();
        int n = FederalStateId.values().length;
        double[] sx = new double[n], sz = new double[n];
        long[] cnt = new long[n];
        List<int[]> lattice = new ArrayList<>();
        for (double z = b.minZ(); z <= b.maxZ(); z += 64) for (double x = b.minX(); x <= b.maxX(); x += 64) {
            FederalStateId st = map.stateAt(x, z);
            if (st == null || s.waterClass(x, z) != 0) continue;
            sx[st.ordinal()] += x;
            sz[st.ordinal()] += z;
            cnt[st.ordinal()]++;
            lattice.add(new int[]{(int) x, (int) z, st.ordinal(), FederalEcologyBiomes.biomeAt(s, s.ecology, x, z)});
        }
        int[][] centre = new int[n][];
        for (int k = 0; k < n; k++) {
            if (cnt[k] == 0) continue;
            double mx = sx[k] / cnt[k], mz = sz[k] / cnt[k];
            int[] best = null;
            double bd = Double.MAX_VALUE;
            for (int[] p : lattice) {
                if (p[2] != k) continue;
                double d = (p[0] - mx) * (p[0] - mx) + (p[1] - mz) * (p[1] - mz);
                if (d < bd) { bd = d; best = p; }
            }
            centre[k] = best;
            out.add(new C2Probe.Site(FederalStateId.values()[k].key() + "_centre", best[0], best[1], 2));
        }
        nearest(out, "s4_lake_shore", lattice, centre[3], p -> p[3] == FederalEcologyBiomes.FRESHWATER_LAKE && shore(s, p), 3);
        nearest(out, "s3_snow_peak", lattice, centre[2], p -> p[3] == FederalEcologyBiomes.ALPINE_PEAKS, 2);
        nearest(out, "s3_treeline", lattice, centre[2], p -> p[3] == FederalEcologyBiomes.ALPINE_MEADOW
                && biome(s, p[0] + 64, p[1]) == FederalEcologyBiomes.MONTANE_CONIFER, 2);
        nearest(out, "s6_canyon", lattice, centre[5], p -> p[3] == FederalEcologyBiomes.CANYON_BADLANDS, 2);
        nearest(out, "s6_steppe", lattice, centre[5], p -> p[3] == FederalEcologyBiomes.PLATEAU_STEPPE && p[2] == 5
                && Math.abs(p[0] - centre[5][0]) + Math.abs(p[1] - centre[5][1]) > 1500, 2);
        nearest(out, "s5_swamp", lattice, centre[4], p -> p[3] == EcologyBiomes.SWAMP_FOREST, 2);
        nearest(out, "s5_tidal_marsh", lattice, centre[4], p -> p[3] == EcologyBiomes.TIDAL_MARSH && p[2] == 4, 2);
        nearest(out, "s2_open_plain", lattice, centre[1], p -> p[3] == EcologyBiomes.GRASSLAND && p[2] == 1
                && Math.abs(p[0] - centre[1][0]) + Math.abs(p[1] - centre[1][1]) > 1500, 2);
        border(out, "border_s1_s2", map, lattice, 0, 1);
        border(out, "border_s2_s6", map, lattice, 1, 5);
        border(out, "border_s3_s4", map, lattice, 2, 3);
        border(out, "border_s1_s5", map, lattice, 0, 4);
        return out;
    }

    private static int biome(TerrainPlanSurface s, double x, double z) { return FederalEcologyBiomes.biomeAt(s, s.ecology, x, z); }

    private static boolean shore(TerrainPlanSurface s, int[] p) {
        for (int k = 0; k < 4; k++)
            if (biome(s, p[0] + (k == 0 ? 64 : k == 1 ? -64 : 0), p[1] + (k == 2 ? 64 : k == 3 ? -64 : 0)) != FederalEcologyBiomes.FRESHWATER_LAKE) return true;
        return false;
    }

    private static void nearest(List<C2Probe.Site> out, String name, List<int[]> lattice, int[] from,
                                java.util.function.Predicate<int[]> test, int radius) {
        if (from == null) return;
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int[] p : lattice) {
            double d = (double) (p[0] - from[0]) * (p[0] - from[0]) + (double) (p[1] - from[1]) * (p[1] - from[1]);
            if (d < bd && test.test(p)) { bd = d; best = p; }
        }
        if (best != null) out.add(new C2Probe.Site(name, best[0], best[1], radius));
    }

    /** The land lattice point of state a, with state b 64 m east or south, nearest the middle of the two states' shared border points. */
    private static void border(List<C2Probe.Site> out, String name, FederalMap map, List<int[]> lattice, int a, int b) {
        List<int[]> pts = new ArrayList<>();
        for (int[] p : lattice) {
            if (p[2] != a && p[2] != b) continue;
            for (int k = 0; k < 2; k++) {
                FederalStateId q = map.stateAt(p[0] + (k == 0 ? 64 : 0), p[1] + (k == 1 ? 64 : 0));
                if (q != null && q.ordinal() == (p[2] == a ? b : a)) { pts.add(p); break; }
            }
        }
        if (pts.isEmpty()) return;
        double mx = 0, mz = 0;
        for (int[] p : pts) { mx += p[0]; mz += p[1]; }
        mx /= pts.size();
        mz /= pts.size();
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int[] p : pts) {
            double d = (p[0] - mx) * (p[0] - mx) + (p[1] - mz) * (p[1] - mz);
            if (d < bd) { bd = d; best = p; }
        }
        out.add(new C2Probe.Site(name, best[0], best[1], 2));
    }

    static String siteInfo(TerrainPlanSurface s, int x, int z) {
        String base = String.format("plan: height %.1f, water class %d, shore %d, stable %d, slope %.3f", s.heightAt(x, z),
                s.waterClass(x, z), s.shoreDistance(x, z), s.stableDepth(x, z), s.slopeAt(x, z));
        if (!s.federal()) return base;
        FederalStateId st = FederalMap.forSeed(s.seed).stateAt(x, z);
        return base + ", state " + (st == null ? "sea" : st.key()) + ", plan biome " + EcologyBiomes.id(biome(s, x, z));
    }

    static String lockStatus(ServerLevel level) {
        Path f = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("apocalypse_firstlight_worldgen_lock.dat");
        try {
            return "lock record " + (Files.exists(f) ? "present, " + Files.size(f) + " bytes" : "absent");
        } catch (Exception e) {
            return "lock record unreadable: " + e;
        }
    }
}
