package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Client-only biome-to-ambience mapping. Radiation values are deliberately not consulted here. */
public enum EnvironmentalParticleProfile {
    NONE,
    DEAD_LEAF_DEBRIS;

    public static EnvironmentalParticleProfile at(ClientLevel level, BlockPos position) {
        return level.getBiome(position).is(AflBiomes.IRRADIATED_WOODLAND)
                ? DEAD_LEAF_DEBRIS
                : NONE;
    }
}
