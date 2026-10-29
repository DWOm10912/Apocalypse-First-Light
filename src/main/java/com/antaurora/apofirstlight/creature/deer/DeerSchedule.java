package com.antaurora.apofirstlight.creature.deer;

/**
 * The grazing rhythm (docs/gameplay/afl_whitetail_deer_d0_design.md 3): head down 5–20 s pulling grass, a bite every
 * {@link #BITE_EVERY} ticks; head up 2–5 s chewing and looking round; again. Worked out from the deer's id, the game time
 * the grazing began (DeerEntity's synced STATE_AT) and the game time now, so the server (the bite sounds) and every client
 * (the head) keep the same beat without any packet.
 */
public final class DeerSchedule {
    /** Ticks between bites while the head is down; the first comes {@link #BITE_FIRST} ticks after it goes down. */
    public static final int BITE_EVERY = 32, BITE_FIRST = 14;

    private DeerSchedule() {
    }

    /** Head down at {@code now}? {@code into[0]} gets the ticks into the current head-down or head-up spell. */
    public static boolean headDown(int id, int start, long now, int[] into) {
        long t = Math.max(0, (int) now - start);   // STATE_AT is the game time cut to an int
        boolean down = true;
        long at = 0;
        for (int k = 0; k < 4096; k++) {
            int len = down ? 100 + hash(id, start, k) % 300 : 40 + hash(id, start, k) % 60;
            if (t < at + len) {
                into[0] = (int) (t - at);
                return down;
            }
            at += len;
            down = !down;
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
