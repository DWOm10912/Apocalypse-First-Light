package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;

public record RadiationZoneAreaResult(
        BlockPos center,
        double distance,
        double baseField,
        RadiationZone baseZone,
        int matchingSamples,
        int totalSamples,
        int validationRadius
) {
}
