package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadiationSicknessEvents {
    private RadiationSicknessEvents() {
    }

    @SubscribeEvent
    public static void reducePlayerHealing(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getCapability(RadiationExposureProvider.CAPABILITY).ifPresent(exposure -> {
            int stage = RadiationSicknessManager.getStage(exposure.getDose());
            if (stage == 0) return;
            float amount = event.getAmount();
            if (!Float.isFinite(amount) || amount <= 0.0F) {
                event.setAmount(0.0F);
                return;
            }
            float multiplier = RadiationSicknessManager.healingMultiplier(stage);
            if (multiplier >= 1.0F) return;
            event.setAmount(Math.max(0.0F, amount * multiplier));
        });
    }

    @SubscribeEvent
    public static void refreshOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationSicknessManager.refreshState(player);
        }
    }

    @SubscribeEvent
    public static void refreshOnRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationSicknessManager.refreshState(player);
        }
    }

    @SubscribeEvent
    public static void clearOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationSicknessManager.clearTransientState(player);
        }
    }
}
