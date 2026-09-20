package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.worldgen.aquifer.SurfaceWaterSuppressingAquifer;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Establishes macro surface water for both actual noise fill and generator-time column queries,
 * Filters Scorched near-surface water at the shared aquifer, before surface building.
 * Real macro water and deep aquifers retain their existing behavior.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkScorchedAquiferMixin {
    private static final int DRY_ENVELOPE_DEPTH = 12;
    @Shadow @Final @Mutable private Aquifer aquifer;
    @Unique private ChunkAccess apocalypse$chunk;
    @Unique private boolean[] apocalypse$surfaceChecked;
    @Unique private boolean[] apocalypse$scorched;
    @Unique private int[] apocalypse$surfaceY;
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
            apocalypse$surfaceChecked = new boolean[256];
            apocalypse$scorched = new boolean[256];
            apocalypse$surfaceY = new int[256];
            aquifer = new SurfaceWaterSuppressingAquifer(aquifer, this::apocalypse$suppressWater);
        }
    }

    @Inject(method = "forChunk", at = @At("RETURN"))
    private static void apocalypse$bindChunk(ChunkAccess chunk, RandomState randomState,
                                             DensityFunctions.BeardifierOrMarker beardifier,
                                             NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
                                             Blender blender, CallbackInfoReturnable<NoiseChunk> cir) {
        ((NoiseChunkScorchedAquiferMixin) (Object) cir.getReturnValue()).apocalypse$chunk = chunk;
    }

    @Unique private MacroGeographySample apocalypse$column(int x, int z) {
        int index = (x & 15) | ((z & 15) << 4);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (apocalypse$columns[index] == null || apocalypse$columnKeys[index] != key) {
            apocalypse$columns[index] = apocalypse$geography.sample(x, z);
            apocalypse$columnKeys[index] = key;
            apocalypse$surfaceChecked[index] = false;
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
        }
    }

    @Unique private boolean apocalypse$suppressWater(DensityFunction.FunctionContext position) {
        // Generator-only column queries have no generated biome container: do not guess their biome.
        if (apocalypse$chunk == null || apocalypse$geography == null) return false;
        int x = position.blockX();
        int z = position.blockZ();
        MacroGeographySample column = apocalypse$column(x, z);
        if (column.nationId() != MacroGeographySample.NationId.MAIN_NATION
                || !column.isLand() || column.waterClass() != MacroGeographySample.WaterClass.NONE
                || (column.surfaceClass() != MacroGeographySample.SurfaceClass.LAND
                    && column.surfaceClass() != MacroGeographySample.SurfaceClass.COAST)) return false;

        int index = (x & 15) | ((z & 15) << 4);
        if (!apocalypse$surfaceChecked[index]) {
            int surfaceY = ((NoiseChunk) (Object) this).preliminarySurfaceLevel(x, z);
            apocalypse$surfaceY[index] = surfaceY;
            apocalypse$scorched[index] = surfaceY != Integer.MAX_VALUE
                    && apocalypse$chunk.getNoiseBiome(QuartPos.fromBlock(x),
                        QuartPos.fromBlock(Math.max(surfaceY, MacroGeography.SEA_LEVEL)),
                        QuartPos.fromBlock(z)).is(AflBiomes.SCORCHED_LANDS);
            apocalypse$surfaceChecked[index] = true;
        }
        // No upper bound: flooded depressions can have water ABOVE the preliminary ground level.
        return apocalypse$scorched[index]
                && position.blockY() >= apocalypse$surfaceY[index] - DRY_ENVELOPE_DEPTH;
    }
}
