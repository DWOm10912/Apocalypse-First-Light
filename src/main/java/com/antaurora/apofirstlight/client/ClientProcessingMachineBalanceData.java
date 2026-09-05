package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientProcessingMachineBalanceData {
    private static volatile int chemicalReactorWorkFePerTick;
    private static volatile int industrialFurnaceWorkFePerTickPerLane;

    private ClientProcessingMachineBalanceData() {
    }

    public static void update(int chemicalWorkFePerTick, int industrialWorkFePerTickPerLane) {
        chemicalReactorWorkFePerTick = Math.max(0, chemicalWorkFePerTick);
        industrialFurnaceWorkFePerTickPerLane = Math.max(0, industrialWorkFePerTickPerLane);
    }

    public static int chemicalReactorWorkFePerTick() {
        return chemicalReactorWorkFePerTick;
    }

    public static int industrialFurnaceWorkFePerTickPerLane() {
        return industrialFurnaceWorkFePerTickPerLane;
    }

    @SubscribeEvent
    public static void clearOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        chemicalReactorWorkFePerTick = 0;
        industrialFurnaceWorkFePerTickPerLane = 0;
    }
}
