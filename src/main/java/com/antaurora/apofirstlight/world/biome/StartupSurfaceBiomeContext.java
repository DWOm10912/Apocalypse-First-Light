package com.antaurora.apofirstlight.world.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Thread-local context used only while vanilla SurfaceSystem evaluates surface rules. */
public final class StartupSurfaceBiomeContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private StartupSurfaceBiomeContext() {
    }

    public static void begin(long seed, Registry<Biome> registry) {
        CURRENT.set(new Context(seed, registry));
    }

    public static void end() {
        CURRENT.remove();
    }

    public static Holder<Biome> resolve(int x, int z, Holder<Biome> original) {
        Context context = CURRENT.get();
        if (context == null) return original;
        ResourceKey<Biome> target = StartupPlainsEnclave.resolveBiome(x, z, context.seed(), original);
        if (target == null) return original;
        return context.registry().getHolder(target)
                .map(holder -> (Holder<Biome>) holder)
                .orElse(original);
    }

    private record Context(long seed, Registry<Biome> registry) {
    }
}
