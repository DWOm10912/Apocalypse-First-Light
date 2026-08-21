package com.antaurora.apofirstlight.worldgen.aquifer;

import javax.annotation.Nullable;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Generation-thread context used only while NoiseBasedChunkGenerator fills a chunk. */
public final class ScorchedAquiferContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private ScorchedAquiferContext() {
    }

    public static void begin(BiomeSource biomeSource, Climate.Sampler sampler, long seed) {
        CURRENT.set(new Context(biomeSource, sampler, seed));
    }

    public static void end() {
        CURRENT.remove();
    }

    @Nullable
    public static Context current() {
        return CURRENT.get();
    }

    public record Context(BiomeSource biomeSource, Climate.Sampler sampler, long seed) {
    }
}
