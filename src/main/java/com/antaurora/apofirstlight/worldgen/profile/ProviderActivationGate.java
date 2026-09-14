package com.antaurora.apofirstlight.worldgen.profile;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import com.antaurora.apofirstlight.worldgen.core.*;
import com.antaurora.apofirstlight.worldgen.spatial.*;

/**
 * Pure, area-scoped preflight of explicitly required providers. No provider callback/world access.
 * READY means supplied contracts/evidence pass, NOT live activation, collision-free world proof,
 * rerouting or authorization to bypass future placement checks. Missing legacy profile fails closed.
 * No owner-name-based City/Highway exclusion: geometry/soft-hard policy remains in claim resolvers.
 */
public final class ProviderActivationGate {
    private ProviderActivationGate() {}
    public enum Status { READY, UNKNOWN, INCOMPATIBLE, MISSING_PROVIDER, LEGACY_UNSUPPORTED }
    public record Evidence(ProviderDescriptor descriptor, WorldgenIdentity scope, BoundsXZ area, ClaimQueryResult query) {
        public Evidence { Objects.requireNonNull(descriptor); Objects.requireNonNull(scope); Objects.requireNonNull(area); Objects.requireNonNull(query); }
    }
    public record Issue(Status status, String detail) {
        public Issue {
            Objects.requireNonNull(status); WorldgenProfile.requireText(detail);
            if (status == Status.READY) throw new IllegalArgumentException("READY is not an issue");
        }
    }
    /** Canonical all-issues report; severity precedence is independent of input/provider order. */
    public record Result(List<Issue> issues) {
        public Result { issues = List.copyOf(issues).stream().distinct().sorted(Comparator
                .comparingInt((Issue i) -> rank(i.status())).reversed().thenComparing(Issue::detail)).toList(); }
        public Status status() { return issues.isEmpty() ? Status.READY : issues.get(0).status(); }
    }
    private static int rank(Status status) {
        return switch (status) {
            case READY -> 0; case UNKNOWN -> 1; case MISSING_PROVIDER -> 2;
            case INCOMPATIBLE -> 3; case LEGACY_UNSUPPORTED -> 4;
        };
    }
    public static Result evaluate(Optional<WorldgenProfile> frozen, WorldgenProfile requested,
            WorldgenIdentity activationScope, BoundsXZ area,
            Map<ResourceLocation, Set<ProviderDescriptor.Capability>> required, List<Evidence> supplied) {
        Objects.requireNonNull(activationScope); Objects.requireNonNull(area);
        var requirements = new TreeMap<ResourceLocation, Set<ProviderDescriptor.Capability>>(Comparator.comparing(ResourceLocation::toString));
        required.forEach((id, caps) -> requirements.put(Objects.requireNonNull(id), Set.copyOf(caps)));
        var compatibility = ProfileCompatibility.check(frozen, requested, requirements.keySet());
        var issues = new ArrayList<Issue>();
        if (compatibility.status() == ProfileCompatibility.Status.LEGACY)
            issues.add(new Issue(Status.LEGACY_UNSUPPORTED, "Legacy world has no frozen profile/protection baseline; no automatic activation"));
        else for (var failure : compatibility.failures()) issues.add(new Issue(Status.INCOMPATIBLE, failure.message()));
        if (requirements.isEmpty()) issues.add(new Issue(Status.INCOMPATIBLE, "Required-provider policy must not be empty"));
        if (area.isEmpty()) issues.add(new Issue(Status.UNKNOWN, "No nonempty activation area was evaluated"));
        if (!activationScope.resourceSnapshot().equals(requested.resourceSnapshot())
                || !Objects.equals(requested.systemVersions().get(activationScope.system()), activationScope.generationVersion()))
            issues.add(new Issue(Status.INCOMPATIBLE, "Activation scope differs from frozen system/resource profile"));
        var providers = new TreeMap<ResourceLocation, Evidence>(Comparator.comparing(ResourceLocation::toString));
        var duplicates = new HashSet<ResourceLocation>();
        for (var evidence : List.copyOf(supplied)) {
            var old = providers.putIfAbsent(evidence.descriptor().providerId(), evidence);
            if (old != null && !old.equals(evidence)) duplicates.add(evidence.descriptor().providerId());
        }
        for (var requirement : requirements.entrySet()) {
            var id = requirement.getKey(); var evidence = providers.get(id);
            if (duplicates.contains(id)) { issues.add(new Issue(Status.INCOMPATIBLE, "Conflicting provider evidence: " + id)); continue; }
            if (evidence == null) { issues.add(new Issue(Status.MISSING_PROVIDER, "Missing required provider: " + id)); continue; }
            var d = evidence.descriptor(); var scope = evidence.scope();
            if (!d.deterministic() || !d.capabilities().contains(ProviderDescriptor.Capability.CLAIM_QUERY)
                    || !d.capabilities().containsAll(requirement.getValue()))
                issues.add(new Issue(Status.INCOMPATIBLE, "Missing deterministic/capability contract: " + id));
            if (!Objects.equals(requested.systemVersions().get(id), d.generationVersion())
                    || !scope.generationVersion().equals(d.generationVersion()) || !scope.system().equals(id)
                    || scope.seed() != activationScope.seed() || !scope.dimension().equals(activationScope.dimension())
                    || !scope.resourceSnapshot().equals(requested.resourceSnapshot()))
                issues.add(new Issue(Status.INCOMPATIBLE, "Provider evidence scope/version mismatch: " + id));
            if (d.queryCompletenessGuarantee() != ClaimQueryCompleteness.COMPLETE || evidence.query().isUnknown())
                issues.add(new Issue(Status.UNKNOWN, "Provider cannot supply COMPLETE evidence: " + id));
            var coverage = evidence.area();
            if (coverage.isEmpty() || coverage.minX() > area.minX() || coverage.minZ() > area.minZ()
                    || coverage.maxXExclusive() < area.maxXExclusive() || coverage.maxZExclusive() < area.maxZExclusive())
                issues.add(new Issue(Status.UNKNOWN, "Provider evidence does not cover activation area: " + id));
            if (d.capabilities().contains(ProviderDescriptor.Capability.PROTECTION_QUERY) && !d.corridorCompatibleProtection())
                issues.add(new Issue(Status.UNKNOWN, "No audited corridor-compatible protection policy: " + id));
            for (var failure : evidence.query().failures()) {
                if (failure.reason() == GenerationFailureReason.VERSION_MISMATCH)
                    issues.add(new Issue(Status.INCOMPATIBLE, "Provider query version mismatch: " + id));
            }
            // A fabricated COMPLETE result from another scope must not pass as this provider's evidence.
            for (var claim : evidence.query().claims()) {
                if (!claim.owner().equals(id) || !claim.dimension().equals(scope.dimension())
                        || !claim.generationVersion().equals(d.generationVersion()))
                    issues.add(new Issue(Status.INCOMPATIBLE, "Out-of-scope provider claim: " + id));
            }
        }
        return new Result(issues);
    }
}
