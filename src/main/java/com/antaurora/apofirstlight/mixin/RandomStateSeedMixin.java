package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.geography.MacroHeightDensity;
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

    /** Only the explicitly registered macro node is changed; Nether/End routers have no such node. */
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseRouter()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter apocalypse$bindMacroSeed(NoiseGeneratorSettings owner, NoiseGeneratorSettings settings,
                                                 HolderGetter<NormalNoise.NoiseParameters> noises, long seed) {
        return owner.noiseRouter().mapAll(function -> {
            if (function instanceof MacroHeightDensity height) {
                apocalypse$macroGeography = true;
                return height.withSeed(seed);
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
}
