package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.worldgen.aquifer.ScorchedAquiferContext;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import net.minecraft.core.QuartPos;
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

/**
 * Establishes macro surface water for both actual noise fill and generator-time column queries,
 * then applies the existing inland Scorched drying rule. Aquifers below the seafloor remain vanilla.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkScorchedAquiferMixin {
    private static final int DRY_ENVELOPE_DEPTH = 12;
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
    private void apocalypse$dryScorchedNearSurfaceWater(CallbackInfoReturnable<BlockState> cir) {
        NoiseChunk noiseChunk = (NoiseChunk) (Object) this;
        int x = noiseChunk.blockX();
        int y = noiseChunk.blockY();
        int z = noiseChunk.blockZ();
        if (apocalypse$geography != null) {
            MacroGeographySample column = apocalypse$column(x, z);
            if (column.isWater() && y >= column.surfaceHeight() && y < MacroGeography.SEA_LEVEL) {
                cir.setReturnValue(Blocks.WATER.defaultBlockState());
                return;
            }
            if (column.coastDistance() < MacroGeography.COAST_WIDTH) return;
        }
        BlockState state = cir.getReturnValue();
        if (state == null || !state.is(Blocks.WATER)) {
            return;
        }

        ScorchedAquiferContext.Context context = ScorchedAquiferContext.current();
        if (context == null) {
            return;
        }

        if (!context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                QuartPos.fromBlock(z), context.sampler()).is(AflBiomes.SCORCHED_LANDS)) {
            return;
        }

        int surfaceY = noiseChunk.preliminarySurfaceLevel(x, z);
        if (surfaceY != Integer.MAX_VALUE && y <= surfaceY && y >= surfaceY - DRY_ENVELOPE_DEPTH) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
