package com.antaurora.apofirstlight.world.biome;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
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

    public static void begin(long seed, Registry<Biome> registry, DensityFunction preliminarySurface,
                             com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface plan) {
        CURRENT.set(new Context(registry, MainNationBiomeRegionPlan.forSeed(seed), preliminarySurface, plan));
    }

    public static void end() {
        CURRENT.remove();
    }

    public static Holder<Biome> resolve(int x, int y, int z, Holder<Biome> original) {
        Context context = CURRENT.get();
        if (context == null) return original;
        // biomeAt snaps to the quart and depends on nothing else, so one answer serves every block of the quart
        // (the surface rules ask once per stone cell; deep world performance, design 23.5)
        long quart = BlockPos.asLong(x >> 2, y >> 2, z >> 2);
        Resolved hit = context.resolved.get(quart);
        if (hit != null && hit.original == original) return hit.result;
        Holder<Biome> result = resolveUncached(context, x, y, z, original);
        context.resolved.put(quart, new Resolved(original, result));
        return result;
    }

    private static Holder<Biome> resolveUncached(Context context, int x, int y, int z, Holder<Biome> original) {
        ResourceKey<Biome> key = original.unwrapKey().orElse(null);
        ResourceKey<Biome> target = context.plan.biomeAt(context.terrainPlan, x, y, z, key, context.preliminarySurface);
        if (target == null) return original;
        return context.registry.getHolder(target)
                .map(holder -> (Holder<Biome>) holder)
                .orElse(original);
    }

    private record Resolved(Holder<Biome> original, Holder<Biome> result) {
    }

    private static final class Context {
        final Registry<Biome> registry;
        final MainNationBiomeRegionPlan plan;
        final DensityFunction preliminarySurface;
        final com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface terrainPlan;
        final Long2ObjectOpenHashMap<Resolved> resolved = new Long2ObjectOpenHashMap<>();

        Context(Registry<Biome> registry, MainNationBiomeRegionPlan plan, DensityFunction preliminarySurface,
                com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface terrainPlan) {
            this.registry = registry;
            this.plan = plan;
            this.preliminarySurface = preliminarySurface;
            this.terrainPlan = terrainPlan;
        }
    }
}
