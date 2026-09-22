package com.antaurora.apofirstlight.worldgen;

import com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan;
import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Immutable state owned by one TerraBlender ParameterList, not by a worker or global world. */
public record StartupEcologyState(long seed, Holder<Biome> plains, Holder<Biome> fallout,
                                  Holder<Biome> ocean, Holder<Biome> deepOcean, Holder<Biome> beach,
                                  MacroGeography geography, DensityFunction preliminarySurface,
                                  MainNationBiomeRegionPlan regionPlan) {
    public StartupEcologyState {
        java.util.Objects.requireNonNull(plains,"Startup plains holder");
        java.util.Objects.requireNonNull(fallout,"Startup fallout holder");
        java.util.Objects.requireNonNull(preliminarySurface,"Startup preliminary surface density");
    }
    public Holder<Biome> resolve(int quartX,int quartY,int quartZ,Holder<Biome> original) {
        var override = regionPlan.biomeAt(quartX << 2, quartY << 2, quartZ << 2,
                original.unwrapKey().orElse(null), preliminarySurface);
        if (Biomes.OCEAN.equals(override)) return ocean;
        if (Biomes.DEEP_OCEAN.equals(override)) return deepOcean;
        if (Biomes.BEACH.equals(override)) return beach;
        if (Biomes.PLAINS.equals(override)) return plains;
        if (AflBiomes.FALLOUT_BARRENS.equals(override)) return fallout;
        return original;
    }
}
