package com.antaurora.apofirstlight.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesh Trees P0-B (docs/rendering/mesh_trees_p0b.md, B): the tree form system. A species turns a variant (0..15) into
 * a {@link Form}: branches (polylines with radii, how many sides each level draws, whether they collide) and foliage
 * groups (where the leaf cards go and how each level draws them). Pure Java, deterministic per (species, variant).
 * <p>
 * Branch rules for every species (what made the P0-A joints look cut):
 * <ul>
 * <li>a child starts on its parent's axis, thinner than the parent there, so its first ring is hidden inside;</li>
 * <li>the trunk runs on into the leader (oak) or to the very top (pine) as one polyline: no open top;</li>
 * <li>free ends taper to a point.</li>
 * </ul>
 */
public final class TreeForms {
    /** Thick enough to collide (base radius, m) and the radius below which a branch stops colliding along its length. */
    static final float COLLIDE_BASE = 0.12F, COLLIDE_MIN = 0.065F;

    /**
     * The species. {@code id} is also the wood id of the planned AFL wood system (docs/dev/wood_system_plan_v1.md):
     * {@code <id>_log} (P2.2: a dev log block), later {@code <id>_lumber}, {@code <id>_planks}. P3
     * (docs/rendering/mesh_trees_p3_species.md): four more eastern North American species. New species go at the end:
     * the ordinal orders shared crown cells (ForestPlan#wins), the sprite list and the falling tree's packet.
     */
    public enum Species {
        WHITE_OAK("white_oak", "white_oak_leaves", 0, false, new int[]{3000, 1100, 600}),
        WHITE_PINE("white_pine", "white_pine_needles", 1, true, new int[]{3400, 1800, 750}),
        RED_OAK("red_oak", "red_oak_leaves", 0, false, new int[]{3400, 1300, 700}),
        SHAGBARK_HICKORY("shagbark_hickory", "shagbark_hickory_leaves", 0, false, new int[]{2800, 1100, 600}),
        RED_MAPLE("red_maple", "red_maple_leaves", 0, false, new int[]{3200, 1250, 700}),
        EASTERN_HEMLOCK("eastern_hemlock", "eastern_hemlock_needles", 2, true, new int[]{3800, 1900, 850});

        public final String id;
        /** The leaf material's item id and the base name of the leaf textures ({@code _mid}, {@code _far}, {@code _proxy}). */
        public final String leavesId;
        /** Leaf tint index: 0 the biome's foliage colour, 1 white pine's own colour, 2 eastern hemlock's (TreeModels). */
        public final int tint;
        /**
         * A conifer: one stem to the top (LOD2 draws the whole trunk; the thin branches' bark tile is 2 m at LOD0 too),
         * needle foliage (Oculus: the spruce leaves ID, so the pack waves it as needles).
         */
        public final boolean conifer;
        /** Triangle budgets per level (both sides of a leaf card counted). */
        public final int[] budget;

        Species(String id, String leavesId, int tint, boolean conifer, int[] budget) {
            this.id = id; this.leavesId = leavesId; this.tint = tint; this.conifer = conifer; this.budget = budget;
        }

        public static Species byId(String id) {
            for (var s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }

    /** Leaf card styles: broadleaf clusters, white pine's level needle plates, eastern hemlock's drooping flat sprays. */
    static final int BROADLEAF = 0, NEEDLE = 1, SPRAY = 2;

    /** A branch: polyline points and radii; sides per level (0: not drawn); LOD2 draws its first lod2Segments only. */
    record Branch(float[][] points, float[] radii, int depth, boolean collide, int[] sides, int lod2Segments, boolean taper) {}

    /** A foliage group: centre, radius; outer = on the crown's outside; dir = the branch's horizontal direction. */
    record Group(float x, float y, float z, float r, boolean outer, int style, float dirX, float dirZ) {}

    /** One tree: what TreeShape turns into quads, cells and collision. */
    static final class Form {
        final Species species;
        final List<Branch> branches = new ArrayList<>();
        final List<Group> groups = new ArrayList<>();
        float trunkTop, crownBottom, crownTop, crownRadius, crownCx, crownCz;

        Form(Species species) { this.species = species; }

        void crown() {
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, sx = 0, sz = 0, rad = 0;
            for (var g : groups) { minY = Math.min(minY, g.y() - g.r()); maxY = Math.max(maxY, g.y() + g.r()); sx += g.x(); sz += g.z(); }
            crownCx = sx / groups.size(); crownCz = sz / groups.size();
            for (var g : groups) rad = Math.max(rad, (float) Math.hypot(g.x() - crownCx, g.z() - crownCz) + g.r());
            crownBottom = minY; crownTop = maxY; crownRadius = rad;
        }
    }

    static Form grow(Species species, int variant) {
        return switch (species) {
            case WHITE_OAK -> whiteOak(variant);
            case WHITE_PINE -> whitePine(variant);
            case RED_OAK -> redOak(variant);
            case SHAGBARK_HICKORY -> hickory(variant);
            case RED_MAPLE -> redMaple(variant);
            case EASTERN_HEMLOCK -> hemlock(variant);
        };
    }

    // ------------------------------------------------------------------------------------------- white oak

    /**
     * Quercus alba, open grown: short massive trunk, three to six wide limbs (the low ones nearly level and turning up),
     * a broad irregular dome. Per variant: size 0.85-1.15, fork height, limb count and spread, a crown leaning to one
     * side, with or without a central leader (no leader: a flatter, wider dome).
     */
    static Form whiteOak(int variant) {
        var rng = new Rng(0x5EED_0A4BL * 31 + variant * 0x9E3779B1L + 7);
        var f = new Form(Species.WHITE_OAK);
        float size = rng.range(0.85F, 1.15F);
        boolean leader = variant % 4 != 3;
        float fork = rng.range(3.2F, 4.4F) * (0.9F + 0.1F * size);
        float leanAz = rng.range(0, 6.2832F), lean = rng.range(0.0F, 0.15F);
        float sideAz = rng.range(0, 6.2832F), side = rng.range(0.0F, 0.25F);   // the crown grows longer toward sideAz
        float r0 = 0.4F * size + 0.04F;
        // trunk, and the leader as its continuation: one polyline, so the top is never an open ring
        float trunkLen = leader ? fork + rng.range(3.0F, 4.0F) * size : fork + 0.7F;
        int rings = (int) Math.ceil(trunkLen);
        float[][] tp = new float[rings + 1][];
        float[] tr = new float[rings + 1];
        for (int i = 0; i <= rings; i++) {
            float y = Math.min(i, trunkLen), u = Math.min(1, y / fork);   // points every whole metre: rings fall on the bark tiles
            float wob = y > fork ? (y - fork) * 0.08F : 0;
            tp[i] = new float[]{0.5F + (float) Math.cos(leanAz) * lean * u * u + wob * (float) Math.cos(sideAz + 1),
                    y, 0.5F + (float) Math.sin(leanAz) * lean * u * u + wob * (float) Math.sin(sideAz + 1)};
            float above = Math.max(0, y - fork) / Math.max(0.5F, trunkLen - fork);
            tr[i] = (r0 - 0.11F * u) * (1 - 0.75F * above) + (y < 0.6F ? 0.2F * size * (1 - y / 0.6F) * (1 - y / 0.6F) : 0);
        }
        tr[rings] = 0.05F;
        f.branches.add(new Branch(tp, tr, 0, true, new int[]{12, 6, 4}, Integer.MAX_VALUE, false));
        f.trunkTop = fork;
        Branch trunk = f.branches.get(0);
        // limbs
        int limbs = 3 + (int) (rng.next() * 3.5F);
        int low = Math.max(2, limbs - (leader ? 2 : 1));
        float base = rng.range(0, 6.2832F);
        for (int i = 0; i < limbs; i++) {
            boolean isLow = i < low;
            float az = base + (float) (Math.PI * 2 * (isLow ? i / (double) low : (i - low + 0.5) / Math.max(1, limbs - low) + 0.1))
                    + rng.range(-0.3F, 0.3F);
            float toward = (float) Math.cos(az - sideAz) * side;                 // -side .. +side
            float el = (float) Math.toRadians(isLow ? rng.range(6, 16) : rng.range(28, 42));
            float len = (isLow ? rng.range(6.0F, 7.4F) : rng.range(4.8F, 5.8F)) * size * (1 + toward) * (leader ? 1 : 1.08F);
            float y0 = fork - (isLow ? 0.5F : 0.1F) + 0.1F * i;
            float[] start = at(trunk, y0);
            float rl = Math.min(radiusAt(trunk, y0) * 0.8F, rng.range(0.19F, 0.25F) * size);
            Branch limb = bend(start, az, el, len, 4, isLow ? 0.1F : 0.07F, rl, 0.06F, 1, rng, new int[]{6, 3, 3}, 2);
            f.branches.add(limb);
            int subs = 2 + (rng.next() < 0.6F ? 1 : 0);
            for (int k = 0; k < subs; k++) {
                float t = 0.3F + 0.6F * (k + 0.2F + rng.next() * 0.5F) / subs;
                float[] s = along(limb, t);
                float saz = az + (k % 2 == 0 ? 1 : -1) * (float) Math.toRadians(rng.range(25, 55));
                float sel = el + (float) Math.toRadians(rng.range(28, 55));
                float sr = Math.min(radiusAlong(limb, t) * 0.75F, 0.1F);
                Branch sub = bend(s, saz, sel, rng.range(2.2F, 3.4F) * size, 2, 0.06F, sr, 0.035F, 2, rng, new int[]{4, 0, 0}, 0);
                f.branches.add(sub);
                float[] st = sub.points()[2];
                f.groups.add(new Group(st[0], st[1] + 0.1F, st[2], rng.range(1.25F, 1.5F), true, BROADLEAF, 0, 0));
                float[] mid = along(sub, 0.5F);
                f.groups.add(new Group(mid[0], mid[1] + 0.2F, mid[2], rng.range(1.0F, 1.2F), false, BROADLEAF, 0, 0));
            }
            float[] tip = limb.points()[limb.points().length - 1];
            f.groups.add(new Group(tip[0], tip[1] + 0.1F, tip[2], rng.range(1.35F, 1.6F), true, BROADLEAF, 0, 0));
            for (float t = 0.3F; t < 0.92F; t += 0.19F) {
                float[] p = along(limb, t);
                f.groups.add(new Group(p[0], p[1] + 0.45F, p[2], rng.range(1.1F, 1.35F), t > 0.6F, BROADLEAF, 0, 0));
            }
        }
        if (leader) {
            // side shoots off the leader, and the top of the dome
            float laz = rng.range(0, 6.2832F);
            for (int k = 0; k < 3; k++) {
                float y = fork + (trunkLen - fork) * (0.3F + 0.22F * k);
                float[] s = at(trunk, y);
                Branch shoot = bend(s, laz + (float) (Math.PI * 2 * k / 3), (float) Math.toRadians(rng.range(20, 34)),
                        rng.range(2.4F, 3.2F) * size, 2, 0.06F, Math.min(radiusAt(trunk, y) * 0.75F, 0.1F), 0.035F, 2, rng, new int[]{4, 0, 0}, 0);
                f.branches.add(shoot);
                float[] st = shoot.points()[2];
                f.groups.add(new Group(st[0], st[1] + 0.1F, st[2], rng.range(1.35F, 1.55F), true, BROADLEAF, 0, 0));
            }
            float[] top = tp[rings];
            f.groups.add(new Group(top[0], top[1] + 0.2F, top[2], rng.range(1.5F, 1.7F), true, BROADLEAF, 0, 0));
            float[] mid = at(trunk, fork + (trunkLen - fork) * 0.55F);
            f.groups.add(new Group(mid[0], mid[1], mid[2], 1.3F, false, BROADLEAF, 0, 0));
        } else {
            float[] top = tp[rings];
            f.groups.add(new Group(top[0], top[1] + 1.2F, top[2], 1.4F, false, BROADLEAF, 0, 0));
        }
        f.crown();
        return f;
    }

    // ------------------------------------------------------------------------------------------ white pine

    /**
     * Pinus strobus, mature: a straight tall trunk (17-23 m by variant), self-pruned clear for the lower quarter or
     * third, whorls of four or five near level branches about a metre apart (some whorls missing), tips turning up,
     * the crown widest low and narrowing upward but irregular, soft needle sprays in level plates.
     */
    static Form whitePine(int variant) {
        var rng = new Rng(0x9A1E_5EEDL * 17 + variant * 0x632BE59BL + 3);
        var f = new Form(Species.WHITE_PINE);
        float size = rng.range(0.9F, 1.1F);
        float height = rng.range(17.0F, 22.0F) * size;
        float r0 = 0.32F + 0.012F * height;
        float leanAz = rng.range(0, 6.2832F), lean = rng.range(0.0F, 0.25F);
        int rings = (int) Math.ceil(height);
        float[][] tp = new float[rings + 1][];
        float[] tr = new float[rings + 1];
        float wobAz = rng.range(0, 6.2832F);
        for (int i = 0; i <= rings; i++) {
            float y = Math.min(i, height), u = y / height;
            float wob = 0.08F * (float) Math.sin(u * 7 + wobAz) * u;
            tp[i] = new float[]{0.5F + (float) Math.cos(leanAz) * lean * u * u + wob, y,
                    0.5F + (float) Math.sin(leanAz) * lean * u * u + wob * 0.7F};
            tr[i] = r0 * (float) Math.pow(1 - u, 0.85) + 0.03F + (y < 0.5F ? 0.14F * (1 - y / 0.5F) * (1 - y / 0.5F) : 0);
        }
        tr[rings] = 0.02F;
        f.branches.add(new Branch(tp, tr, 0, true, new int[]{10, 6, 4}, Integer.MAX_VALUE, false));
        Branch trunk = f.branches.get(0);
        float clear = rng.range(0.24F, 0.34F) * height;
        f.trunkTop = height;
        float lmax = rng.range(5.0F, 6.4F) * size;
        float az0 = rng.range(0, 6.2832F);
        int whorl = 0;
        for (float h = clear; h < height - 0.7F; h += rng.range(0.8F, 1.25F), whorl++) {
            float u = (h - clear) / (height - clear);
            if (u < 0.85F && rng.next() < 0.18F) continue;                       // a missing whorl
            int n = 4 + (rng.next() < 0.4F ? 1 : 0) - (u > 0.8F ? 1 : 0);
            az0 += 2.4F + rng.range(-0.3F, 0.3F);
            for (int k = 0; k < n; k++) {
                if (u < 0.6F && rng.next() < 0.12F) continue;                    // a lost branch
                float az = az0 + (float) (Math.PI * 2 * k / n) + rng.range(-0.25F, 0.25F);
                float len = Math.max(0.8F, lmax * (0.22F + 0.78F * (float) Math.pow(1 - u, 0.8)) * rng.range(0.75F, 1.15F));
                float el = (float) Math.toRadians(u < 0.5F ? rng.range(-6, 6) : rng.range(4, 20));
                float rb = Math.min(radiusAt(trunk, h) * 0.6F, 0.03F + 0.016F * len);
                int segs = len > 3 ? 3 : 2;
                Branch b = bend(at(trunk, h), az, el, len, segs, 0.1F, rb, 0.025F, 1, rng,
                        new int[]{3, rb >= 0.09F && u < 0.45F ? 3 : 0, 0}, 0);
                f.branches.add(b);
                float dx = (float) Math.cos(az), dz = (float) Math.sin(az);
                // needle sprays along the outer part, about every 1.1 m, the tip one larger
                float t0 = Math.max(0.3F, 1 - 2.8F / len);
                for (float t = t0; t < 0.98F; t += 1.3F / len) {
                    float[] p = along(b, t);
                    f.groups.add(new Group(p[0], p[1] + 0.12F, p[2], rng.range(0.75F, 0.9F), t > 0.75F || u > 0.85F, NEEDLE, dx, dz));
                }
                float[] tip = b.points()[b.points().length - 1];
                f.groups.add(new Group(tip[0], tip[1] + 0.12F, tip[2], rng.range(0.9F, 1.0F), true, NEEDLE, dx, dz));
                // side shoots on the long branches (needles only: the twigs are too thin to see)
                if (len > 3.5F) for (float t : new float[]{0.5F, 0.78F}) {
                    float[] s = along(b, t);
                    float sa = az + (rng.next() < 0.5F ? 1 : -1) * (float) Math.toRadians(rng.range(40, 60));
                    float sl = rng.range(1.2F, 1.8F);
                    f.groups.add(new Group(s[0] + (float) Math.cos(sa) * sl * 0.6F, s[1] + 0.1F, s[2] + (float) Math.sin(sa) * sl * 0.6F,
                            0.75F, false, NEEDLE, (float) Math.cos(sa), (float) Math.sin(sa)));
                }
            }
        }
        // the leader's tuft
        float[] top = tp[rings];
        f.groups.add(new Group(top[0], top[1] - 0.2F, top[2], 0.9F, true, NEEDLE, 1, 0));
        float[] sub = at(trunk, height - 1.2F);
        f.groups.add(new Group(sub[0], sub[1], sub[2], 0.9F, true, NEEDLE, 0, 1));
        f.crown();
        return f;
    }

    // --------------------------------------------------------------------------------------------- red oak

    /**
     * Quercus rubra (Silvics: 20-30 m, 61-91 cm d.b.h.; forest-grown a tall straight bole, open-grown a short bole and
     * a spreading crown): the test forests' trees stand fairly open, so a clear bole of 5-7 m, then a rounded crown,
     * taller and narrower than the white oak's, of five to seven stout limbs that ascend (30-55 degrees, the white oak's
     * low limbs run nearly level). Three variants in four keep a central stem into the crown; the fourth divides at the
     * bole's top into steep co-dominant limbs. Per variant: size, bole height, limb count, a crown longer to one side.
     */
    static Form redOak(int variant) {
        var rng = new Rng(0x7ED0_A4B1L * 37 + variant * 0x85EBCA6BL + 11);
        var f = new Form(Species.RED_OAK);
        float size = rng.range(0.88F, 1.12F);
        boolean leader = variant % 4 != 1;
        float height = rng.range(18.5F, 22.5F) * size;
        float fork = rng.range(4.4F, 6.0F) * size;
        float leanAz = rng.range(0, 6.2832F), lean = rng.range(0.0F, 0.18F);
        float sideAz = rng.range(0, 6.2832F), side = rng.range(0.0F, 0.15F);
        float r0 = 0.33F * size + 0.04F;
        float trunkLen = leader ? height - rng.range(2.2F, 3.2F) : fork + 0.9F;
        int rings = (int) Math.ceil(trunkLen);
        float[][] tp = new float[rings + 1][];
        float[] tr = new float[rings + 1];
        for (int i = 0; i <= rings; i++) {
            float y = Math.min(i, trunkLen), u = Math.min(1, y / fork);
            float wob = y > fork ? (y - fork) * 0.05F : 0;
            tp[i] = new float[]{0.5F + (float) Math.cos(leanAz) * lean * u * u + wob * (float) Math.cos(sideAz + 1),
                    y, 0.5F + (float) Math.sin(leanAz) * lean * u * u + wob * (float) Math.sin(sideAz + 1)};
            float above = Math.max(0, y - fork) / Math.max(0.5F, trunkLen - fork);
            tr[i] = (r0 - 0.07F * u) * (1 - 0.8F * above) + (y < 0.6F ? 0.17F * size * (1 - y / 0.6F) * (1 - y / 0.6F) : 0);
        }
        tr[rings] = 0.04F;
        f.branches.add(new Branch(tp, tr, 0, true, new int[]{12, 6, 4}, Integer.MAX_VALUE, false));
        f.trunkTop = fork;
        Branch trunk = f.branches.get(0);
        int limbs = 5 + (int) (rng.next() * 2.99F);
        float base = rng.range(0, 6.2832F);
        float span = leader ? (trunkLen - fork) * 0.55F : 1.0F;
        for (int i = 0; i < limbs; i++) {
            float t = i / (float) (limbs - 1);
            float y0 = fork - 0.3F + span * t;
            float az = base + i * 2.39996F + rng.range(-0.25F, 0.25F);
            float toward = (float) Math.cos(az - sideAz) * side;
            float el = (float) Math.toRadians(leader ? rng.range(22, 32) + 24 * t : rng.range(46, 60));
            float len = leader ? rng.range(6.2F, 7.4F) * size * (1 - 0.4F * t) * (1 + toward) : rng.range(7.4F, 8.8F) * size * (1 - 0.25F * t);
            float[] start = at(trunk, y0);
            float rl = Math.min(radiusAt(trunk, y0) * (leader ? 0.72F : 0.85F), rng.range(0.17F, 0.22F) * size * (1 - 0.3F * t));
            Branch limb = bend(start, az, el, len, 4, 0.03F, rl, 0.05F, 1, rng, new int[]{6, 3, 3}, 2);
            f.branches.add(limb);
            int subs = 2 + (rng.next() < 0.7F ? 1 : 0);
            for (int k = 0; k < subs; k++) {
                float ts = 0.3F + 0.6F * (k + 0.2F + rng.next() * 0.5F) / subs;
                float[] s = along(limb, ts);
                float saz = az + (k % 2 == 0 ? 1 : -1) * (float) Math.toRadians(rng.range(28, 55));
                float sel = el + (float) Math.toRadians(rng.range(0, 28));
                float sr = Math.min(radiusAlong(limb, ts) * 0.72F, 0.09F);
                Branch sub = bend(s, saz, sel, rng.range(2.4F, 3.4F) * size, 2, 0.05F, sr, 0.03F, 2, rng, new int[]{4, 0, 0}, 0);
                f.branches.add(sub);
                float[] st = sub.points()[2];
                f.groups.add(new Group(st[0], st[1] + 0.1F, st[2], rng.range(1.3F, 1.55F), true, BROADLEAF, 0, 0));
                float[] mid = along(sub, 0.45F);
                f.groups.add(new Group(mid[0], mid[1] + 0.2F, mid[2], rng.range(1.0F, 1.2F), false, BROADLEAF, 0, 0));
            }
            float[] tip = limb.points()[limb.points().length - 1];
            f.groups.add(new Group(tip[0], tip[1] + 0.1F, tip[2], rng.range(1.4F, 1.6F), true, BROADLEAF, 0, 0));
            for (float ta = 0.35F; ta < 0.9F; ta += 0.2F) {
                float[] p = along(limb, ta);
                f.groups.add(new Group(p[0], p[1] + 0.35F, p[2], rng.range(1.1F, 1.3F), ta > 0.6F, BROADLEAF, 0, 0));
            }
            // the dome above the limb (shoots rising from its upper side fill the gap to the crown above)
            float[] up = along(limb, 0.6F);
            f.groups.add(new Group(up[0], up[1] + 1.1F, up[2], rng.range(1.1F, 1.3F), false, BROADLEAF, 0, 0));
        }
        if (leader) {
            float laz = rng.range(0, 6.2832F);
            for (int k = 0; k < 2; k++) {
                float y = trunkLen - 2.6F + 1.3F * k;
                float[] s = at(trunk, y);
                Branch shoot = bend(s, laz + (float) Math.PI * k, (float) Math.toRadians(rng.range(30, 45)), rng.range(2.0F, 2.8F) * size, 2, 0.05F,
                        Math.min(radiusAt(trunk, y) * 0.75F, 0.08F), 0.03F, 2, rng, new int[]{4, 0, 0}, 0);
                f.branches.add(shoot);
                float[] st = shoot.points()[2];
                f.groups.add(new Group(st[0], st[1] + 0.1F, st[2], rng.range(1.3F, 1.5F), true, BROADLEAF, 0, 0));
            }
            float[] top = tp[rings];
            f.groups.add(new Group(top[0], top[1] + 0.3F, top[2], rng.range(1.5F, 1.7F), true, BROADLEAF, 0, 0));
            float[] mid = at(trunk, fork + (trunkLen - fork) * 0.5F);
            f.groups.add(new Group(mid[0], mid[1], mid[2], 1.3F, false, BROADLEAF, 0, 0));
        } else {
            float[] top = tp[rings];
            f.groups.add(new Group(top[0], top[1] + 2.2F, top[2], 1.5F, false, BROADLEAF, 0, 0));
        }
        f.crown();
        return f;
    }

    // ------------------------------------------------------------------------------------ shagbark hickory

    /**
     * Carya ovata (Silvics: 21-24 m, 30-61 cm d.b.h.; a clear straight cylindrical bole, the main stem forking at one half
     * to two thirds of the height; open, irregular branching): a slim bole that tapers little, a few short side branches
     * below the fork (the lowest drooping), two or three steep stems above it with short irregular side branches, and a
     * narrow oblong crown of loose foliage groups (big compound leaves, an open crown).
     */
    static Form hickory(int variant) {
        var rng = new Rng(0x41C0_0A7AL * 29 + variant * 0xC2B2AE35L + 5);
        var f = new Form(Species.SHAGBARK_HICKORY);
        float size = rng.range(0.9F, 1.1F);
        float height = rng.range(20.0F, 24.5F) * size;
        float fork = height * rng.range(0.5F, 0.64F);
        int stems = variant % 3 == 0 ? 3 : 2;
        float r0 = 0.25F * size + 0.05F;
        float leanAz = rng.range(0, 6.2832F), lean = rng.range(0.0F, 0.15F);
        float leadAz = rng.range(0, 6.2832F), leadTilt = rng.range(0.12F, 0.24F);
        // the trunk runs on as the main stem: above the fork it leans away (radians from vertical) and tapers to a point
        float trunkLen = height - rng.range(1.4F, 2.2F);
        int rings = (int) Math.ceil(trunkLen);
        float[][] tp = new float[rings + 1][];
        float[] tr = new float[rings + 1];
        for (int i = 0; i <= rings; i++) {
            float y = Math.min(i, trunkLen), u = Math.min(1, y / fork);
            float off = y > fork ? (y - fork) * (float) Math.tan(leadTilt) : 0;
            tp[i] = new float[]{0.5F + (float) Math.cos(leanAz) * lean * u * u + (float) Math.cos(leadAz) * off, y,
                    0.5F + (float) Math.sin(leanAz) * lean * u * u + (float) Math.sin(leadAz) * off};
            float above = Math.max(0, y - fork) / Math.max(0.5F, trunkLen - fork);
            tr[i] = (r0 - 0.06F * u) * (1 - 0.78F * above) + (y < 0.5F ? 0.11F * size * (1 - y / 0.5F) * (1 - y / 0.5F) : 0);
        }
        tr[rings] = 0.03F;
        f.branches.add(new Branch(tp, tr, 0, true, new int[]{10, 6, 4}, Integer.MAX_VALUE, false));
        f.trunkTop = fork;
        Branch trunk = f.branches.get(0);
        var leaders = new ArrayList<Branch>();
        leaders.add(trunk);
        for (int k = 1; k < stems; k++) {
            float az = leadAz + (float) (Math.PI * 2 * k / stems) + rng.range(-0.3F, 0.3F);
            float y0 = fork + rng.range(-0.3F, 0.4F);
            float el = (float) Math.toRadians(90 - rng.range(16, 28));
            Branch stem = bend(at(trunk, y0), az, el, (height - y0) * rng.range(0.75F, 0.88F), 4, -0.02F,
                    Math.min(radiusAt(trunk, y0) * 0.82F, 0.2F * size), 0.03F, 1, rng, new int[]{8, 4, 3}, 3);
            f.branches.add(stem);
            leaders.add(stem);
        }
        // below the fork: a few short side branches, the lowest ones drooping
        int lows = 4 + (int) (rng.next() * 2.5F);
        float az = rng.range(0, 6.2832F);
        for (int k = 0; k < lows; k++) {
            float h = height * 0.33F + (fork - 0.6F - height * 0.33F) * (k + rng.next() * 0.5F) / lows;
            az += 2.39996F + rng.range(-0.3F, 0.3F);
            float u = k / (float) Math.max(1, lows - 1);
            float el = (float) Math.toRadians(-10 + 34 * u + rng.range(-6, 6));
            float len = rng.range(3.4F, 4.8F) * size;
            Branch b = bend(at(trunk, h), az, el, len, 3, u < 0.5F ? -0.06F : 0.03F, Math.min(radiusAt(trunk, h) * 0.42F, 0.11F), 0.03F, 1, rng,
                    new int[]{4, 3, 0}, 1);
            f.branches.add(b);
            float[] tip = b.points()[3];
            f.groups.add(new Group(tip[0], tip[1] + 0.15F, tip[2], rng.range(1.2F, 1.4F), true, BROADLEAF, 0, 0));
            float[] mid = along(b, 0.55F);
            f.groups.add(new Group(mid[0], mid[1] + 0.25F, mid[2], rng.range(1.0F, 1.2F), false, BROADLEAF, 0, 0));
            float[] s = along(b, 0.45F);
            float sa = az + (rng.next() < 0.5F ? 1 : -1) * (float) Math.toRadians(rng.range(30, 55));
            Branch twig = bend(s, sa, el + (float) Math.toRadians(rng.range(5, 25)), rng.range(1.6F, 2.4F) * size, 2, 0.04F,
                    Math.min(radiusAlong(b, 0.45F) * 0.6F, 0.05F), 0.02F, 2, rng, new int[]{4, 0, 0}, 0);
            f.branches.add(twig);
            float[] tt = twig.points()[2];
            f.groups.add(new Group(tt[0], tt[1] + 0.1F, tt[2], rng.range(1.1F, 1.3F), true, BROADLEAF, 0, 0));
        }
        // the stems: short irregular side branches every 1.5-2.2 m, a tuft at each top
        for (Branch stem : leaders) {
            boolean main = stem == trunk;
            float[][] pts = stem.points();
            float len = 0;
            for (int i = 0; i + 1 < pts.length; i++) len += (float) Math.hypot(Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][2] - pts[i][2]), pts[i + 1][1] - pts[i][1]);
            float t0 = main ? Math.min(0.92F, (fork + 0.6F) / trunkLen) : 0.12F;
            float saz = rng.range(0, 6.2832F);
            for (float t = t0; t < 0.9F; t += rng.range(1.1F, 1.5F) / len) {
                float[] s = along(stem, t);
                saz += 2.39996F + rng.range(-0.5F, 0.5F);
                float sl = rng.range(2.6F, 4.0F) * size * (1 - 0.35F * t);
                float sel = (float) Math.toRadians(rng.range(10, 40));
                Branch b = bend(s, saz, sel, sl, 2, 0.04F, Math.min(radiusAlong(stem, t) * 0.55F, 0.08F), 0.025F, 2, rng,
                        new int[]{4, 0, 0}, 0);
                f.branches.add(b);
                float[] tip = b.points()[2];
                f.groups.add(new Group(tip[0], tip[1] + 0.1F, tip[2], rng.range(1.2F, 1.4F), true, BROADLEAF, 0, 0));
                if (rng.next() < 0.7F) {
                    float[] mid = along(b, 0.5F);
                    f.groups.add(new Group(mid[0], mid[1] + 0.2F, mid[2], rng.range(1.0F, 1.15F), false, BROADLEAF, 0, 0));
                }
                if (sl > 2.8F && rng.next() < 0.6F) {
                    float[] ss = along(b, 0.5F);
                    float sa = saz + (rng.next() < 0.5F ? 1 : -1) * (float) Math.toRadians(rng.range(35, 60));
                    Branch twig = bend(ss, sa, sel + (float) Math.toRadians(rng.range(5, 20)), rng.range(1.4F, 2.0F) * size, 2, 0.04F,
                            Math.min(radiusAlong(b, 0.5F) * 0.6F, 0.045F), 0.02F, 2, rng, new int[]{4, 0, 0}, 0);
                    f.branches.add(twig);
                    float[] tt = twig.points()[2];
                    f.groups.add(new Group(tt[0], tt[1] + 0.1F, tt[2], rng.range(1.1F, 1.3F), true, BROADLEAF, 0, 0));
                }
            }
            float[] top = pts[pts.length - 1];
            f.groups.add(new Group(top[0], top[1] + 0.2F, top[2], rng.range(1.25F, 1.45F), true, BROADLEAF, 0, 0));
            float[] mid = along(stem, main ? 0.82F : 0.6F);
            f.groups.add(new Group(mid[0], mid[1], mid[2], 1.1F, false, BROADLEAF, 0, 0));
        }
        f.crown();
        return f;
    }

    // ------------------------------------------------------------------------------------------- red maple

    /**
     * Acer rubrum (Silvics: 18-27 m, 46-76 cm d.b.h.; a prolific sprouter, stems in clumps from a stump): smaller and
     * finer than the oaks. Half the variants fork low (2.3-3.1 m) into a clump of two or three stems leaning out; the
     * others keep one stem with a fork at 5-6 m. The branches ascend steeply (a V of 35-55 degrees), the crown is an
     * oval to rounded mass of many small dense foliage groups.
     */
    static Form redMaple(int variant) {
        var rng = new Rng(0x3A91_E5E1L * 23 + variant * 0x27D4EB2FL + 9);
        var f = new Form(Species.RED_MAPLE);
        float size = rng.range(0.88F, 1.12F);
        float height = rng.range(15.5F, 20.0F) * size;
        boolean clump = variant % 2 == 0;
        float fork = clump ? rng.range(2.3F, 3.1F) : rng.range(4.8F, 6.2F) * size;
        int stems = clump ? (variant % 4 == 0 ? 3 : 2) : 1;
        float r0 = 0.24F * size + 0.05F;
        float leanAz = rng.range(0, 6.2832F), lean = rng.range(0.0F, 0.15F);
        float leadAz = rng.range(0, 6.2832F), leadTilt = clump ? rng.range(0.2F, 0.34F) : rng.range(0.0F, 0.08F);
        float trunkLen = height - rng.range(1.2F, 2.0F);
        int rings = (int) Math.ceil(trunkLen);
        float[][] tp = new float[rings + 1][];
        float[] tr = new float[rings + 1];
        for (int i = 0; i <= rings; i++) {
            float y = Math.min(i, trunkLen), u = Math.min(1, y / fork);
            float off = y > fork ? (y - fork) * (float) Math.tan(leadTilt) : 0;
            tp[i] = new float[]{0.5F + (float) Math.cos(leanAz) * lean * u * u + (float) Math.cos(leadAz) * off, y,
                    0.5F + (float) Math.sin(leanAz) * lean * u * u + (float) Math.sin(leadAz) * off};
            float above = Math.max(0, y - fork) / Math.max(0.5F, trunkLen - fork);
            float stemShare = clump && y > fork ? 0.82F : 1;
            tr[i] = (r0 - 0.05F * u) * stemShare * (1 - 0.8F * above) + (y < 0.5F ? 0.12F * size * (1 - y / 0.5F) * (1 - y / 0.5F) : 0);
        }
        tr[rings] = 0.03F;
        f.branches.add(new Branch(tp, tr, 0, true, new int[]{10, 6, 4}, Integer.MAX_VALUE, false));
        f.trunkTop = fork;
        Branch trunk = f.branches.get(0);
        var leaders = new ArrayList<Branch>();
        leaders.add(trunk);
        for (int k = 1; k < stems; k++) {
            float az = leadAz + (float) (Math.PI * 2 * k / stems) + rng.range(-0.35F, 0.35F);
            float y0 = fork + rng.range(-0.2F, 0.3F);
            float el = (float) Math.toRadians(90 - rng.range(18, 30));
            Branch stem = bend(at(trunk, y0), az, el, (height - y0) * rng.range(0.78F, 0.9F), 4, 0.01F,
                    Math.min(radiusAt(trunk, y0) * 0.85F, 0.19F * size), 0.03F, 1, rng, new int[]{8, 4, 3}, 3);
            f.branches.add(stem);
            leaders.add(stem);
        }
        // single-stem variants: ascending limbs from the fork, a V
        if (!clump) {
            int limbs = 3 + (int) (rng.next() * 1.99F);
            float az = rng.range(0, 6.2832F);
            for (int i = 0; i < limbs; i++) {
                az += 2.39996F + rng.range(-0.3F, 0.3F);
                float y0 = fork - 0.2F + 1.4F * i / limbs;
                float el = (float) Math.toRadians(rng.range(40, 55));
                Branch limb = bend(at(trunk, y0), az, el, rng.range(5.4F, 6.6F) * size, 3, 0.02F,
                        Math.min(radiusAt(trunk, y0) * 0.7F, 0.15F * size), 0.04F, 1, rng, new int[]{6, 3, 3}, 2);
                f.branches.add(limb);
                leaders.add(limb);
            }
        }
        // every stem and limb: steep side branches with dense foliage groups along their outer part
        for (Branch stem : leaders) {
            boolean main = stem == trunk;
            float[][] pts = stem.points();
            float len = 0;
            for (int i = 0; i + 1 < pts.length; i++) len += (float) Math.hypot(Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][2] - pts[i][2]), pts[i + 1][1] - pts[i][1]);
            float t0 = main ? Math.min(0.92F, (fork + (clump ? 1.6F : 0.8F)) / trunkLen) : 0.25F;
            float saz = rng.range(0, 6.2832F), spacing = leaders.size() >= 3 ? 1.45F : 1.0F;
            for (float t = t0; t < 0.88F; t += rng.range(1.0F, 1.4F) * spacing / len) {
                float[] s = along(stem, t);
                saz += 2.39996F + rng.range(-0.4F, 0.4F);
                float sl = rng.range(3.0F, 4.4F) * size * (1 - 0.4F * t);
                float sel = (float) Math.toRadians(rng.range(25, 45));
                Branch b = bend(s, saz, sel, sl, 2, 0.04F, Math.min(radiusAlong(stem, t) * 0.55F, 0.08F), 0.025F, 2, rng,
                        new int[]{4, 0, 0}, 0);
                f.branches.add(b);
                float[] tip = b.points()[2];
                f.groups.add(new Group(tip[0], tip[1] + 0.1F, tip[2], rng.range(1.2F, 1.4F), true, BROADLEAF, 0, 0));
                float[] mid = along(b, 0.5F);
                f.groups.add(new Group(mid[0], mid[1] + 0.2F, mid[2], rng.range(1.0F, 1.2F), rng.next() < 0.4F, BROADLEAF, 0, 0));
                if (rng.next() < 0.6F) {
                    float[] ss = along(b, 0.45F);
                    float sa = saz + (rng.next() < 0.5F ? 1 : -1) * (float) Math.toRadians(rng.range(30, 55));
                    Branch twig = bend(ss, sa, sel + (float) Math.toRadians(rng.range(5, 20)), rng.range(1.4F, 2.2F) * size, 2, 0.04F,
                            Math.min(radiusAlong(b, 0.45F) * 0.6F, 0.045F), 0.02F, 2, rng, new int[]{4, 0, 0}, 0);
                    f.branches.add(twig);
                    float[] tt = twig.points()[2];
                    f.groups.add(new Group(tt[0], tt[1] + 0.1F, tt[2], rng.range(1.1F, 1.3F), true, BROADLEAF, 0, 0));
                }
            }
            float[] top = pts[pts.length - 1];
            f.groups.add(new Group(top[0], top[1] + 0.25F, top[2], rng.range(1.3F, 1.5F), true, BROADLEAF, 0, 0));
            float[] mid = along(stem, main ? 0.8F : 0.65F);
            f.groups.add(new Group(mid[0], mid[1], mid[2], 1.15F, false, BROADLEAF, 0, 0));
        }
        f.crown();
        return f;
    }

    // ------------------------------------------------------------------------------------- eastern hemlock

    /**
     * Tsuga canadensis (Silvics: over 30 m and 89-102 cm in old stands, the most shade tolerant tree, cool moist coves,
     * north slopes and stream banks; dendrology: a dense conical crown, fine branches, a drooping leader, a poor self
     * pruner): a strongly tapering trunk, slender branches not in whorls (one or two every half metre, round the trunk)
     * from low down (live crown from 2-6 m), level to drooping, shortening steadily toward the top; the top nods to one
     * side. Foliage: flat sprays hanging off the branches (SPRAY), smaller and denser than the white pine's.
     */
    static Form hemlock(int variant) {
        var rng = new Rng(0x7E31_0C4BL * 41 + variant * 0x165667B1L + 13);
        var f = new Form(Species.EASTERN_HEMLOCK);
        float size = rng.range(0.9F, 1.1F);
        float height = rng.range(18.5F, 24.5F) * size;
        float r0 = 0.22F + 0.011F * height;
        float leanAz = rng.range(0, 6.2832F), lean = rng.range(0.0F, 0.2F);
        float nodAz = rng.range(0, 6.2832F), nod = rng.range(0.6F, 1.0F);
        int rings = (int) Math.ceil(height);
        float[][] tp = new float[rings + 1][];
        float[] tr = new float[rings + 1];
        for (int i = 0; i <= rings; i++) {
            float y = Math.min(i, height), u = y / height;
            // the drooping leader: the last 1.6 m bends over toward nodAz
            float bendT = Math.max(0, y - (height - 1.6F)) / 1.6F;
            float off = nod * bendT * bendT * 1.1F;
            tp[i] = new float[]{0.5F + (float) Math.cos(leanAz) * lean * u * u + (float) Math.cos(nodAz) * off, y - nod * bendT * bendT * 0.4F,
                    0.5F + (float) Math.sin(leanAz) * lean * u * u + (float) Math.sin(nodAz) * off};
            tr[i] = r0 * (float) Math.pow(1 - u, 0.95) + 0.025F + (y < 0.5F ? 0.12F * (1 - y / 0.5F) * (1 - y / 0.5F) : 0);
        }
        tr[rings] = 0.015F;
        f.branches.add(new Branch(tp, tr, 0, true, new int[]{10, 6, 4}, Integer.MAX_VALUE, false));
        Branch trunk = f.branches.get(0);
        f.trunkTop = height;
        float crownBase = Math.max(2.4F, rng.range(0.08F, 0.2F) * height);
        float lmax = rng.range(5.0F, 6.0F) * size;
        float az = rng.range(0, 6.2832F);
        for (float h = crownBase; h < height - 1.1F; h += rng.range(0.42F, 0.62F)) {
            float u = (h - crownBase) / (height - crownBase);
            int n = rng.next() < 0.22F ? 2 : 1;
            for (int k = 0; k < n; k++) {
                az += k == 0 ? 2.39996F + rng.range(-0.4F, 0.4F) : (float) Math.PI + rng.range(-0.5F, 0.5F);
                float len = Math.max(0.7F, lmax * (0.1F + 0.9F * (float) Math.pow(1 - u, 1.05)) * rng.range(0.85F, 1.1F));
                float el = (float) Math.toRadians(u < 0.3F ? rng.range(-16, -4) : u < 0.7F ? rng.range(-8, 4) : rng.range(0, 14));
                float rise = u > 0.8F ? -0.04F : -0.09F;
                // near the ground the sweep is gentler: the tip stays above about 1 m
                if (h - 0.5F * len < 1.2F) { el = Math.max(el, (float) Math.toRadians(-2)); rise = -0.04F; }
                float rb = Math.min(radiusAt(trunk, h) * 0.45F, 0.025F + 0.012F * len);
                int segs = len > 2.8F ? 3 : 2;
                Branch b = bend(at(trunk, h), az, el, len, segs, rise, rb, 0.02F, 1, rng,
                        new int[]{3, rb >= 0.07F && u < 0.4F ? 3 : 0, 0}, 0);
                f.branches.add(b);
                float dx = (float) Math.cos(az), dz = (float) Math.sin(az);
                float t0 = Math.max(0.15F, 1 - 3.2F / len);
                for (float t = t0; t < 0.97F; t += 0.78F / len) {
                    float[] p = along(b, t);
                    f.groups.add(new Group(p[0], p[1] + 0.05F, p[2], rng.range(0.62F, 0.74F), t > 0.7F || u > 0.85F, SPRAY, dx, dz));
                }
                float[] tip = b.points()[b.points().length - 1];
                f.groups.add(new Group(tip[0], tip[1] + 0.02F, tip[2], rng.range(0.74F, 0.84F), true, SPRAY, dx, dz));
                if (len > 3.0F) for (float t : new float[]{0.48F, 0.74F}) {
                    float[] s = along(b, t);
                    float sa = az + (rng.next() < 0.5F ? 1 : -1) * (float) Math.toRadians(rng.range(45, 70));
                    float sl = rng.range(0.6F, 0.95F);
                    f.groups.add(new Group(s[0] + (float) Math.cos(sa) * sl, s[1] - 0.05F, s[2] + (float) Math.sin(sa) * sl,
                            0.62F, false, SPRAY, (float) Math.cos(sa), (float) Math.sin(sa)));
                }
            }
        }
        // the nodding top: sprays along the bent leader
        for (float d : new float[]{0.4F, 1.2F}) {
            float[] p = along(trunk, (height - d) / height);
            f.groups.add(new Group(p[0], p[1], p[2], 0.7F, true, SPRAY, (float) Math.cos(nodAz), (float) Math.sin(nodAz)));
        }
        f.crown();
        return f;
    }

    // ---------------------------------------------------------------------------------------------- helpers

    static final class Rng {
        private long s;
        Rng(long seed) { s = seed; }
        float next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return ((z ^ (z >>> 31)) >>> 40) / (float) (1 << 24);
        }
        float range(float a, float b) { return a + (b - a) * next(); }
    }

    /** A branch from start (on the parent's axis), azimuth az, elevation el: n segments bending upward, r0 -> r1. */
    static Branch bend(float[] start, float az, float el, float len, int n, float rise, float r0, float r1, int depth, Rng rng,
                       int[] sides, int lod2Segments) {
        float[][] pts = new float[n + 1][];
        float[] rad = new float[n + 1];
        pts[0] = start.clone();
        rad[0] = r0;
        float seg = len / n, e = el, a = az;
        for (int i = 1; i <= n; i++) {
            e += rise + rng.range(-0.06F, 0.06F);
            a += rng.range(-0.15F, 0.15F);
            float c = (float) Math.cos(e);
            pts[i] = new float[]{pts[i - 1][0] + seg * c * (float) Math.cos(a), pts[i - 1][1] + seg * (float) Math.sin(e),
                    pts[i - 1][2] + seg * c * (float) Math.sin(a)};
            rad[i] = r0 + (r1 - r0) * i / n;
        }
        return new Branch(pts, rad, depth, r0 >= COLLIDE_BASE, sides, lod2Segments, true);
    }

    static float[] along(Branch b, float t) {
        float[][] p = b.points();
        float f = Math.max(0, Math.min(1, t)) * (p.length - 1);
        int i = Math.min(p.length - 2, (int) f);
        float u = f - i;
        return new float[]{p[i][0] + (p[i + 1][0] - p[i][0]) * u, p[i][1] + (p[i + 1][1] - p[i][1]) * u, p[i][2] + (p[i + 1][2] - p[i][2]) * u};
    }

    static float radiusAlong(Branch b, float t) {
        float[] r = b.radii();
        float f = Math.max(0, Math.min(1, t)) * (r.length - 1);
        int i = Math.min(r.length - 2, (int) f);
        return r[i] + (r[i + 1] - r[i]) * (f - i);
    }

    /** The point of a (vertical-ish) trunk polyline at height y. */
    static float[] at(Branch b, float y) {
        float[][] p = b.points();
        for (int i = 0; i + 1 < p.length; i++) {
            if (y >= p[i][1] && y <= p[i + 1][1]) {
                float u = (y - p[i][1]) / Math.max(1e-4F, p[i + 1][1] - p[i][1]);
                return new float[]{p[i][0] + (p[i + 1][0] - p[i][0]) * u, y, p[i][2] + (p[i + 1][2] - p[i][2]) * u};
            }
        }
        return p[p.length - 1].clone();
    }

    static float radiusAt(Branch b, float y) {
        float[][] p = b.points();
        for (int i = 0; i + 1 < p.length; i++) {
            if (y >= p[i][1] && y <= p[i + 1][1])
                return b.radii()[i] + (b.radii()[i + 1] - b.radii()[i]) * (y - p[i][1]) / Math.max(1e-4F, p[i + 1][1] - p[i][1]);
        }
        return b.radii()[b.radii().length - 1];
    }

    private TreeForms() {}
}
