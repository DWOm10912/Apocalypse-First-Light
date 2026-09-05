package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAlloyFurnaceBalanceData {
    private static volatile int workFePerTick;

    private ClientAlloyFurnaceBalanceData() {
    }

    public static void update(int updatedWorkFePerTick) {
        workFePerTick = Math.max(0, updatedWorkFePerTick);
    }

    public static int workFePerTick() {
        return workFePerTick;
    }

    @SubscribeEvent
    public static void clearOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        workFePerTick = 0;
    }
}
