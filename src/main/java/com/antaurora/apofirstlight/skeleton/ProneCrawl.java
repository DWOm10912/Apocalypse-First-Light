package com.antaurora.apofirstlight.skeleton;

import net.minecraft.util.Mth;

/**
 * The stride of crawling on the front (docs/gameplay/lightning_injury_v1_plan.md 10; clips prone.drag / prone.crawl,
 * written by tools/medical-actions-v1a/author_prone_v1.cjs, which keeps the same numbers): one cycle is two pulls, each
 * half cycle an arm reaches ({@link #REACH} of it, the body nearly still) then pulls (the body fastest mid-pull, the palm
 * still on the ground). The clips' phase is the distance crawled, so the palms stay put whatever the speed; the own
 * player's input is × speed / {@link #MEAN} at that phase (client/skeleton/ProneClient), so the phase runs evenly in
 * time and the mean speed stays vanilla's crawl.
 */
public final class ProneCrawl {
    /** Share of each half cycle an arm reaches (the rest it pulls); the speed while reaching (1 = the pull's peak). */
    public static final float REACH = 0.35F, SLOW = 0.2F;
    /** One cycle (two pulls) covers this many blocks; the clips' length for one cycle (s: the phase × this). */
    public static final float CYCLE_BLOCKS = 1.2F, CYCLE_SECONDS = 2.0F;
    /** The mean of {@link #speed} over a cycle: the input × speed / MEAN keeps vanilla's mean crawl. */
    public static final float MEAN = SLOW + (1 - SLOW) * (1 - REACH) / 2;
    private static final int TABLE = 256;
    /** covered() at TABLE + 1 even phases over one cycle (0..1). */
    private static final float[] COVERED = new float[TABLE + 1];

    static {
        for (int i = 0; i <= TABLE; i++) COVERED[i] = covered((float) i / TABLE);
    }

    private ProneCrawl() {
    }

    /** The speed (0..1 of the peak) at phase u. */
    public static float speed(float u) {
        float v = ((u % 0.5F) + 0.5F) % 0.5F / 0.5F;
        if (v < REACH) return SLOW;
        float s = Mth.sin(Mth.PI * (v - REACH) / (1 - REACH));
        return SLOW + (1 - SLOW) * s * s;
    }

    /** The share of a cycle's distance covered from phase 0 to u (0..1 over a cycle, growing past it). */
    public static float covered(float u) {
        float half = (float) Math.floor(u / 0.5F), v = (u - half * 0.5F) / 0.5F;
        float g = v < REACH ? 0 : (v - REACH) / 2 - (1 - REACH) / (4 * Mth.PI) * Mth.sin(2 * Mth.PI * (v - REACH) / (1 - REACH));
        return (half + (SLOW * v + (1 - SLOW) * g) / MEAN) / 2;
    }

    /** The phase (0..1) once this many blocks have been crawled (wrapping each cycle; backwards counts down). */
    public static float phase(double blocks) {
        double c = blocks / CYCLE_BLOCKS;
        float share = (float) (c - Math.floor(c));
        int lo = 0, hi = TABLE;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (COVERED[mid] <= share) lo = mid;
            else hi = mid;
        }
        float span = COVERED[hi] - COVERED[lo];
        float f = span > 1e-6F ? (share - COVERED[lo]) / span : 0;
        return (lo + Mth.clamp(f, 0, 1)) / TABLE;
    }
}
