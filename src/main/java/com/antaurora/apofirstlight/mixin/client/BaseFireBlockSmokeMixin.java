package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FireBlockFlames;
import com.antaurora.apofirstlight.client.FireFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire blocks' looks on this client (docs/gameplay/fuel_fire_v1.md "火焰效果 V2"): the black pixel smoke vanilla puts over
 * a fire becomes a soft grey puff (client/FireFx) one time in three, a little higher (the flames are taller); and a fire
 * block vanilla ticks is made known to client/FireBlockFlames (in case its chunk arrived before it was being followed).
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockSmokeMixin {
    @Inject(method = "animateTick", at = @At("HEAD"))
    private void afl$noticeFire(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (level.isClientSide && state.is(Blocks.FIRE)) FireBlockFlames.seen(pos);
    }

    @Redirect(method = "animateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void afl$softSmoke(Level level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        if (particle != ParticleTypes.LARGE_SMOKE) {
            level.addParticle(particle, x, y, z, vx, vy, vz);
            return;
        }
        if (level instanceof ClientLevel client && level.random.nextInt(3) == 0) {
            FireFx.smoke(client, x, y + 0.6, z, 0.2F, FireFx.WOOD_SMOKE, 0.5F, 70, 0.05);
        }
    }
}
