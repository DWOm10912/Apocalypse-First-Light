package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadiationExposureEvents {
    private static final ResourceLocation CAPABILITY_ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "radiation_exposure");
    private static final int UPDATE_INTERVAL_TICKS = 20;

    private RadiationExposureEvents() {}

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            event.addCapability(CAPABILITY_ID, new RadiationExposureProvider());
        }
    }

    @SubscribeEvent
    public static void copyOnClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(RadiationExposureProvider.CAPABILITY).ifPresent(original ->
                event.getEntity().getCapability(RadiationExposureProvider.CAPABILITY)
                        .ifPresent(copy -> copy.setDose(original.getDose())));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void accumulateDose(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % UPDATE_INTERVAL_TICKS != 0) return;
        double rate = RadiationManager.getFinalRadiation(player.serverLevel(), player.blockPosition());
        player.getCapability(RadiationExposureProvider.CAPABILITY)
                .ifPresent(exposure -> exposure.addDose(rate / 3600.0));
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(RadiationExposureData.class);
        }
    }
}
