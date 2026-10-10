package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The near-surface stable layer (`apocalypse_firstlight:plan_stability`, Phase 2 2026-10-10,
 * docs/worldgen/terrain_v2_phase2_generation_v1.md), in two forms:
 * <ul>
 *   <li>ramp (terrain/plan_stability): a positive density the cave graph adds to the cave-entrance and noodle-cave
 *   terms (terrain/plan_caves.json): 1.5 per block of shortfall below the column's stable depth (plains and coastal
 *   plain 12, foothills 6, fold belt 4, water floors 6). It keeps entrance noise from opening caves near the surface,
 *   but it is soft at its lower end and the cave graph is interpolated over 8-block cells, so a large cave below
 *   can still bulge a few blocks into it (Phase 2b save audit: up to plan depth 9 on the plains);</li>
 *   <li>hard (terrain/plan_stable_floor, "hard": true; Phase 2b): per block and outside the interpolation, a solid
 *   floor from the planned surface down to the hard depth 2 x (stable - 6): plains / coastal plain / marsh 12, the
 *   plain-to-foothill blend in between, foothills and the fold belt 0 (their cave mouths stay). plan_caves takes
 *   max(caves, floor), so nothing opens there at all. The depth is measured from the exact planned surface of the
 *   column (cached per column), never from the 4-block flat cache, so the floor cannot add blocks above the plan.</li>
 * </ul>
 * The carvers get the same depth in mixin/WorldCarverStabilityMixin, lava lakes in mixin/LakeFeatureStabilityMixin.
 */
public record PlanStabilityDensity(DensityFunction height, boolean hard, TerrainPlanSurface surface) implements DensityFunction {
    public static final KeyDispatchDataCodec<PlanStabilityDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height").forGetter(PlanStabilityDensity::height),
                    Codec.BOOL.optionalFieldOf("hard", false).forGetter(PlanStabilityDensity::hard)
            ).apply(instance, (h, hard) -> new PlanStabilityDensity(h, hard, null))));

    static final double FLOOR = 0.2, NONE = -1_000_000;

    public PlanStabilityDensity withSeed(long seed) { return new PlanStabilityDensity(height, hard, TerrainPlanStore.get(seed)); }

    /** Blocks below the planned surface that the hard floor keeps solid. */
    public static int hardDepth(int stable) { return Math.max(0, Math.min(stable, 2 * (stable - 6))); }

    @Override public double compute(FunctionContext context) {
        if (surface == null) throw new IllegalStateException("AFL plan stability used before seed binding");
        int stable = surface.stableDepth(context.blockX(), context.blockZ());
        if (hard) {
            int hd = hardDepth(stable);
            if (hd <= 0) return NONE;
            double depth = Columns.height(surface, context.blockX(), context.blockZ()) - context.blockY();
            return depth > 0 && depth <= hd ? FLOOR : NONE;
        }
        if (stable <= 0) return 0;
        double depth = height.compute(context) - context.blockY();
        return depth < stable ? 1.5 * (stable - Math.max(depth, -2)) : 0;
    }
    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new PlanStabilityDensity(height.mapAll(visitor), hard, surface));
    }
    @Override public double minValue() { return hard ? NONE : 0; }
    @Override public double maxValue() { return hard ? FLOOR : 1.5 * (127 + 2); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

    /** Exact planned surface per block column, cached for the 256 columns a worker thread is filling. */
    static final class Columns {
        private static final ThreadLocal<Columns> LOCAL = ThreadLocal.withInitial(Columns::new);
        private final long[] keys = new long[256];
        private final double[] heights = new double[256];
        private final TerrainPlanSurface[] owners = new TerrainPlanSurface[256];

        static double height(TerrainPlanSurface surface, int x, int z) {
            Columns c = LOCAL.get();
            int i = (x & 15) | ((z & 15) << 4);
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            if (c.owners[i] != surface || c.keys[i] != key) {
                c.heights[i] = surface.heightAt(x, z);
                c.keys[i] = key;
                c.owners[i] = surface;
            }
            return c.heights[i];
        }
    }
}
