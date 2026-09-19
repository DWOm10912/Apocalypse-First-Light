package com.antaurora.apofirstlight.worldgen;

import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroBiomePolicy;

/** Immutable state owned by one TerraBlender ParameterList, not by a worker or global world. */
public record StartupEcologyState(long seed, Holder<Biome> plains, Holder<Biome> fallout,
                                  Holder<Biome> ocean, Holder<Biome> deepOcean, Holder<Biome> beach,
                                  MacroGeography geography) {
    public StartupEcologyState {
        java.util.Objects.requireNonNull(plains,"Startup plains holder");
        java.util.Objects.requireNonNull(fallout,"Startup fallout holder");
    }
    public Holder<Biome> resolve(int quartX,int quartY,int quartZ,Holder<Biome> original) {
        boolean underground=AflVanillaBiomePolicy.isAllowedUndergroundBiome(original.unwrapKey().orElse(null));
        var sample = geography.sample(quartX << 2, quartZ << 2);
        if (underground && (quartY << 2) < sample.surfaceHeight() - 12) return original;
        var override = MacroBiomePolicy.override(sample, original.unwrapKey().orElse(null));
        if (Biomes.OCEAN.equals(override)) return ocean;
        if (Biomes.DEEP_OCEAN.equals(override)) return deepOcean;
        if (Biomes.BEACH.equals(override)) return beach;
        if (Biomes.PLAINS.equals(override)) original = plains;
        return switch(StartupPlainsEnclave.zoneAt(quartX<<2,quartZ<<2,seed)) {
            case CORE_PLAINS,FRINGE_PLAINS -> plains;
            case FALLOUT_BUFFER -> fallout;
            // Preserve existing surface cave-biome policy; ordinary outside biomes are untouched.
            case OUTSIDE -> underground?plains:original;
        };
    }
}
