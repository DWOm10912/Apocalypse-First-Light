package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One non-positional, non-looping local feedback sound. No changes to user volume settings. */
public final class ExplosionTinnitusSound extends AbstractTickableSoundInstance {
    private final ExplosionTinnitusEnvelope envelope;

    public ExplosionTinnitusSound(ExplosionTinnitusEnvelope envelope) {
        super(AflSounds.EXPLOSION_TINNITUS.get(), SoundSource.PLAYERS, RandomSource.create());
        this.envelope = envelope;
        looping = false;
        delay = 0;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        pitch = 1.0F;
        volume = envelope.volume();
    }

    @Override
    public void tick() {
        volume = envelope.volume();
        if (!envelope.active()) stopNow();
    }

    public void stopNow() {
        volume = 0;
        stop();
    }
}
