package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class AflBiomes {
    public static final ResourceKey<Biome> FALLOUT_BARRENS = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fallout_barrens"));

    private AflBiomes() {
    }
}
