package com.antaurora.apofirstlight.vegetation.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared geometry of the mesh plants (AFL vegetation, docs/gameplay/vegetation_v1_design.md 10-13): pure, no
 * Minecraft types. A block is a square metre of one species: {@value #VARIANTS} fixed layouts per species (BluegrassShape,
 * CattailShape, YarrowShape; P2b: BigBluestemShape, LittleBluestemShape, SedgeShape, FernShape, PlantainShape,
 * DandelionShape, ConeflowerShape, SusanShape, GoldenrodShape, ReedShape, BlackberryShape), picked by the position,
 * each with its clumps at three levels of detail.
 * <ul>
 * <li>A {@link Blade} is anything that bends along its length: a leaf (a flat strip, both faces drawn), a stem (a tube),
 * a card standing in for a culm. Its points ({@link Pose}) are what the near layer (VegetationDynamics) simulates.</li>
 * <li>A {@link Piece} is a rigid part carried by a blade at a share of its length: a cattail's spikes, a yarrow's flower
 * head and stem leaves. It turns with the blade's tangent there and moves with its point.</li>
 * <li>A {@link Card} is a far level's crossed picture of a whole clump.</li>
 * </ul>
 * One sheet for every species ({@value #COLUMNS} columns of 32 x 128 px): texture coordinates run from a part's foot (v 1)
 * to its tip (v 0), so the wind in the terrain vertex stage (VegetationWind) reads the height along a plant from v, and a
 * rigid part takes the rows of the height it sits at. {@link #WIND} scales the wind per column (a cattail sways more
 * than the turf). Pressed ({@link Press}: a footprint, a wheel, rising again), what lies or leans takes the still copy of
 * the sheet ({@link #STILL}): the shader moves only the standing sheet.
 */
final class PlantShape {
    static final int VARIANTS = 32, LEVELS = 3, NONE = LEVELS;
    /** The species in MeshPlantBlock.Species' order (the layouts' index). */
    static final int BLUEGRASS = 0, CATTAIL = 1, YARROW = 2, BIG_BLUESTEM = 3, LITTLE_BLUESTEM = 4, SEDGE = 5, FERN = 6,
            PLANTAIN = 7, DANDELION = 8, CONEFLOWER = 9, SUSAN = 10, GOLDENROD = 11, REED = 12, BLACKBERRY = 13, SPECIES = 14;
    /** Sheet columns, each 32 px wide and 128 tall (the art: tools/vegetation/build-mesh-plants-art.mjs; P2b: 32 -> 64). */
    static final int COLUMNS = 64;
    /** A column's card is 32 px wide and 128 px tall: width = height * ASPECT before the stretch. */
    static final float ASPECT = 0.25F;
    /** The two sprites: the standing sheet (waves) and its still copy (lies or leans, does not move). */
    static final int STANDING = 0, STILL = 1;
    /** A footprint's half length and half width (a boot pressing the plants, m), and the band around it where blades lean away. */
    static final float SOLE_ALONG = 0.17F, SOLE_ACROSS = 0.1F, EDGE = 0.13F;
    /**
     * Width (strip) or radius (tube) along a blade: even, Poa's boat tip, a cattail's sword, a cattail stem, tapering, a
     * female spike, a male spike, a long-pointed grass leaf (the bluestems).
     */
    static final int FLAT = 0, POA = 1, SWORD = 2, CATTAIL_STEM = 3, TAPER = 4, SPIKE = 5, MALE = 6, ACUMINATE = 7;
    static final int MAX_POINTS = 8, MAX_SIDES = 10;
    /** The wind's reach per sheet column (1 the turf; tall plants further). */
    static final float[] WIND = new float[COLUMNS];
    /** How far a quad's u keeps off its column's edges (a share of the column: a sixteenth of a texel). */
    static final float U_EDGE = 1 / 512F;
    /** The light without a shader pack: from high in the sky (the blades' brightness by their normals, baked). */
    private static final float[] SUN = norm(0.3F, 0.85F, 0.45F);
    /** How far a strip's edge normals turn out across it (a leaf reads as folded along its midrib). */
    private static final float ROUND = 0.7F;

    static {
        java.util.Arrays.fill(WIND, 1);
        // 13.8 (user 2026-10-24 "摇头晃脑"): were 3 and 1.6
        for (int c : new int[]{CattailShape.DEAD, CattailShape.LEAF, CattailShape.STEM, CattailShape.SPIKES, CattailShape.CARD}) WIND[c] = 1.6F;
        for (int c : new int[]{YarrowShape.STEM, YarrowShape.LEAF, YarrowShape.CARD, YarrowShape.CARD_FLOWERS, YarrowShape.PARTS}) WIND[c] = 1.2F;
        // P2b (doc 15): the tall grass further, the bunchgrass a little, the fern's stiff stipe and frond less
        for (int c : new int[]{BigBluestemShape.LEAF, BigBluestemShape.CULM, BigBluestemShape.PARTS, BigBluestemShape.CARD, BigBluestemShape.CARD_LEAVES}) WIND[c] = 1.4F;
        for (int c : new int[]{LittleBluestemShape.LEAF, LittleBluestemShape.CULM, LittleBluestemShape.CARD}) WIND[c] = 1.1F;
        for (int c : new int[]{FernShape.STIPE, FernShape.FROND}) WIND[c] = 0.9F;
        // batch 2 (doc 16): a flower's parts a little further than its stem (their rows sit a little below the stem's top)
        WIND[DandelionShape.PARTS] = 1.08F;
        for (int c : new int[]{ConeflowerShape.LEAF, ConeflowerShape.STEM, ConeflowerShape.CARD, ConeflowerShape.CARD_FLOWERS}) WIND[c] = 1.3F;
        WIND[ConeflowerShape.PARTS] = 1.4F;
        for (int c : new int[]{SusanShape.LEAF, SusanShape.STEM, SusanShape.CARD, SusanShape.CARD_FLOWERS}) WIND[c] = 1.2F;
        WIND[SusanShape.PARTS] = 1.3F;
        // batch 3 (doc 18): the reed as far as the cattail, the goldenrod as the yarrow, the bramble's canes stiff
        for (int c : new int[]{GoldenrodShape.STEM, GoldenrodShape.PARTS, GoldenrodShape.CARD, GoldenrodShape.CARD_FLOWERS}) WIND[c] = 1.2F;
        for (int c : new int[]{ReedShape.CULM, ReedShape.PARTS, ReedShape.CARD, ReedShape.CARD_PLUMES}) WIND[c] = 1.6F;
        for (int c : new int[]{BlackberryShape.CANE, BlackberryShape.PARTS, BlackberryShape.CARD}) WIND[c] = 0.8F;
    }

    /**
     * The far levels' cards fitted to the near geometry (doc 17.2; user 2026-10-24 "远处看很大，但是靠近就变小了"): per
     * species and level a height and a width factor for every card at that level, so a block's side silhouette there has
     * LOD0's area and mean height (fitted offline over the 32 layouts and 8 views, cut-out tested against the sheet). Level
     * 0 is never scaled.
     */
    static final float[][][] FIT = {
            {{1, 1}, {0.97F, 1.78F}, {1.09F, 1.66F}},   // bluegrass
            {{1, 1}, {0.91F, 0.55F}, {0.87F, 1.34F}},   // cattail
            {{1, 1}, {0.70F, 0.70F}, {0.67F, 1.91F}},   // yarrow
            {{1, 1}, {0.46F, 1.00F}, {0.43F, 1.30F}},   // big bluestem (its culms are too thin to see from afar)
            {{1, 1}, {0.97F, 1.27F}, {0.96F, 2.28F}},   // little bluestem
            {{1, 1}, {1.38F, 0.57F}, {1.42F, 1.66F}},   // sedge
            {{1, 1}, {1, 1}, {1, 1}},                   // fern (no cards: its far levels are simpler fronds)
            {{1, 1}, {0.47F, 1.26F}, {0.48F, 3.51F}},   // plantain
            {{1, 1}, {0.74F, 0.99F}, {0.76F, 2.86F}},   // dandelion
            {{1, 1}, {0.81F, 0.95F}, {0.71F, 2.00F}},   // coneflower
            {{1, 1}, {0.68F, 0.87F}, {0.65F, 1.78F}},   // black-eyed Susan
            {{1, 1}, {0.80F, 0.40F}, {0.81F, 0.63F}},   // goldenrod
            {{1, 1}, {0.99F, 0.31F}, {0.90F, 0.53F}},   // common reed
            {{1, 1}, {0.91F, 0.23F}, {0.88F, 0.29F}}};  // blackberry (its leaves face every way: a sparse silhouette)

    @SuppressWarnings("unchecked")
    private static final List<Clump>[][][] RAW = new List[][][]{BluegrassShape.layouts(), CattailShape.layouts(), YarrowShape.layouts(),
            BigBluestemShape.layouts(), LittleBluestemShape.layouts(), SedgeShape.layouts(), FernShape.layouts(),
            PlantainShape.layouts(), DandelionShape.layouts(), ConeflowerShape.layouts(), SusanShape.layouts(),
            GoldenrodShape.layouts(), ReedShape.layouts(), BlackberryShape.layouts()};
    private static List<Clump>[][][] LAYOUTS = fitted(FIT);

    private PlantShape() {}

    /**
     * The layouts with every stand-in card at level l of species s scaled by fit[s][l] (height, width) about its foot. A card
     * whose column LOD0 draws as a card too (a plantain's spikes, a little bluestem's culms) is the plant itself, not a
     * stand-in, and keeps its size.
     */
    @SuppressWarnings("unchecked")
    static List<Clump>[][][] fitted(float[][][] fit) {
        List<Clump>[][][] out = new List[SPECIES][VARIANTS][LEVELS];
        for (int s = 0; s < SPECIES; s++) {
            boolean[] near = new boolean[COLUMNS];
            for (var layout : RAW[s]) for (Clump c : layout[0]) for (Card k : c.cards) near[k.column()] = true;
            for (int v = 0; v < VARIANTS; v++)
                for (int l = 0; l < LEVELS; l++) {
                    float fh = l == 0 ? 1 : fit[s][l][0], fw = l == 0 ? 1 : fit[s][l][1];
                    if (fh == 1 && fw == 1) {
                        out[s][v][l] = RAW[s][v][l];
                        continue;
                    }
                    var list = new ArrayList<Clump>();
                    for (Clump c : RAW[s][v][l]) {
                        if (c.cards.isEmpty()) {
                            list.add(c);
                            continue;
                        }
                        var cards = new ArrayList<Card>();
                        for (Card k : c.cards)
                            cards.add(near[k.column()] ? k : new Card(k.x(), k.z(), k.yaw(), k.h() * fh, k.w() * fw, k.arch(), k.column(), k.segments(), k.tinted()));
                        list.add(new Clump(c.x, c.z, c.rank, c.blades, cards, c.pieces));
                    }
                    out[s][v][l] = List.copyOf(list);
                }
        }
        return out;
    }

    /** A species' mean quads a block at LOD0 (over its layouts): how heavy its sections are to rebuild (VegetationClient). */
    static int nearQuads(int species) {
        return NearQuads.MEAN[species];
    }

    private static final class NearQuads {
        static final int[] MEAN = new int[SPECIES];

        static {
            var quads = new ArrayList<Quad>();
            for (int s = 0; s < SPECIES; s++) {
                int sum = 0;
                for (int v = 0; v < VARIANTS; v++) {
                    quads.clear();
                    for (Clump c : RAW[s][v][0]) clump(c, Press.NONE, quads);
                    sum += quads.size();
                }
                MEAN[s] = sum / VARIANTS;
            }
        }
    }

    /** Offline fitting (SizeProbe): use these factors from now on. */
    static void refit(float[][][] fit) {
        LAYOUTS = fitted(fit);
    }

    /** A species' layout's clumps at a level (empty for NONE). */
    static List<Clump> clumps(int species, int variant, int level) {
        return level >= LEVELS ? List.of() : LAYOUTS[species][variant][level];
    }

    /** One card: foot (x, z) in the cell, yaw, height, width, arch (out of plane, share of the height), column, segments. */
    record Card(float x, float z, float yaw, float h, float w, float arch, int column, int segments, boolean tinted) {}

    /**
     * One blade: foot (x, y, z), the way it arches (bendYaw), the way its width lies (widthYaw, turning by {@code twist}
     * towards the tip), length, width (a tube's diameter), its angle from upright at the foot and at the tip (radians),
     * the sheet column. The rest set by the builder methods: the width's profile, tube sides (0: a flat strip, both faces),
     * points (the simulated ones; their shares of the length), tint, solid (no cutout) or cutout, the v at its foot and tip,
     * caps on a tube's ends, how far the strip's u keeps off the column's edges, and whether it is the second card of a
     * pair (left out when lying: the pair would lie on itself). Built once by a layout and not changed after.
     */
    static final class Blade {
        final float x, z, bendYaw, widthYaw, len, width, phi0, phi1;
        final int column;
        float y, twist, vFoot = 1, vTip = 0, inset;
        int profile = POA, sides;
        float[] at = {0, 0.4F, 0.75F, 1};
        boolean tinted = true, solid = true, caps, secondary;

        Blade(float x, float z, float bendYaw, float widthYaw, float len, float width, float phi0, float phi1, int column) {
            this.x = x;
            this.z = z;
            this.bendYaw = bendYaw;
            this.widthYaw = widthYaw;
            this.len = len;
            this.width = width;
            this.phi0 = phi0;
            this.phi1 = phi1;
            this.column = column;
        }

        Blade profile(int p) { profile = p; return this; }
        Blade tube(int n) { sides = n; return this; }
        Blade at(float... f) { at = f; return this; }
        Blade untinted() { tinted = false; return this; }
        Blade cutout() { solid = false; return this; }
        Blade twist(float t) { twist = t; return this; }
        Blade v(float foot, float tip) { vFoot = foot; vTip = tip; return this; }
        Blade inset(float i) { inset = i; return this; }
        Blade capped() { caps = true; return this; }
        Blade secondary() { secondary = true; return this; }
        Blade y(float h) { y = h; return this; }

        /** The same blade with another curve (folded: an arc over to the ground). */
        Blade arc(float bend, float a0, float a1) {
            Blade b = new Blade(x, z, bend, 0, len, width, a0, a1, column);
            b.y = y; b.twist = 0; b.vFoot = vFoot; b.vTip = vTip; b.inset = inset; b.profile = profile; b.sides = sides;
            b.at = at; b.tinted = tinted; b.solid = solid; b.caps = caps;
            return b;
        }
    }

    /**
     * A rigid part carried by blade number {@code blade} of its clump at the share {@code at} of its length: quads in the
     * blade's grown pose. A flat part (a fern's blade held level) is not turned with the blade: it follows the point, keeps
     * its heading and lies down as the blade does ({@link #carried(Piece, Quad, float[], float[], float[], int)}).
     */
    record Piece(int blade, float at, List<Quad> quads, boolean flat) {
        Piece(int blade, float at, List<Quad> quads) {
            this(blade, at, quads, false);
        }
    }

    /** A clump: its blades, cards and rigid parts, its rank (0..1) for the blends; (x, z) its foot, where the slope is read. */
    static final class Clump {
        final float x, z, rank;
        final List<Blade> blades;
        final List<Card> cards;
        final List<Piece> pieces;
        /** Each blade as grown (the frame its pieces were made in). */
        final Pose[] grown;

        Clump(float x, float z, float rank, List<Blade> blades, List<Card> cards, List<Piece> pieces) {
            this.x = x;
            this.z = z;
            this.rank = rank;
            this.blades = List.copyOf(blades);
            this.cards = List.copyOf(cards);
            this.pieces = List.copyOf(pieces);
            this.grown = new Pose[blades.size()];
            for (int i = 0; i < grown.length; i++) grown[i] = grown(blades.get(i));
        }
    }

    /**
     * What presses a block: kind 0 nothing, 1 a footprint, 2 a wheel (the whole block), 3 rising again (after either);
     * (dx, dz) the way walked or driven; (fx, fz) the footprint's middle in the cell.
     */
    record Press(int kind, float dx, float dz, float fx, float fz) {
        static final Press NONE = new Press(0, 0, 1, 0.5F, 0.5F);
    }

    // ------------------------------------------------------------------------------------------------ quads

    /**
     * One quad: 4 corners (p xyz, uv in the sheet 0..1, n xyz for a pack, brightness for a pack and baked without one),
     * counter-clockwise from its front; a tone (r, g, b factors) and whether it goes in the solid layer.
     */
    static final class Quad {
        final float[] p = new float[12], uv = new float[8], n = new float[12], light = new float[4], lit = new float[4];
        final float[] tone = {1, 1, 1};
        boolean tinted, solid;
        /** STANDING or STILL. */
        int sprite;
        /** The clump's foot, where PlantModel reads the slope. */
        float footX, footZ;

        Quad copy() {
            Quad q = new Quad();
            System.arraycopy(p, 0, q.p, 0, 12);
            System.arraycopy(uv, 0, q.uv, 0, 8);
            System.arraycopy(n, 0, q.n, 0, 12);
            System.arraycopy(light, 0, q.light, 0, 4);
            System.arraycopy(lit, 0, q.lit, 0, 4);
            System.arraycopy(tone, 0, q.tone, 0, 3);
            q.tinted = tinted;
            q.solid = solid;
            q.sprite = sprite;
            q.footX = footX;
            q.footZ = footZ;
            return q;
        }
    }

    /**
     * A blade's rest shape under a press: its centre points (x y z in the cell), the share of its length at each, the
     * width's direction at each, the brightness along it, its tone and sheet (STANDING or STILL). The chunk mesh draws it as
     * it is, the near layer (VegetationDynamics) moves the points and draws the same quads ({@link Writer}).
     */
    static final class Pose {
        final Blade blade;
        final float[] pts, f, wx, wz, light, tone;
        final int sprite;

        Pose(Blade blade, float[] pts, float[] f, float[] wx, float[] wz, float[] light, int sprite) {
            this.blade = blade;
            this.pts = pts;
            this.f = f;
            this.wx = wx;
            this.wz = wz;
            this.light = light;
            this.sprite = sprite;
            this.tone = tone(blade);
        }

        int points() {
            return f.length;
        }

        boolean solid() {
            return blade.solid;
        }
    }

    /** Where a point is against a footprint: 2 under the sole, 1 at its edge (pushed aside), 0 clear. */
    static int under(float x, float z, Press p, float grow) {
        float rx = x - p.fx(), rz = z - p.fz();
        float along = rx * p.dx() + rz * p.dz(), across = rx * -p.dz() + rz * p.dx();
        float a = along / (SOLE_ALONG + grow), c = across / (SOLE_ACROSS + grow);
        if (a * a + c * c <= 1) return 2;
        a = along / (SOLE_ALONG + grow + EDGE);
        c = across / (SOLE_ACROSS + grow + EDGE);
        return a * a + c * c <= 1 ? 1 : 0;
    }

    /** A clump's quads under a press: its blades, their rigid parts carried along, its cards. */
    static void clump(Clump c, Press p, List<Quad> out) {
        Pose[] poses = poses(c, p);
        Writer w = new Writer();
        Sink sink = (pose, pp, uv, lit, light, n) -> out.add(quad(pose, pp, uv, lit, light, n, c.x, c.z));
        for (Pose pose : poses) if (pose != null) w.pose(pose, pose.pts, sink);
        float[] from = new float[6], to = new float[6], m = new float[9];
        for (Piece piece : c.pieces) {
            Pose now = poses[piece.blade()];
            if (now == null) continue;
            Pose grown = c.grown[piece.blade()];
            frame(grown.pts, grown.f, piece.at(), from);
            frame(now.pts, now.f, piece.at(), to);
            turn(from, to, m);
            for (Quad q : piece.quads()) out.add(carried(piece, q, from, to, m, now.sprite));
        }
        for (Card k : c.cards) {
            int kind = p.kind(), at = kind == 2 ? 2 : kind == 0 ? 0 : under(c.x, c.z, p, 0.12F);
            float ax = c.x - p.fx(), az = c.z - p.fz(), al = (float) Math.sqrt(ax * ax + az * az);
            if (al < 1e-3F) { ax = p.dx(); az = p.dz(); al = 1; }
            if (at == 2 && kind != 3) card(k, c.x, c.z, (float) Math.toRadians(84), 0.85F, p.dx(), p.dz(), STILL, out);
            else if (at == 2) card(k, c.x, c.z, (float) Math.toRadians(50), 0.55F, p.dx(), p.dz(), STILL, out);
            else if (at == 1) card(k, c.x, c.z, (float) Math.toRadians(kind == 3 ? 15 : 30), 0.3F, ax / al, az / al, STILL, out);
            else card(k, c.x, c.z, 0, 0, 0, 1, STANDING, out);
        }
    }

    /**
     * A quad of a piece carried from frame a to frame b (rotation m), on a sheet. A flat piece moves with the point instead,
     * its heights above the point squeezed by the cosine between the two tangents and kept off the ground: a trodden fern's
     * blade lies on the ground at its stipe's end rather than standing on edge.
     */
    static Quad carried(Piece piece, Quad q, float[] a, float[] b, float[] m, int sprite) {
        if (!piece.flat()) return carried(q, a, b, m, sprite);
        Quad c = q.copy();
        float squeeze = Math.max(0.05F, a[3] * b[3] + a[4] * b[4] + a[5] * b[5]);
        for (int i = 0; i < 4; i++) {
            int o = i * 3;
            c.p[o] = q.p[o] + b[0] - a[0];
            c.p[o + 1] = Math.max(0.006F, b[1] + (q.p[o + 1] - a[1]) * squeeze);
            c.p[o + 2] = q.p[o + 2] + b[2] - a[2];
        }
        c.sprite = sprite;
        return c;
    }

    /** A quad of a rigid part carried from frame a to frame b (rotation m), on a sheet. */
    static Quad carried(Quad q, float[] a, float[] b, float[] m, int sprite) {
        Quad c = q.copy();
        for (int i = 0; i < 4; i++) carry(q.p, i * 3, a, b, m, c.p, i * 3);
        c.sprite = sprite;
        return c;
    }

    private static Quad quad(Pose pose, float[] p, float[] uv, float[] lit, float[] light, float[] n, float footX, float footZ) {
        Quad q = new Quad();
        System.arraycopy(p, 0, q.p, 0, 12);
        System.arraycopy(uv, 0, q.uv, 0, 8);
        System.arraycopy(lit, 0, q.lit, 0, 4);
        System.arraycopy(light, 0, q.light, 0, 4);
        System.arraycopy(n, 0, q.n, 0, 12);
        System.arraycopy(pose.tone, 0, q.tone, 0, 3);
        q.tinted = pose.blade.tinted;
        q.solid = pose.blade.solid;
        q.sprite = pose.sprite;
        q.footX = footX;
        q.footZ = footZ;
        return q;
    }

    /** The quads of a pose as it is (a layout's rigid parts are made this way), with no foot (set by the caller). */
    static List<Quad> quads(Pose pose) {
        var out = new ArrayList<Quad>();
        new Writer().pose(pose, pose.pts, (ps, p, uv, lit, light, n) -> out.add(quad(ps, p, uv, lit, light, n, 0, 0)));
        return out;
    }

    /**
     * Every blade's rest shape under a press, by the blade's number (null: left out). A footprint: blades whose foot or
     * pressed part (a third along) is under the sole fold (a third only bent over); at its edge they lean away from it;
     * rising, they stand half. A wheel folds all.
     */
    static Pose[] poses(Clump c, Press p) {
        Pose[] out = new Pose[c.blades.size()];
        int kind = p.kind();
        float[] third = new float[6];
        for (int i = 0; i < out.length; i++) {
            Blade b = c.blades.get(i);
            if (kind == 0) {
                out[i] = c.grown[i];
                continue;
            }
            long seed = Float.floatToIntBits(b.x) * 31L + Float.floatToIntBits(b.z) * 0x9E3779B9L + i;
            int at;
            if (kind == 2) at = 2;
            else {
                // the foot, or the blade a third along (low enough for a sole to catch: below 0.4 m)
                frame(c.grown[i].pts, c.grown[i].f, 0.33F, third);
                at = under(b.x, b.z, p, 0);
                if (third[1] - b.y < 0.4F) at = Math.max(at, under(third[0], third[2], p, 0));
            }
            if (at == 2 && kind != 3) out[i] = folded(b, p.dx(), p.dz(), kind == 1, new Rng(seed));
            else if (at == 2) out[i] = standing(b, (float) Math.toRadians(50), 0.55F, p.dx(), p.dz(), STILL);
            else if (at == 1) {
                float ax = b.x - p.fx(), az = b.z - p.fz(), al = (float) Math.sqrt(ax * ax + az * az);
                if (al < 1e-3F) { ax = p.dx(); az = p.dz(); al = 1; }
                out[i] = standing(b, (float) Math.toRadians(kind == 3 ? 15 : 35), 0.3F, ax / al, az / al, STILL);
            } else out[i] = c.grown[i];
        }
        return out;
    }

    /** A blade as grown, upright and still. */
    static Pose grown(Blade b) {
        return standing(b, 0, 0, 0, 1, STANDING);
    }

    /**
     * A standing blade: its own curve (phi0 at the foot to phi1 at the tip, towards bendYaw), then bent over as a whole by a
     * pose (lean towards (dx, dz), the share {@code base} of it at the foot; 0: upright as grown).
     */
    private static Pose standing(Blade b, float lean, float base, float dx, float dz, int sprite) {
        float[] f = b.at.clone();
        float[] pts = curve(b, f);
        if (lean > 1e-3F) for (int k = 0; k < f.length; k++) {
            float[] bent = bend(pts[k * 3], pts[k * 3 + 1] - b.y, pts[k * 3 + 2], b.len, lean, base, dx, dz);
            pts[k * 3] = bent[0];
            pts[k * 3 + 1] = bent[1] + b.y;
            pts[k * 3 + 2] = bent[2];
        }
        float[] light = new float[f.length], wx = new float[f.length], wz = new float[f.length];
        for (int k = 0; k < f.length; k++) {
            light[k] = shade(f[k]);
            double yaw = b.widthYaw + b.twist * Math.pow(f[k], 1.5);
            wx[k] = (float) Math.cos(yaw);
            wz[k] = (float) Math.sin(yaw);
        }
        return new Pose(b, pts, f, wx, wz, light, sprite);
    }

    /**
     * A blade trodden or driven down along (dx, dz): folded a few centimetres above the foot, the rest lying along the way,
     * fanned out by up to 25 degrees, lifted 5-10 degrees and sinking to the ground; under a foot a third only bent over in
     * a low arc (a wheel folds them all). The second card of a pair is left out (null).
     */
    private static Pose folded(Blade b, float dx, float dz, boolean foot, Rng r) {
        if (b.secondary) return null;
        double spread = Math.toRadians((r.nextFloat() - 0.5F) * 50);
        float lx = (float) (dx * Math.cos(spread) - dz * Math.sin(spread)), lz = (float) (dx * Math.sin(spread) + dz * Math.cos(spread));
        int n = b.at.length;
        float[] wx = new float[n], wz = new float[n], light = new float[n];
        java.util.Arrays.fill(wx, -lz);   // the width lies flat, across the blade
        java.util.Arrays.fill(wz, lx);
        if (r.nextFloat() < 0.3F && foot) {
            // bent, not broken: a low arc from the foot over to the ground
            Blade arc = b.arc((float) Math.atan2(lz, lx), 1.1F, 1.52F);
            float[] f = b.at.clone();
            for (int k = 0; k < n; k++) light[k] = k == 0 ? 0.8F : k == 1 ? 0.86F : 0.9F + 0.02F * k / n;
            return new Pose(b, curve(arc, f), f, wx, wz, light, STILL);
        }
        float kink = Math.min(b.len * 0.4F, 0.02F + r.nextFloat() * 0.06F), rest = b.len - kink;
        double lift = Math.toRadians(5 + r.nextFloat() * 5);
        float x1 = b.x + dx * kink * 0.3F, z1 = b.z + dz * kink * 0.3F;
        float[] pts = new float[n * 3], f = new float[n];
        pts[0] = b.x; pts[1] = b.y; pts[2] = b.z;
        pts[3] = x1; pts[4] = b.y + kink; pts[5] = z1;
        f[1] = kink / b.len;
        float half = rest * 0.5F, top = kink + half * (float) Math.sin(lift);
        int m = n - 2;
        for (int j = 1; j <= m; j++) {
            float s = j / (float) m, run = rest * s;
            // rising a little to the middle, the outer half sinking back to the ground (a few millimetres above it)
            float y = s <= 0.5F ? kink + run * (float) Math.sin(lift) : top - (s - 0.5F) / 0.5F * rest * 0.075F;
            int o = (j + 1) * 3;
            pts[o] = x1 + lx * run * (float) Math.cos(lift);
            pts[o + 1] = b.y + Math.max(0.006F, y);
            pts[o + 2] = z1 + lz * run * (float) Math.cos(lift);
            f[j + 1] = (kink + run) / b.len;
        }
        float lit = 0.88F + r.nextFloat() * 0.1F;
        for (int k = 0; k < n; k++) light[k] = k == 0 ? 0.8F : k == 1 ? 0.86F : lit;
        return new Pose(b, pts, f, wx, wz, light, STILL);
    }

    /** Points along a blade's curve at the shares f of its length, integrated (24 steps a metre, at least 24): x y z per point. */
    private static float[] curve(Blade b, float[] f) {
        float[] pts = new float[f.length * 3];
        float cx = (float) Math.cos(b.bendYaw), cz = (float) Math.sin(b.bendYaw);
        int steps = Math.max(24, (int) (b.len * 24)), next = 0;
        double x = b.x, y = b.y, z = b.z, ds = b.len / steps;
        for (int s = 0; s <= steps; s++) {
            double at = s / (double) steps;
            while (next < f.length && f[next] <= at + 1e-6) { pts[next * 3] = (float) x; pts[next * 3 + 1] = (float) y; pts[next * 3 + 2] = (float) z; next++; }
            if (s == steps) break;
            double mid = (s + 0.5) / steps, phi = b.phi0 + (b.phi1 - b.phi0) * Math.pow(mid, 1.5);
            x += cx * Math.sin(phi) * ds;
            y += Math.cos(phi) * ds;
            z += cz * Math.sin(phi) * ds;
        }
        for (; next < f.length; next++) { pts[next * 3] = (float) x; pts[next * 3 + 1] = (float) y; pts[next * 3 + 2] = (float) z; }
        return pts;
    }

    // --------------------------------------------------------------------------------- rigid parts' frames

    /** The position (0..2) and unit tangent (3..5) at the share {@code at} along points pts (their shares f) into out. */
    static void frame(float[] pts, float[] f, float at, float[] out) {
        int n = f.length, i = 0;
        while (i < n - 2 && f[i + 1] < at) i++;
        float t = Math.max(0, Math.min(1, (at - f[i]) / Math.max(1e-6F, f[i + 1] - f[i])));
        int a = i * 3, b = a + 3;
        for (int c = 0; c < 3; c++) out[c] = pts[a + c] + (pts[b + c] - pts[a + c]) * t;
        float tx = pts[b] - pts[a], ty = pts[b + 1] - pts[a + 1], tz = pts[b + 2] - pts[a + 2];
        float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (l < 1e-6F) { out[3] = 0; out[4] = 1; out[5] = 0; return; }
        out[3] = tx / l; out[4] = ty / l; out[5] = tz / l;
    }

    /** The shortest turn of frame a's tangent onto frame b's (3 x 3, rows) into m. */
    static void turn(float[] a, float[] b, float[] m) {
        float ax = a[3], ay = a[4], az = a[5], bx = b[3], by = b[4], bz = b[5];
        float vx = ay * bz - az * by, vy = az * bx - ax * bz, vz = ax * by - ay * bx, c = ax * bx + ay * by + az * bz;
        if (c < -0.999F) {
            // a half turn about a horizontal axis across a
            float kx = az, kz = -ax, kl = (float) Math.sqrt(kx * kx + kz * kz);
            if (kl < 1e-4F) { kx = 1; kz = 0; kl = 1; }
            kx /= kl; kz /= kl;
            m[0] = 2 * kx * kx - 1; m[1] = 0; m[2] = 2 * kx * kz;
            m[3] = 0; m[4] = -1; m[5] = 0;
            m[6] = 2 * kx * kz; m[7] = 0; m[8] = 2 * kz * kz - 1;
            return;
        }
        float s = 1 / (1 + c), vv = vx * vx + vy * vy + vz * vz;
        m[0] = 1 + (vx * vx - vv) * s; m[1] = -vz + vx * vy * s; m[2] = vy + vx * vz * s;
        m[3] = vz + vx * vy * s; m[4] = 1 + (vy * vy - vv) * s; m[5] = -vx + vy * vz * s;
        m[6] = -vy + vx * vz * s; m[7] = vx + vy * vz * s; m[8] = 1 + (vz * vz - vv) * s;
    }

    /** The point at p[o..o+2] carried from frame a to frame b (rotation m) into out[d..d+2]. */
    static void carry(float[] p, int o, float[] a, float[] b, float[] m, float[] out, int d) {
        float x = p[o] - a[0], y = p[o + 1] - a[1], z = p[o + 2] - a[2];
        out[d] = b[0] + m[0] * x + m[1] * y + m[2] * z;
        out[d + 1] = b[1] + m[3] * x + m[4] * y + m[5] * z;
        out[d + 2] = b[2] + m[6] * x + m[7] * y + m[8] * z;
    }

    /**
     * A quad's vertex u kept inside the quad's own column (by its middle), {@link #U_EDGE} off both edges: a vertex on a
     * column's edge read back from the atlas would otherwise fall in the next column and take its wind (13.9; Aureveil's
     * finding 2026-10-24: the vertex stage only sees its own u). Used where the quads become vertices (PlantModel, the near layer).
     */
    static float inColumn(float u, float[] uv) {
        float mid = (uv[0] + uv[2] + uv[4] + uv[6]) / 4;
        int c = Math.min(COLUMNS - 1, Math.max(0, (int) (mid * COLUMNS)));
        return Math.max((c + U_EDGE) / COLUMNS, Math.min((c + 1 - U_EDGE) / COLUMNS, u));
    }

    // ------------------------------------------------------------------------------------- writing a blade

    /** Takes a blade's quads one by one (the arrays are the writer's scratch: copy what is kept). */
    interface Sink {
        void quad(Pose pose, float[] p, float[] uv, float[] lit, float[] light, float[] n);
    }

    /**
     * Writes a blade's quads from its pose with its points at pts (the rest pose's own, or moved by the near layer). A strip:
     * front and back of each segment, the normal turned out towards each edge (the leaf reads as folded along its midrib).
     * A tube: its sides around rings at the points, caps on the ends if asked. Without a pack the brightness by the normal
     * is baked (lit); a pack gets the normal leaning up and the brightness along the blade. Scratch arrays inside: one
     * writer per thread.
     */
    static final class Writer {
        private final float[] left = new float[MAX_POINTS * 3], right = new float[MAX_POINTS * 3], normal = new float[MAX_POINTS * 3];
        private final float[] ring = new float[MAX_POINTS * MAX_SIDES * 3], radial = new float[MAX_POINTS * MAX_SIDES * 3];
        private final float[] p = new float[12], uv = new float[8], lit = new float[4], light = new float[4], n = new float[12];
        private final float[] m = new float[3], up = new float[3], t = new float[3];

        void pose(Pose pose, float[] pts, Sink sink) {
            if (pose.blade.sides > 0) tube(pose, pts, sink);
            else strip(pose, pts, sink);
        }

        private void strip(Pose pose, float[] pts, Sink sink) {
            int count = pose.f.length;
            edges(pose, pts, left, right, normal);
            Blade b = pose.blade;
            float u0 = (b.column + b.inset) / COLUMNS, u1 = (b.column + 1 - b.inset) / COLUMNS;
            for (int k = 0; k + 1 < count; k++) {
                float v0 = v(b, pose.f[k]), v1 = v(b, pose.f[k + 1]);
                for (int face = 1; face >= -1; face -= 2) {
                    // front: left, right, right above, left above; back: right, left, left above, right above
                    for (int i = 0; i < 4; i++) {
                        boolean l = face == 1 ? i == 0 || i == 3 : i == 1 || i == 2;
                        int at = i < 2 ? k : k + 1;
                        System.arraycopy(l ? left : right, at * 3, p, i * 3, 3);
                        uv[i * 2] = l ? u0 : u1;
                        uv[i * 2 + 1] = at == k ? v0 : v1;
                        corner(normal, at, l ? -1 : 1, face, pose.wx[at], pose.wz[at], m);
                        lit[i] = lit(m, pose.light[at], pose.sprite);
                        light[i] = pose.light[at];
                        packNormal(m, up);
                        System.arraycopy(up, 0, n, i * 3, 3);
                    }
                    sink.quad(pose, p, uv, lit, light, n);
                }
            }
        }

        private void tube(Pose pose, float[] pts, Sink sink) {
            Blade b = pose.blade;
            int count = pose.f.length, sides = b.sides;
            for (int k = 0; k < count; k++) {
                tangent(pts, count, k, t);
                // across: the width's direction made square to the tangent
                float wx = pose.wx[k], wz = pose.wz[k], d = wx * t[0] + wz * t[2];
                float ax = wx - d * t[0], ay = -d * t[1], az = wz - d * t[2], al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                if (al < 1e-4F) { ax = 1 - t[0] * t[0]; ay = -t[0] * t[1]; az = -t[0] * t[2]; al = (float) Math.sqrt(ax * ax + ay * ay + az * az); }
                ax /= al; ay /= al; az /= al;
                float bx = t[1] * az - t[2] * ay, by = t[2] * ax - t[0] * az, bz = t[0] * ay - t[1] * ax;
                float r = b.width / 2 * profile(b.profile, pose.f[k]);
                for (int s = 0; s < sides; s++) {
                    double a = Math.PI * 2 * s / sides;
                    float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                    int o = (k * sides + s) * 3;
                    radial[o] = ax * c + bx * sn; radial[o + 1] = ay * c + by * sn; radial[o + 2] = az * c + bz * sn;
                    ring[o] = pts[k * 3] + radial[o] * r; ring[o + 1] = pts[k * 3 + 1] + radial[o + 1] * r; ring[o + 2] = pts[k * 3 + 2] + radial[o + 2] * r;
                }
            }
            float u0 = b.column / (float) COLUMNS, du = 1F / COLUMNS / sides;
            for (int k = 0; k + 1 < count; k++) {
                float v0 = v(b, pose.f[k]), v1 = v(b, pose.f[k + 1]);
                for (int s = 0; s < sides; s++) {
                    int s1 = (s + 1) % sides;
                    // outside: (k, s), (k, s + 1), (k + 1, s + 1), (k + 1, s)
                    ringCorner(0, k, s, u0 + du * s, v0, pose);
                    ringCorner(1, k, s1, u0 + du * (s + 1), v0, pose);
                    ringCorner(2, k + 1, s1, u0 + du * (s + 1), v1, pose);
                    ringCorner(3, k + 1, s, u0 + du * s, v1, pose);
                    sink.quad(pose, p, uv, lit, light, n);
                }
            }
            if (!b.caps) return;
            for (int end = 0; end < 2; end++) {
                int k = end == 0 ? 0 : count - 1;
                tangent(pts, count, k, t);
                float sign = end == 0 ? -1 : 1, vv = v(b, pose.f[k]), mid = u0 + du * sides / 2;
                for (int s = 0; s < sides; s++) {
                    int s1 = (s + 1) % sides;
                    int first = end == 0 ? s1 : s, second = end == 0 ? s : s1;
                    for (int i = 0; i < 4; i++) {
                        int o = i == 1 ? (k * sides + first) * 3 : i == 2 ? (k * sides + second) * 3 : -1;
                        if (o < 0) System.arraycopy(pts, k * 3, p, i * 3, 3);
                        else System.arraycopy(ring, o, p, i * 3, 3);
                        uv[i * 2] = o < 0 ? mid : u0 + du * (i == 1 ? first : second);
                        uv[i * 2 + 1] = vv;
                        m[0] = t[0] * sign; m[1] = t[1] * sign; m[2] = t[2] * sign;
                        lit[i] = lit(m, pose.light[k], pose.sprite);
                        light[i] = pose.light[k];
                        packNormal(m, up);
                        System.arraycopy(up, 0, n, i * 3, 3);
                    }
                    sink.quad(pose, p, uv, lit, light, n);
                }
            }
        }

        private void ringCorner(int i, int k, int s, float u, float v, Pose pose) {
            int o = (k * pose.blade.sides + s) * 3;
            System.arraycopy(ring, o, p, i * 3, 3);
            uv[i * 2] = u;
            uv[i * 2 + 1] = v;
            m[0] = radial[o]; m[1] = radial[o + 1]; m[2] = radial[o + 2];
            lit[i] = lit(m, pose.light[k], pose.sprite);
            light[i] = pose.light[k];
            packNormal(m, up);
            System.arraycopy(up, 0, n, i * 3, 3);
        }
    }

    /** v on the sheet at the share f of a blade's length. */
    private static float v(Blade b, float f) {
        return b.vFoot + (b.vTip - b.vFoot) * f;
    }

    /** The unit tangent at point k: the mean of the segments meeting there. */
    private static void tangent(float[] pts, int count, int k, float[] out) {
        int a = Math.max(0, k - 1) * 3, z = Math.min(count - 1, k + 1) * 3;
        float tx = pts[z] - pts[a], ty = pts[z + 1] - pts[a + 1], tz = pts[z + 2] - pts[a + 2];
        float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (l < 1e-6F) { out[0] = 0; out[1] = 1; out[2] = 0; return; }
        out[0] = tx / l; out[1] = ty / l; out[2] = tz / l;
    }

    /**
     * A strip's edges and normals at its points (pts: x y z per point): left and right edge points (width across the
     * pose's width direction there, by the profile) and the front normal (width x the curve's tangent there).
     */
    static void edges(Pose pose, float[] pts, float[] left, float[] right, float[] normal) {
        Blade b = pose.blade;
        int n = pose.f.length;
        for (int k = 0; k < n; k++) {
            float wx = pose.wx[k], wz = pose.wz[k];
            float half = b.width / 2 * profile(b.profile, pose.f[k]);
            int o = k * 3;
            left[o] = pts[o] - wx * half; left[o + 1] = pts[o + 1]; left[o + 2] = pts[o + 2] - wz * half;
            right[o] = pts[o] + wx * half; right[o + 1] = pts[o + 1]; right[o + 2] = pts[o + 2] + wz * half;
            int a = Math.max(0, k - 1) * 3, z = Math.min(n - 1, k + 1) * 3;
            float tx = pts[z] - pts[a], ty = pts[z + 1] - pts[a + 1], tz = pts[z + 2] - pts[a + 2];
            // front normal = width x tangent (the front's winding: left, right, right above, left above)
            float nx = -ty * wz, ny = tx * wz - tz * wx, nz = ty * wx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6F) { nx = -wz; ny = 0; nz = wx; l = 1; }
            normal[o] = nx / l; normal[o + 1] = ny / l; normal[o + 2] = nz / l;
        }
    }

    /** A strip corner's normal: the face's normal (front 1, back -1) at point k turned out towards its edge (-1 left, 1 right), into out. */
    static void corner(float[] normal, int k, float edge, int face, float wx, float wz, float[] out) {
        float x = normal[k * 3] * face + wx * edge * ROUND, y = normal[k * 3 + 1] * face, z = normal[k * 3 + 2] * face + wz * edge * ROUND;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        out[0] = x / l; out[1] = y / l; out[2] = z / l;
    }

    /**
     * Brightness without a pack: along the blade times the light by the normal (0.62 turned away .. 1 facing the sky's
     * light; what lies or leans is lit through from above whichever side shows, not below 0.82).
     */
    static float lit(float[] n, float light, int sprite) {
        float facing = n[0] * SUN[0] + n[1] * SUN[1] + n[2] * SUN[2];
        return light * Math.max(sprite == STILL ? 0.82F : 0.62F, Math.min(1, 0.9F + 0.3F * facing));
    }

    /** The normal handed to a pack: the given one leaning up (packs light foliage from above), into out. */
    static void packNormal(float[] n, float[] out) {
        float x = n[0], y = n[1] * 0.5F + 0.9F, z = n[2];
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        out[0] = x / l; out[1] = y / l; out[2] = z / l;
    }

    /** Width or radius along a blade (1 at the foot) by profile. */
    static float profile(int profile, float f) {
        return switch (profile) {
            // slowly narrower to 75 %, then closing to a narrow tip (Poa's boat-shaped tip)
            case POA -> f < 0.75F ? 1 - 0.3F * f : (1 - 0.3F * 0.75F) * (1 - (f - 0.75F) / 0.25F * 0.85F);
            // a long even blade closing to a point over its last fifth (Typha)
            case SWORD -> f < 0.8F ? 1 - 0.1F * f : 0.92F * (1 - (f - 0.8F) / 0.2F * 0.92F);
            // a cattail's stem: a little thinner up to the female spike, a third as thick inside the male one
            case CATTAIL_STEM -> f < 0.9F ? 1 - 0.2F * f : 0.35F;
            case TAPER -> 1 - 0.4F * f;
            // a female spike: rounded in at both ends
            case SPIKE -> f < 0.06F ? 0.55F + 0.45F * f / 0.06F : f > 0.94F ? 1 - 0.35F * (f - 0.94F) / 0.06F : 1;
            // a male spike: narrowing to its tip
            case MALE -> 1 - 0.55F * f;
            // a grass leaf drawn out to a long point: nearly even to 55 %, then narrowing to a fine tip (Andropogon, Schizachyrium)
            case ACUMINATE -> f < 0.55F ? 1 - 0.15F * f : (1 - 0.15F * 0.55F) * (1 - (f - 0.55F) / 0.45F * 0.94F);
            default -> 1;
        };
    }

    /** A blade's own tone: a little lighter or darker, a little yellower or bluer (fixed by its foot). */
    static float[] tone(Blade b) {
        Rng r = new Rng(Float.floatToIntBits(b.x) * 0x9E3779B97F4A7C15L ^ Float.floatToIntBits(b.z) * 0xC2B2AE3D27D4EB4FL ^ Float.floatToIntBits(b.len));
        float v = 0.9F + r.nextFloat() * 0.18F, warm = (r.nextFloat() - 0.4F) * 0.12F;
        return new float[]{v * (1 + warm), v, v * (1 - warm * 1.5F)};
    }

    // ------------------------------------------------------------------------------------- hand-made quads

    /**
     * A quad for a rigid part from 4 corners (x y z each, any winding: turned to face {@code facing}) and their uv; lit by
     * that face normal, the brightness along it {@code light}; double: also its back.
     */
    static void face(List<Quad> out, float[][] c, float[][] uv, float[] facing, float light, boolean tinted, boolean solid, boolean twoSided) {
        float ex = c[2][0] - c[0][0], ey = c[2][1] - c[0][1], ez = c[2][2] - c[0][2];
        float fx = c[3][0] - c[1][0], fy = c[3][1] - c[1][1], fz = c[3][2] - c[1][2];
        float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9F) { nx = facing[0]; ny = facing[1]; nz = facing[2]; l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz); }
        nx /= l; ny /= l; nz /= l;
        boolean flip = nx * facing[0] + ny * facing[1] + nz * facing[2] < 0;
        if (flip) { nx = -nx; ny = -ny; nz = -nz; }
        for (int side = 0; side < (twoSided ? 2 : 1); side++) {
            boolean reverse = flip ^ side == 1;
            float s = side == 0 ? 1 : -1;
            float[] nn = {nx * s, ny * s, nz * s}, up = new float[3];
            packNormal(nn, up);
            Quad q = new Quad();
            for (int i = 0; i < 4; i++) {
                int from = reverse ? 3 - i : i;
                System.arraycopy(c[from], 0, q.p, i * 3, 3);
                q.uv[i * 2] = uv[from][0];
                q.uv[i * 2 + 1] = uv[from][1];
                System.arraycopy(up, 0, q.n, i * 3, 3);
                q.light[i] = light;
                q.lit[i] = lit(nn, light, STANDING);
            }
            q.tinted = tinted;
            q.solid = solid;
            q.sprite = STANDING;
            out.add(q);
        }
    }

    /** Sets every quad's foot (where PlantModel reads the slope): a rigid part's clump. */
    static List<Quad> footed(List<Quad> quads, float x, float z) {
        for (Quad q : quads) { q.footX = x; q.footZ = z; }
        return quads;
    }

    // ------------------------------------------------------------------------------------------- cards

    /**
     * A card's quads (front and back of each segment) bent by a pose: lean angle at the tips (radians, 0 = upright) towards
     * (dx, dz), the share {@code base} of it already at the foot and the rest growing along the card (0.3: pushed aside;
     * 0.85: lying flat), so the foot stays and the tips go down and away. Brightness: darker at the foot.
     */
    static void card(Card c, float footX, float footZ, float lean, float base, float dx, float dz, int sprite, List<Quad> out) {
        float ax = (float) Math.cos(c.yaw()), az = (float) Math.sin(c.yaw());   // across the card
        float nx = -az, nz = ax;                                                // the card's normal (horizontal)
        int s = c.segments();
        float[][] left = new float[s + 1][], right = new float[s + 1][];
        for (int k = 0; k <= s; k++) {
            float f = k / (float) s, y = c.h() * f, bow = c.arch() * c.h() * f * f;
            float cx = c.x() + nx * bow, cz = c.z() + nz * bow;
            left[k] = bend(cx - ax * c.w() / 2, y, cz - az * c.w() / 2, c.h(), lean, base, dx, dz);
            right[k] = bend(cx + ax * c.w() / 2, y, cz + az * c.w() / 2, c.h(), lean, base, dx, dz);
        }
        float u0 = c.column() / (float) COLUMNS, u1 = (c.column() + 1) / (float) COLUMNS;
        // normals mostly up (plants are lit from above in every pack), tilted towards each face
        float[] front = norm(nx * 0.35F, 1, nz * 0.35F), back = norm(-nx * 0.35F, 1, -nz * 0.35F);
        for (int k = 0; k < s; k++) {
            float v0 = 1 - k / (float) s, v1 = 1 - (k + 1) / (float) s;
            float l0 = shade(k / (float) s), l1 = shade((k + 1) / (float) s);
            // front: left-bottom, right-bottom, right-top, left-top
            out.add(cardQuad(new float[][]{left[k], right[k], right[k + 1], left[k + 1]}, new float[]{u0, v0, u1, v0, u1, v1, u0, v1},
                    front, new float[]{l0, l0, l1, l1}, c.tinted(), sprite, footX, footZ));
            out.add(cardQuad(new float[][]{right[k], left[k], left[k + 1], right[k + 1]}, new float[]{u1, v0, u0, v0, u0, v1, u1, v1},
                    back, new float[]{l0, l0, l1, l1}, c.tinted(), sprite, footX, footZ));
        }
    }

    /**
     * A point (y above the foot, on something h tall) bent towards (dx, dz): the angle from upright grows from lean * base
     * at the foot to lean at the top (phi(s) = phi0 + k s), integrated exactly; the point's own sideways offset is kept.
     */
    private static float[] bend(float x, float y, float z, float h, float lean, float base, float dx, float dz) {
        if (lean <= 1e-3F || y <= 0) return new float[]{x, y, z};
        double phi0 = lean * base, k = lean * (1 - base) / h;
        double up, away;
        if (k < 1e-4) { up = y * Math.cos(phi0); away = y * Math.sin(phi0); }
        else { up = (Math.sin(phi0 + k * y) - Math.sin(phi0)) / k; away = (Math.cos(phi0) - Math.cos(phi0 + k * y)) / k; }
        return new float[]{x + dx * (float) away, (float) up, z + dz * (float) away};
    }

    /** Brightness along a blade or card: 0.55 at the foot, full from 70 % of the height. */
    static float shade(float f) {
        return 0.55F + 0.45F * Math.min(1, f / 0.7F);
    }

    static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    /** A card's quad: one normal, brightness along it; without a pack the plants' own shading (0.92) baked. */
    private static Quad cardQuad(float[][] p, float[] uv, float[] n, float[] light, boolean tinted, int sprite, float footX, float footZ) {
        Quad q = new Quad();
        for (int i = 0; i < 4; i++) {
            System.arraycopy(p[i], 0, q.p, i * 3, 3);
            System.arraycopy(n, 0, q.n, i * 3, 3);
            q.light[i] = light[i];
            q.lit[i] = light[i] * 0.92F;
        }
        System.arraycopy(uv, 0, q.uv, 0, 8);
        q.tinted = tinted;
        q.sprite = sprite;
        q.footX = footX;
        q.footZ = footZ;
        return q;
    }

    static float clamp(float f) {
        return Math.max(0.1F, Math.min(0.9F, f));
    }

    /** SplitMix64: the layouts are the same on every client and every run. */
    static final class Rng {
        private long s;

        Rng(long seed) { s = seed; }

        long next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        float nextFloat() { return (next() >>> 40) / (float) (1 << 24); }

        int nextInt(int n) { return (int) ((next() >>> 33) % n); }

        /** The 9 slots of a 3 x 3 grid in a random order. */
        int[] slots() {
            int[] s = {0, 1, 2, 3, 4, 5, 6, 7, 8};
            for (int i = 8; i > 0; i--) { int j = nextInt(i + 1), t = s[i]; s[i] = s[j]; s[j] = t; }
            return s;
        }
    }
}
