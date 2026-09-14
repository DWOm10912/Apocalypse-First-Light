package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailure;

/**
 * Defensive, stable priority-DESC/id-ASC publication. Duplicate content errors downgrade to UNKNOWN
 * and quarantine that ID. Any failure implies UNKNOWN; a caller cannot label a failure COMPLETE.
 * Partial claims are evidence only, not a complete candidate set for arbitration.
 */
public record ClaimQueryResult(List<SpatialClaim> claims, ClaimQueryCompleteness completeness,
                               List<GenerationFailure> failures, int operationsUsed) {
    public ClaimQueryResult {
        Objects.requireNonNull(completeness);
        if (operationsUsed < 0) throw new IllegalArgumentException("Negative operation count");
        var normalized = ClaimSets.normalize(claims);
        claims = normalized.claims();
        var combined = new ArrayList<>(List.copyOf(failures));
        combined.addAll(normalized.failures());
        failures = ClaimSets.failures(combined);
        if (!failures.isEmpty()) completeness = ClaimQueryCompleteness.UNKNOWN;
    }

    public boolean isKnownEmpty() { return completeness == ClaimQueryCompleteness.COMPLETE && claims.isEmpty(); }
    public boolean isUnknown() { return completeness == ClaimQueryCompleteness.UNKNOWN; }
    public boolean hasKnownClaims() { return !claims.isEmpty(); }
}
