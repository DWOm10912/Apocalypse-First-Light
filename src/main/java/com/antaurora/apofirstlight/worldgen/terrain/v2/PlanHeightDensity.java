package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.Codec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The Terrain V2 planned surface Y at a column (`apocalypse_firstlight:plan_height`, Phase 2 2026-10-10). Wrapped in
 * flat_cache / cache_2d in data/apocalypse_firstlight/worldgen/density_function/terrain/plan_height.json, so it is read
 * once per quart column per chunk. Bound to the world seed by RandomStateSeedMixin; unbound it fails loudly.
 */
public record PlanHeightDensity(TerrainPlanSurface surface) implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<PlanHeightDensity> CODEC =
            KeyDispatchDataCodec.of(Codec.unit(() -> new PlanHeightDensity(null)));

    public PlanHeightDensity withSeed(long seed) { return new PlanHeightDensity(TerrainPlanStore.get(seed)); }

    @Override public double compute(FunctionContext context) {
        if (surface == null) throw new IllegalStateException("AFL plan height used before seed binding");
        return surface.heightAt(context.blockX(), context.blockZ());
    }
    @Override public double minValue() { return -64; }
    @Override public double maxValue() { return 320; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
