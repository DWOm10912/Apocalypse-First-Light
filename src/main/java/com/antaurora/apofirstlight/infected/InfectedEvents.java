package com.antaurora.apofirstlight.infected;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InvestigateNoiseGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectedEvents {
    private static final String GOAL_ADDED = "apocalypse_firstlight_hearing_goal_added";

    private InfectedEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Zombie zombie)
                || event.getEntity().getType() != EntityType.ZOMBIE
                || zombie.getPersistentData().getBoolean(GOAL_ADDED)) {
            return;
        }
        zombie.goalSelector.addGoal(4, new InvestigateNoiseGoal(zombie));
        zombie.getPersistentData().putBoolean(GOAL_ADDED, true);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)
                || !InfectedEntityRules.isSunlightImmune(zombie)
                || !InfectedEntityRules.isDirectSunlight(zombie)
                || !zombie.isOnFire()
                || zombie.getRemainingFireTicks() != 160) {
            return;
        }

        // Zombie.aiStep() uses setSecondsOnFire(8) for the vanilla sunlight burn.
        // Only this exact fresh duration in direct sunlight is cleared; other fire
        // durations, lava, and fire damage are intentionally left untouched.
        zombie.clearFire();
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL INFECTED] Prevented daylight ignition for Zombie={}", zombie.getId()
        );
    }
}
