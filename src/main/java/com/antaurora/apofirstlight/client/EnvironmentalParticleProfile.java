package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Client-only biome-to-ambience mapping. Radiation values are deliberately not consulted here. */
public enum EnvironmentalParticleProfile {
    NONE,
    DEAD_LEAF_DEBRIS,
    FALLOUT_DUST,
    WHITE_ASH;

    public static EnvironmentalParticleProfile at(ClientLevel level, BlockPos position) {
        if (level.getBiome(position).is(AflBiomes.IRRADIATED_WOODLAND)) return DEAD_LEAF_DEBRIS;
        if (level.getBiome(position).is(AflBiomes.FALLOUT_BARRENS)) return FALLOUT_DUST;
        if (level.getBiome(position).is(AflBiomes.SCORCHED_LANDS)) return WHITE_ASH;
        return NONE;
    }
}
