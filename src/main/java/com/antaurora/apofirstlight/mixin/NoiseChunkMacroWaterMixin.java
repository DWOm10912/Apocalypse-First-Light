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
    // Terrain V2: the drowned valleys (estuaries) the plan adds to the macro sea; per column: floor Y, or NaN (no estuary)
    @Unique private com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface apocalypse$plan;
    @Unique private double[] apocalypse$estuaryFloor;

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
            if (access.apocalypse$hasTerrainPlan()) {
                apocalypse$plan = com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore.peek(access.apocalypse$getSeed());
                apocalypse$estuaryFloor = new double[256];
            }
        }
    }

    @Unique private MacroGeographySample apocalypse$column(int x, int z) {
        int index = (x & 15) | ((z & 15) << 4);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (apocalypse$columns[index] == null || apocalypse$columnKeys[index] != key) {
            apocalypse$columns[index] = apocalypse$geography.sample(x, z);
            apocalypse$columnKeys[index] = key;
            if (apocalypse$plan != null) {
                // an estuary column, or (Phase 2b) a low shore column within 48 m of open water whose ground lies under
                // Y63: the water edge then follows the ground's Y63 contour, not the plan's 16 m cells
                int wc = apocalypse$plan.waterClass(x, z);
                double floor = Double.NaN;
                if (wc == 2) floor = apocalypse$plan.heightAt(x, z);
                else if (wc == 0 && apocalypse$plan.shoreDistance(x, z) <= 48) {
                    double h = apocalypse$plan.heightAt(x, z);
                    if (h < MacroGeography.SEA_LEVEL) floor = h;
                }
                apocalypse$estuaryFloor[index] = floor;
            }
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
            else if (apocalypse$plan != null && y < MacroGeography.SEA_LEVEL) {
                // an estuary (or the low shore beside it): open air over the drowned floor (and the floor's top block)
                // fills to sea level
                double floor = apocalypse$estuaryFloor[(noiseChunk.blockX() & 15) | ((noiseChunk.blockZ() & 15) << 4)];
                BlockState state = cir.getReturnValue();
                if (!Double.isNaN(floor) && y >= floor - 1 && state != null && state.isAir())   // null = the default (solid) block
                    cir.setReturnValue(Blocks.WATER.defaultBlockState());
            }
        }
    }
}
