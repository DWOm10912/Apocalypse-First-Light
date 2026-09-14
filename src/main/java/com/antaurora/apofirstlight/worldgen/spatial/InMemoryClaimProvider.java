package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailure;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailureReason;
import com.antaurora.apofirstlight.worldgen.core.QueryBudget;
import com.antaurora.apofirstlight.worldgen.core.WorldgenIdentity;

/**
 * Unregistered, immutable finite WG-04 prototype, NOT a live occupancy index or SavedData.
 * Construction copies/normalizes the entire supplied finite snapshot outside the QUERY budget:
 * identical IDs deduplicate, inconsistent IDs are quarantined, unsupported owners/versions are excluded
 * with snapshot-wide UNKNOWN diagnostics (even outside the requested area/dimension).
 * This is a SINGLE-OWNER fixture, not a cross-system coordinator. The snapshot is bound to
 * scope.system and its owner-local version, seed/resourceSnapshot; query.system may be any consumer
 * using this provider's version scope (not that consumer's own algorithm version),
 * and dimension filters claims. No worlds are read to prove that caller-supplied scope is correct.
 * Query visits normalized supported claims in priority-DESC/id-ASC order, charging ONE operation
 * for each visited claim, including area/dimension misses. It stops at maxOperations; result
 * copying/sorting is finite O(visited log visited), not an extra hidden scan of the snapshot.
 * Repeated and concurrent reads have identical results and consume no shared mutable budget.
 */
public final class InMemoryClaimProvider implements ClaimQuery {
    private final WorldgenIdentity scope;
    private final List<SpatialClaim> claims;
    private final List<GenerationFailure> failures;
    private final boolean available;

    public InMemoryClaimProvider(WorldgenIdentity scope, List<SpatialClaim> input, boolean available) {
        this.scope = Objects.requireNonNull(scope);
        this.available = available;
        var normalized = ClaimSets.normalize(input);
        var issues = new ArrayList<>(normalized.failures());
        var supported = new ArrayList<SpatialClaim>();
        for (var claim : normalized.claims()) {
            if (scope.system().equals(claim.owner()) && scope.generationVersion().equals(claim.generationVersion()))
                supported.add(claim);
            else issues.add(ClaimSets.mismatch("Unsupported snapshot claim owner/version for ID: " + claim.id()));
        }
        this.claims = List.copyOf(supported);
        this.failures = ClaimSets.failures(issues);
    }

    @Override
    public ClaimQueryResult query(WorldgenIdentity identity, BoundsXZ area, QueryBudget budget) {
        Objects.requireNonNull(identity); Objects.requireNonNull(area); Objects.requireNonNull(budget);
        if (identity.seed() != scope.seed() || !identity.generationVersion().equals(scope.generationVersion())
                || !identity.resourceSnapshot().equals(scope.resourceSnapshot()))
            return new ClaimQueryResult(List.of(), ClaimQueryCompleteness.UNKNOWN,
                    List.of(ClaimSets.mismatch("Query seed/version/resource snapshot does not match provider scope")), 0);
        if (!available) return new ClaimQueryResult(List.of(), ClaimQueryCompleteness.UNKNOWN,
                List.of(new GenerationFailure(GenerationFailureReason.MISSING_RESOURCE, "Claim snapshot unavailable")), 0);
        var issues = new ArrayList<>(failures);
        var found = new ArrayList<SpatialClaim>();
        int used = 0;
        for (var claim : claims) {
            if (used == budget.maxOperations()) {
                issues.add(new GenerationFailure(GenerationFailureReason.BUDGET_EXCEEDED, "Claim snapshot query budget exhausted"));
                break;
            }
            used++;
            if (identity.dimension().equals(claim.dimension())
                    && ClaimConflictResolver.overlapsXZ(claim.boundsXZ(), area, claim.exclusionMargin())) found.add(claim);
        }
        return new ClaimQueryResult(found, issues.isEmpty() ? ClaimQueryCompleteness.COMPLETE : ClaimQueryCompleteness.UNKNOWN,
                issues, used);
    }
}
