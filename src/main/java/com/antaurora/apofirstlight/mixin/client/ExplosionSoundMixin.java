package com.antaurora.apofirstlight.mixin.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The blast's own sound is not played here, on the client at vanilla's volume 4 (heard 64 blocks, and only by the players
 * the explosion packet reaches, also 64). The server plays it instead, heard as far as the blast's noise: 96-256 blocks
 * (noise/ExplosionNoiseEvents, noise/RangedSound, 2026-10-09).
 */
@Mixin(Explosion.class)
public abstract class ExplosionSoundMixin {
    @Redirect(method = "finalizeExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void afl$blastPlayedByTheServer(Level level, double x, double y, double z, SoundEvent sound, SoundSource source,
                                            float volume, float pitch, boolean distanceDelay) {
    }
}
