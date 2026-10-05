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
        event.registerSpriteSet(AflParticles.CHAMBER_GAS.get(), ChamberGasParticle.Provider::new);
        event.registerSpriteSet(AflParticles.COLD_MIST.get(), ColdMistParticle.Provider::new);
        event.registerSpriteSet(AflParticles.FALLOUT_DUST.get(), FalloutDustParticle.Provider::new);
        event.registerSpriteSet(AflParticles.HIT_YELLOW_STAR.get(), StripHitParticle.Provider::new);
        event.registerSpriteSet(AflParticles.HIT_BLUE_STAR.get(), StripHitParticle.Provider::new);
        event.registerSpriteSet(AflParticles.HIT_DIZZY.get(), StripHitParticle.Provider::new);
        // fuel drips: no sprite set of their own, the fuel's still texture from the block atlas (velocity in blocks a tick)
        event.registerSpecial(AflParticles.GASOLINE_DRIP.get(), (type, level, x, y, z, xd, yd, zd) -> LiquidDroplet.of(level,
                new net.minecraft.world.phys.Vec3(x, y, z), new net.minecraft.world.phys.Vec3(xd, yd, zd).scale(20), com.antaurora.apofirstlight.registry.AflFluids.GASOLINE.get(), 0.018F));
        event.registerSpecial(AflParticles.DIESEL_DRIP.get(), (type, level, x, y, z, xd, yd, zd) -> LiquidDroplet.of(level,
                new net.minecraft.world.phys.Vec3(x, y, z), new net.minecraft.world.phys.Vec3(xd, yd, zd).scale(20), com.antaurora.apofirstlight.registry.AflFluids.DIESEL.get(), 0.018F));
    }
}
