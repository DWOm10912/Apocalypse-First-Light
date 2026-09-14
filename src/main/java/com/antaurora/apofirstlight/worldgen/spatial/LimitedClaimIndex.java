package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.core.*;
import com.antaurora.apofirstlight.worldgen.profile.WorldgenProfile;

/**
 * Server-thread-owned finite mirror; NOT thread-safe and NOT a first-writer-wins allocator.
 * Distinct accepted IDs may overlap: this records authoritative decisions, never arbitrates them.
 * No global instance, SavedData, dirty flag, automatic eviction/release, scanning or world callback.
 * Future lifecycle adapters must validate source authority before submitting these pure values.
 */
public final class LimitedClaimIndex {
    public static final int MAX_ENTRIES = 100_000;
    private final long worldSeed;
    private final WorldgenProfile profile;
    private final int capacity;
    private long indexRevision;
    private final TreeMap<String, ClaimIndexSnapshot.VerifiedEntry> entries = new TreeMap<>();
    public enum UpdateStatus { INSERTED, ADVANCED, IDEMPOTENT, REJECTED }
    public record UpdateResult(UpdateStatus status, List<GenerationFailure> failures) {
        public UpdateResult {
            Objects.requireNonNull(status); failures = List.copyOf(failures);
            if ((status == UpdateStatus.REJECTED) == failures.isEmpty()) throw new IllegalArgumentException("Invalid update result");
        }
    }
    public LimitedClaimIndex(long worldSeed, WorldgenProfile profile, int capacity) {
        this(new ClaimIndexSnapshot(worldSeed, profile, 0, capacity, List.of()));
    }
    /** Explicit restore only; persisted confirmation is NEVER reused as current source verification. */
    public LimitedClaimIndex(ClaimIndexSnapshot restored) {
        this.worldSeed = restored.worldSeed(); this.profile = restored.profile(); this.capacity = restored.capacity();
        this.indexRevision = restored.indexRevision();
        for (var value : restored.entries()) entries.put(value.entry().claimId(),
                new ClaimIndexSnapshot.VerifiedEntry(value.entry(), ClaimIndexVerification.UNVERIFIED));
    }
    public ClaimIndexSnapshot snapshot() {
        return new ClaimIndexSnapshot(worldSeed, profile, indexRevision, capacity, List.copyOf(entries.values()));
    }
    /**
     * Repeating the same plan/stage/revision is idempotent. A newer source revision
     * may advance ACCEPTED->PARTIAL/COMMITTED or PARTIAL->COMMITTED. Old events never regress state;
     * COMMITTED->PARTIAL at a newer revision is rejected, requiring separate recovery policy.
     * Conflicting content is rejected and marks the retained entry MISMATCH, not overwritten.
     */
    public UpdateResult upsert(WorldgenProfile planProfile, ClaimIndexEntry incoming) {
        Objects.requireNonNull(incoming); Objects.requireNonNull(planProfile);
        if (!profile.equals(planProfile))
            return reject(GenerationFailureReason.VERSION_MISMATCH, "Plan profile/resource snapshot differs from frozen index");
        var previous = entries.get(incoming.claimId());
        if (previous != null && !previous.entry().samePlan(incoming)) {
            put(previous.entry(), ClaimIndexVerification.MISMATCH);
            return reject(GenerationFailureReason.VERSION_MISMATCH, "Conflicting claim content/version/digest/source: " + incoming.claimId());
        }
        if (!Objects.equals(profile.systemVersions().get(incoming.claim().owner()), incoming.claim().generationVersion()))
            return reject(GenerationFailureReason.VERSION_MISMATCH, "Entry outside frozen system version");
        if (previous == null) {
            if (entries.size() == capacity) return reject(GenerationFailureReason.BUDGET_EXCEEDED, "Finite index full; no eviction");
            put(incoming, ClaimIndexVerification.UNVERIFIED); return result(UpdateStatus.INSERTED);
        }
        var old = previous.entry();
        if (incoming.equals(old)) return result(UpdateStatus.IDEMPOTENT);
        if (incoming.revision() < old.revision()) return result(UpdateStatus.IDEMPOTENT);
        if (incoming.revision() == old.revision() && incoming.stage() != old.stage()) {
            put(old, ClaimIndexVerification.MISMATCH);
            return reject(GenerationFailureReason.VERSION_MISMATCH, "Different stages at same source revision");
        }
        if (incoming.stage() == old.stage()) {
            put(incoming, ClaimIndexVerification.UNVERIFIED); return result(UpdateStatus.ADVANCED);
        }
        if (old.stage() == ClaimStage.COMMITTED || incoming.stage() == ClaimStage.ACCEPTED_PLAN)
            return reject(GenerationFailureReason.VERSION_MISMATCH, "Lifecycle regression requires explicit recovery policy");
        put(incoming, ClaimIndexVerification.UNVERIFIED); return result(UpdateStatus.ADVANCED);
    }
    /** Missing index/source is UNVERIFIED, never proof of absence. Verification cannot change geometry. */
    public ClaimIndexVerification verify(String id, Optional<ClaimIndexEntry> source) {
        WorldgenProfile.requireText(id); Objects.requireNonNull(source);
        var value = entries.get(id);
        var status = ClaimIndexVerification.compare(value == null ? Optional.empty() : Optional.of(value.entry()), source);
        if (value != null) put(value.entry(), status);
        return status;
    }
    private void put(ClaimIndexEntry entry, ClaimIndexVerification status) {
        var value = new ClaimIndexSnapshot.VerifiedEntry(entry, status);
        if (value.equals(entries.get(entry.claimId()))) return;
        long next = Math.incrementExact(indexRevision); // Overflow must reject BEFORE mutating the map.
        entries.put(entry.claimId(), value); indexRevision = next;
    }
    private static UpdateResult result(UpdateStatus status) { return new UpdateResult(status, List.of()); }
    private static UpdateResult reject(GenerationFailureReason reason, String message) {
        return new UpdateResult(UpdateStatus.REJECTED, List.of(new GenerationFailure(reason, message)));
    }
}
