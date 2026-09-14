package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;
import com.antaurora.apofirstlight.worldgen.profile.WorldgenProfile;

/**
 * Finite accepted-site mirror, retaining the WHOLE WG-04 claim (including margin/edges).
 * V1 persists HARD sites/buildings/protected sites/connections only. SOFT zoning, candidates,
 * and INFRASTRUCTURE corridors belong to reconstructible providers, not this index.
 * A future finite infrastructure exception needs an explicit policy, not one entry per road segment.
 * Digest is an opaque caller-frozen content identifier, not recomputed/trusted world evidence.
 * revision is the authoritative source's monotonic revision, distinct from index revision.
 */
public record ClaimIndexEntry(SpatialClaim claim, ClaimStage stage, String planDigest,
                              String authoritativeSourceId, long revision) {
    public ClaimIndexEntry {
        Objects.requireNonNull(claim); Objects.requireNonNull(stage);
        WorldgenProfile.requireText(planDigest); WorldgenProfile.requireText(authoritativeSourceId);
        if (revision < 0) throw new IllegalArgumentException("Negative source revision");
        if (stage == ClaimStage.CANDIDATE_RESERVED || claim.strength() != SpatialClaimStrength.HARD
                || claim.type() == SpatialClaimType.INFRASTRUCTURE)
            throw new IllegalArgumentException("Only finite accepted HARD sites may be indexed");
    }
    public String claimId() { return claim.id(); }
    public boolean samePlan(ClaimIndexEntry other) {
        return claim.equals(other.claim) && planDigest.equals(other.planDigest)
                && authoritativeSourceId.equals(other.authoritativeSourceId);
    }
}
