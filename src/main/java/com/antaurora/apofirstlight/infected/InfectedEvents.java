package com.antaurora.apofirstlight.infected;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InvestigateNoiseGoal;
import com.antaurora.apofirstlight.infected.ai.AflPlayerTargetGoal;
import com.antaurora.apofirstlight.infected.breach.InfectedBreachGoal;
import com.antaurora.apofirstlight.infected.breach.InfectedEntrySeekingGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Zombie zombie)) {
            return;
        }
        // Runs after normal spawn initialization and also for entities loaded from disk.
        disableVanillaReinforcements(zombie);
        if (zombie.getType() != EntityType.ZOMBIE) {
            return;
        }
        if (zombie.isBaby()) {
            zombie.setBaby(false);
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL INFECTED] Converted baby minecraft:zombie to adult entity={}", zombie.getId()
            );
        }
        if (!InfectedEntityRules.isNoiseResponsive(zombie) || !GOALS_ADDED.add(zombie)) {
            return;
        }
        zombie.goalSelector.addGoal(1, new InfectedBreachGoal(zombie));
        zombie.goalSelector.addGoal(2, new InfectedEntrySeekingGoal(zombie));
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingConversion(LivingConversionEvent.Post event) {
        if (event.getOutcome() instanceof Zombie zombie && !zombie.level().isClientSide()) {
            // Zombie conversions reroll attributes after the new entity has joined the level.
            disableVanillaReinforcements(zombie);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onZombieSummonAid(ZombieEvent.SummonAidEvent event) {
        if (event.getLevel().isClientSide()
                || !InfectedEntityRules.hasVanillaReinforcementsDisabled(event.getEntity())) {
            return;
        }
        // Reassert the attribute rule if another system changed it after world entry.
        disableVanillaReinforcements(event.getEntity());
        event.setResult(Event.Result.DENY);
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL INFECTED] Disabled vanilla reinforcement for Zombie={}", event.getEntity().getId()
        );
    }

    private static void disableVanillaReinforcements(Zombie zombie) {
        AttributeInstance chance = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (chance == null) {
            return;
        }
        // Only this dedicated attribute is cleared, including permanent leader bonuses.
        chance.removeModifiers();
        chance.setBaseValue(0.0D);
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL INFECTED] Reinforcement reset Zombie={} base={} effective={} modifiers={}",
                zombie.getId(), chance.getBaseValue(), chance.getValue(), chance.getModifiers().size()
        );
    }
}
