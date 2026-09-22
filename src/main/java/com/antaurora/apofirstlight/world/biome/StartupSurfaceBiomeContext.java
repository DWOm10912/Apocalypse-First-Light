package com.antaurora.apofirstlight.world.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Thread-local context used only while vanilla SurfaceSystem evaluates surface rules. */
public final class StartupSurfaceBiomeContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private StartupSurfaceBiomeContext() {
    }

    public static void begin(long seed, Registry<Biome> registry, DensityFunction preliminarySurface) {
        CURRENT.set(new Context(registry, MainNationBiomeRegionPlan.forSeed(seed), preliminarySurface));
    }

    public static void end() {
        CURRENT.remove();
    }

    public static Holder<Biome> resolve(int x, int y, int z, Holder<Biome> original) {
        Context context = CURRENT.get();
        if (context == null) return original;
        ResourceKey<Biome> key = original.unwrapKey().orElse(null);
        ResourceKey<Biome> target = context.plan().biomeAt(x, y, z, key, context.preliminarySurface());
        if (target == null) return original;
        return context.registry().getHolder(target)
                .map(holder -> (Holder<Biome>) holder)
                .orElse(original);
    }

    private record Context(Registry<Biome> registry, MainNationBiomeRegionPlan plan,
                           DensityFunction preliminarySurface) {
    }
}
