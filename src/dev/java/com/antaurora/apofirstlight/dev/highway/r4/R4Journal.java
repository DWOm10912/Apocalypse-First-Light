package com.antaurora.apofirstlight.dev.highway.r4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * The rules of the R4 write journal (docs/worldgen/highway_r4_sample_v1.md, F), pure and generic over the state type so
 * the offline check runs them on plain strings: comparing two preflights of the same request, the per-entry decision of
 * the batched build, and of the rollback.
 * <p>
 * Two preflights describe the same works when the recipe version, the journal positions and the written (after) states are
 * identical. Their recorded original (before) states may then differ only by changes the live world makes by itself
 * (grass spreading onto dirt or eaten back to dirt, a plant eaten, natural leaves decayed, snow): those are absorbed, the
 * newer preflight's states go into the journal. The same holds for an entry only one side has because the world itself
 * moved between the original and the target (a plant planned for removal was eaten: the newer preflight has nothing left to
 * write there). Any other difference fails closed.
 */
public final class R4Journal {
    private R4Journal() {}

    /** What the journal needs to know about a state type. */
    public interface States<S> {
        /** A change from was to now the world makes by itself (both already replaceable natural states). */
        boolean natural(S was, S now);
        String name(S s);
    }

    public record Diff(boolean sameRecipe, int onlyOld, int onlyNew, int targetChanged, int naturalChanges, int otherChanges, List<String> examples) {
        public boolean sameWorks() { return sameRecipe && onlyOld == 0 && onlyNew == 0 && targetChanged == 0; }

        /** The newer preflight may replace the older one. */
        public boolean acceptable() { return sameWorks() && otherChanges == 0; }

        public String summary() {
            return String.format(Locale.ROOT, "recipe %s, positions only in the older %d / only in the newer %d, target states changed %d, original states changed %d natural + %d other",
                    sameRecipe ? "same" : "CHANGED", onlyOld, onlyNew, targetChanged, naturalChanges, otherChanges);
        }
    }

    public static <S> Diff diff(String recipeOld, long[] posOld, S[] beforeOld, S[] afterOld, String recipeNew, long[] posNew, S[] beforeNew, S[] afterNew,
                                States<S> states, int maxExamples, LongFunction<String> where) {
        Map<Long, Integer> index = new HashMap<>(posOld.length * 2);
        for (int i = 0; i < posOld.length; i++) index.put(posOld[i], i);
        boolean[] matched = new boolean[posOld.length];
        int onlyNew = 0, target = 0, natural = 0, other = 0;
        List<String> examples = new ArrayList<>();
        for (int j = 0; j < posNew.length; j++) {
            Integer i = index.get(posNew[j]);
            if (i == null) {
                // held the target before (no entry), changed by itself since (snow fell where air is planned): natural
                boolean nat = states.natural(afterNew[j], beforeNew[j]);
                if (nat) natural++; else onlyNew++;
                if (examples.size() < maxExamples) examples.add((nat ? "natural new " : "new position ") + where.apply(posNew[j]) + ": " + states.name(beforeNew[j]) + " -> " + states.name(afterNew[j]));
                continue;
            }
            matched[i] = true;
            if (!afterOld[i].equals(afterNew[j])) {
                target++;
                if (examples.size() < maxExamples) examples.add("target " + where.apply(posNew[j]) + ": " + states.name(afterOld[i]) + " -> " + states.name(afterNew[j]));
            }
            if (!beforeOld[i].equals(beforeNew[j])) {
                boolean nat = states.natural(beforeOld[i], beforeNew[j]);
                if (nat) natural++; else other++;
                if (examples.size() < maxExamples)
                    examples.add((nat ? "natural " : "OTHER ") + where.apply(posNew[j]) + ": was " + states.name(beforeOld[i]) + ", now " + states.name(beforeNew[j]));
            }
        }
        int onlyOld = 0;
        for (int i = 0; i < posOld.length; i++) if (!matched[i]) {
            // the world reached the target by itself (a plant planned for removal was eaten): natural, nothing left to write
            boolean nat = states.natural(beforeOld[i], afterOld[i]);
            if (nat) natural++; else onlyOld++;
            if (examples.size() < maxExamples) examples.add((nat ? "natural done " : "dropped position ") + where.apply(posOld[i]) + ": " + states.name(beforeOld[i]) + " -> " + states.name(afterOld[i]));
        }
        return new Diff(recipeOld.equals(recipeNew), onlyOld, onlyNew, target, natural, other, List.copyOf(examples));
    }

    /** Build, one entry: write, already written, write over a natural change (record the state found), or leave it (conflict). */
    public enum Step { WRITE, ALREADY_DONE, WRITE_NATURAL, CONFLICT }

    public static <S> Step build(S current, S was, S will, States<S> states) {
        if (current.equals(was)) return Step.WRITE;
        if (current.equals(will)) return Step.ALREADY_DONE;
        if (states.natural(was, current)) return Step.WRITE_NATURAL;
        return Step.CONFLICT;
    }

    /** Remove, one entry: restore only what still holds the written state; keep anything changed since (never deleted). */
    public enum Undo { RESTORE, ORIGINAL, PRESERVE }

    public static <S> Undo remove(S current, S was, S will) {
        if (current.equals(will)) return Undo.RESTORE;
        if (current.equals(was)) return Undo.ORIGINAL;
        return Undo.PRESERVE;
    }

    /** Every journal position appears once (a second entry would record a state the first one wrote). */
    public static boolean unique(long[] pos) {
        var seen = new java.util.HashSet<Long>(pos.length * 2);
        for (long p : pos) if (!seen.add(p)) return false;
        return true;
    }

    /** FNV-1a over positions and state ids: a short fingerprint for the log. */
    public static String fingerprint(long[] pos, int[] ids) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < pos.length; i++) {
            h = (h ^ pos[i]) * 0x100000001b3L;
            h = (h ^ ids[i]) * 0x100000001b3L;
        }
        return String.format(Locale.ROOT, "%016x", h);
    }
}
