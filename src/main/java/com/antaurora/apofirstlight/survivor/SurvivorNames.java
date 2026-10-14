package com.antaurora.apofirstlight.survivor;

import java.util.List;
import java.util.UUID;

/**
 * Survivor name rolls (docs/gameplay/survivor_death_succession_v0.md 5). V0 rolls only a name: there is no profession,
 * trait, body or origin system yet, so a random name is not a character build. Names are common North American given
 * names and surnames (the Columbian Federation's mixed population); the player never picks one.
 *
 * <p>Deterministic: the roll seed comes from the world seed, the account UUID and the career index, so the same death
 * always yields the same successor, and the result is stored at the death anyway.
 */
public final class SurvivorNames {
    static final List<String> FIRST = List.of(
            "Aaron", "Abigail", "Alex", "Alicia", "Andre", "Angela", "Ben", "Bianca", "Caleb", "Carmen",
            "Chris", "Claire", "Dale", "Dana", "Daniel", "Denise", "Eli", "Elena", "Eric", "Erin",
            "Frank", "Gloria", "Grace", "Hank", "Hector", "Holly", "Isaac", "Ivy", "Jake", "Janet",
            "Jesse", "Joanna", "Kevin", "Kim", "Lena", "Leon", "Lucas", "Maria", "Marcus", "Megan",
            "Miguel", "Nadia", "Nathan", "Nina", "Owen", "Paula", "Ray", "Rosa", "Ryan", "Sam",
            "Sandra", "Sean", "Tara", "Terry", "Tom", "Vanessa", "Victor", "Wade", "Wendy", "Zoe");
    static final List<String> LAST = List.of(
            "Alvarez", "Anderson", "Baker", "Bell", "Brooks", "Campbell", "Carter", "Castillo", "Chen", "Collins",
            "Cooper", "Cruz", "Davis", "Diaz", "Dubois", "Edwards", "Fischer", "Flores", "Foster", "Garcia",
            "Gray", "Hayes", "Hughes", "Jensen", "Kelly", "Kim", "Kowalski", "Lambert", "Larsen", "Lee",
            "Martin", "Mendez", "Miller", "Morgan", "Murphy", "Nguyen", "Novak", "O'Brien", "Ortiz", "Parker",
            "Patel", "Price", "Ramirez", "Reed", "Reyes", "Riley", "Russo", "Sanders", "Schmidt", "Shaw",
            "Silva", "Sullivan", "Tanaka", "Torres", "Turner", "Walker", "Ward", "Webb", "Young", "Zimmer");

    private SurvivorNames() {
    }

    /** The roll seed for one life of one player in one world. */
    static long rollSeed(long worldSeed, UUID player, int lifeIndex) {
        long h = mix(worldSeed ^ 0x53555256L);   // "SURV"
        h = mix(h ^ player.getMostSignificantBits());
        h = mix(h ^ player.getLeastSignificantBits());
        return mix(h ^ lifeIndex * 0x9E3779B97F4A7C15L);
    }

    static String first(long seed) {
        return FIRST.get((int) Long.remainderUnsigned(mix(seed ^ 0x46L), FIRST.size()));
    }

    static String last(long seed) {
        return LAST.get((int) Long.remainderUnsigned(mix(seed ^ 0x4CL), LAST.size()));
    }

    /** A further independent value from the roll seed (spawn angle, distance, candidate order). */
    static long stream(long seed, int index) {
        return mix(seed ^ (index + 1L) * 0xD1B54A32D192ED03L);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
