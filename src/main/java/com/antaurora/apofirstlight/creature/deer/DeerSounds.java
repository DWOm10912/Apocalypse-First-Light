package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The deer's sounds (docs/gameplay/afl_whitetail_deer_d0_design.md 4.1; the takes balanced by tools/build-deer-sounds-v1.mjs):
 * every sound through RangedSound, its range the distance it carries (the noise radius rule). The alarm snort carries 48
 * blocks; a bound through brush 20; the bleat 24, the buck's grunt 16; a walking step 8 and a bite of grass 8. The pitch
 * varies a little per sound, so a herd does not sound like one animal.
 */
public final class DeerSounds {
    private DeerSounds() {
    }

    static void snort(Entity e) {
        play(e, AflSounds.DEER_SNORT.get(), 48, e.getEyePosition());
    }

    static void stomp(Entity e) {
        play(e, AflSounds.DEER_STOMP.get(), 16, e.position());
    }

    static void bleat(Entity e) {
        play(e, AflSounds.DEER_BLEAT.get(), 24, e.getEyePosition());
    }

    static void grunt(Entity e) {
        play(e, AflSounds.DEER_GRUNT.get(), 16, e.getEyePosition());
    }

    static void hurt(Entity e) {
        play(e, AflSounds.DEER_HURT.get(), 24, e.getEyePosition());
    }

    static void step(Entity e, double radius) {
        play(e, AflSounds.DEER_STEP.get(), radius, e.position());
    }

    static void bound(Entity e) {
        play(e, AflSounds.DEER_BOUND.get(), 20, e.position());
    }

    static void brush(Entity e) {
        play(e, AflSounds.DEER_BRUSH.get(), 20, e.position().add(0, 0.6, 0));
    }

    static void graze(Entity e) {
        play(e, AflSounds.DEER_GRAZE.get(), 8, e.position().add(0, 0.2, 0));
    }

    private static void play(Entity e, SoundEvent sound, double radius, Vec3 at) {
        if (!(e.level() instanceof ServerLevel level)) return;
        float pitch = 0.92F + level.random.nextFloat() * 0.16F;
        RangedSound.play(level, at, sound, SoundSource.NEUTRAL, radius, 1.0F, pitch);
    }
}
