package com.antaurora.apofirstlight.infected;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

public final class InfectedEntityRules {
    private InfectedEntityRules() {
    }

    public static boolean isNoiseResponsive(LivingEntity entity) {
        return entity.getType() == EntityType.ZOMBIE;
    }

    public static boolean isInfected(LivingEntity entity) {
        return entity.getType() == EntityType.ZOMBIE;
    }

    public static boolean isSunlightImmune(LivingEntity entity) {
        return isInfected(entity);
    }

    public static boolean hasVanillaReinforcementsDisabled(LivingEntity entity) {
        return entity instanceof Zombie;
    }

    public static boolean isDirectSunlight(LivingEntity entity) {
        Level level = entity.level();
        return level.isDay() && level.canSeeSky(entity.blockPosition());
    }
}
