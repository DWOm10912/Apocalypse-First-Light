package com.antaurora.apofirstlight.worldgen.profile;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailure;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailureReason;
import com.antaurora.apofirstlight.worldgen.spatial.*;

/**
 * WG-05.1 owner-local eligibility before spatial arbitration. Pure finite values, no providers/IO.
 * Caller supplies the active, frozen profile after session/profile preflight. This validator does
 * not establish seed/resource provenance or coverage; it never upgrades UNKNOWN evidence to COMPLETE.
 */
public final class ClaimProfileCompatibility {
    private ClaimProfileCompatibility() {}

    public enum Status { COMPATIBLE, MISSING_OWNER_VERSION, VERSION_MISMATCH, PROFILE_UNAVAILABLE }

    public record Result(Status status, List<GenerationFailure> failures) {
        public Result {
            Objects.requireNonNull(status); failures = List.copyOf(failures);
            if ((status == Status.COMPATIBLE) != failures.isEmpty())
                throw new IllegalArgumentException("Invalid claim compatibility result");
        }
    }

    public static Result check(Optional<WorldgenProfile> profile, SpatialClaim claim) {
        Objects.requireNonNull(profile); Objects.requireNonNull(claim);
        if (profile.isEmpty()) return unavailable();
        var expected = profile.orElseThrow().systemVersions().get(claim.owner());
        if (expected == null) return failure(Status.MISSING_OWNER_VERSION, GenerationFailureReason.VERSION_MISMATCH,
                "Profile has no version for claim owner: " + claim.owner());
        if (!expected.equals(claim.generationVersion()))
            return failure(Status.VERSION_MISMATCH, GenerationFailureReason.VERSION_MISMATCH,
                    "Claim owner-local version mismatch: " + claim.id() + "; owner=" + claim.owner()
                            + "; expected=" + expected + "; actual=" + claim.generationVersion());
        return new Result(Status.COMPATIBLE, List.of());
    }

    /**
     * Only constructed by validation. Compatible evidence is retained for diagnostics even on UNKNOWN,
     * but resolveCandidates then publishes NO accepted/rejected decisions. Not proof of free terrain.
     */
    public static final class ValidatedClaims {
        private final ClaimQueryResult query;
        private ValidatedClaims(ClaimQueryResult query) { this.query = query; }
        public ClaimQueryResult query() { return query; }
        public ClaimConflictResolver.Resolution resolveCandidates() {
            return ClaimConflictResolver.resolveCandidates(query);
        }
    }

    /**
     * Validate a normalized finite query, preserving its completeness, failures and operation count.
     * Normalization happens BEFORE eligibility filtering, so invalid duplicate variants cannot be
     * laundered into an apparently valid surviving ID. Any incompatible member defers the ENTIRE set.
     * The finite validation pass is separate from the provider's query-operation budget.
     */
    public static ValidatedClaims validateClaimsForProfile(Optional<WorldgenProfile> profile, ClaimQueryResult candidates) {
        Objects.requireNonNull(profile); Objects.requireNonNull(candidates);
        var failures = new ArrayList<>(candidates.failures());
        var compatible = new ArrayList<SpatialClaim>();
        // Absence must fail closed even for an empty candidate list.
        if (profile.isEmpty()) failures.addAll(unavailable().failures());
        else for (var claim : candidates.claims()) {
            var result = check(profile, claim);
            if (result.status() == Status.COMPATIBLE) compatible.add(claim);
            else failures.addAll(result.failures());
        }
        return new ValidatedClaims(new ClaimQueryResult(compatible, candidates.completeness(), failures, candidates.operationsUsed()));
    }

    private static Result unavailable() {
        return failure(Status.PROFILE_UNAVAILABLE, GenerationFailureReason.MISSING_RESOURCE,
                "Active frozen claim profile unavailable; no implicit version compatibility");
    }

    private static Result failure(Status status, GenerationFailureReason reason, String message) {
        return new Result(status, List.of(new GenerationFailure(reason, message)));
    }
}
