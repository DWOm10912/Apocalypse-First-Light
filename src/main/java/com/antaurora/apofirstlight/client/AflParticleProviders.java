package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflParticleProviders {
    private AflParticleProviders() {
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AflParticles.FALLOUT_DUST.get(), FalloutDustParticle.Provider::new);
        event.registerSpriteSet(AflParticles.HIT_YELLOW_STAR.get(), StripHitParticle.Provider::new);
        event.registerSpriteSet(AflParticles.HIT_BLUE_STAR.get(), StripHitParticle.Provider::new);
        event.registerSpriteSet(AflParticles.HIT_DIZZY.get(), StripHitParticle.Provider::new);
    }
}
