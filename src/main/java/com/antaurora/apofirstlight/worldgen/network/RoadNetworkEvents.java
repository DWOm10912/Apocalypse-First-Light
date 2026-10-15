package com.antaurora.apofirstlight.worldgen.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** C3.2: sends the overworld's built routes to each player at login (RoadNetworkS2CPacket; empty when there are none). */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoadNetworkEvents {
    private RoadNetworkEvents() {}

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(player.server.overworld());
        AflNetwork.sendRoadNetwork(player, new RoadNetworkS2CPacket(n == null ? "" : n.builtJson()));
    }
}
