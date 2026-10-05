package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<SimpleParticleType> FALLOUT_DUST =
            PARTICLE_TYPES.register("fallout_dust", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> CHAMBER_GAS =
            PARTICLE_TYPES.register("chamber_gas", () -> new SimpleParticleType(false));

    /** Cold air of a powered chest freezer (client/ColdMist). */
    public static final RegistryObject<SimpleParticleType> COLD_MIST =
            PARTICLE_TYPES.register("cold_mist", () -> new SimpleParticleType(false));

    private AflParticles() {
    }
    public static final RegistryObject<SimpleParticleType> HIT_YELLOW_STAR =
            PARTICLE_TYPES.register("hit_yellow_star", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> HIT_BLUE_STAR =
            PARTICLE_TYPES.register("hit_blue_star", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> HIT_DIZZY =
            PARTICLE_TYPES.register("hit_dizzy", () -> new SimpleParticleType(false));
    /** A drop of fuel falling off a fuel-soaked body (fluid/FuelSoakedEffect): client/LiquidDroplet in that fuel's look. */
    public static final RegistryObject<SimpleParticleType> GASOLINE_DRIP =
            PARTICLE_TYPES.register("gasoline_drip", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DIESEL_DRIP =
            PARTICLE_TYPES.register("diesel_drip", () -> new SimpleParticleType(false));
}
