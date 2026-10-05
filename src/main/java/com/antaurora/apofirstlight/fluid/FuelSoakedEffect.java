package com.antaurora.apofirstlight.fluid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gasoline- / diesel-soaked (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"): given by standing on a stain of that fuel
 * (fluid/FuelSpills: gasoline 30 s, diesel 90 s, renewed while standing in it). For now it only drips: drops of that fuel
 * (AflParticles gasoline_drip / diesel_drip, drawn as the jet's droplets, gone when they land) fall off the body, seen by
 * everyone; being set alight burning harder and longer comes with the ignition step. No swirl particles (added hidden).
 */
public final class FuelSoakedEffect extends MobEffect {
    private final boolean diesel;

    public FuelSoakedEffect(boolean diesel) {
        super(MobEffectCategory.HARMFUL, diesel ? 0xD29F44 : 0xE8D49A);
        this.diesel = diesel;
    }

    public boolean diesel() {
        return diesel;
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 6 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        double w = entity.getBbWidth() * 0.4, h = entity.getBbHeight();
        // count 0: the offsets are the drop's velocity (blocks a tick), a slow start downward
        level.sendParticles((diesel ? com.antaurora.apofirstlight.registry.AflParticles.DIESEL_DRIP : com.antaurora.apofirstlight.registry.AflParticles.GASOLINE_DRIP).get(),
                entity.getX() + (level.random.nextDouble() - 0.5) * 2 * w, entity.getY() + h * (0.2 + 0.5 * level.random.nextDouble()),
                entity.getZ() + (level.random.nextDouble() - 0.5) * 2 * w, 0, 0, -0.02, 0, 1);
    }
}
