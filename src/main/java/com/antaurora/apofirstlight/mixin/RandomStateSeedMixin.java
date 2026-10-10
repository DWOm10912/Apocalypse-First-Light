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

    /** Expand terrain recipes before vanilla traverses/seeds the resulting spline/noise graph. */
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseRouter()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter apocalypse$bindMacroSeed(NoiseGeneratorSettings owner, NoiseGeneratorSettings settings,
                                                 HolderGetter<NormalNoise.NoiseParameters> noises, long seed) {
        java.util.Map<LandTerrainRelief, DensityFunction> resolved = new java.util.HashMap<>();
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
                return height.withSeed(seed);
            }
            if (function instanceof com.antaurora.apofirstlight.worldgen.terrain.v2.PlanStabilityDensity stability) {
                apocalypse$macroGeography = apocalypse$terrainPlan = true;
                return stability.withSeed(seed);
            }
            if (function instanceof com.antaurora.apofirstlight.worldgen.terrain.v2.PlanTerrainDensity terrain) {
                apocalypse$macroGeography = apocalypse$terrainPlan = true;
                return terrain.withSeed(seed);
            }
            if (function instanceof MacroTerrainDensity macro) {
                apocalypse$macroGeography = true;
                return macro.withSeed(seed);
            }
            return function;
        });
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
}
