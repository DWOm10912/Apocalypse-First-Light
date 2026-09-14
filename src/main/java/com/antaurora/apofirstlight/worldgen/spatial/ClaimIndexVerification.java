package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;
import java.util.Optional;

/** Comparison of supplied values only; this does not discover or authenticate a StructureStart. */
public enum ClaimIndexVerification {
    CONFIRMED, UNVERIFIED, STALE, MISMATCH;

    public static ClaimIndexVerification compare(Optional<ClaimIndexEntry> indexed, Optional<ClaimIndexEntry> source) {
        Objects.requireNonNull(indexed); Objects.requireNonNull(source);
        if (indexed.isEmpty() || source.isEmpty()) return UNVERIFIED;
        var a = indexed.orElseThrow(); var b = source.orElseThrow();
        if (!a.samePlan(b)) return MISMATCH;
        return a.revision() == b.revision() && a.stage() == b.stage() ? CONFIRMED : STALE;
    }
}
