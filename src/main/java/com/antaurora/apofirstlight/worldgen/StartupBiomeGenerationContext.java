package com.antaurora.apofirstlight.worldgen;

import net.minecraft.world.level.biome.BiomeSource;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Thread-independent worldgen seed registry for biome-source callbacks.
 * BiomeSource instances are per world/dimension, so the weak identity-backed
 * map avoids a process-wide single seed while allowing async generation work
 * to resolve the same immutable seed on any worker thread.
 */
public final class StartupBiomeGenerationContext {
    private static final Map<BiomeSource, Long> SEEDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private StartupBiomeGenerationContext() {
    }

    public static void register(BiomeSource biomeSource, long seed) {
        SEEDS.put(biomeSource, seed);
    }

    public static Long seedFor(BiomeSource biomeSource) {
        return SEEDS.get(biomeSource);
    }
}
