package com.antaurora.apofirstlight.integration.tacz;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczDebugEvents {
    private TaczDebugEvents() {
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getShooter() instanceof ServerPlayer player)) {
            return;
        }

        IGun gun = IGun.getIGunOrNull(event.getGunItemStack());
        ResourceLocation gunId = gun != null ? gun.getGunId(event.getGunItemStack()) : null;

        ApocalypseFirstLight.LOGGER.info("[AFL DEBUG] Player fired TaCZ weapon");
        ApocalypseFirstLight.LOGGER.info("[AFL DEBUG] Player: {}", player.getGameProfile().getName());
        ApocalypseFirstLight.LOGGER.info("[AFL DEBUG] Gun ID: {}", gunId != null ? gunId : "unknown");
    }
}
