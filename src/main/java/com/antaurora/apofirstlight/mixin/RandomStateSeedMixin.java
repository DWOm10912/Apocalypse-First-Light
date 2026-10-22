package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.geography.MacroHeightDensity;
import com.antaurora.apofirstlight.worldgen.geography.LandTerrainRelief;
import com.antaurora.apofirstlight.worldgen.geography.LandTerrainBias;
import com.antaurora.apofirstlight.worldgen.geography.InlandElevationBias;
import com.antaurora.apofirstlight.worldgen.geography.MacroTerrainDensity;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomState.class)
public abstract class RandomStateSeedMixin implements RandomStateSeedAccess {
    @Unique
    private long apocalypse$seed;
    @Unique private boolean apocalypse$macroGeography;
    @Unique private boolean apocalypse$terrainPlan;
    // C2 (2026-10-14): the plan this router was bound to, and its profile (r1 or the federal experimental plan)
    @Unique private com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface apocalypse$planSurface;
    @Unique private com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile apocalypse$planProfile;
    // C3 (2026-10-14): the gated generation systems on for this world (WorldgenGates), bound by the version lock at load
    @Unique private volatile java.util.Set<String> apocalypse$gates = java.util.Set.of();
    // C3.2 (2026-10-14): the terrain works the plan densities apply, and the road network bound by the version lock
    @Unique private com.antaurora.apofirstlight.worldgen.network.road.RoadWorksRef apocalypse$worksRef;
    @Unique private volatile com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime.Network apocalypse$network;
    // U2 (2026-10-22): the planned caves of a deep world (plan_terrain "planned_caves"), filled by UndergroundRuntime.bind
    @Unique private com.antaurora.apofirstlight.worldgen.underground.UndergroundRef apocalypse$underground;

    /** Expand terrain recipes before vanilla traverses/seeds the resulting spline/noise graph. */
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseRouter()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter apocalypse$bindMacroSeed(NoiseGeneratorSettings owner, NoiseGeneratorSettings settings,
                                                 HolderGetter<NormalNoise.NoiseParameters> noises, long seed) {
        java.util.Map<LandTerrainRelief, DensityFunction> resolved = new java.util.HashMap<>();
        if (apocalypse$worksRef == null) apocalypse$worksRef = new com.antaurora.apofirstlight.worldgen.network.road.RoadWorksRef();
        return owner.noiseRouter().mapAll(function -> {
            if (function instanceof MacroHeightDensity height) {
                apocalypse$macroGeography = true;
                return height.withSeed(seed);
            }
            if (function instanceof LandTerrainRelief relief) {
                apocalypse$macroGeography = true;
                return resolved.computeIfAbsent(relief, recipe -> recipe.resolve(seed));
            }
            if (function instanceof LandTerrainBias bias) {
                apocalypse$macroGeography = true;
                return bias.withSeed(seed);
            }
            if (function instanceof InlandElevationBias bias) {
                apocalypse$macroGeography = true;
                return bias.withSeed(seed);
            }
            // Terrain V2: the plan is fetched (memory / disk cache / one build) here, at world load, never per chunk
            if (function instanceof com.antaurora.apofirstlight.worldgen.terrain.v2.PlanHeightDensity height) {
                apocalypse$macroGeography = apocalypse$terrainPlan = true;
                var bound = height.withSeed(seed, apocalypse$worksRef);
                apocalypse$bindPlan(bound.profile(), bound.surface());
                return bound;
            }
            if (function instanceof com.antaurora.apofirstlight.worldgen.terrain.v2.PlanStabilityDensity stability) {
                apocalypse$macroGeography = apocalypse$terrainPlan = true;
                var bound = stability.withSeed(seed, apocalypse$worksRef);
                apocalypse$bindPlan(bound.profile(), bound.surface());
                return bound;
            }
            if (function instanceof com.antaurora.apofirstlight.worldgen.terrain.v2.PlanTerrainDensity terrain) {
                apocalypse$macroGeography = apocalypse$terrainPlan = true;
                var bound = terrain.withSeed(seed);
                apocalypse$bindPlan(bound.profile(), bound.surface());
                if (bound.plannedCaves() && apocalypse$underground == null)
                    apocalypse$underground = new com.antaurora.apofirstlight.worldgen.underground.UndergroundRef();
                return bound;
            }
            if (function instanceof MacroTerrainDensity macro) {
                apocalypse$macroGeography = true;
                return macro.withSeed(seed);
            }
            return function;
        });
    }

    /** One router, one plan: a noise settings file that mixes r1 and federal plan functions is a data error. */
    @Unique
    private void apocalypse$bindPlan(com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile profile,
                                     com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface surface) {
        if (apocalypse$planProfile != null && apocalypse$planProfile != profile)
            throw new IllegalStateException("AFL noise router mixes Terrain V2 profiles " + apocalypse$planProfile + " and " + profile);
        apocalypse$planProfile = profile;
        apocalypse$planSurface = surface;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void apocalypse$captureSeed(NoiseGeneratorSettings settings,
                                        HolderGetter<NormalNoise.NoiseParameters> noises,
                                        long seed, CallbackInfo ci) {
        apocalypse$seed = seed;
    }

    @Override
    public long apocalypse$getSeed() {
        return apocalypse$seed;
    }

    @Override public boolean apocalypse$hasMacroGeography() { return apocalypse$macroGeography; }

    @Override public boolean apocalypse$hasTerrainPlan() { return apocalypse$terrainPlan; }

    @Override public com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface apocalypse$terrainPlanSurface() { return apocalypse$planSurface; }

    @Override public com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile apocalypse$terrainProfile() { return apocalypse$planProfile; }

    @Override public java.util.Set<String> apocalypse$worldgenGates() { return apocalypse$gates; }

    @Override public void apocalypse$setWorldgenGates(java.util.Set<String> on) { apocalypse$gates = java.util.Set.copyOf(on); }

    @Override public com.antaurora.apofirstlight.worldgen.network.road.RoadWorksRef apocalypse$roadWorksRef() {
        if (apocalypse$worksRef == null) apocalypse$worksRef = new com.antaurora.apofirstlight.worldgen.network.road.RoadWorksRef();
        return apocalypse$worksRef;
    }

    @Override public com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime.Network apocalypse$roadNetwork() { return apocalypse$network; }

    @Override public com.antaurora.apofirstlight.worldgen.underground.UndergroundRef apocalypse$underground() { return apocalypse$underground; }

    @Override public void apocalypse$setRoadNetwork(com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime.Network network) {
        apocalypse$network = network;
    }
}
