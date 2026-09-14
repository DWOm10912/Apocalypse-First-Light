package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Immutable, nonempty candidate reservation value, with no world reference or lifecycle state.
 * Missing Y means the full column; present Y must be nonempty. Bounds remain half-open.
 * Edges are inert references, never permission to overlap. Their ordered list is part of identity
 * content: providers must emit it deterministically. IDs are scoped to a caller-owned world snapshot.
 */
public record SpatialClaim(String id, ResourceLocation owner, ResourceKey<Level> dimension,
        String generationVersion, BoundsXZ boundsXZ, Optional<YRange> yRange, SpatialClaimType type,
        SpatialClaimStrength strength, int priority, int exclusionMargin, List<String> connectionEdges) {
    public SpatialClaim {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(dimension);
        Objects.requireNonNull(generationVersion); Objects.requireNonNull(boundsXZ);
        Objects.requireNonNull(yRange); Objects.requireNonNull(type); Objects.requireNonNull(strength);
        if (id.isBlank() || generationVersion.isBlank()) throw new IllegalArgumentException("Blank claim identity");
        if (boundsXZ.isEmpty() || yRange.filter(YRange::isEmpty).isPresent())
            throw new IllegalArgumentException("A claim must occupy nonempty bounds");
        if (exclusionMargin < 0) throw new IllegalArgumentException("Negative exclusion margin");
        ClaimPriorityPolicy.validatePriority(type, strength, priority);
        connectionEdges = List.copyOf(connectionEdges);
    }
}
