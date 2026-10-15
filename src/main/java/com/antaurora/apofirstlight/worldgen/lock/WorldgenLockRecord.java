package com.antaurora.apofirstlight.worldgen.lock;

import java.util.List;
import java.util.Objects;

/**
 * The version lock a world keeps (data/apocalypse_firstlight_worldgen_lock.dat): the fingerprint its chunks are
 * generated with, where that baseline came from, and every accepted change. Pure value; WorldgenLockData stores it.
 *
 * @param origin  {@link #CREATED} (written when the world was created, before its first chunk) or {@link #ADOPTED}
 *                (a world from before the lock: the baseline is the build that first loaded it with the lock, and the
 *                chunks generated before that have unknown rules; the world stays LEGACY for ADR-11 purposes)
 */
public record WorldgenLockRecord(int schema, String origin, long createdMillis, String createdBy,
                                 WorldgenFingerprint fingerprint, List<HistoryEntry> history) {
    public static final int SCHEMA = 1;
    public static final String CREATED = "created", ADOPTED = "adopted_legacy";

    public record HistoryEntry(long timeMillis, String event, String detail) {
        public HistoryEntry {
            Objects.requireNonNull(event);
            Objects.requireNonNull(detail);
        }
    }

    public WorldgenLockRecord {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(createdBy);
        Objects.requireNonNull(fingerprint);
        history = List.copyOf(history);
    }

    /** Whether the world's own chunks before the lock have known rules (ADR-11: an adopted world stays LEGACY). */
    public boolean legacy() { return ADOPTED.equals(origin); }

    public WorldgenLockRecord withBaseline(WorldgenFingerprint next, HistoryEntry entry) {
        var h = new java.util.ArrayList<>(history);
        h.add(entry);
        // a long-lived world keeps at most the 64 latest entries (the first, its creation or adoption, is always kept)
        while (h.size() > 64) h.remove(1);
        return new WorldgenLockRecord(SCHEMA, origin, createdMillis, createdBy, next, h);
    }
}
