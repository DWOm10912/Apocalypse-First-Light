package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.worldgen.aquifer.ScorchedAquiferContext;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes only aquifer-produced near-surface water for Scorched Lands before it
 * is written into the generated chunk. Lava and all other final states remain
 * untouched.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkScorchedAquiferMixin {
    private static final int DRY_ENVELOPE_DEPTH = 12;

    @Inject(method = "getInterpolatedState", at = @At("RETURN"), cancellable = true)
    private void apocalypse$dryScorchedNearSurfaceWater(CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null || !state.is(Blocks.WATER)) {
            return;
        }

        ScorchedAquiferContext.Context context = ScorchedAquiferContext.current();
        if (context == null) {
            return;
        }

        NoiseChunk noiseChunk = (NoiseChunk) (Object) this;
        int x = noiseChunk.blockX();
        int y = noiseChunk.blockY();
        int z = noiseChunk.blockZ();
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
