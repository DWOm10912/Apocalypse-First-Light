package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.core.*;
import com.antaurora.apofirstlight.worldgen.profile.WorldgenProfile;

/**
 * Immutable worker view. A finite mirror is NEVER proof of complete world coverage, even if every
 * stored source is confirmed. Queries therefore remain UNKNOWN (including an empty index).
 * Known PARTIAL/STALE/MISMATCH entries still return their protected envelope. Missing source data
 * does not release it. No conversion to WG-04 candidate acceptance is provided.
 * Mixed owner versions are legal if each matches the frozen profile. The separate query result
 * preserves mirror provenance; profile compatibility never upgrades mirror UNKNOWN to COMPLETE.
 */
public record ClaimIndexSnapshot(long worldSeed, WorldgenProfile profile, long indexRevision,
                                 int capacity, List<VerifiedEntry> entries) {
    public record VerifiedEntry(ClaimIndexEntry entry, ClaimIndexVerification verification) {
        public VerifiedEntry { Objects.requireNonNull(entry); Objects.requireNonNull(verification); }
    }
    public ClaimIndexSnapshot {
        Objects.requireNonNull(profile);
        if (indexRevision < 0 || capacity < 1 || capacity > LimitedClaimIndex.MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid index revision/capacity");
        entries = List.copyOf(entries);
        if (entries.size() > capacity) throw new IllegalArgumentException("Index exceeds capacity");
        var ids = new HashSet<String>();
        for (var value : entries) {
            if (!ids.add(value.entry().claimId())) throw new IllegalArgumentException("Duplicate index claim ID");
            if (!Objects.equals(profile.systemVersions().get(value.entry().claim().owner()), value.entry().claim().generationVersion()))
                throw new IllegalArgumentException("Entry outside frozen system versions");
        }
        entries = entries.stream().sorted(Comparator.comparing(v -> v.entry().claimId())).toList();
    }
    public record QueryResult(List<VerifiedEntry> entries, ClaimQueryCompleteness completeness,
                              List<GenerationFailure> failures, int operationsUsed) {
        public QueryResult {
            entries = List.copyOf(entries); failures = List.copyOf(failures);
            if (completeness != ClaimQueryCompleteness.UNKNOWN || operationsUsed < 0)
                throw new IllegalArgumentException("Mirror cannot certify world completeness");
        }
        public boolean isKnownEmpty() { return false; }
    }
    /** One operation per visited entry including dimension/area misses. No verification IO. */
    public QueryResult query(WorldgenIdentity identity, BoundsXZ area, QueryBudget budget) {
        Objects.requireNonNull(identity); Objects.requireNonNull(area); Objects.requireNonNull(budget);
        var failures = new ArrayList<GenerationFailure>();
        if (identity.seed() != worldSeed || !identity.resourceSnapshot().equals(profile.resourceSnapshot())
                || !Objects.equals(profile.systemVersions().get(identity.system()), identity.generationVersion()))
            return new QueryResult(List.of(), ClaimQueryCompleteness.UNKNOWN,
                    List.of(ClaimSets.mismatch("Query does not match frozen index world/profile")), 0);
        failures.add(new GenerationFailure(GenerationFailureReason.MISSING_RESOURCE, "Finite mirror does not prove authoritative world coverage"));
        var found = new ArrayList<VerifiedEntry>();
        int used = 0;
        for (var value : entries) {
            if (used == budget.maxOperations()) {
                failures.add(new GenerationFailure(GenerationFailureReason.BUDGET_EXCEEDED, "Finite index query budget exhausted")); break;
            }
            used++;
            var claim = value.entry().claim();
            if (identity.dimension().equals(claim.dimension()) && ClaimConflictResolver.overlapsXZ(claim.boundsXZ(), area, claim.exclusionMargin())) {
                found.add(value);
                if (value.verification() == ClaimIndexVerification.MISMATCH) failures.add(ClaimSets.mismatch("Source mismatch: " + claim.id()));
            }
        }
        return new QueryResult(found, ClaimQueryCompleteness.UNKNOWN, ClaimSets.failures(failures), used);
    }
}
