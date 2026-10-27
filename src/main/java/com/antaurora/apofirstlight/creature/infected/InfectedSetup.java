package com.antaurora.apofirstlight.creature.infected;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Infected Z1a: the infected's attributes (mod bus). */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InfectedSetup {
    private InfectedSetup() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(AflEntities.INFECTED_SHAMBLER.get(), InfectedEntity.createAttributes().build());
    }
}
