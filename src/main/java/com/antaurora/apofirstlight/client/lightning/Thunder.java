package com.antaurora.apofirstlight.client.lightning;

import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * Thunder V1 (docs/audio/thunder_sounds_v1_plan.md): what a strike sounds like depends on how far the nearest part of
 * its channel is: a crack overhead, a boom with a sharp start, a rumble, a low far roll. Each band is its own recording
 * (tools/thunder-sounds/cut.py: the farther, the more air has taken off the highs). The sound comes in late by the
 * distance at the speed of sound, from the side of the channel (placed a little way off toward it, not attenuated by
 * distance: its loudness is set here), and walls muffle it (client/weather/WeatherMuffle).
 */
final class Thunder {
    /** Band edges (m, to the nearest point of the main channel); each strike moves them up to SPREAD either way. */
    static final double CRACK = 80, CLOSE = 600, MID = 3000;
    /** Nothing is heard beyond this (the server sends far strikes this far: world/LightningStrikes). */
    static final double RANGE = 15000;
    private static final double SPREAD = 0.15;
    /** Full loudness within this distance, falling as the square root beyond it, never below FLOOR. */
    private static final double FULL = 250;
    private static final float FLOOR = 0.15F;
    /** The sound is placed this far from the camera toward the channel, for its direction only. */
    private static final double PLACE = 16;

    private Thunder() {
    }

    /**
     * Plays the strike's thunder (once, as its flash starts), heard from (nx, ny, nz), the channel's nearest point.
     * @return the delay (ticks)
     */
    static int play(Minecraft mc, LightningBolt bolt, Vec3 cam, double nx, double ny, double nz) {
        double d = Math.sqrt(cam.distanceToSqr(nx, ny, nz));
        if (d > RANGE) return -1;
        RandomSource r = RandomSource.create(bolt.getUUID().getLeastSignificantBits());
        double edge = 1 + SPREAD * (2 * r.nextDouble() - 1);
        SoundEvent band = d < CRACK * edge ? AflSounds.THUNDER_CRACK.get()
                : d < CLOSE * edge ? AflSounds.THUNDER_CLOSE.get()
                : d < MID * edge ? AflSounds.THUNDER_MID.get()
                : AflSounds.THUNDER_FAR.get();
        float volume = (float) Math.max(FLOOR, Math.min(1, Math.sqrt(FULL / Math.max(d, 1))));
        float pitch = 0.92F + 0.14F * r.nextFloat();
        double k = d > 1e-3 ? Math.min(1, PLACE / d) : 0;
        double x = cam.x + (nx - cam.x) * k, y = cam.y + (ny - cam.y) * k, z = cam.z + (nz - cam.z) * k;
        int delay = (int) Math.round(d / AflLightning.SOUND_SPEED * 20);
        mc.getSoundManager().playDelayed(new SimpleSoundInstance(band.getLocation(), SoundSource.WEATHER, volume, pitch,
                RandomSource.create(r.nextLong()), false, 0, SoundInstance.Attenuation.NONE, x, y, z, false), delay);
        return delay;
    }
}
