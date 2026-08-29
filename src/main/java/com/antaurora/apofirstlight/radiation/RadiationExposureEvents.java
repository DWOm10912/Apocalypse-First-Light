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
    private static final double RESIDUAL_ACCUMULATION_FACTOR_PER_SECOND = 0.02;
    private static final double RESIDUAL_DECAY_MULTIPLIER_PER_SECOND = 0.9995;
    private static final double RESIDUAL_ZERO_THRESHOLD = 0.01;

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
                        .ifPresent(copy -> {
                            copy.setDose(original.getDose());
                            if (event.isWasDeath()) {
                                copy.resetResidualRadiation();
                            } else {
                                copy.setResidualRadiationRate(original.getResidualRadiationRate());
                            }
                        }));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void accumulateDose(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % UPDATE_INTERVAL_TICKS != 0) return;
        RadiationSample sample = RadiationManager.getRadiationSample(player.serverLevel(), player.blockPosition());
        player.getCapability(RadiationExposureProvider.CAPABILITY).ifPresent(exposure -> {
            exposure.addDose(sample.finalRadiation() / 3600.0);
            if (sample.zone() != RadiationZone.SAFE && sample.finalRadiation() > exposure.getResidualRadiationRate()) {
                exposure.accumulateResidualToward(sample.finalRadiation(), RESIDUAL_ACCUMULATION_FACTOR_PER_SECOND);
            } else if (sample.zone() == RadiationZone.SAFE) {
                exposure.decayResidual(RESIDUAL_DECAY_MULTIPLIER_PER_SECOND, RESIDUAL_ZERO_THRESHOLD);
            }
        });
        RadiationSicknessManager.update(player);
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
