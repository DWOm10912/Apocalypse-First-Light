package com.antaurora.apofirstlight.infected.breach;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Immutable, static pursuit data that Breach is allowed to consume. */
public record InfectedBreachContext(
        Vec3 targetPosition,
        Source source,
        long createdGameTime,
        @Nullable String noiseType
) {
    public enum Source {
        VISION_CONFIRMED,
        LAST_VISIBLE,
        HIGH_INTENSITY_NOISE
    }
}
