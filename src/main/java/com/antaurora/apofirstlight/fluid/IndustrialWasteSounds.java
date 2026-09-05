package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.registry.AflFluids;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

/** Sound-only counterpart of MC 1.20.1 Entity water splash/swim calculations. */
public final class IndustrialWasteSounds {
    private static final float SPLASH_VOLUME_MULTIPLIER = 1.25F;

    private IndustrialWasteSounds() {
    }

    public static boolean isInWaste(Entity entity) {
        // Read Forge's existing collision-volume fluid contact; never change water identity or physics.
        return entity.getFluidTypeHeight(AflFluids.INDUSTRIAL_WASTE_TYPE.get()) > 0.0;
    }

    public static boolean canSound(Entity entity) {
        return !entity.isSilent() && !entity.isInWater()
                && !(entity.getVehicle() instanceof Boat boat && !boat.isUnderWater());
    }

    private static Entity velocityOwner(Entity entity) {
        return entity.isVehicle() && entity.getControllingPassenger() != null
                ? entity.getControllingPassenger() : entity;
    }

    private static float weightedVolume(Vec3 velocity, float scale) {
        // Vanilla uses 0.2F promoted to double, Y impact unscaled; size affects particles, not sound.
        return Math.min(1.0F, (float) Math.sqrt(velocity.x * velocity.x * 0.2F
                + velocity.y * velocity.y + velocity.z * velocity.z * 0.2F) * scale);
    }

    private static float vanillaPitch(RandomSource random) {
        return 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F;
    }

    public static void splash(Entity entity, RandomSource random,
                              SoundEvent splashSound, SoundEvent highSpeedSplashSound) {
        if (!canSound(entity)) return;
        Entity owner = velocityOwner(entity);
        float vanillaVolume = weightedVolume(owner.getDeltaMovement(), owner == entity ? 0.2F : 0.9F);
        SoundEvent sound = vanillaVolume < 0.25F ? splashSound : highSpeedSplashSound;
        float playbackVolume = Math.min(1.0F, vanillaVolume * SPLASH_VOLUME_MULTIPLIER);
        entity.playSound(sound, playbackVolume, vanillaPitch(random));
    }

    public static void swim(Entity entity, RandomSource random, SoundEvent swimSound) {
        if (!canSound(entity)) return;
        Entity owner = velocityOwner(entity);
        Vec3 velocity = owner.getDeltaMovement();
        float volume = weightedVolume(velocity, owner == entity ? 0.35F : 0.4F);
        if (volume <= 0.0F) return;
        entity.playSound(swimSound, volume, vanillaPitch(random));
    }
}
