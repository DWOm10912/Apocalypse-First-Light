package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class AflBiomes {
    /** Bound by data/apocalypse_firstlight/worldgen/biome/irradiated_woodland.json. */
    public static final ResourceKey<Biome> IRRADIATED_WOODLAND = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "irradiated_woodland"));
    public static final ResourceKey<Biome> FALLOUT_BARRENS = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fallout_barrens"));

    private AflBiomes() {
    }
}
