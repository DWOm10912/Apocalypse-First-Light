package com.antaurora.apofirstlight.worldgen.geography;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Shared positional surface decision for TerraBlender biome lookup and SurfaceSystem. */
public final class MacroBiomePolicy {
    private MacroBiomePolicy() {}

    /** Null means continue the existing AFL land/startup policy. */
    public static ResourceKey<Biome> override(MacroGeographySample sample, ResourceKey<Biome> original) {
        if (sample.isWater()) return sample.surfaceHeight() < 32 ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
        if (sample.surfaceClass() == MacroGeographySample.SurfaceClass.COAST) return Biomes.BEACH;
        return isMarine(original) ? Biomes.PLAINS : null;
    }

    public static boolean isMarine(ResourceKey<Biome> biome) {
        return Biomes.OCEAN.equals(biome) || Biomes.DEEP_OCEAN.equals(biome) || Biomes.BEACH.equals(biome);
    }
}
