package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The Terrain V2 planned surface Y at a column (`apocalypse_firstlight:plan_height`, Phase 2 2026-10-10). Wrapped in
 * flat_cache / cache_2d in data/apocalypse_firstlight/worldgen/density_function/terrain/plan_height.json, so it is read
 * once per quart column per chunk. Bound to the world seed by RandomStateSeedMixin; unbound it fails loudly.
 * {@code "profile"} (C2, optional, default r1): which plan (TerrainProfile); the federal experimental preset sets it.
 */
public record PlanHeightDensity(TerrainProfile profile, TerrainPlanSurface surface) implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<PlanHeightDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    TerrainProfile.CODEC.optionalFieldOf("profile", TerrainProfile.R1).forGetter(PlanHeightDensity::profile)
            ).apply(instance, p -> new PlanHeightDensity(p, null))));

    public PlanHeightDensity withSeed(long seed) { return new PlanHeightDensity(profile, TerrainPlanStore.get(profile, seed)); }

    @Override public double compute(FunctionContext context) {
        if (surface == null) throw new IllegalStateException("AFL plan height used before seed binding");
        return surface.heightAt(context.blockX(), context.blockZ());
    }
    @Override public double minValue() { return -64; }
    @Override public double maxValue() { return 320; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
