package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.tree.ForestGate;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanFingerprint;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the running build's {@link WorldgenFingerprint} for an overworld (docs/worldgen/worldgen_version_lock_v1.md).
 * Besides the plan's own fingerprint it probes what the chunk generator does with it, at fixed points over the plan
 * grid, before any chunk is generated (LevelEvent.Load):
 * <ul>
 *   <li>{@code density}: the noise router's final density at 16 x 16 columns x 7 heights (-40 .. 130), quantised to
 *   1/1024 (the overworld noise settings, the Plan*Density functions, caves);</li>
 *   <li>{@code biomes}: the biome source's noise biome at 32 x 32 columns x 3 heights (Y0, Y64, Y80): the AFL ecology
 *   mapping through TerraBlender's parameter list (initialised at ServerAboutToStart, before this runs);</li>
 *   <li>{@code road_network} (federal, C3.2; a gated system's probe): the digest of the planned road network JSON
 *   (RoadNetworkRuntime: the plan's routes, profiles and structures). The first load of a seed plans it (about 8 s) and
 *   caches it; the density probe runs before the works are bound, so it never depends on them.</li>
 * </ul>
 * About 3 600 evaluations, tens of milliseconds, on the server thread while the world loads.
 */
final class WorldgenProbe {
    private WorldgenProbe() {}

    static final int[] DENSITY_Y = {-40, 0, 40, 60, 70, 90, 130};
    static final int[] BIOME_QY = {0, 16, 20};

    static WorldgenFingerprint current(ServerLevel level, long seed, TerrainPlanSurface plan,
                                       com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile profile) {
        var rules = WorldgenRules.current(profile, ForestGate.enabled(),
                ((com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess) (Object) level.getChunkSource().randomState()).apocalypse$underground() != null);
        TerrainPlanFingerprint.Result pf = TerrainPlanFingerprint.of(plan);
        Map<String, String> probes = new LinkedHashMap<>();
        probes.put(WorldgenFingerprint.PROBE_PLAN, pf.queryDigest());
        probes.put(WorldgenFingerprint.PROBE_DENSITY, density(level, plan));
        probes.put(WorldgenFingerprint.PROBE_BIOMES, biomes(level, plan));
        if (plan.federal() && rules.stream().anyMatch(r -> r.id().equals(WorldgenGates.ROAD_NETWORK)))
            probes.put(WorldgenGates.ROAD_NETWORK, com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime.get(seed, plan, pf.planDigest()).digest());
        return new WorldgenFingerprint(profile.lockProfile, seed, WorldgenSystem.versions(rules),
                plan.federal() ? TerrainPlanSurface.FORMAT_LAYERS : TerrainPlanSurface.FORMAT,
                pf.planDigest(), probes, pf.tiles());
    }

    /** Lattice point (i, j) of an n x n lattice over the plan's grid, off the chunk grid. */
    static int lattice(TerrainPlanSurface s, int i, int j, int n, boolean zAxis) {
        double span = s.gridN * TerrainPlanSurface.CELL;
        double f = zAxis ? (j + 0.5 + 0.29 * frac(i * 0.5772156649 + j * 0.6931471805)) / n
                : (i + 0.5 + 0.29 * frac(i * 0.3010299956 + j * 0.8414709848)) / n;
        return (int) Math.floor(s.gridOrigin + f * span);
    }

    private static double frac(double v) { return v - Math.floor(v); }

    static String density(ServerLevel level, TerrainPlanSurface s) {
        RandomState rs = level.getChunkSource().randomState();
        DensityFunction f = rs.router().finalDensity();
        Sha d = new Sha("afl-density-probe-1");
        for (int j = 0; j < 16; j++) for (int i = 0; i < 16; i++) {
            int x = lattice(s, i, j, 16, false), z = lattice(s, i, j, 16, true);
            for (int y : DENSITY_Y) {
                double v = f.compute(new DensityFunction.SinglePointContext(x, y, z));
                d.add(Double.isNaN(v) ? "nan" : Long.toString(Math.round(Math.max(-1e6, Math.min(1e6, v)) * 1024)));
            }
        }
        return d.hex();
    }

    static String biomes(ServerLevel level, TerrainPlanSurface s) {
        RandomState rs = level.getChunkSource().randomState();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = rs.sampler();
        Sha d = new Sha("afl-biome-probe-1");
        for (int j = 0; j < 32; j++) for (int i = 0; i < 32; i++) {
            int x = lattice(s, i, j, 32, false), z = lattice(s, i, j, 32, true);
            for (int qy : BIOME_QY) {
                Holder<Biome> b = source.getNoiseBiome(x >> 2, qy, z >> 2, sampler);
                d.add(b.unwrapKey().map(k -> k.location().toString()).orElse("?"));
            }
        }
        return d.hex();
    }

    private static final class Sha {
        private final MessageDigest md;

        Sha(String tag) {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            add(tag);
        }

        void add(String s) {
            md.update(s.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
        }

        String hex() {
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int k = 0; k < 8; k++) sb.append(String.format("%02x", h[k] & 0xff));
            return sb.toString();
        }
    }
}
