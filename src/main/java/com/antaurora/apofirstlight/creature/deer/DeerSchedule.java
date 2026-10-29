package com.antaurora.apofirstlight.creature.deer;

/**
 * The grazing rhythm (docs/gameplay/afl_whitetail_deer_d0_design.md 3): head down 5–20 s pulling grass, a bite every
 * {@link #BITE_EVERY} ticks; head up 2–5 s chewing and looking round; again. Worked out from the deer's id, the game time
 * the grazing began (DeerEntity's synced STATE_AT) and the game time now, so the server (the bite sounds) and every client
 * (the head) keep the same beat without any packet.
 *
 * <p>D2b (doc 13): the day's round by the clock ({@link #bedTime}) and, lying, chewing the cud head up or resting head
 * down ({@link #resting}), worked out the same way.
 */
public final class DeerSchedule {
    /** Ticks between bites while the head is down; the first comes {@link #BITE_FIRST} ticks after it goes down. */
    public static final int BITE_EVERY = 32, BITE_FIRST = 14;

    private DeerSchedule() {
    }

    /** Head down at {@code now}? {@code into[0]} gets the ticks into the current head-down or head-up spell. */
    public static boolean headDown(int id, int start, long now, int[] into) {
        return spells(id, start, now, 100, 300, 40, 60, into);
    }

    /** Lying (BED): resting, head down, at {@code now}? It lies chewing the cud head up 30–90 s first, then rests 15–60 s,
     *  and so on. */
    public static boolean resting(int id, int start, long now) {
        return !spells(id, start, now, 600, 1200, 300, 900, new int[1]);
    }

    /**
     * Bed time by the clock (hour 0–24, 0 midnight), the same for the whole herd ({@code herd}: the leader's id): from late
     * morning through the afternoon, 9:30–16:30, and a spell after midnight, 0:30–3:00, each moved up to 40 minutes either
     * way per herd. The rest of the day it feeds: dawn and dusk most of all (doc 3).
     */
    public static boolean bedTime(double hour, int herd) {
        double h = hour - ((hash(herd, 0, 99) % 81) - 40) / 60.0;
        h = (h % 24 + 24) % 24;
        return (h >= 9.5 && h < 16.5) || (h >= 0.5 && h < 3.0);
    }

    /** Alternating spells from {@code start}, the first first: true while in a first spell. */
    private static boolean spells(int id, int start, long now, int first, int firstRange, int second, int secondRange, int[] into) {
        long t = Math.max(0, (int) now - start);   // STATE_AT is the game time cut to an int
        boolean one = true;
        long at = 0;
        for (int k = 0; k < 4096; k++) {
            int len = one ? first + hash(id, start, k) % firstRange : second + hash(id, start, k) % secondRange;
            if (t < at + len) {
                into[0] = (int) (t - at);
                return one;
            }
            at += len;
            one = !one;
        }
        into[0] = 0;
        return true;
    }

    /** A bite lands this tick (server: the sound). */
    public static boolean biteNow(int id, int start, long now) {
        int[] into = new int[1];
        return headDown(id, start, now, into) && into[0] >= BITE_FIRST && (into[0] - BITE_FIRST) % BITE_EVERY == 0;
    }

    private static int hash(int id, int start, int k) {
        int h = id * 0x27D4EB2D ^ start * 0x165667B1 ^ k * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
    }
}
