package com.antaurora.apofirstlight.worldgen;

import com.antaurora.apofirstlight.world.biome.AflVanillaBiomePolicy;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Immutable state owned by one TerraBlender ParameterList, not by a worker or global world. */
public record StartupEcologyState(long seed, Holder<Biome> plains, Holder<Biome> woodland) {
    public StartupEcologyState {
        java.util.Objects.requireNonNull(plains,"Startup plains holder");
        java.util.Objects.requireNonNull(woodland,"Startup woodland holder");
    }
    public Holder<Biome> resolve(int quartX,int quartY,int quartZ,Holder<Biome> original) {
        boolean underground=AflVanillaBiomePolicy.isAllowedUndergroundBiome(original.unwrapKey().orElse(null));
        if(underground&&!StartupPlainsEnclave.isSurfaceQuartY(quartY))return original;
        return switch(StartupPlainsEnclave.zoneAt(quartX<<2,quartZ<<2,seed)) {
            case CORE_PLAINS,FRINGE_PLAINS -> plains;
            case WOODLAND_BUFFER -> woodland;
            // Preserve existing surface cave-biome policy; ordinary outside biomes are untouched.
            case OUTSIDE -> underground?plains:original;
        };
    }
}
