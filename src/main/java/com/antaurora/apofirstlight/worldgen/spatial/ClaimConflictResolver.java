package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailure;
import static com.antaurora.apofirstlight.worldgen.spatial.ClaimConflict.Kind.*;

/** Pure finite-set arbitration. No accepted-plan memory, provider callbacks, writes or scoring. */
public final class ClaimConflictResolver {
    private ClaimConflictResolver() {}

    /**
     * Rectangular (L-infinity) exclusion: on each axis an actual overlap, or gap strictly less than
     * margin, is required. Exact gap == margin is allowed; margin 0 is ordinary half-open overlap.
     * Apply the PAIR maximum margin once, never expand both operands. All arithmetic uses long.
     * Empty BoundsXZ remains inert even with a margin (SpatialClaim itself rejects empty bounds).
     */
    public static boolean overlapsXZ(BoundsXZ a, BoundsXZ b, int margin) {
        Objects.requireNonNull(a); Objects.requireNonNull(b);
        if (margin < 0) throw new IllegalArgumentException("Negative margin");
        return !a.isEmpty() && !b.isEmpty()
                && (long) a.minX() < (long) b.maxXExclusive() + margin
                && (long) b.minX() < (long) a.maxXExclusive() + margin
                && (long) a.minZ() < (long) b.maxZExclusive() + margin
                && (long) b.minZ() < (long) a.maxZExclusive() + margin;
    }

    /**
     * Spatial primitive: callers must validate each owner-local version against the active profile
     * before arbitration. This method does NOT certify profile eligibility; same-ID full-content
     * collision checks remain defensive even for already validated inputs.
     */
    public static ClaimConflict resolveWinner(SpatialClaim a, SpatialClaim b) {
        Objects.requireNonNull(a); Objects.requireNonNull(b);
        if (a.id().equals(b.id())) {
            if (a.equals(b)) return new ClaimConflict(IDENTICAL, Optional.of(a), List.of());
            return invalid("Inconsistent content/version for claim ID: " + a.id());
        }
        if (!a.dimension().equals(b.dimension())) return outcome(NONE);
        if (!overlapsXZ(a.boundsXZ(), b.boundsXZ(), Math.max(a.exclusionMargin(), b.exclusionMargin()))
                || (a.yRange().isPresent() && b.yRange().isPresent()
                && !a.yRange().orElseThrow().intersects(b.yRange().orElseThrow()))) return outcome(NONE);
        if (a.strength() == SpatialClaimStrength.SOFT || b.strength() == SpatialClaimStrength.SOFT)
            return outcome(SOFT_OVERLAP);
        // connectionEdges are deliberately not examined: no connection exemption in V1.
        return new ClaimConflict(BLOCKING, Optional.of(ClaimSets.ORDER.compare(a, b) < 0 ? a : b), List.of());
    }

    private static ClaimConflict outcome(ClaimConflict.Kind kind) {
        return new ClaimConflict(kind, Optional.empty(), List.of());
    }

    private static ClaimConflict invalid(String message) {
        return new ClaimConflict(INVALID, Optional.empty(), List.of(ClaimSets.mismatch(message)));
    }

    /** UNKNOWN publishes no accepted candidates; rejected is also empty because arbitration was deferred. */
    public record Resolution(List<SpatialClaim> accepted, List<SpatialClaim> rejected,
                             ClaimQueryCompleteness completeness, List<GenerationFailure> failures) {
        public Resolution {
            accepted = List.copyOf(accepted); rejected = List.copyOf(rejected);
            Objects.requireNonNull(completeness); failures = ClaimSets.failures(failures);
            if (completeness == ClaimQueryCompleteness.UNKNOWN && (!accepted.isEmpty() || !rejected.isEmpty())
                    || completeness == ClaimQueryCompleteness.COMPLETE && !failures.isEmpty())
                throw new IllegalArgumentException("Incomplete arbitration cannot publish decisions");
        }
    }

    /**
     * Caller must supply a COMPLETE candidate envelope covering ALL potential competitors, not just
     * already accepted plans. Completeness is provider/area scoped; this method cannot certify coverage.
     * Every claim must also have passed owner-scoped profile validation; a raw COMPLETE query alone
     * is not an eligibility certificate. Use the profile layer's validated-set entry for coordination.
     * ADR-02: reject against EVERY higher-ranked hard candidate, even if that candidate loses elsewhere.
     * Thus A>B>C with overlaps A-B and B-C admits A only, not greedy A+C. Finite prototype O(n^2);
     * no lifecycle stage, refill, registration-order arbitration or durable acceptance is implied.
     */
    public static Resolution resolveCandidates(ClaimQueryResult candidates) {
        Objects.requireNonNull(candidates);
        if (candidates.isUnknown())
            return new Resolution(List.of(), List.of(), ClaimQueryCompleteness.UNKNOWN, candidates.failures());
        var accepted = new ArrayList<SpatialClaim>();
        var rejected = new ArrayList<SpatialClaim>();
        var claims = candidates.claims();
        for (int i = 0; i < claims.size(); i++) {
            var candidate = claims.get(i);
            boolean blocked = false;
            for (int j = 0; j < i; j++) {
                if (resolveWinner(claims.get(j), candidate).kind() == BLOCKING) { blocked = true; break; }
            }
            (blocked ? rejected : accepted).add(candidate);
        }
        return new Resolution(accepted, rejected, ClaimQueryCompleteness.COMPLETE, List.of());
    }
}
