package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;

/**
 * Fixed WG-04 policy. No JSON priority field or author-supplied override is supported.
 * BUILDING and CONNECTION share SITE's 50; no known-existing-content type is invented.
 * This validates priority values, NOT owner authorization; real provider authority is deferred.
 */
public final class ClaimPriorityPolicy {
    private ClaimPriorityPolicy() {}

    public static int defaultPriorityFor(SpatialClaimType type, SpatialClaimStrength strength) {
        Objects.requireNonNull(type); Objects.requireNonNull(strength);
        if (strength == SpatialClaimStrength.SOFT) return 10;
        return switch (type) {
            case PROTECTED_SITE -> 100;
            case INFRASTRUCTURE -> 70;
            case SITE, BUILDING, CONNECTION -> 50;
        };
    }

    public static void validatePriority(SpatialClaimType type, SpatialClaimStrength strength, int priority) {
        if (priority != defaultPriorityFor(type, strength))
            throw new IllegalArgumentException("Priority must equal the fixed type/strength policy");
    }
}
