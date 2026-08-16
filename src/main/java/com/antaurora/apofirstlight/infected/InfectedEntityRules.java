package com.antaurora.apofirstlight.infected;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;

public final class InfectedEntityRules {
    private InfectedEntityRules() {
    }

    public static boolean isNoiseResponsive(LivingEntity entity) {
        return entity.getType() == EntityType.ZOMBIE;
    }
}
