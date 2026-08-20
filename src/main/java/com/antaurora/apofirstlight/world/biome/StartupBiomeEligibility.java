package com.antaurora.apofirstlight.world.biome;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Pure policy for whether an already-resolved surface biome is valid for AFL startup.
 * This class deliberately does not resolve biomes from a world or access chunks.
 */
public final class StartupBiomeEligibility {
    private StartupBiomeEligibility() {
    }

    public static boolean isStartupEligible(Holder<Biome> biome) {
        return biome != null && (biome.is(Biomes.PLAINS) || biome.is(AflBiomes.IRRADIATED_WOODLAND));
    }

    public static boolean isStartupEligible(ResourceKey<Biome> biome) {
        return Biomes.PLAINS.equals(biome) || AflBiomes.IRRADIATED_WOODLAND.equals(biome);
    }
}
