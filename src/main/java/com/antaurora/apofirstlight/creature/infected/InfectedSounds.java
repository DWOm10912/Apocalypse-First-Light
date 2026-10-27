package com.antaurora.apofirstlight.creature.infected;

import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Infected Z1a's voice (docs/gameplay/afl_infected_z0_design.md 12.1; the takes balanced by tools/build-infected-sounds-v1.mjs):
 * every sound through RangedSound, its range the distance it carries (the noise radius rule). A groan carries 16 blocks, the
 * snarl when it finds a target and its blows on a door 24; the ragged breath while chasing only 10. The pitch varies a
 * little per sound so a crowd does not sound like one voice.
 */
public final class InfectedSounds {
    private InfectedSounds() {
    }

    static void idle(Entity e) {
        play(e, AflSounds.INFECTED_IDLE.get(), 16);
    }

    static void alert(Entity e) {
        play(e, AflSounds.INFECTED_ALERT.get(), 24);
    }

    static void chase(Entity e) {
        play(e, AflSounds.INFECTED_CHASE.get(), 10);
    }

    static void attack(Entity e) {
        play(e, AflSounds.INFECTED_ATTACK.get(), 16);
    }

    static void hurt(Entity e) {
        play(e, AflSounds.INFECTED_HURT.get(), 16);
    }

    static void death(Entity e) {
        play(e, AflSounds.INFECTED_DEATH.get(), 16);
    }

    /** Z1b's door attack (not used by Z1a). */
    static void doorBang(Entity e) {
        play(e, AflSounds.INFECTED_DOOR_BANG.get(), 24);
    }

    private static void play(Entity e, SoundEvent sound, double radius) {
        if (!(e.level() instanceof ServerLevel level)) return;
        float pitch = 0.92F + level.random.nextFloat() * 0.16F;
        RangedSound.play(level, e.getEyePosition(), sound, SoundSource.HOSTILE, radius, 1.0F, pitch);
    }
}
