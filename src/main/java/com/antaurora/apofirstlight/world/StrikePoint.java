package com.antaurora.apofirstlight.world;

import java.util.List;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 2): where a strike lands, by the rolling-sphere method of lightning
 * protection (NFPA 780, IEC 62305). The stepped leader comes down over a column; the first thing within the strike
 * distance D of its tip is struck. With the tip at height H over the column, a top at height y, h across, is within D once
 * H <= y + sqrt(D² - h²); so the struck thing is the one with the highest y + sqrt(D² - h²). Height and standing alone
 * decide it, not metal. D is drawn per strike (a weak stroke has a short one): triangular, [least, likeliest, most] m.
 * <p>
 * A thing h tall on flat ground draws strikes from within sqrt(h (2D - h)) across: a person (1.8 m) about 12.6 m at D 45,
 * a 20 m tree about 37 m. A lightning rod's tip counts {@code reach} higher (its upward streamer): it takes what would have
 * struck near it. Pure: the server's columns and things come in (world/LightningAim), the offline check uses its own.
 */
public final class StrikePoint {
    private StrikePoint() {
    }

    /** One column's top: false where it is not loaded (never loaded for a strike). out[0] the top (m), out[1] a bonus (a rod's reach, else 0). */
    public interface Columns {
        boolean column(int x, int z, double[] out);
    }

    /** A thing standing up over the columns (a living thing, a vehicle): its top, the column it stands in counts too. */
    public record Thing(double x, double z, double top, Object ref) {}

    /** The struck place: a column (thing null) or a thing; its top, the score (the leader's height when it was reached). */
    public record Pick(double x, double y, double z, Object thing, double score) {
        public boolean column() { return thing == null; }
    }

    /** The strike distance (m) for a uniform u in [0, 1): triangular [least, likeliest, most]. */
    public static double distance(double u, double[] tri) {
        double a = tri[0], c = tri[1], b = tri[2];
        if (b <= a) return a;
        double f = (c - a) / (b - a);
        return u < f ? a + Math.sqrt(u * (b - a) * (c - a)) : b - Math.sqrt((1 - u) * (b - a) * (b - c));
    }

    /** The leader's height when a top y, h across, comes within d; -inf when it never does. */
    public static double score(double y, double h, double d) {
        return h >= d ? Double.NEGATIVE_INFINITY : y + Math.sqrt(d * d - h * h);
    }

    /** How far across a thing h above flat ground draws strikes, at strike distance d. */
    public static double capture(double h, double d) {
        return h >= d ? d : Math.sqrt(h * (2 * d - h));
    }

    /**
     * The struck place for a leader over (x0, z0) at strike distance d: every loaded column within d (its centre), then the
     * things. Ties keep the nearer. Null when no column is loaded.
     */
    public static Pick pick(double x0, double z0, double d, Columns columns, List<Thing> things) {
        int r = (int) Math.ceil(d) + 1, bx = (int) Math.floor(x0), bz = (int) Math.floor(z0);
        double[] out = new double[2];
        Pick best = null;
        double bestH = 0;
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++) {
                int x = bx + dx, z = bz + dz;
                double h = Math.hypot(x + 0.5 - x0, z + 0.5 - z0);
                if (h >= d || !columns.column(x, z, out)) continue;
                double s = score(out[0] + out[1], h, d);
                if (best == null || s > best.score || s == best.score && h < bestH) {
                    best = new Pick(x + 0.5, out[0], z + 0.5, null, s);
                    bestH = h;
                }
            }
        for (Thing t : things) {
            double h = Math.hypot(t.x - x0, t.z - z0), s = score(t.top, h, d);
            if (best != null && (s > best.score || s == best.score && h < bestH)) {
                best = new Pick(t.x, t.top, t.z, t.ref, s);
                bestH = h;
            }
        }
        return best;
    }
}
