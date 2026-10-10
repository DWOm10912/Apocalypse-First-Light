package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The Terrain V2 land / water router (`apocalypse_firstlight:plan_terrain`, Phase 2 2026-10-10), replacing
 * MacroTerrainDensity in overworld.json. Both branches stand on the SAME planned surface (terrain/plan_sloped_cheese
 * = 0.125 x (plan height - y)), so nothing can step at the shore; they differ only in what lies under it:
 * <ul>
 *   <li>land (shore distance >= 48 m): the vanilla cave graph on the plan surface (terrain/plan_caves);</li>
 *   <li>water (the unified mask: macro sea and drowned valleys): a solid floor, caves only from 6 blocks under it
 *   (MacroTerrainDensity's sea-floor rule, unchanged);</li>
 *   <li>the 0..48 m dry shore: a smoothstep between the two.</li>
 * </ul>
 * initial = true is the preliminary-surface graph (initial_density_without_jaggedness): the land branch is
 * terrain/plan_initial_land, the water branch the plain surface density.
 */
public record PlanTerrainDensity(DensityFunction land, DensityFunction terrain, DensityFunction underground,
                                 boolean initial, TerrainPlanSurface surface) implements DensityFunction {
    public static final KeyDispatchDataCodec<PlanTerrainDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("land").forGetter(PlanTerrainDensity::land),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain").forGetter(PlanTerrainDensity::terrain),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("underground").forGetter(PlanTerrainDensity::underground),
                    Codec.BOOL.optionalFieldOf("initial", false).forGetter(PlanTerrainDensity::initial)
            ).apply(instance, (l, t, u, i) -> new PlanTerrainDensity(l, t, u, i, null))));

    static final double SHORE_BAND = 48;

    public PlanTerrainDensity withSeed(long seed) {
        return new PlanTerrainDensity(land, terrain, underground, initial, TerrainPlanStore.get(seed));
    }

    @Override public double compute(FunctionContext context) {
        if (surface == null) throw new IllegalStateException("AFL plan terrain used before seed binding");
        int shore = surface.shoreDistance(context.blockX(), context.blockZ());
        if (shore >= SHORE_BAND) return land.compute(context);
        double water = initial ? terrain.compute(context) : waterDensity(context);
        if (shore <= 0) return water;
        double t = shore / SHORE_BAND;
        t = t * t * (3 - 2 * t);
        return water + (land.compute(context) - water) * t;
    }

    private double waterDensity(FunctionContext context) {
        double depth = terrain.compute(context) * 8;           // blocks below the floor
        double floor = Math.max(-1, Math.min(1, depth / 8));
        if (depth <= 6) return floor;
        double caves = underground.compute(context);
        double t = Math.max(0, Math.min(1, (depth - 6) / 18));
        t = t * t * (3 - 2 * t);
        return Math.min(floor, floor + (caves - floor) * t);
    }

    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new PlanTerrainDensity(land.mapAll(visitor), terrain.mapAll(visitor),
                underground.mapAll(visitor), initial, surface));
    }
    @Override public double minValue() {
        return Math.min(land.minValue(), initial ? terrain.minValue() : Math.min(-1, underground.minValue()));
    }
    @Override public double maxValue() { return Math.max(land.maxValue(), initial ? terrain.maxValue() : 1); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
