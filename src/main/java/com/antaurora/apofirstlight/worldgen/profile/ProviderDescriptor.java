package com.antaurora.apofirstlight.worldgen.profile;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import com.antaurora.apofirstlight.worldgen.spatial.ClaimQueryCompleteness;

/**
 * Declarative synthetic contract, NOT registration. A real activation adapter must audit these
 * promises. In particular corridorCompatibleProtection is an explicit placement-policy guarantee,
 * not inferred from an empty sampled query or the mere existence of ClaimQuery (ADR-03).
 * Deterministic infinite corridor providers need no persisted segment/index entries.
 */
public record ProviderDescriptor(ResourceLocation providerId, String generationVersion, Set<Capability> capabilities,
        boolean deterministic, ClaimQueryCompleteness queryCompletenessGuarantee, boolean corridorCompatibleProtection) {
    public enum Capability { CLAIM_QUERY, DETERMINISTIC_CANDIDATES, PROTECTION_QUERY }
    public ProviderDescriptor {
        Objects.requireNonNull(providerId); WorldgenProfile.requireText(generationVersion);
        capabilities = Set.copyOf(capabilities); Objects.requireNonNull(queryCompletenessGuarantee);
    }
}
