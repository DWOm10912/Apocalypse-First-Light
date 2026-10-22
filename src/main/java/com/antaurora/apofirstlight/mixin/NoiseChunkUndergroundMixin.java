package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundRef;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * U2 (docs/worldgen/underground_geology_caves_v0_design.md section 20): cuts the planned caves of a deep world into the
 * noise fill. Only solid rock is cut (the default block, null here): a cave block above its waterY becomes air, below it
 * water, and a stream trench is water whatever the level (section 21.2). Sea, rivers and open air are left as the density made them. The shape list is gathered once per chunk.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkUndergroundMixin {
    @Unique private UndergroundRef apocalypse$caveRef;
    @Unique private UndergroundPlan apocalypse$cavePlan;
    @Unique private long apocalypse$caveChunk = Long.MIN_VALUE;
    @Unique private int[] apocalypse$caveShapes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void apocalypse$bindUnderground(int cellCount, RandomState randomState, int x, int z,
                                            NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier,
                                            NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
                                            Blender blender, CallbackInfo ci) {
        apocalypse$caveRef = ((RandomStateSeedAccess) (Object) randomState).apocalypse$underground();
    }

    @Inject(method = "getInterpolatedState", at = @At("RETURN"), cancellable = true)
    private void apocalypse$cutPlannedCaves(CallbackInfoReturnable<BlockState> cir) {
        if (apocalypse$caveRef == null || cir.getReturnValue() != null) return;   // null = the default (solid) block
        NoiseChunk chunk = (NoiseChunk) (Object) this;
        int x = chunk.blockX(), y = chunk.blockY(), z = chunk.blockZ();
        long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        if (key != apocalypse$caveChunk || apocalypse$cavePlan != apocalypse$caveRef.get()) {
            apocalypse$cavePlan = apocalypse$caveRef.get();
            apocalypse$caveChunk = key;
            int x0 = x & ~15, z0 = z & ~15;
            apocalypse$caveShapes = apocalypse$cavePlan == null ? new int[0]
                    : apocalypse$cavePlan.shapesInBox(x0, z0, x0 + 16, z0 + 16);
        }
        if (apocalypse$caveShapes.length == 0) return;
        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
        int shape = apocalypse$cavePlan.shapeAmong(apocalypse$caveShapes, px, py, pz);
        if (shape < 0) return;
        cir.setReturnValue(apocalypse$cavePlan.wetAt(shape, px, py, pz)
                ? Blocks.WATER.defaultBlockState() : Blocks.CAVE_AIR.defaultBlockState());
    }
}
