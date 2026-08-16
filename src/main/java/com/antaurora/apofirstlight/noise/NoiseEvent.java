package com.antaurora.apofirstlight.noise;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record NoiseEvent(
        @Nullable Entity source,
        Vec3 position,
        NoiseType type,
        long gameTime,
        @Nullable ResourceLocation sourceId,
        double radius
) {
    public NoiseEvent {
        if (radius < 0) {
            throw new IllegalArgumentException("Noise event radius cannot be negative");
        }
    }
}
