package com.antaurora.apofirstlight.worldgen.federal;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/** Implemented on BiomeSource (BiomeSourceFederalMixin): extra possible biomes for the federal experimental overworld. */
public interface FederalBiomeSourceAccess {
    void apocalypse$setFederalBiomes(List<Holder<Biome>> biomes);
}
