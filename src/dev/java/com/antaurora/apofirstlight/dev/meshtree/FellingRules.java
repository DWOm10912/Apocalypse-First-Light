package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Form;
import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, C/G/H/K): the felling numbers, one rule set for every
 * species and variant, driven by the tree's own measurements (the cut's cross-section, the layout's wood volumes, its
 * height and crown) and a small parameter row per species. Pure Java: the server, the client and the offline check
 * (tools/mesh-trees/FellCheck.java) compute the same values.
 */
public final class FellingRules {
    /** Visible cut stages before the fall (the root's {@code chop} block state 0..4) and notch directions. */
    public static final int STAGES = 4, SIDES = 8;
    /** Ticks between chops a client sends while the attack key is held; the server accepts no closer than MIN. */
    public static final int CHOP_TICKS = 10, MIN_CHOP_TICKS = 8;
    /** Upright after the last chop, lying after the contact, the rebound after the contact. */
    public static final int PAUSE_TICKS = 4, LIE_TICKS = 20, BOUNCE_TICKS = 10;
    public static final float MAX_REST_DEG = 105;

    /**
     * Per species: wood hardness (white oak hardwood 1, eastern white pine soft 0.55), chop work per m2 of cut at
     * hardness 1 (iron axe power 1 per chop), the cut height range (m above the root's floor), m3 of wood per log item
     * and per branch item, leaf items per metre of crown radius, caps, how much the crown props a fallen tree up (its
     * radius share) and the effective length share of the falling tree (its mass sits below the top).
     */
    public record Params(float hardness, float workPerArea, float cutLow, float cutHigh, float logM3, float branchM3,
                         float leafPerRadius, int maxLogs, int maxBranches, float crownSupport, float lengthShare) {}

    // work per m2: the mesh trunk flares linearly to the first ring (1 m), so a cut at 0.45-0.75 m crosses 0.4-0.75 m
    // of radius; 12 gives an iron axe about 7 chops on a small white oak, 14 on the biggest (offline check)
    static final Params WHITE_OAK = new Params(1.0F, 12F, 0.45F, 0.75F, 0.5F, 0.1F, 0.33F, 48, 24, 0.22F, 0.72F);
    static final Params WHITE_PINE = new Params(0.55F, 12F, 0.45F, 0.75F, 0.5F, 0.1F, 0.40F, 48, 24, 0.18F, 0.66F);

    public static Params params(Species s) { return s == Species.WHITE_PINE ? WHITE_PINE : WHITE_OAK; }

    private FellingRules() {}

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** A hash of a root position (cut height, notch and direction jitter). */
    public static long hash(int x, int y, int z, int salt) {
        return mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ (long) salt << 52);
    }

    static float unit(long h) { return (h >>> 40) / (float) (1L << 24); }

    // ----------------------------------------------------------------------------------------------- the cut

    /** The cut height above the root's floor: within the species range by a hash of the root, in 1/32 block steps. */
    public static float cutHeight(Species s, int x, int y, int z) {
        Params p = params(s);
        float h = p.cutLow + (p.cutHigh - p.cutLow) * unit(hash(x, y, z, 1));
        return Math.max((float) Math.ceil(p.cutLow * 32) / 32F, Math.min((float) Math.floor(p.cutHigh * 32) / 32F, Math.round(h * 32) / 32F));
    }

    /** The trunk radius of a form at a height above the root's floor. */
    static float trunkRadius(Form f, float y) {
        return TreeForms.radiusAt(f.branches.get(0), y);
    }

    /** Chop work to cut through: cross-section area at the cut x hardness x work per m2 (at least four chops of power 1). */
    public static float work(Species s, float cutRadius) {
        Params p = params(s);
        return Math.max(4F, (float) (Math.PI * cutRadius * cutRadius) * p.hardness * p.workPerArea);
    }

    /** A tool's work per chop: sqrt(tier speed / 6) (wood 0.58, stone 0.82, iron 1, diamond 1.15, netherite 1.22, gold 1.41) + 0.1 per Efficiency level. */
    public static float power(float tierSpeed, int efficiency) {
        return (float) Math.sqrt(Math.max(1F, tierSpeed) / 6F) + 0.1F * Math.max(0, efficiency);
    }

    /** Chops to fell (offline and diagnostics). */
    public static int chops(float need, float power) {
        return (int) Math.ceil(need / power - 1e-4F);
    }

    /** The cut stage shown for done work: 1 from 20 %, 2 from 40 %, 3 from 60 %, 4 from 80 % (100 % fells). */
    public static int stage(float done, float need) {
        return Math.max(0, Math.min(STAGES, (int) Math.floor(5 * done / need + 1e-4F)));
    }

    /** The notch direction (0..7, 45 degree steps, angle atan2(dz, dx)) from the trunk toward the chopping player. */
    public static int side(double dx, double dz) {
        double a = Math.atan2(dz, dx);
        return Math.floorMod((int) Math.round(a / (Math.PI / 4)), SIDES);
    }

    public static double sideAngle(int side) { return side * Math.PI / 4; }

    // ---------------------------------------------------------------------------------------------- the fall

    /**
     * The supporting profile of a falling tree, 0.5 m steps from the pivot (the cut) up its length: how far below the
     * axis it touches the ground first, the trunk's radius, and in the crown a share of the crown's radius (the crown is
     * crushed a little, but props the tree up; a bell over the crown's height).
     */
    public static float[] profile(Form f, float cut, Species s) {
        float height = f.crownTop - cut;
        int n = Math.max(2, (int) Math.ceil(height / 0.5F) + 1);
        float[] r = new float[n];
        float support = params(s).crownSupport * f.crownRadius;
        float span = Math.max(0.5F, f.crownTop - f.crownBottom);
        for (int i = 0; i < n; i++) {
            float y = cut + i * 0.5F;
            float bell = y < f.crownBottom || y > f.crownTop ? 0 : (float) Math.sin(Math.PI * (y - f.crownBottom) / span);
            r[i] = Math.max(trunkRadius(f, Math.min(y, f.trunkTop)), support * bell);
        }
        return r;
    }

    /** Ground height relative to the pivot at a horizontal distance along the fall direction. */
    public interface Ground {
        double at(double d);
    }

    /**
     * The angle (degrees from upright) at which the falling tree first touches the ground: the lowest point of each
     * profile ring (its axis point minus its radius across the axis) against the ground under it, from 1.5 m along the
     * tree on (the stump end never touches while it turns). At most MAX_REST_DEG (over a drop it would go on falling).
     */
    public static float restAngle(float[] profile, Ground g) {
        for (float deg = 2; deg <= MAX_REST_DEG; deg += 0.5F) {
            double a = Math.toRadians(deg), sn = Math.sin(a), cs = Math.cos(a);
            for (int i = 3; i < profile.length; i++) {
                double s = i * 0.5, r = profile[i];
                double hx = s * sn + r * cs, vy = s * cs - r * sn;
                if (vy <= g.at(hx)) return deg;
            }
        }
        return MAX_REST_DEG;
    }

    /** A fall: the angle (degrees) per tick from the end of the pause, the contact tick, the rest angle. */
    public record Curve(float[] angle, int contact, float rest) {
        /** The angle at a time in ticks since the fall began (after the pause); 0 before. */
        public float at(float t) {
            if (t <= 0) return 0;
            int i = (int) Math.floor(t);
            if (i >= angle.length - 1) return angle[angle.length - 1];
            float u = t - i;
            return angle[i] + (angle[i + 1] - angle[i]) * u;
        }

        public int end() { return angle.length - 1; }
    }

    /**
     * The fall as a rigid rod pivoting at its foot: theta'' = 3g / (2L) sin theta, L the effective length (the tree's
     * height above the cut times the species' share), from upright with the last chop's small push (0.25 rad/s): it
     * loses balance slowly, gathers speed, and stops hard at the rest angle, with a small rebound (up to 3 degrees, from
     * the impact speed). Deterministic (the same table on the server and every client).
     */
    public static Curve curve(Species s, float height, float cut, float rest) {
        double len = Math.max(3.0, (height - cut) * params(s).lengthShare);
        double k = 3 * 9.81 / (2 * len), th = 0, w = 0.25, target = Math.toRadians(rest);
        float[] buf = new float[400];
        int n = 0, contact = -1;
        buf[n++] = 0;
        int sub = 20;
        double dt = 0.05 / sub;
        while (n < 380) {
            for (int i = 0; i < sub; i++) {
                w += k * Math.sin(th) * dt;
                th += w * dt;
            }
            if (th >= target) { buf[n++] = rest; contact = n - 1; break; }
            buf[n++] = (float) Math.toDegrees(th);
        }
        if (contact < 0) { contact = n - 1; buf[contact] = rest; }
        float bounce = (float) Math.max(0.5, Math.min(3.0, Math.toDegrees(w) * 0.02));
        for (int t = 1; t <= BOUNCE_TICKS && n < buf.length; t++) {
            double u = t / (double) BOUNCE_TICKS;
            buf[n++] = (float) (rest - bounce * Math.sin(Math.PI * Math.min(1, u * 1.6)) * (1 - u));
        }
        buf[n++] = rest;
        float[] out = new float[n];
        System.arraycopy(buf, 0, out, 0, n);
        return new Curve(out, contact, rest);
    }

    /**
     * The fall direction (radians, atan2(dz, dx)): candidates every 15 degrees within 90 of the preferred one (the
     * chopper's look, pulled toward the crown's lean, a small hash jitter), at least 100 degrees away from the direction
     * toward the chopper, each scored by its turn from the preferred one plus the scorer's cost (obstacles, a poor rest
     * angle); the lowest wins, ties to the first candidate in a fixed order. Pure: the same inputs give the same
     * direction. If no candidate qualifies, straight away from the chopper.
     */
    public static double direction(double preferred, double towardChopper, double lean, double leanWeight, double jitter, Scorer scorer) {
        double base = preferred + Math.atan2(Math.sin(lean - preferred), Math.cos(lean - preferred)) * Math.min(0.5, leanWeight) + jitter;
        double best = towardChopper + Math.PI, bestScore = Double.MAX_VALUE;
        for (int k = 0; k <= 12; k++) {
            int step = (k + 1) / 2 * (k % 2 == 0 ? -1 : 1);
            double a = base + Math.toRadians(15 * step);
            double away = Math.abs(Math.atan2(Math.sin(a - towardChopper), Math.cos(a - towardChopper)));
            if (away < Math.toRadians(100)) continue;
            double score = Math.abs(step) * 15 / 45.0 + scorer.cost(a);
            if (score < bestScore - 1e-9) { bestScore = score; best = a; }
        }
        return best;
    }

    public interface Scorer {
        double cost(double angle);
    }

    // ----------------------------------------------------------------------------------------- shared cells

    /**
     * What a shared cell keeps when a tree there is felled: the highest of the other trees' cells there (rank, species;
     * ForestPlan#wins), as their index, or -1 when no other tree has a cell there (the cell empties). The same maximum
     * natural generation took, so the cell ends as if the felled tree had never been there.
     */
    public static int keeper(int[] rank, int[] species, int n) {
        int best = -1;
        for (int i = 0; i < n; i++) if (best < 0 || ForestPlan.wins(rank[i], species[i], rank[best], species[best])) best = i;
        return best;
    }

    // ----------------------------------------------------------------------------------------------- drops

    /** What a felled tree yields: species logs, branches, leaf material. */
    public record Drops(int logs, int branches, int leaves) {}

    /** From the layout's wood volumes (trunk + thick limbs to logs, twigs to branches) and the crown's size. */
    public static Drops drops(Species s, float trunkM3, float limbM3, float twigM3, float crownRadius) {
        Params p = params(s);
        int logs = Math.max(2, Math.min(p.maxLogs, Math.round((trunkM3 + limbM3) / p.logM3)));
        int branches = Math.max(1, Math.min(p.maxBranches, Math.round(twigM3 / p.branchM3)));
        int leaves = Math.max(1, Math.round(crownRadius * p.leafPerRadius));
        return new Drops(logs, branches, leaves);
    }
}
