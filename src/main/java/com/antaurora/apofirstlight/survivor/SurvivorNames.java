package com.antaurora.apofirstlight.survivor;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Survivor roll seeds and name rules (docs/gameplay/survivor_death_succession_v0.md 5 and 13). Successors come from the
 * fixed roster (SurvivorRoster: one name and one skin per person); this class keeps the deterministic roll seed, the sex
 * roll that steers the roster pick, the further roll streams, and the nickname rules for the first survivor of a lineage
 * (shared by the client field and the server check). The player never picks a successor's name.
 *
 * <p>Deterministic: the roll seed comes from the world seed, the account UUID and the career index, and the result is
 * stored at the death anyway.
 */
public final class SurvivorNames {
    private SurvivorNames() {
    }

    /** The roll seed for one life of one player in one world. */
    static long rollSeed(long worldSeed, UUID player, int lifeIndex) {
        long h = mix(worldSeed ^ 0x53555256L);   // "SURV"
        h = mix(h ^ player.getMostSignificantBits());
        h = mix(h ^ player.getLeastSignificantBits());
        return mix(h ^ lifeIndex * 0x9E3779B97F4A7C15L);
    }

    /** Longest nickname, in characters. */
    public static final int NICKNAME_MAX = 20;

    /** Characters a nickname may hold: letters and digits of any script, space, hyphen, apostrophe, period, underscore. */
    public static boolean nicknameChar(int cp) {
        return Character.isLetterOrDigit(cp) || cp == ' ' || cp == '-' || cp == '\'' || cp == '.' || cp == '_';
    }

    /**
     * The nickname as stored (trimmed, runs of spaces made single), or null when it is empty, longer than
     * {@link #NICKNAME_MAX}, holds no letter or holds any other character (formatting codes, symbols, emoji).
     */
    @Nullable
    public static String nickname(String raw) {
        String name = raw.strip().replaceAll(" {2,}", " ");
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NICKNAME_MAX) return null;
        if (!name.codePoints().allMatch(SurvivorNames::nicknameChar) || name.codePoints().noneMatch(Character::isLetter)) {
            return null;
        }
        return name;
    }

    /** The successor's sex, rolled before the roster pick (even odds; never taken from the account). */
    static SurvivorRecord.Sex sex(long seed) {
        return stream(seed, 9) < 0 ? SurvivorRecord.Sex.FEMALE : SurvivorRecord.Sex.MALE;
    }

    /** A further independent value from the roll seed (roster pick, spawn angle, distance, candidate order). */
    static long stream(long seed, int index) {
        return mix(seed ^ (index + 1L) * 0xD1B54A32D192ED03L);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
