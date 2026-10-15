package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanFingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What an AFL overworld is generated with (docs/worldgen/worldgen_version_lock_v1.md): the profile, the seed, the
 * declared system versions (WorldgenRules), and the measured output: the plan's value digest, the probe digests
 * ({@code plan_queries}, {@code density}, {@code biomes}) and the plan's tile summary. {@link #planCacheFormat} is
 * informational only (how the plan cache was stored); it is not part of {@link #id()} and never decides compatibility.
 * Pure value.
 */
public record WorldgenFingerprint(String profile, long seed, Map<String, String> systems, int planCacheFormat,
                                  String planDigest, Map<String, String> probes, TerrainPlanFingerprint.Tiles tiles) {
    public static final String PROBE_PLAN = "plan_queries", PROBE_DENSITY = "density", PROBE_BIOMES = "biomes";

    public WorldgenFingerprint {
        Objects.requireNonNull(profile);
        Objects.requireNonNull(planDigest);
        systems = Collections.unmodifiableMap(new TreeMap<>(systems));
        probes = Collections.unmodifiableMap(new TreeMap<>(probes));
    }

    /** Identity of the generation output (profile, seed, declared versions, digests); not the cache format, not the tiles. */
    public String id() {
        StringBuilder sb = new StringBuilder(profile).append('|').append(seed);
        systems.forEach((k, v) -> sb.append('|').append(k).append('=').append(v));
        sb.append("|plan=").append(planDigest);
        probes.forEach((k, v) -> sb.append('|').append(k).append('=').append(v));
        return sha16(sb.toString());
    }

    static String sha16(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int k = 0; k < 8; k++) sb.append(String.format("%02x", h[k] & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
