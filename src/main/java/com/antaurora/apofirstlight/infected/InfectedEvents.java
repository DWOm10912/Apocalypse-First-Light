package com.antaurora.apofirstlight.infected;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InvestigateNoiseGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
}
