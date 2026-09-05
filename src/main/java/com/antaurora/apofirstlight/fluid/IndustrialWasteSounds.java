package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

/** Sound-only counterpart of MC 1.20.1 Entity water splash/swim calculations. */
public final class IndustrialWasteSounds {
    private IndustrialWasteSounds() {
    }

    public static boolean isInWaste(Entity entity) {
        // Read Forge's existing collision-volume fluid contact; never change water identity or physics.
        return entity.getFluidTypeHeight(AflFluids.INDUSTRIAL_WASTE_TYPE.get()) > 0.0;
    }

    public static boolean canSound(Entity entity) {
        return !entity.level().isClientSide() && !entity.isSilent() && !entity.isInWater()
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

    public static void splash(Entity entity, RandomSource random) {
        if (!canSound(entity)) return;
        Entity owner = velocityOwner(entity);
        float volume = weightedVolume(owner.getDeltaMovement(), owner == entity ? 0.2F : 0.9F);
        boolean small = volume < 0.25F; // Vanilla small/high-speed splash branch.
        float pitch = vanillaPitch(random);
        if (small) {
            volume *= 0.65F;
            pitch = Math.min(1.45F, pitch * 1.075F);
        }
        broadcast(entity, AflSounds.INDUSTRIAL_WASTE_SPLASH.get(), volume, pitch);
    }

    public static void swim(Entity entity, Vec3 actualMovement, RandomSource random) {
        if (!canSound(entity)) return;
        Entity owner = velocityOwner(entity);
        // ServerPlayer movement arrives as position updates, not reliable horizontal deltaMovement.
        // Use the collision-resolved displacement for that case; the vanilla weighting is unchanged.
        Vec3 velocity = owner == entity && entity instanceof ServerPlayer
                ? actualMovement : owner.getDeltaMovement();
        float volume = weightedVolume(velocity, owner == entity ? 0.35F : 0.4F);
        if (volume <= 0.0F) return;
        float pitch = vanillaPitch(random);
        // Vanilla has no separate fast-swim event/threshold: sprinting selects the requested V1 variation.
        if (owner.isSprinting()) {
            volume = Math.min(1.0F, volume * 1.15F);
            pitch = Math.min(1.45F, pitch * 1.075F);
        }
        broadcast(entity, AflSounds.INDUSTRIAL_WASTE_SWIM.get(), volume, pitch);
    }

    private static void broadcast(Entity entity, SoundEvent sound, float volume, float pitch) {
        // null excludes nobody: the moving player and nearby players hear the same single server event.
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                sound, entity.getSoundSource(), volume, pitch);
    }
}
