package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.BlockParticleSampler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Breaking and digging particles cut their few texels from the block's own faces when its particle texture is an atlas
 * (client/BlockParticleSampler; 2026-10-06, the user: they showed whole patches of an atlas). Sampled when the particle is
 * made and again when Forge sets its sprite for the block's model data (updateSprite); vanilla's quarter otherwise.
 */
@Mixin(TerrainParticle.class)
public abstract class TerrainParticleSampleMixin extends TextureSheetParticle {
    @Unique
    private float[] afl$window;

    protected TerrainParticleSampleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void afl$sampleMade(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, BlockState state, BlockPos pos, CallbackInfo ci) {
        afl$window = BlockParticleSampler.sample(state, sprite, random);
    }

    @Inject(method = "updateSprite", at = @At("RETURN"), remap = false)
    private void afl$sampleUpdated(BlockState state, BlockPos pos, CallbackInfoReturnable<Particle> cir) {
        afl$window = BlockParticleSampler.sample(state, sprite, random);
    }

    @Inject(method = "getU0", at = @At("HEAD"), cancellable = true)
    private void afl$u0(CallbackInfoReturnable<Float> cir) {
        if (afl$window != null) cir.setReturnValue(afl$window[0]);
    }

    @Inject(method = "getU1", at = @At("HEAD"), cancellable = true)
    private void afl$u1(CallbackInfoReturnable<Float> cir) {
        if (afl$window != null) cir.setReturnValue(afl$window[1]);
    }

    @Inject(method = "getV0", at = @At("HEAD"), cancellable = true)
    private void afl$v0(CallbackInfoReturnable<Float> cir) {
        if (afl$window != null) cir.setReturnValue(afl$window[2]);
    }

    @Inject(method = "getV1", at = @At("HEAD"), cancellable = true)
    private void afl$v1(CallbackInfoReturnable<Float> cir) {
        if (afl$window != null) cir.setReturnValue(afl$window[3]);
    }
}
