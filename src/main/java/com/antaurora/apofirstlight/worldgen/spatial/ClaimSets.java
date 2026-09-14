package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailure;
import com.antaurora.apofirstlight.worldgen.core.GenerationFailureReason;

/** Package-local normalization shared by immutable query publication and snapshot construction. */
final class ClaimSets {
    private ClaimSets() {}
    static final Comparator<SpatialClaim> ORDER = Comparator.comparingInt(SpatialClaim::priority).reversed()
            .thenComparing(SpatialClaim::id);

    record Normalized(List<SpatialClaim> claims, List<GenerationFailure> failures) {}

    /** Identical IDs deduplicate; ALL variants of an inconsistent ID are quarantined, never last-wins. */
    static Normalized normalize(List<SpatialClaim> input) {
        var byId = new TreeMap<String, SpatialClaim>();
        var invalid = new TreeSet<String>();
        for (var claim : List.copyOf(input)) {
            var previous = byId.putIfAbsent(claim.id(), claim);
            if (previous != null && !previous.equals(claim)) invalid.add(claim.id());
        }
        var failures = new ArrayList<GenerationFailure>();
        for (String id : invalid) {
            byId.remove(id);
            failures.add(mismatch("Inconsistent content/version for claim ID: " + id));
        }
        var claims = byId.values().stream().sorted(ORDER).toList();
        if (claims.stream().map(SpatialClaim::generationVersion).distinct().limit(2).count() > 1)
            failures.add(mismatch("Mixed generation versions in one claim snapshot"));
        return new Normalized(claims, List.copyOf(failures));
    }

    static GenerationFailure mismatch(String message) {
        return new GenerationFailure(GenerationFailureReason.VERSION_MISMATCH, message);
    }

    static List<GenerationFailure> failures(List<GenerationFailure> input) {
        return List.copyOf(input).stream().distinct().sorted(Comparator
                .comparing((GenerationFailure f) -> f.reason().name()).thenComparing(GenerationFailure::message)).toList();
    }
}
