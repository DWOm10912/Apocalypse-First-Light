package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Registry-only save migration; facing and part properties are unchanged. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WorkstationLegacyMappings {
    private WorkstationLegacyMappings() {}
    @SubscribeEvent public static void remap(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, ApocalypseFirstLight.MOD_ID))
            if (mapping.getKey().getPath().equals("gun_workbench")) mapping.remap(AflBlocks.GUN_MAINTENANCE_BENCH.get());
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, ApocalypseFirstLight.MOD_ID))
            if (mapping.getKey().getPath().equals("gun_workbench")) mapping.remap(AflItems.GUN_MAINTENANCE_BENCH.get());
    }
}
