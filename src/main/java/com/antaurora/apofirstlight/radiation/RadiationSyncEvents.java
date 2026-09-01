package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side radiation and Geiger data synchronization; it does not create particles. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadiationSyncEvents {
    private RadiationSyncEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 10 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) continue;
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                RadiationManager.PlayerRadiation playerRadiation = RadiationManager.getPlayerRadiation(player);
                RadiationSample worldSample = playerRadiation.worldSample();
                AflNetwork.sendRadiation(player, worldSample.finalRadiation());
                if (player.getMainHandItem().is(AflItems.GEIGER_COUNTER.get())
                        || player.getOffhandItem().is(AflItems.GEIGER_COUNTER.get())) {
                    player.getCapability(RadiationExposureProvider.CAPABILITY).ifPresent(exposure -> {
                        double measuredRate = worldSample.zone() == RadiationZone.SAFE
                                ? exposure.getResidualRadiationRate() + playerRadiation.carriedItemRadiation()
                                : playerRadiation.effectiveRadiation();
                        AflNetwork.sendGeigerData(player, measuredRate, exposure.getDose(),
                                exposure.getResidualRadiationRate(), worldSample.zone());
                    });
                }
            }
        }
    }
}
