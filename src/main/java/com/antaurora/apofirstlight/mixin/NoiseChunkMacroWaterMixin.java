package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared macro surface water for noise fill and generator-time column queries. */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMacroWaterMixin {
    @Unique private MacroGeography apocalypse$geography;
    @Unique private MacroGeographySample[] apocalypse$columns;
    @Unique private long[] apocalypse$columnKeys;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void apocalypse$bindGeography(int cellCount, RandomState randomState, int x, int z,
                                          NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier,
                                          NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
                                          Blender blender, CallbackInfo ci) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) randomState;
        if (access.apocalypse$hasMacroGeography()) {
            apocalypse$geography = MacroGeography.forSeed(access.apocalypse$getSeed());
            apocalypse$columns = new MacroGeographySample[256];
            apocalypse$columnKeys = new long[256];
        }
    }

    @Unique private MacroGeographySample apocalypse$column(int x, int z) {
        int index = (x & 15) | ((z & 15) << 4);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (apocalypse$columns[index] == null || apocalypse$columnKeys[index] != key) {
            apocalypse$columns[index] = apocalypse$geography.sample(x, z);
            apocalypse$columnKeys[index] = key;
        }
        return apocalypse$columns[index];
    }

    @Inject(method = "getInterpolatedState", at = @At("RETURN"), cancellable = true)
    private void apocalypse$establishMacroWater(CallbackInfoReturnable<BlockState> cir) {
        NoiseChunk noiseChunk = (NoiseChunk) (Object) this;
        if (apocalypse$geography != null) {
            MacroGeographySample column = apocalypse$column(noiseChunk.blockX(), noiseChunk.blockZ());
            int y = noiseChunk.blockY();
            if (column.isWater() && y >= column.surfaceHeight() && y < MacroGeography.SEA_LEVEL)
                cir.setReturnValue(Blocks.WATER.defaultBlockState());
        }
    }
}
