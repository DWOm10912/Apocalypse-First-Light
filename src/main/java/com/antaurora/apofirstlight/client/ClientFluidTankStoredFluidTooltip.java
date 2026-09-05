package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.StoredFluidTooltip;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientFluidTankStoredFluidTooltip {
    private ClientFluidTankStoredFluidTooltip() {
    }

    @SubscribeEvent
    public static void appendStoredFluid(ItemTooltipEvent event) {
        StoredFluidTooltip.append(event.getItemStack(), event.getToolTip());
    }
}
