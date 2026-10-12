package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Branch;
import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Form;
import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Group;
import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Rng;
import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesh Trees (docs/rendering/mesh_trees_p0a.md, mesh_trees_p0b.md): a tree form ({@link TreeForms}) as quads, cells
 * and collision. Pure Java (no Minecraft): the server places the cells and answers collisions, the client draws them,
 * tools/mesh-trees checks them offline.
 * <p>
 * Frame: blocks, root-local. The root cell is (0,0,0); the ground is y = 0; the trunk axis starts at (0.5, 0, 0.5).
 * A tree is the root cell plus wood cells (trunk, branches, and every cell a thick branch passes) and foliage cells (the
 * crown volume). Every quad belongs to one cell and stays within {@link #REACH} of it: Embeddium frustum-tests a section
 * with a 1.125 block margin, and Oculus packs each vertex's offset from its block's centre into a byte (under 2 blocks).
 * Wood quads go to wood cells (static: the shader pack sees a log), leaf quads to foliage cells (it sees leaves, waves).
 * <p>
 * Collision: the trunk and the thick branches ({@link TreeForms#COLLIDE_BASE}) are voxelized at 1/{@value #SUB} block
 * into the wood cells they pass; a cell keeps its 8 x 8 x 8 bits ({@link Cell#solid}).
 * <p>
 * Levels (AFL's own view distance LOD; Voxy never reads them): LOD0 near, LOD1 middle, LOD2 far. The foliage cells also
 * fill the crown volume so that a voxel view (Voxy, a map) sees the crown's height, width and outline.
 */
public final class TreeShape {
    public static final int WOOD = 0, FOLIAGE = 1;
    /** Sprite slots per species: sprite = species ordinal * SLOTS + slot. */
    public static final int BARK = 0, LEAVES = 1, LEAVES_MID = 2, LEAVES_FAR = 3, SLOTS = 4;
    public static final int LODS = 3, VARIANTS = 16;
    /** How far a cell's quads may reach outside the cell (blocks). */
    public static final float REACH = 1.1F;
    /** Collision voxels per block edge. */
    public static final int SUB = 8;
    /** Leaf card sides (blocks): any orientation stays within REACH of the centroid's cell. */
    static final float CARD = 1.5F, CARD_LOW = 1.55F;
    /** Foliage volume: cells whose centre lies inside a foliage group sphere of this many radii, then closed. */
    static final float VOLUME = 1.05F;
    static final int CLOSE = 2;

    /** One quad: corner c has position p[3c..], uv[2c..] (0..1 of the sprite, v down), normal n[3c..], occlusion ao[c]. */
    public static final class Quad {
        public final float[] p = new float[12], uv = new float[8], n = new float[12], ao = new float[4];
        public int sprite, kind, tint = -1;
        public boolean twoSided;
        /** Where the quad belongs (build time only): a tube quad's axis midpoint, a card's centre. */
        float ax, ay, az;

        public float cx() { return (p[0] + p[3] + p[6] + p[9]) * 0.25F; }
        public float cy() { return (p[1] + p[4] + p[7] + p[10]) * 0.25F; }
        public float cz() { return (p[2] + p[5] + p[8] + p[11]) * 0.25F; }

        Quad rotated(int q) {
            var r = new Quad();
            r.sprite = sprite; r.kind = kind; r.tint = tint; r.twoSided = twoSided;
            System.arraycopy(uv, 0, r.uv, 0, 8);
            System.arraycopy(ao, 0, r.ao, 0, 4);
            for (int c = 0; c < 4; c++) {
                float x = p[c * 3], z = p[c * 3 + 2], nx = n[c * 3], nz = n[c * 3 + 2];
                for (int k = 0; k < q; k++) { float t = x; x = 1 - z; z = t; t = nx; nx = -nz; nz = t; }
                r.p[c * 3] = x; r.p[c * 3 + 1] = p[c * 3 + 1]; r.p[c * 3 + 2] = z;
                r.n[c * 3] = nx; r.n[c * 3 + 1] = n[c * 3 + 1]; r.n[c * 3 + 2] = nz;
            }
            return r;
        }
    }

    /** A cell of the layout: wood or foliage; a wood cell a thick branch passes has its collision voxels. */
    public static final class Cell {
        public final int x, y, z, kind;
        /** Collision voxels: SUB layers (y), each a 64-bit mask of z * SUB + x; null: no collision. */
        public long[] solid;
        @SuppressWarnings("unchecked")
        public final List<Quad>[] quads = new List[LODS];

        Cell(int x, int y, int z, int kind) {
            this.x = x; this.y = y; this.z = z; this.kind = kind;
            for (int l = 0; l < LODS; l++) quads[l] = new ArrayList<>();
        }

        public boolean carries() {
            for (var l : quads) if (!l.isEmpty()) return true;
            return false;
        }

        public boolean solid(int sx, int sy, int sz) {
            return solid != null && (solid[sy] >>> (sz * SUB + sx) & 1L) != 0;
        }
    }

    /** A foliage sphere (for the crown volume and the checks). */
    public record Cluster(float x, float y, float z, float r, boolean outer) {}

    /** The cells of one species variant at one rotation, and the numbers the checks and the status command report. */
    public static final class Layout {
        public final Species species;
        public final int variant, turns;
        public final Map<Integer, Cell> cells = new HashMap<>();
        public final List<Cluster> clusters;
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public final int[] triangles = new int[LODS], quadsPerLod = new int[LODS];
        public int lost, moved, solidCells;
        public float height, crownRadius;
        /**
         * Wood volume (m3) for the future felling yield (docs/dev/development_order_v1.md, AFL wood system): trunk,
         * thick branches (those that collide), thin branches. Logs come from the first two, sticks from the third.
         */
        public float trunkVolume, limbVolume, twigVolume;

        Layout(Species species, int variant, int turns, List<Cluster> clusters) {
            this.species = species; this.variant = variant; this.turns = turns; this.clusters = clusters;
        }

        public Cell cell(int x, int y, int z) { return cells.get(key(x, y, z)); }

        public int count(int kind) {
            int n = 0;
            for (var c : cells.values()) if (c.kind == kind) n++;
            return n;
        }

        public int carriers(int kind) {
            int n = 0;
            for (var c : cells.values()) if (c.kind == kind && c.carries()) n++;
            return n;
        }

        void bounds() {
            minX = minY = minZ = Integer.MAX_VALUE; maxX = maxY = maxZ = Integer.MIN_VALUE;
            solidCells = 0;
            for (var c : cells.values()) {
                minX = Math.min(minX, c.x); minY = Math.min(minY, c.y); minZ = Math.min(minZ, c.z);
                maxX = Math.max(maxX, c.x); maxY = Math.max(maxY, c.y); maxZ = Math.max(maxZ, c.z);
                if (c.solid != null) solidCells++;
            }
        }
    }

    public static int key(int x, int y, int z) {
        return (x + 64) << 16 | (y + 64) << 8 | (z + 64);
    }

    private static final Map<Integer, Layout> LAYOUTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Layouts built so far (status). */
    public static int cached() { return LAYOUTS.size(); }

    /** The layout of a species variant (0..15) turned by quarter turns (0..3); built once, then shared (read only). */
    public static Layout layout(Species species, int variant, int turns) {
        int key = (species.ordinal() * VARIANTS + variant) * 4 + turns;
        Layout done = LAYOUTS.get(key);
        if (done != null) return done;
        // the unturned base first, outside computeIfAbsent (a nested insert into the same map would throw)
        Layout base = turns == 0 ? null : layout(species, variant, 0);
        return LAYOUTS.computeIfAbsent(key, k -> turns == 0 ? build(species, variant) : rotate(base, turns));
    }

    // --------------------------------------------------------------------------------------------- geometry

    private static List<Quad>[] geometry(Form f, int variant) {
        @SuppressWarnings("unchecked")
        List<Quad>[] out = new List[LODS];
        for (int l = 0; l < LODS; l++) out[l] = new ArrayList<>();
        int bark = f.species.ordinal() * SLOTS + BARK;
        for (var b : f.branches) {
            for (int l = 0; l < LODS; l++) {
                int sides = b.sides()[l];
                if (sides == 0) continue;
                // LOD2: the trunk up to the crown (oak) or whole (pine), a branch's first segments; the bark tile is
                // 2 m there (stretched, nobody sees it), 1 m nearer
                boolean far = l == 2;
                float maxY = far && b.depth() == 0 && f.species == Species.WHITE_OAK ? f.trunkTop : Float.MAX_VALUE;
                int segments = far && b.depth() > 0 ? b.lod2Segments() : Integer.MAX_VALUE;
                // LOD1 and LOD2: a 2 m bark tile (fewer rings; the stretch is not visible from 64 m on); the thin pine
                // branches use it at LOD0 too
                float tile = l > 0 || b.depth() > 0 && f.species == Species.WHITE_PINE ? 2.0F : 1.0F;
                tube(f, b, sides, tile, maxY, segments, bark, out[l]);
            }
        }
        var rng = new Rng(0xC1A5_7E50L + variant * 131L + f.species.ordinal() * 7919L);
        int leaves = f.species.ordinal() * SLOTS;
        int index = 0;
        for (var g : f.groups) {
            boolean even = (index++ & 1) == 0;
            if (g.style() == TreeForms.BROADLEAF) {
                cards(f, g, 5, 0, CARD, leaves + LEAVES, rng, out[0]);
                cards(f, g, g.outer() ? 3 : 2, 0, CARD_LOW, leaves + LEAVES_MID, rng, out[1]);
                // LOD2: every group (the crown keeps LOD0's size and its lower half): outer a level and an outward
                // card, inner the outward card only
                if (g.outer()) cards(f, g, 2, 0, CARD_LOW, leaves + LEAVES_FAR, rng, out[2]);
                else cards(f, g, 1, 1, CARD_LOW, leaves + LEAVES_FAR, rng, out[2]);
            } else {
                // needle sprays: level plates along the branch, plus a card along the branch seen from the side
                // LOD0: a level plate (k 0) and the card along the branch (k 2): from the side, plates alone are edge-on
                sprays(f, g, 1, 0, CARD, leaves + LEAVES, rng, out[0]);
                sprays(f, g, 1, 2, CARD, leaves + LEAVES, rng, out[0]);
                sprays(f, g, 1, g.outer() ? 1 : 2, CARD_LOW, leaves + LEAVES_MID, rng, out[1]);
                // LOD2: every other group along the branches (inner ones too: outer ones alone leave a hollow crown)
                if (even) sprays(f, g, 1, 2, CARD_LOW, leaves + LEAVES_FAR, rng, out[2]);
            }
        }
        return out;
    }

    /**
     * A tube along the branch, sides around. The bark runs on along the branch: rings fall on every whole tile of length
     * (and at the polyline's corners), so v continues from ring to ring without restarting; around, the tile count
     * divides the sides. Only the first {@code segments} polyline segments, nothing above maxY; a free end tapers.
     */
    private static void tube(Form f, Branch b, int sides, float tile, float maxY, int segments, int sprite, List<Quad> out) {
        float[][] p = b.points();
        float[] r = b.radii();
        int last = Math.min(p.length - 1, segments);
        if (last < 1) return;
        // stations: every whole tile of length (the bark's v runs on across them) and the polyline's corners, except a
        // corner within 0.3 tile of a tile station (one ring there is enough; the tube cuts that corner slightly)
        float[] start = new float[last + 1];
        for (int i = 0; i < last; i++) start[i + 1] = start[i] + dist(p[i], p[i + 1]);
        float total = start[last];
        var s = new ArrayList<Float>();
        s.add(0F);
        for (float k = 1; k * tile < total - 1e-3F; k++) s.add(k * tile);
        // a branch's gentle bends may be cut by up to half a tile; the trunk keeps its corners unless within 0.3 tile
        float keepCorner = b.depth() > 0 ? 0.5F : 0.3F;
        for (int i = 1; i < last; i++) {
            float c = start[i], near = Math.abs(c - Math.round(c / tile) * tile);
            if (near > keepCorner * tile && c < total - 0.3F * tile) s.add(c);
        }
        s.add(total);
        s.sort(null);
        var seg = new ArrayList<Integer>();
        for (float v : s) {
            int i = 0;
            while (i < last - 1 && v > start[i + 1]) i++;
            seg.add(i);
        }
        int m = s.size();
        float[][] pts = new float[m][];
        float[] rad = new float[m];
        for (int j = 0; j < m; j++) {
            int i = seg.get(j);
            float len = Math.max(1e-5F, start[i + 1] - start[i]);
            float u = Math.max(0, Math.min(1, (s.get(j) - start[i]) / len));
            pts[j] = new float[]{p[i][0] + (p[i + 1][0] - p[i][0]) * u, p[i][1] + (p[i + 1][1] - p[i][1]) * u, p[i][2] + (p[i + 1][2] - p[i][2]) * u};
            rad[j] = r[i] + (r[i + 1] - r[i]) * u;
        }
        if (b.taper() && last == p.length - 1) rad[m - 1] = Math.max(0.008F, rad[m - 1] * 0.3F);
        // frames by parallel transport
        float[][] tan = new float[m][], nor = new float[m][], bin = new float[m][];
        for (int i = 0; i < m; i++) {
            float[] a = pts[Math.max(0, i - 1)], c = pts[Math.min(m - 1, i + 1)];
            tan[i] = norm(new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]});
        }
        float[] up = Math.abs(tan[0][1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        nor[0] = norm(cross(up, tan[0]));
        for (int i = 0; i < m; i++) {
            if (i > 0) {
                float[] n = nor[i - 1];
                float d = dot(n, tan[i]);
                nor[i] = norm(new float[]{n[0] - d * tan[i][0], n[1] - d * tan[i][1], n[2] - d * tan[i][2]});
            }
            bin[i] = cross(tan[i], nor[i]);
        }
        int tiles = Math.max(1, Math.round((float) (2 * Math.PI * rad[0])));
        while (sides % tiles != 0) tiles--;
        float du = tiles / (float) sides;
        for (int i = 0; i + 1 < m; i++) {
            float[] p0 = pts[i], p1 = pts[i + 1];
            if (Math.min(p0[1], p1[1]) >= maxY) break;
            // v: the tile this span lies in, its two ends' positions in it (the upper end nearer v 0)
            float k = (float) Math.floor(s.get(i) / tile + 1e-4F);
            float v0 = Math.min(1, Math.max(0, 1 - (s.get(i) / tile - k))), v1 = Math.min(1, Math.max(0, 1 - (s.get(i + 1) / tile - k)));
            for (int side = 0; side < sides; side++) {
                var q = new Quad();
                q.kind = WOOD; q.sprite = sprite;
                int[] rings = {i, i, i + 1, i + 1};
                int[] around = {side, side + 1, side + 1, side};
                for (int c = 0; c < 4; c++) {
                    int ri = rings[c];
                    double th = 2 * Math.PI * around[c] / sides;
                    float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
                    float[] dir = {nor[ri][0] * cs + bin[ri][0] * sn, nor[ri][1] * cs + bin[ri][1] * sn, nor[ri][2] * cs + bin[ri][2] * sn};
                    q.p[c * 3] = pts[ri][0] + dir[0] * rad[ri]; q.p[c * 3 + 1] = pts[ri][1] + dir[1] * rad[ri]; q.p[c * 3 + 2] = pts[ri][2] + dir[2] * rad[ri];
                    q.n[c * 3] = dir[0]; q.n[c * 3 + 1] = dir[1]; q.n[c * 3 + 2] = dir[2];
                    q.uv[c * 2] = (side % (sides / tiles)) * du + (around[c] == side ? 0 : du);
                    q.uv[c * 2 + 1] = c < 2 ? v0 : v1;
                    q.ao[c] = woodAo(f, q.p[c * 3], q.p[c * 3 + 1], q.p[c * 3 + 2]);
                }
                q.ax = (p0[0] + p1[0]) * 0.5F; q.ay = (p0[1] + p1[1]) * 0.5F; q.az = (p0[2] + p1[2]) * 0.5F;
                out.add(q);
            }
        }
    }

    /**
     * Broadleaf cards in a group: n squares of side s. LOD0 at random orientations but never within 15 degrees of
     * vertical (an upright card seen edge-on reads as a loose sheet against the sky), centres pulled a little toward the
     * crown's middle; the low levels a level card, an outward one and one across.
     */
    private static void cards(Form f, Group g, int n, int first, float s, int sprite, Rng rng, List<Quad> out) {
        for (int k = first; k < first + n; k++) {
            float spread = sprite % SLOTS == LEAVES ? 0.5F : sprite % SLOTS == LEAVES_MID ? 0.25F : 0.45F;
            float[] o = inSphere(rng);
            float cx = g.x() + o[0] * g.r() * spread, cy = g.y() + o[1] * g.r() * spread * 0.8F, cz = g.z() + o[2] * g.r() * spread;
            if (g.outer()) {   // pull outer cards in a little so their edges stay inside the crown's outline
                cx += (f.crownCx - cx) * 0.08F; cz += (f.crownCz - cz) * 0.08F;
            }
            float[] w;
            if (sprite % SLOTS != LEAVES) {
                float face = (float) Math.atan2(g.z() - f.crownCz, g.x() - f.crownCx) + rng.range(-0.3F, 0.3F);
                if (k == 2) face += (float) (Math.PI / 2);
                w = k == 0 ? norm(new float[]{rng.range(-0.3F, 0.3F), 1, rng.range(-0.3F, 0.3F)})
                        : norm(new float[]{(float) Math.cos(face), rng.range(-0.2F, 0.2F), (float) Math.sin(face)});
            } else {
                w = norm(inSphere(rng));
                if (Math.abs(w[1]) < 0.26F) w[1] = w[1] < 0 ? -0.26F : 0.26F;
                w = norm(w);
            }
            out.add(card(f, cx, cy, cz, w, s, sprite, f.species.tint, rng));
        }
    }

    /**
     * Needle sprays: kind 0 and 1 level plates (tilted 10 to 35 degrees) side by side along the branch, kind 2 a card
     * standing along the branch, leaning out, which is what a pine's crown shows from the side.
     */
    private static void sprays(Form f, Group g, int n, int first, float s, int sprite, Rng rng, List<Quad> out) {
        float dx = g.dirX(), dz = g.dirZ();
        float l = (float) Math.hypot(dx, dz);
        if (l < 1e-4F) { dx = 1; dz = 0; } else { dx /= l; dz /= l; }
        for (int k = first; k < first + n; k++) {
            float along = (k == 0 ? -0.3F : k == 1 ? 0.3F : 0) * g.r() + rng.range(-0.15F, 0.15F);
            float across = (k == 0 ? 0.25F : k == 1 ? -0.25F : 0) * g.r() + rng.range(-0.1F, 0.1F);
            float cx = g.x() + dx * along - dz * across, cz = g.z() + dz * along + dx * across;
            float cy = g.y() + rng.range(-0.08F, 0.12F);
            float[] w;
            if (k < 2) {
                float tilt = (float) Math.toRadians(rng.range(10, 35)), ta = rng.range(0, 6.2832F);
                w = norm(new float[]{(float) (Math.sin(tilt) * Math.cos(ta)), (float) Math.cos(tilt), (float) (Math.sin(tilt) * Math.sin(ta))});
            } else {
                // normal across the branch, leaning out and up
                w = norm(new float[]{-dz + dx * 0.25F, 0.35F, dx + dz * 0.25F});
            }
            out.add(card(f, cx, cy, cz, w, s, sprite, f.species.tint, rng));
        }
    }

    private static Quad card(Form f, float cx, float cy, float cz, float[] w, float s, int sprite, int tint, Rng rng) {
        float h = s * 0.5F;
        float[] ref = Math.abs(w[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] u = norm(cross(ref, w));
        float[] v = cross(w, u);
        double spin = rng.range(0, 6.283F);
        float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        float[] U = {u[0] * cs + v[0] * sn, u[1] * cs + v[1] * sn, u[2] * cs + v[2] * sn};
        float[] V = cross(w, U);
        var q = new Quad();
        q.kind = FOLIAGE; q.sprite = sprite; q.twoSided = true; q.tint = tint;
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        int rot = (int) (rng.next() * 4) & 3;
        boolean flip = rng.next() < 0.5F;
        for (int i = 0; i < 4; i++) {
            float a = corner[i][0] * h, b = corner[i][1] * h;
            q.p[i * 3] = cx + U[0] * a + V[0] * b; q.p[i * 3 + 1] = cy + U[1] * a + V[1] * b; q.p[i * 3 + 2] = cz + U[2] * a + V[2] * b;
            q.n[i * 3] = w[0]; q.n[i * 3 + 1] = w[1]; q.n[i * 3 + 2] = w[2];
            int j = (i + rot) & 3;
            float tu = corner[j][0] > 0 ? 1 : 0, tv = corner[j][1] > 0 ? 0 : 1;
            q.uv[i * 2] = flip ? 1 - tu : tu; q.uv[i * 2 + 1] = tv;
            q.ao[i] = leafAo(f, q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2]);
        }
        q.ax = cx; q.ay = cy; q.az = cz;
        return q;
    }

    /** Leaves inside the crown are darker (no light reaches there), the underside slightly darker. */
    private static float leafAo(Form f, float x, float y, float z) {
        float cy = (f.crownBottom + f.crownTop) * 0.5F, hy = Math.max(0.5F, (f.crownTop - f.crownBottom) * 0.5F);
        float dx = (x - f.crownCx) / f.crownRadius, dy = (y - cy) / hy, dz = (z - f.crownCz) / f.crownRadius;
        float d = Math.min(1, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        float under = 0.9F + 0.1F * Math.max(0, Math.min(1, (y - f.crownBottom) / (2 * hy)));
        return (0.72F + 0.28F * d * d) * under;
    }

    /** Wood: lighter below the crown, darker inside it, with no step (a smooth blend over 3 m at the crown's bottom). */
    private static float woodAo(Form f, float x, float y, float z) {
        float inside = smooth((y - (f.crownBottom - 1.5F)) / 3.0F);
        float d = Math.min(1, (float) Math.hypot(x - f.crownCx, z - f.crownCz) / f.crownRadius);
        return 0.95F + inside * ((0.72F + 0.22F * d) - 0.95F);
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    // -------------------------------------------------------------------------------------------- layout

    /** A new unturned layout, not cached (the offline check builds twice to prove it is deterministic). */
    static Layout build(Species species, int variant) {
        Form f = TreeForms.grow(species, variant);
        var geometry = geometry(f, variant);
        var clusters = new ArrayList<Cluster>();
        for (var g : f.groups) clusters.add(new Cluster(g.x(), g.y(), g.z(), g.r(), g.outer()));
        var layout = new Layout(species, variant, 0, List.copyOf(clusters));
        layout.height = f.crownTop;
        layout.crownRadius = f.crownRadius;
        for (var b : f.branches) {
            float v = volume(b);
            if (b.depth() == 0) layout.trunkVolume += v; else if (b.collide()) layout.limbVolume += v; else layout.twigVolume += v;
        }
        // 1. wood: every wood quad in the cell its branch axis passes (the quads are at most a branch radius from the
        //    axis, so they fit; the trunk flare stays in the trunk column)
        for (int l = 0; l < LODS; l++) for (var q : geometry[l]) {
            if (q.kind != WOOD) continue;
            int x = floor(q.ax), y = floor(q.ay), z = floor(q.az);
            var cell = layout.cells.computeIfAbsent(key(x, y, z), k -> new Cell(x, y, z, WOOD));
            if (!fits(q, x, y, z)) layout.lost++;
            else cell.quads[l].add(q);
        }
        // 2. collision: the trunk and the thick branches, voxelized; every cell they pass becomes wood
        for (var b : f.branches) if (b.collide()) voxelize(layout, b);
        layout.cells.computeIfAbsent(key(0, 0, 0), k -> new Cell(0, 0, 0, WOOD));
        // 3. leaves: the centroid's cell, or the nearest non-wood neighbour the quad still fits (or is moved into)
        for (int l = 0; l < LODS; l++) for (var q : geometry[l]) {
            if (q.kind != FOLIAGE) continue;
            Cell target = foliageCell(layout, q);
            if (target == null) layout.lost++;
            else target.quads[l].add(q);
        }
        // 4. the crown volume: foliage cells that carry nothing in AFL's view but give voxel views the crown.
        //    Group spheres, then a closing (dilate, erode by CLOSE cells) fills the gaps; the outline stays the groups'.
        volume(layout, f);
        count(layout);
        layout.bounds();
        return layout;
    }

    /** A branch's volume as stacked cone frustums. */
    private static float volume(Branch b) {
        float v = 0;
        for (int i = 0; i + 1 < b.points().length; i++) {
            float h = dist(b.points()[i], b.points()[i + 1]), r0 = b.radii()[i], r1 = b.radii()[i + 1];
            v += (float) (Math.PI / 3 * h * (r0 * r0 + r0 * r1 + r1 * r1));
        }
        return v;
    }

    /** The voxels of a tapered tube within its collision radius, into the wood cells they lie in. */
    private static void voxelize(Layout layout, Branch b) {
        float[][] p = b.points();
        float[] r = b.radii();
        float step = 1F / SUB;
        for (int i = 0; i + 1 < p.length; i++) {
            float r0 = r[i], r1 = r[i + 1];
            if (Math.max(r0, r1) < TreeForms.COLLIDE_MIN) continue;
            float[] a = p[i], c = p[i + 1];
            float[] d = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
            float dd = Math.max(1e-6F, dot(d, d));
            float rm = Math.max(0.09F, Math.max(r0, r1));
            int x0 = floor((Math.min(a[0], c[0]) - rm) * SUB), x1 = floor((Math.max(a[0], c[0]) + rm) * SUB);
            int y0 = floor((Math.min(a[1], c[1]) - rm) * SUB), y1 = floor((Math.max(a[1], c[1]) + rm) * SUB);
            int z0 = floor((Math.min(a[2], c[2]) - rm) * SUB), z1 = floor((Math.max(a[2], c[2]) + rm) * SUB);
            for (int vy = Math.max(0, y0); vy <= y1; vy++) for (int vz = z0; vz <= z1; vz++) for (int vx = x0; vx <= x1; vx++) {
                float px = (vx + 0.5F) * step, py = (vy + 0.5F) * step, pz = (vz + 0.5F) * step;
                float t = Math.max(0, Math.min(1, ((px - a[0]) * d[0] + (py - a[1]) * d[1] + (pz - a[2]) * d[2]) / dd));
                float rad = r0 + (r1 - r0) * t;
                if (rad < TreeForms.COLLIDE_MIN) continue;
                rad = Math.max(rad, 0.09F);   // at least about one and a half voxels wide: a diagonal stays connected
                float ex = px - (a[0] + d[0] * t), ey = py - (a[1] + d[1] * t), ez = pz - (a[2] + d[2] * t);
                if (ex * ex + ey * ey + ez * ez > rad * rad) continue;
                int cx = Math.floorDiv(vx, SUB), cy = Math.floorDiv(vy, SUB), cz = Math.floorDiv(vz, SUB);
                var cell = layout.cells.computeIfAbsent(key(cx, cy, cz), k -> new Cell(cx, cy, cz, WOOD));
                if (cell.kind != WOOD) continue;
                if (cell.solid == null) cell.solid = new long[SUB];
                cell.solid[vy - cy * SUB] |= 1L << ((vz - cz * SUB) * SUB + (vx - cx * SUB));
            }
        }
    }

    private static void volume(Layout layout, Form f) {
        int pad = CLOSE + 3;
        int gx0 = Integer.MAX_VALUE, gy0 = Integer.MAX_VALUE, gz0 = Integer.MAX_VALUE, gx1 = Integer.MIN_VALUE, gy1 = Integer.MIN_VALUE, gz1 = Integer.MIN_VALUE;
        for (var c : f.groups) {
            gx0 = Math.min(gx0, floor(c.x() - c.r())); gy0 = Math.min(gy0, floor(c.y() - c.r())); gz0 = Math.min(gz0, floor(c.z() - c.r()));
            gx1 = Math.max(gx1, floor(c.x() + c.r())); gy1 = Math.max(gy1, floor(c.y() + c.r())); gz1 = Math.max(gz1, floor(c.z() + c.r()));
        }
        gx0 -= pad; gy0 -= pad; gz0 -= pad; gx1 += pad; gy1 += pad; gz1 += pad;
        int nx = gx1 - gx0 + 1, ny = gy1 - gy0 + 1, nz = gz1 - gz0 + 1;
        boolean[] in = new boolean[nx * ny * nz];
        for (var c : f.groups) {
            float r = c.r() * VOLUME;
            for (int x = floor(c.x() - r); x <= floor(c.x() + r); x++)
                for (int y = floor(c.y() - r); y <= floor(c.y() + r); y++)
                    for (int z = floor(c.z() - r); z <= floor(c.z() + r); z++) {
                        float dx = x + 0.5F - c.x(), dy = y + 0.5F - c.y(), dz = z + 0.5F - c.z();
                        if (dx * dx + dy * dy + dz * dz <= r * r) in[((y - gy0) * nz + (z - gz0)) * nx + (x - gx0)] = true;
                    }
        }
        boolean[] closed = morph(morph(in, nx, ny, nz, CLOSE, true), nx, ny, nz, CLOSE, false);
        for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) {
            if (!closed[(y * nz + z) * nx + x] || y + gy0 < 1) continue;
            int xx = x + gx0, yy = y + gy0, zz = z + gz0;
            layout.cells.computeIfAbsent(key(xx, yy, zz), k -> new Cell(xx, yy, zz, FOLIAGE));
        }
    }

    private static Cell foliageCell(Layout layout, Quad q) {
        float cx = q.ax, cy = q.ay, cz = q.az;
        int x = floor(cx), y = floor(cy), z = floor(cz);
        Cell own = layout.cell(x, y, z);
        if ((own == null || own.kind == FOLIAGE) && y >= 1 && fits(q, x, y, z))
            return own != null ? own : put(layout, x, y, z);
        Cell best = null;
        float bestD = Float.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            int nx = x + dx, ny = y + dy, nz = z + dz;
            Cell n = layout.cell(nx, ny, nz);
            if (n != null && n.kind == WOOD || ny < 1 || !fits(q, nx, ny, nz)) continue;
            float ex = nx + 0.5F - cx, ey = ny + 0.5F - cy, ez = nz + 0.5F - cz, d = ex * ex + ey * ey + ez * ez;
            if (d < bestD) { bestD = d; best = n != null ? n : new Cell(nx, ny, nz, FOLIAGE); }
        }
        if (best == null) {
            // no neighbour takes the card where it is: move the card (at most about a metre) to the centre of the
            // nearest non-wood cell around, where any orientation fits
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                int nx = x + dx, ny = y + dy, nz = z + dz;
                Cell n = layout.cell(nx, ny, nz);
                if (n != null && n.kind == WOOD || ny < 1) continue;
                float ex = nx + 0.5F - cx, ey = ny + 0.5F - cy, ez = nz + 0.5F - cz, d = ex * ex + ey * ey + ez * ez;
                if (d < bestD) { bestD = d; best = n != null ? n : new Cell(nx, ny, nz, FOLIAGE); }
            }
            if (best == null) return null;
            float sx = best.x + 0.5F - cx, sy = best.y + 0.5F - cy, sz = best.z + 0.5F - cz;
            for (int c = 0; c < 4; c++) { q.p[c * 3] += sx; q.p[c * 3 + 1] += sy; q.p[c * 3 + 2] += sz; }
            q.ax += sx; q.ay += sy; q.az += sz;
            if (!fits(q, best.x, best.y, best.z)) return null;
        }
        layout.moved++;
        Cell b = best;
        return layout.cells.computeIfAbsent(key(b.x, b.y, b.z), k -> b);
    }

    /** Dilation (or erosion) by r cells, 26-neighbourhood, r times. */
    private static boolean[] morph(boolean[] in, int nx, int ny, int nz, int r, boolean dilate) {
        boolean[] cur = in;
        for (int k = 0; k < r; k++) {
            boolean[] next = new boolean[cur.length];
            for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) {
                boolean v = !dilate;
                for (int dy = -1; dy <= 1 && v != dilate; dy++) for (int dz = -1; dz <= 1 && v != dilate; dz++) for (int dx = -1; dx <= 1 && v != dilate; dx++) {
                    int X = x + dx, Y = y + dy, Z = z + dz;
                    boolean c = X >= 0 && Y >= 0 && Z >= 0 && X < nx && Y < ny && Z < nz && cur[(Y * nz + Z) * nx + X];
                    if (dilate ? c : !c) v = dilate;
                }
                next[(y * nz + z) * nx + x] = v;
            }
            cur = next;
        }
        return cur;
    }

    private static Cell put(Layout layout, int x, int y, int z) {
        return layout.cells.computeIfAbsent(key(x, y, z), k -> new Cell(x, y, z, FOLIAGE));
    }

    /** Every corner within REACH of the cell. */
    public static boolean fits(Quad q, int x, int y, int z) {
        for (int c = 0; c < 4; c++) {
            if (q.p[c * 3] < x - REACH || q.p[c * 3] > x + 1 + REACH) return false;
            if (q.p[c * 3 + 1] < y - REACH || q.p[c * 3 + 1] > y + 1 + REACH) return false;
            if (q.p[c * 3 + 2] < z - REACH || q.p[c * 3 + 2] > z + 1 + REACH) return false;
        }
        return true;
    }

    private static void count(Layout layout) {
        for (var cell : layout.cells.values()) for (int l = 0; l < LODS; l++) for (var q : cell.quads[l]) {
            int sides = q.twoSided ? 2 : 1;
            layout.quadsPerLod[l] += sides;
            layout.triangles[l] += 2 * sides;
        }
    }

    private static Layout rotate(Layout base, int turns) {
        var r = new Layout(base.species, base.variant, turns, base.clusters);
        var done = new java.util.IdentityHashMap<Quad, Quad>();
        for (var c : base.cells.values()) {
            int x = c.x, z = c.z;
            for (int k = 0; k < turns; k++) { int t = x; x = -z; z = t; }
            var cell = new Cell(x, c.y, z, c.kind);
            if (c.solid != null) {
                cell.solid = new long[SUB];
                for (int sy = 0; sy < SUB; sy++) for (int sz = 0; sz < SUB; sz++) for (int sx = 0; sx < SUB; sx++) {
                    if (!c.solid(sx, sy, sz)) continue;
                    int tx = sx, tz = sz;
                    for (int k = 0; k < turns; k++) { int t = tx; tx = SUB - 1 - tz; tz = t; }
                    cell.solid[sy] |= 1L << (tz * SUB + tx);
                }
            }
            for (int l = 0; l < LODS; l++) for (var q : c.quads[l]) cell.quads[l].add(done.computeIfAbsent(q, k -> k.rotated(turns)));
            r.cells.put(key(x, c.y, z), cell);
        }
        r.lost = base.lost; r.moved = base.moved; r.height = base.height; r.crownRadius = base.crownRadius;
        r.trunkVolume = base.trunkVolume; r.limbVolume = base.limbVolume; r.twigVolume = base.twigVolume;
        System.arraycopy(base.triangles, 0, r.triangles, 0, LODS);
        System.arraycopy(base.quadsPerLod, 0, r.quadsPerLod, 0, LODS);
        r.bounds();
        return r;
    }

    /** Sorted cells (deterministic order) for the server's placement journal. */
    public static List<Cell> ordered(Layout layout) {
        var list = new ArrayList<>(layout.cells.values());
        list.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.x, b.x));
        return Collections.unmodifiableList(list);
    }

    static int floor(float v) { return (int) Math.floor(v); }
    static float dist(float[] a, float[] b) { return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }
    static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static float[] norm(float[] v) { float l = (float) Math.sqrt(dot(v, v)); return l < 1e-9F ? new float[]{0, 1, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l}; }

    static float[] inSphere(Rng rng) {
        while (true) {
            float x = rng.range(-1, 1), y = rng.range(-1, 1), z = rng.range(-1, 1);
            if (x * x + y * y + z * z <= 1) return new float[]{x, y, z};
        }
    }

    private TreeShape() {}
}
