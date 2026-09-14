package com.antaurora.apofirstlight.worldgen.profile;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.core.*;
import net.minecraft.resources.ResourceLocation;

/** Exact session/plan freeze, not latest-version selection. Missing world profile is explicitly LEGACY. */
public final class ProfileCompatibility {
    private ProfileCompatibility() {}
    public enum Status { COMPATIBLE, INCOMPATIBLE, LEGACY }
    public record Result(Status status, List<GenerationFailure> failures) {
        public Result {
            Objects.requireNonNull(status); failures = List.copyOf(failures);
            if ((status == Status.COMPATIBLE) != failures.isEmpty()) throw new IllegalArgumentException("Invalid compatibility result");
        }
    }
    public static Result check(Optional<WorldgenProfile> frozen, WorldgenProfile requested, Set<ResourceLocation> required) {
        Objects.requireNonNull(frozen); Objects.requireNonNull(requested); required = Set.copyOf(required);
        if (frozen.isEmpty()) return new Result(Status.LEGACY, List.of(mismatch("Missing frozen profile: LEGACY; no automatic migration")));
        var issues = new ArrayList<GenerationFailure>();
        if (!frozen.orElseThrow().equals(requested)) issues.add(mismatch("Frozen profile/system versions/resource snapshot differ"));
        required.stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            if (!requested.systemVersions().containsKey(id)) issues.add(mismatch("Missing required system version: " + id));
        });
        return new Result(issues.isEmpty() ? Status.COMPATIBLE : Status.INCOMPATIBLE, issues);
    }
    public static GenerationFailure mismatch(String message) {
        return new GenerationFailure(GenerationFailureReason.VERSION_MISMATCH, message);
    }
}
