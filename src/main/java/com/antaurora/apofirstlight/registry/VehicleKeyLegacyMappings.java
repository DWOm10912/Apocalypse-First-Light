package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * Registry-only save migration (2026-10-21): the one shared vehicle_key became a key item per vehicle model. The T15 was
 * the only vehicle, so every old key is a T15 key; its bound vehicle UUID is in the stack's tag and carries over.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class VehicleKeyLegacyMappings {
    private VehicleKeyLegacyMappings() {}
    @SubscribeEvent public static void remap(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, ApocalypseFirstLight.MOD_ID))
            if (mapping.getKey().getPath().equals("vehicle_key")) mapping.remap(AflItems.PICKUP_T15_KEY.get());
    }
}
