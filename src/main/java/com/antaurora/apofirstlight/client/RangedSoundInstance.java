package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.network.RangedSoundS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.util.RandomSource;

/**
 * A positional sound that fades linearly to silence at its own radius (noise/RangedSound: the radius the infected hear it
 * at), not at the sounds.json attenuation. The engine's range is max(volume, 1) x the resolved Sound's attenuation
 * distance (SoundEngine.play), so the resolved Sound is swapped for a copy with the radius as its attenuation distance and
 * the volume kept at most 1.
 */
public final class RangedSoundInstance extends SimpleSoundInstance {
    private final int radius;

    private RangedSoundInstance(RangedSoundS2CPacket packet) {
        super(packet.sound(), packet.source(), Math.min(packet.volume(), 1.0F), packet.pitch(), RandomSource.create(packet.seed()),
                false, 0, Attenuation.LINEAR, packet.x(), packet.y(), packet.z(), false);
        this.radius = Math.max(1, Math.round(packet.radius()));
    }

    public static void play(RangedSoundS2CPacket packet) {
        Minecraft.getInstance().getSoundManager().play(new RangedSoundInstance(packet));
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents events = super.resolve(manager);
        Sound s = sound;
        if (s != null && s != SoundManager.EMPTY_SOUND && s != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            sound = new Sound(s.getLocation().toString(), s.getVolume(), s.getPitch(), s.getWeight(), s.getType(), s.shouldStream(),
                    s.shouldPreload(), radius);
        }
        return events;
    }
}
