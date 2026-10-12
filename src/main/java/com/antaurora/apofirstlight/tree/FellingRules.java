package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Form;
import com.antaurora.apofirstlight.tree.TreeForms.Species;

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
    // P2.2: a log item is a log block, one cubic metre of wood (docs/rendering/mesh_trees_p2_felling.md, V)
    static final Params WHITE_OAK = new Params(1.0F, 12F, 0.45F, 0.75F, 1.0F, 0.1F, 0.33F, 48, 24, 0.22F, 0.72F);
    static final Params WHITE_PINE = new Params(0.55F, 12F, 0.45F, 0.75F, 1.0F, 0.1F, 0.40F, 48, 24, 0.18F, 0.66F);

    // P3 (docs/rendering/mesh_trees_p3_species.md, F): hardness as the square root of the Janka hardness over white oak's
    // 1360 lbf (USDA Wood Handbook: northern red oak 1290, shagbark hickory 1820, red maple 950, eastern hemlock 500;
    // white pine 380 gives 0.53, about the 0.55 already set); crown props and length shares like the nearest form
    static final Params RED_OAK = new Params(0.97F, 12F, 0.45F, 0.75F, 1.0F, 0.1F, 0.33F, 48, 24, 0.21F, 0.70F);
    static final Params SHAGBARK_HICKORY = new Params(1.16F, 12F, 0.45F, 0.75F, 1.0F, 0.1F, 0.30F, 48, 24, 0.18F, 0.68F);
    static final Params RED_MAPLE = new Params(0.84F, 12F, 0.45F, 0.75F, 1.0F, 0.1F, 0.36F, 48, 24, 0.20F, 0.70F);
    static final Params EASTERN_HEMLOCK = new Params(0.61F, 12F, 0.45F, 0.75F, 1.0F, 0.1F, 0.42F, 48, 24, 0.17F, 0.64F);

    public static Params params(Species s) {
        return switch (s) {
            case WHITE_OAK -> WHITE_OAK;
            case WHITE_PINE -> WHITE_PINE;
            case RED_OAK -> RED_OAK;
            case SHAGBARK_HICKORY -> SHAGBARK_HICKORY;
            case RED_MAPLE -> RED_MAPLE;
            case EASTERN_HEMLOCK -> EASTERN_HEMLOCK;
        };
    }

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

    // ------------------------------------------------------------------------------------------------- hits

    /** Kinds of hit: the trunk or main limbs passing through a body, the crown's propping limbs under them, its twigs. */
    public static final int CRUSH = 0, LIMBS = 1, TWIGS = 2;

    /** One hit of a falling tree on a body: its kind, the damage, the speed (m/s) of the tree there. */
    public record Hit(int kind, float damage, double speed) {}

    static final double HEFT_RADIUS = 0.3;

    /**
     * Damage of a crush at v m/s by a tree whose trunk is r m in radius at the cut (2026-10-12): (4 + 2.2 v) x the
     * tree's heft (r / HEFT_RADIUS, 0.6..1.3), at most 40. A body in the fall line a third of the way to the crown top
     * takes about 20-35 (offline check): a zombie or anything smaller dies, an unarmoured player at full health about too.
     */
    public static float crushHit(double v, double r) {
        return (float) Math.min(40, (4 + 2.2 * v) * Math.max(0.6, Math.min(1.3, r / HEFT_RADIUS)));
    }

    /** The crown's propping limbs under the trunk's line: half a crush. */
    public static float limbsHit(double v, double r) {
        return crushHit(v, r) * 0.5F;
    }

    /** The crown's twigs at v m/s: 1 + 0.35 v, at most 6 (a chicken, not a player). */
    public static float twigsHit(double v) {
        return (float) Math.min(6, 1 + 0.35 * v);
    }

    /** The tree's core at a height: the trunk, above its top the main limbs going on, tapering to a third at the crown top. */
    static double coreRadius(Form f, double h) {
        if (h <= f.trunkTop) return trunkRadius(f, (float) h);
        double u = Math.min(1, (h - f.trunkTop) / Math.max(0.5, f.crownTop - f.trunkTop));
        return trunkRadius(f, f.trunkTop) * (1 - 0.67 * u);
    }

    /** The crown's radius at a height (a bell over its height, 0 outside it). */
    static double crownAt(Form f, double h) {
        if (h < f.crownBottom || h > f.crownTop) return 0;
        return f.crownRadius * Math.sin(Math.PI * (h - f.crownBottom) / Math.max(0.5, f.crownTop - f.crownBottom));
    }

    /**
     * What a falling tree's sweep from a0 to a1 degrees from upright (turning at omega rad/s) does to a body, in the fall's
     * own frame: the body's centre {@code along} the fall direction and {@code across} it (m, from the pivot), its half
     * width, its bottom and top relative to the pivot. Null for no contact. The body is met at its distance from the
     * pivot, by what reaches it there:
     * <ul>
     * <li>the core (the trunk, then the main limbs, coreRadius), within its radius across the fall line: CRUSH;</li>
     * <li>under the core the crown's propping limbs (how far the tree reaches below its axis where it rests, as in
     * #profile: the species' crownSupport x the crown's radius), within half the crown's radius across: LIMBS;</li>
     * <li>the same reach within 0.8 of the crown's radius across: TWIGS (the twigs' first touch, higher up, is ignored).</li>
     * </ul>
     * Nothing within 0.5 m of the pivot is hit (the butt hardly moves: the chopper beside the stump is safe), nor anything
     * behind it (the tree falls away from it); a body low under a propped-up trunk stays under it.
     */
    public static Hit hit(Form f, Species species, float cut, double a0, double a1, double omega, double along, double across,
                          double halfWidth, double y0, double y1) {
        if (!(a1 > a0)) return null;
        double length = f.crownTop - cut;
        double nearA = Math.min(along + halfWidth, Math.max(along - halfWidth, 0)), nearY = Math.min(y1, Math.max(y0, 0));
        double near = Math.hypot(nearA, nearY);
        if (near > length) return null;
        double s = Math.max(near, Math.min(length, Math.hypot(along, (y0 + y1) / 2)));
        if (s < 0.5) return null;
        double h = cut + s, v = Math.abs(omega) * s, core = coreRadius(f, h), crown = crownAt(f, h);
        double heft = trunkRadius(f, cut);
        if (across <= halfWidth + core && sweeps(a0, a1, along, halfWidth, y0, y1, core, s)) return new Hit(CRUSH, crushHit(v, heft), v);
        double reach = Math.max(core, params(species).crownSupport * crown);
        if (reach <= core || !sweeps(a0, a1, along, halfWidth, y0, y1, reach, s)) return null;
        if (across <= halfWidth + Math.max(core, 0.5 * crown)) return new Hit(LIMBS, limbsHit(v, heft), v);
        if (across <= halfWidth + 0.8 * crown) return new Hit(TWIGS, twigsHit(v), v);
        return null;
    }

    /**
     * Whether the wedge a0..a1 (degrees from upright, toward +along), as thick as r at distance s, crosses the body's
     * outline along +- w, y0..y1 (its part ahead of the pivot: only that is in the tree's way).
     */
    private static boolean sweeps(double a0, double a1, double along, double w, double y0, double y1, double r, double s) {
        double front = Math.max(0, along - w), back = along + w;
        if (back <= 0) return false;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double a : new double[]{front, back})
            for (double y : new double[]{y0, y1}) {
                double phi = Math.toDegrees(Math.atan2(a, y));
                lo = Math.min(lo, phi);
                hi = Math.max(hi, phi);
            }
        double pad = Math.toDegrees(Math.asin(Math.min(1, r / s)));
        return hi + pad >= a0 && lo - pad <= a1;
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
