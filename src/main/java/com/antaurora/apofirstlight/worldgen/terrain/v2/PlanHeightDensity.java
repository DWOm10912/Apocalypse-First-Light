package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorksRef;

/**
 * The Terrain V2 planned surface Y at a column (`apocalypse_firstlight:plan_height`, Phase 2 2026-10-10). Wrapped in
 * flat_cache / cache_2d in data/apocalypse_firstlight/worldgen/density_function/terrain/plan_height.json, so it is read
 * once per quart column per chunk. Bound to the world seed by RandomStateSeedMixin; unbound it fails loudly.
 * {@code "profile"} (C2, optional, default r1): which plan (TerrainProfile); the federal experimental preset sets it.
 * C3.2: with the world's terrain works bound (RoadWorksRef), the height of a road column is the finished road surface
 * (cut, fill, slopes; RoadWorks.surface), so the noise itself carries the earthworks; the chunk pass finishes them exactly.
 */
public record PlanHeightDensity(TerrainProfile profile, TerrainPlanSurface surface, RoadWorksRef works) implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<PlanHeightDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    TerrainProfile.CODEC.optionalFieldOf("profile", TerrainProfile.R1).forGetter(PlanHeightDensity::profile)
            ).apply(instance, p -> new PlanHeightDensity(p, null, null))));

    public PlanHeightDensity withSeed(long seed) { return withSeed(seed, null); }

    /** Bound to the seed and to the router's terrain works (C3.2: road cuts and fills; empty until the lock binds them). */
    public PlanHeightDensity withSeed(long seed, RoadWorksRef works) { return new PlanHeightDensity(profile, TerrainPlanStore.get(profile, seed), works); }

    @Override public double compute(FunctionContext context) {
        if (surface == null) throw new IllegalStateException("AFL plan height used before seed binding");
        double g = surface.heightAt(context.blockX(), context.blockZ());
        return works == null ? g : works.height(context.blockX(), context.blockZ(), g);
    }
    @Override public double minValue() { return -64; }
    @Override public double maxValue() { return 320; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
