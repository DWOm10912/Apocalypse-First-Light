package com.antaurora.apofirstlight.world.bunker;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BunkerWorldEvents {
    private BunkerWorldEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        BunkerPlacementManager.ensureGenerated(overworld);
    }
}
