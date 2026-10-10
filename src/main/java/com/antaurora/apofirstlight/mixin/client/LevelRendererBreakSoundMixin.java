package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.NoiseSoundRanges;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the block a break sound is for while LevelRenderer plays it (levelEvent 2001), so client/NoiseSoundRanges can let
 * it carry as far as that block's break noise (2026-10-09).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBreakSoundMixin {
    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void afl$breakSoundStart(int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type == 2001) NoiseSoundRanges.breaking(pos.immutable(), Block.stateById(data));
    }

    @Inject(method = "levelEvent", at = @At("RETURN"))
    private void afl$breakSoundEnd(int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type == 2001) NoiseSoundRanges.breaking(null, null);
    }
}
