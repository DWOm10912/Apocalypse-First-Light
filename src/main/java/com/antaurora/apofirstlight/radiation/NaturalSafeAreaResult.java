package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;

public record NaturalSafeAreaResult(
        BlockPos center,
        double distance,
        double baseField,
        RadiationZone baseZone,
        int safeSamples,
        int totalSamples,
        int validationRadius
) {
}
