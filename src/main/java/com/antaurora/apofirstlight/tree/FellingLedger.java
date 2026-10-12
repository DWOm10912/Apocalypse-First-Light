package com.antaurora.apofirstlight.tree;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, L/M): the server's felling ledger, pure Java (the saved data
 * wraps it; the offline check drives it through many players, double last chops, reloads and duplicate falls).
 * <ul>
 * <li>work: chop work done per standing tree (root position); removed when the tree falls. Only trees someone has
 * chopped are here.</li>
 * <li>falling: falls under way (fall id -> root); settled: falls whose drops are out (fall id -> game time). A fall
 * settles once: a second settle of the same id (a falling tree entity restored twice) returns false. Settled ids are
 * kept for {@value #KEEP_SETTLED} ticks, longer than any save interval, then dropped, so the ledger stays small.</li>
 * <li>lastChop: per player, not saved; chops closer than MIN_CHOP_TICKS are ignored.</li>
 * </ul>
 */
final class FellingLedger {
    static final long KEEP_SETTLED = 24000;

    enum Result { COOLDOWN, PROGRESS, FELL }

    /** A chop's outcome: done work after it, the stage before and after. */
    record Chop(Result result, float done, int stageBefore, int stageAfter) {}

    final Map<Long, Float> work = new HashMap<>();
    final Map<UUID, Long> falling = new HashMap<>();
    final Map<UUID, Long> settled = new HashMap<>();
    final Map<UUID, Long> lastChop = new HashMap<>();

    /** One chop by a player on a standing tree that needs {@code need} work; the caller checked tool, reach and tree. */
    Chop chop(long root, UUID player, long tick, float power, float need) {
        Long last = lastChop.get(player);
        float before = work.getOrDefault(root, 0F);
        int stage = FellingRules.stage(before, need);
        if (last != null && tick - last < FellingRules.MIN_CHOP_TICKS && tick >= last) return new Chop(Result.COOLDOWN, before, stage, stage);
        lastChop.put(player, tick);
        float done = before + power;
        work.put(root, done);
        if (done >= need - 1e-4F) return new Chop(Result.FELL, done, stage, FellingRules.STAGES);
        return new Chop(Result.PROGRESS, done, stage, FellingRules.stage(done, need));
    }

    /** The tree started to fall (its fall id): its work entry goes, the fall is under way. */
    void fell(long root, UUID id) {
        work.remove(root);
        falling.put(id, root);
    }

    /** Whether this fall may give its drops now; true once per id. */
    boolean settle(UUID id, long tick) {
        if (settled.containsKey(id)) return false;
        settled.put(id, tick);
        falling.remove(id);
        settled.values().removeIf(t -> tick - t > KEEP_SETTLED || t > tick + KEEP_SETTLED);
        return true;
    }

    boolean isSettled(UUID id) { return settled.containsKey(id); }

    /** Forget a tree's work (it is gone: cleared by a command, or no longer a root). */
    void forget(long root) { work.remove(root); }
}
