package com.antaurora.apofirstlight.worldgen.core;

import java.util.Objects;
import com.antaurora.apofirstlight.worldgen.spatial.Bounds3i;

/**
 * Coordinate permission only: lifecycle allowance intersected with the plan envelope.
 * Callers supply height limits. This does not check protection, ownership claims, availability
 * or block entities, and neither reads nor writes a world.
 */
public record ChunkWriteBounds(Bounds3i allowed, Bounds3i planEnvelope) {
    public ChunkWriteBounds {
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(planEnvelope, "planEnvelope");
    }
    public Bounds3i effective() { return allowed.intersection(planEnvelope); }
    public boolean canWrite(int x, int y, int z) {
        return allowed.contains(x, y, z) && planEnvelope.contains(x, y, z);
    }
}
