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

    public static final RegistryObject<SimpleParticleType> DEAD_LEAF_DEBRIS =
            PARTICLE_TYPES.register("dead_leaf_debris", () -> new SimpleParticleType(false));

    private AflParticles() {
    }
}
