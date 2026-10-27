package com.antaurora.apofirstlight.infected;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

public final class InfectedEntityRules {
    private InfectedEntityRules() {
    }

    public static boolean isNoiseResponsive(LivingEntity entity) {
        return entity.getType() == EntityType.ZOMBIE;
    }

    /** A vanilla zombie (old worlds, GameTests) or AFL's own infected (Infected Z1a, creature/infected/InfectedEntity). */
    public static boolean isInfected(LivingEntity entity) {
        return entity.getType() == EntityType.ZOMBIE || entity instanceof com.antaurora.apofirstlight.creature.infected.InfectedEntity;
    }

    public static boolean isSunlightImmune(LivingEntity entity) {
        return isInfected(entity);
    }

    public static boolean hasVanillaReinforcementsDisabled(LivingEntity entity) {
        return entity instanceof Zombie;
    }

}
