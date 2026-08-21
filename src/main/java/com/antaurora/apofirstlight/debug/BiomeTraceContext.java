package com.antaurora.apofirstlight.debug;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** DEV-only thread-local context used to correlate biome-source and parameter-list traces. */
public final class BiomeTraceContext {
    public static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private BiomeTraceContext() {
    }

    public record Context(int quartX, int quartY, int quartZ, String sourceIdentity) {
    }

    public static String biomeId(Holder<Biome> biome) {
        return biome == null ? "null" : biome.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }
}
