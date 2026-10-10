package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Where world generation gets its Terrain V2 plan (Phase 2, 2026-10-10; docs/worldgen/terrain_v2_phase2_generation_v1.md).
 * The planner takes about 9 s for a seed, so it must never run per chunk or per query:
 * <ol>
 *   <li>memory: at most two immutable surfaces (the world being generated, and one more, e.g. a second integrated
 *   server in the same session), keyed by seed;</li>
 *   <li>disk: {@code <game dir>/afl_cache/terrain_plan/<version>_<seed>.bin} (deflated, about 8 MB), validated by format,
 *   version, seed and CRC; a bad or stale file is ignored and rebuilt;</li>
 *   <li>build: once per seed per machine, single-flight (concurrent callers wait for the one build), then written to disk
 *   atomically (temp file + move).</li>
 * </ol>
 * Called when the noise router is bound to the seed (RandomStateSeedMixin, world load), before any chunk is
 * generated, so chunk workers only ever read a finished surface. {@link #peek} never builds.
 */
public final class TerrainPlanStore {
    private static final Map<Long, TerrainPlanSurface> CACHE = new LinkedHashMap<>();
    private static final Object BUILD = new Object();

    private TerrainPlanStore() {
    }

    /** The surface for a seed: memory, then disk, then a build (blocking; world load only). */
    public static TerrainPlanSurface get(long seed) {
        TerrainPlanSurface s = peek(seed);
        if (s != null) return s;
        synchronized (BUILD) {
            s = peek(seed);
            if (s != null) return s;
            long t0 = System.nanoTime();
            Path file = file(seed);
            s = load(file, seed);
            String how = "disk cache";
            if (s == null) {
                ApocalypseFirstLight.LOGGER.info("[AFL Terrain V2] building plan {} for seed {} (first time on this machine, about 10 s)",
                        TerrainPlanV2.VERSION, seed);
                TerrainPlanV2 plan = new TerrainPlanV2(seed);
                s = plan.toSurface();
                save(file, s);
                how = "built " + plan.report;
            }
            remember(seed, s);
            ApocalypseFirstLight.LOGGER.info("[AFL Terrain V2] plan {} seed {} ready in {} ms ({})", TerrainPlanV2.VERSION, seed,
                    (System.nanoTime() - t0) / 1_000_000, how);
            return s;
        }
    }

    /** The surface if it is already in memory; never loads or builds. */
    public static synchronized TerrainPlanSurface peek(long seed) {
        return CACHE.get(seed);
    }

    private static synchronized void remember(long seed, TerrainPlanSurface s) {
        CACHE.remove(seed);
        CACHE.put(seed, s);
        while (CACHE.size() > 2) CACHE.remove(CACHE.keySet().iterator().next());
    }

    static Path file(long seed) {
        return FMLPaths.GAMEDIR.get().resolve("afl_cache").resolve("terrain_plan")
                .resolve(TerrainPlanV2.VERSION + "_" + Long.toUnsignedString(seed) + ".bin");
    }

    private static TerrainPlanSurface load(Path file, long seed) {
        if (!Files.isRegularFile(file)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(file)), 1 << 16))) {
            TerrainPlanSurface s = TerrainPlanSurface.read(in, seed, TerrainPlanV2.VERSION);
            if (s == null) ApocalypseFirstLight.LOGGER.warn("[AFL Terrain V2] ignoring stale plan cache {}", file);
            return s;
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.warn("[AFL Terrain V2] unreadable plan cache {} ({}), rebuilding", file, e.toString());
            return null;
        }
    }

    private static void save(Path file, TerrainPlanSurface s) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(Files.newOutputStream(tmp)), 1 << 16))) {
                s.write(out);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // the cache is only a speed-up: generation goes on with the plan in memory
            ApocalypseFirstLight.LOGGER.warn("[AFL Terrain V2] could not write plan cache {} ({})", file, e.toString());
        }
    }
}
