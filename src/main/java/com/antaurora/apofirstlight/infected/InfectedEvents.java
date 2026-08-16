package com.antaurora.apofirstlight.infected;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InvestigateNoiseGoal;
import com.antaurora.apofirstlight.infected.ai.AflPlayerTargetGoal;
import com.antaurora.apofirstlight.infected.breach.InfectedBreachGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectedEvents {
    private static final Set<Zombie> GOALS_ADDED = Collections.newSetFromMap(new WeakHashMap<>());

    private InfectedEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Zombie zombie)
                || event.getEntity().getType() != EntityType.ZOMBIE
                || !InfectedEntityRules.isNoiseResponsive(zombie)
                || !GOALS_ADDED.add(zombie)) {
            return;
        }
        zombie.goalSelector.addGoal(1, new InfectedBreachGoal(zombie));
        zombie.goalSelector.addGoal(4, new InvestigateNoiseGoal(zombie));
        zombie.targetSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getPriority() == 2
                        && wrapped.getGoal() instanceof NearestAttackableTargetGoal<?>)
                .map(wrapped -> wrapped.getGoal())
                .toList()
                .forEach(zombie.targetSelector::removeGoal);
        zombie.targetSelector.addGoal(2, new AflPlayerTargetGoal(zombie));
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL INFECTED DEBUG] Added InvestigateNoiseGoal and AFL player vision target goal Zombie={}", zombie.getId()
        );
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

    @SubscribeEvent
    public static void onZombieSummonAid(ZombieEvent.SummonAidEvent event) {
        if (!InfectedEntityRules.hasVanillaReinforcementsDisabled(event.getEntity())) {
            return;
        }
        event.setResult(Event.Result.DENY);
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL INFECTED] Disabled vanilla reinforcement for Zombie={}", event.getEntity().getId()
        );
    }
}
