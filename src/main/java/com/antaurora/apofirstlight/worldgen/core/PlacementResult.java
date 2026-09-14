package com.antaurora.apofirstlight.worldgen.core;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.ChunkPos;

/**
 * Immutable placement report, not a writer or completion tracker.
 * COMPLETE and APPLIED_SLICE require zero failed writes, but may retain caller-classified
 * best-effort diagnostics. APPLIED_SLICE does not imply global completion.
 * PARTIAL_COMMIT requires an actual change and either a failed write or failure detail.
 * Counts are independent; their sum is deliberately not computed.
 */
public record PlacementResult(PlacementStatus status, String planId, ChunkPos slice,
                              long changed, long skipped, long failed,
                              List<GenerationFailure> failures) {
    public PlacementResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(slice, "slice");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        if (planId.isBlank()) throw new IllegalArgumentException("Blank planId");
        if (changed < 0 || skipped < 0 || failed < 0) {
            throw new IllegalArgumentException("Negative placement count");
        }
        if (status == PlacementStatus.REJECTED_BEFORE_WRITE && changed != 0) {
            throw new IllegalArgumentException("Rejected placement cannot have changes");
        }
        if ((status == PlacementStatus.COMPLETE || status == PlacementStatus.APPLIED_SLICE) && failed != 0) {
            throw new IllegalArgumentException("Completed scope cannot have failed writes");
        }
        if (status == PlacementStatus.PARTIAL_COMMIT && (changed == 0 || (failed == 0 && failures.isEmpty()))) {
            throw new IllegalArgumentException("Partial commit requires changes and failure evidence");
        }
    }
}
