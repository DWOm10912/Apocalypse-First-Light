package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailure;

/** Symmetric pair outcome. IDENTICAL is idempotent, INVALID is not an occupancy verdict. */
public record ClaimConflict(Kind kind, Optional<SpatialClaim> winner, List<GenerationFailure> failures) {
    public enum Kind { NONE, BLOCKING, SOFT_OVERLAP, IDENTICAL, INVALID }
    public ClaimConflict {
        Objects.requireNonNull(kind); Objects.requireNonNull(winner);
        failures = ClaimSets.failures(failures);
        if (winner.isPresent() != (kind == Kind.BLOCKING || kind == Kind.IDENTICAL)
                || (kind == Kind.INVALID) != !failures.isEmpty())
            throw new IllegalArgumentException("Inconsistent conflict outcome");
    }
}
