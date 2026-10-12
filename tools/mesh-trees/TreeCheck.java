package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Mesh Trees offline checks (docs/rendering/mesh_trees_p0a.md N, mesh_trees_p0b.md N). Run by tools/mesh-trees/check.mjs:
 * compiles TreeForms and TreeShape (pure Java) with this file and runs it. Arguments: textures folder, preview folder.
 * <ol>
 * <li>Every species, variant and rotation: no lost quad; every quad within REACH of its cell (Embeddium's 1.125 block
 * section margin) and under 1.95 blocks from the cell centre on every axis (Oculus' midBlock byte); UVs inside the
 * sprite; wood quads only in wood cells, leaf quads only in foliage cells; a turned layout is the base turned (cells,
 * kinds, collision voxels); two builds are identical; budgets per species and level; bounds.</li>
 * <li>Collision: only wood cells collide; the root and the trunk's lowest cells do; all collision voxels of a tree are one
 * connected piece that reaches the root (no floating bits).</li>
 * <li>Variety: the 16 variants of a species differ in height, crown width and structure.</li>
 * <li>Voxy: the cells are voxelized and mipped with Neo Voxy 1.20.1's rule (more than four solid children, four on the
 * bottom, or eight equal); the side silhouette kept at levels 1 and 2 must be at least a vanilla oak's.</li>
 * <li>Previews: per species LOD0/1/2 + volume proxy + collision voxels; 8 variants; the levels at 64/160/320 m.</li>
 * </ol>
 */
public final class TreeCheck {
    static int failures;

    static void require(boolean ok, String what) {
        if (!ok) { failures++; if (failures < 60) System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        File textures = new File(args[0]), out = new File(args[1]);
        out.mkdirs();
        for (Species sp : Species.values()) {
            float minH = Float.MAX_VALUE, maxH = 0, minW = Float.MAX_VALUE, maxW = 0;
            Set<String> shapes = new HashSet<>();
            int[] worst = new int[TreeShape.LODS];
            int cellsMin = Integer.MAX_VALUE, cellsMax = 0;
            for (int v = 0; v < TreeShape.VARIANTS; v++) {
                var base = TreeShape.layout(sp, v, 0);
                for (int t = 0; t < 4; t++) checkLayout(TreeShape.layout(sp, v, t), base);
                require(signature(TreeShape.build(sp, v)).equals(signature(base)), sp.id + " " + v + " builds the same twice");
                collision(base);
                for (int l = 0; l < TreeShape.LODS; l++) {
                    worst[l] = Math.max(worst[l], base.triangles[l]);
                    require(base.triangles[l] <= sp.budget[l], sp.id + " " + v + " LOD" + l + " " + base.triangles[l] + " <= " + sp.budget[l] + " triangles");
                }
                minH = Math.min(minH, base.height); maxH = Math.max(maxH, base.height);
                minW = Math.min(minW, 2 * base.crownRadius); maxW = Math.max(maxW, 2 * base.crownRadius);
                shapes.add(base.count(TreeShape.WOOD) + "/" + base.triangles[0]);
                cellsMin = Math.min(cellsMin, base.cells.size()); cellsMax = Math.max(cellsMax, base.cells.size());
            }
            var v0 = TreeShape.layout(sp, 0, 0);
            System.out.printf("%s: 16 variants, height %.1f-%.1f m, crown %.1f-%.1f m, cells %d-%d, worst triangles LOD0 %d LOD1 %d LOD2 %d (budget %d/%d/%d), %d distinct structures%n",
                    sp.id, minH, maxH, minW, maxW, cellsMin, cellsMax, worst[0], worst[1], worst[2], sp.budget[0], sp.budget[1], sp.budget[2], shapes.size());
            System.out.printf("  variant 0: cells %d (wood %d of which %d collide, foliage %d: %d carry quads), quads moved %d, wood volume trunk %.2f limbs %.2f twigs %.2f m3%n",
                    v0.cells.size(), v0.count(TreeShape.WOOD), v0.solidCells, v0.count(TreeShape.FOLIAGE), v0.carriers(TreeShape.FOLIAGE), v0.moved,
                    v0.trunkVolume, v0.limbVolume, v0.twigVolume);
            require(maxH - minH > 1.5F && maxW - minW > 1.5F && shapes.size() >= 8, sp.id + ": the variants differ (height, crown, structure)");
        }
        for (Species sp : Species.values()) voxy(TreeShape.layout(sp, 0, 0), sp.id);
        Preview.render(textures, out);
        System.out.println(failures == 0 ? "MESH TREE CHECK OK" : "MESH TREE CHECK: " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void checkLayout(TreeShape.Layout layout, TreeShape.Layout base) {
        String id = layout.species.id + " " + layout.variant + " turn " + layout.turns;
        require(layout.lost == 0, id + ": no lost quad (" + layout.lost + ")");
        require(layout.cells.size() == base.cells.size(), id + ": same cell count as unturned");
        var root = layout.cell(0, 0, 0);
        require(root != null && root.kind == TreeShape.WOOD && root.solid != null, id + ": the root cell is wood and collides");
        for (var c : layout.cells.values()) {
            int x = c.x, z = c.z;
            for (int k = 0; k < (4 - layout.turns) % 4; k++) { int tmp = x; x = -z; z = tmp; }
            var b = base.cell(x, c.y, z);
            require(b != null && b.kind == c.kind && (b.solid == null) == (c.solid == null), id + ": cell " + c.x + " " + c.y + " " + c.z + " is the turned base cell");
            if (b != null && c.solid != null && b.solid != null) {
                int bits = 0, bitsBase = 0;
                for (int sy = 0; sy < TreeShape.SUB; sy++) { bits += Long.bitCount(c.solid[sy]); bitsBase += Long.bitCount(b.solid[sy]); }
                require(bits == bitsBase, id + ": turned collision keeps its voxels");
            }
            require(c.y >= 0, id + ": no cell below the ground");
            require(c.solid == null || c.kind == TreeShape.WOOD, id + ": only wood collides");
            for (int l = 0; l < TreeShape.LODS; l++) for (var q : c.quads[l]) {
                require(TreeShape.fits(q, c.x, c.y, c.z), id + ": quad within REACH of cell " + c.x + " " + c.y + " " + c.z);
                require((q.kind == TreeShape.WOOD) == (c.kind == TreeShape.WOOD), id + ": quad kind matches cell kind");
                for (int i = 0; i < 4; i++) {
                    for (int a = 0; a < 3; a++) {
                        float off = q.p[i * 3 + a] - ((a == 0 ? c.x : a == 1 ? c.y : c.z) + 0.5F);
                        require(Float.isFinite(off) && Math.abs(off) < 1.95F, id + ": midBlock offset under 1.95");
                    }
                    require(q.uv[i * 2] >= -1e-4F && q.uv[i * 2] <= 1 + 1e-4F && q.uv[i * 2 + 1] >= -1e-4F && q.uv[i * 2 + 1] <= 1 + 1e-4F, id + ": uv inside the sprite");
                }
            }
        }
        for (int y = 0; y < 3; y++) {
            var t = layout.cell(0, y, 0);
            require(t != null && t.kind == TreeShape.WOOD && t.solid != null, id + ": trunk cell y " + y + " collides");
        }
        require(layout.maxX - layout.minX <= 24 && layout.maxZ - layout.minZ <= 24 && layout.maxY <= 30, id + ": bounds within 24 x 30 x 24 blocks");
    }

    /** All collision voxels one 26-connected piece reaching the root cell. */
    static void collision(TreeShape.Layout l) {
        Set<Long> voxels = new HashSet<>();
        for (var c : l.cells.values()) {
            if (c.solid == null) continue;
            for (int sy = 0; sy < TreeShape.SUB; sy++) for (int sz = 0; sz < TreeShape.SUB; sz++) for (int sx = 0; sx < TreeShape.SUB; sx++)
                if (c.solid(sx, sy, sz)) voxels.add(pack(c.x * 8 + sx, c.y * 8 + sy, c.z * 8 + sz));
        }
        long start = -1;
        for (long v : voxels) if (unpackY(v) < 8) { start = v; break; }
        require(start != -1, l.species.id + " " + l.variant + ": collision at the root");
        if (start == -1) return;
        Set<Long> seen = new HashSet<>();
        var queue = new ArrayDeque<Long>();
        queue.add(start); seen.add(start);
        while (!queue.isEmpty()) {
            long v = queue.poll();
            int x = unpackX(v), y = unpackY(v), z = unpackZ(v);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                long n = pack(x + dx, y + dy, z + dz);
                if (voxels.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        require(seen.size() == voxels.size(), l.species.id + " " + l.variant + ": collision is one piece (" + seen.size() + " of " + voxels.size() + " voxels reached)");
    }

    static long pack(int x, int y, int z) { return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096); }
    static int unpackX(long v) { return (int) (v >>> 40) - 4096; }
    static int unpackY(long v) { return (int) ((v >>> 20) & 0xFFFFF) - 4096; }
    static int unpackZ(long v) { return (int) (v & 0xFFFFF) - 4096; }

    static String signature(TreeShape.Layout l) {
        long h = 1125899906842597L;
        for (var c : TreeShape.ordered(l)) {
            h = h * 31 + TreeShape.key(c.x, c.y, c.z) * 7L + c.kind * 3L;
            if (c.solid != null) for (long b : c.solid) h = h * 31 + b;
            for (int k = 0; k < TreeShape.LODS; k++) for (var q : c.quads[k]) for (float f : q.p) h = h * 31 + Float.floatToIntBits(f);
        }
        return Long.toHexString(h) + "/" + l.cells.size();
    }

    // ------------------------------------------------------------------------------------------------- Voxy

    /** Neo Voxy 1.20.1 Mipper: 8 children -> 1 parent. ids: 0 air. */
    static int mip(int[] ch) {
        boolean uniform = true;
        for (int i = 1; i < 8; i++) if (ch[i] != ch[0]) uniform = false;
        if (uniform) return ch[0];
        int nonAir = 0;
        for (int c : ch) if (c != 0) nonAir++;
        if (nonAir > 4) return majority(ch);
        if (nonAir == 4 && ch[0] != 0 && ch[1] != 0 && ch[2] != 0 && ch[3] != 0) return majority(ch);
        return 0;
    }

    static int majority(int[] ch) {
        int best = 0, bestN = -1;
        for (int c : ch) {
            if (c == 0) continue;
            int n = 0;
            for (int d : ch) if (d == c) n++;
            if (n > bestN) { bestN = n; best = c; }
        }
        return best;
    }

    record Grid(int size, int[] v) {
        int get(int x, int y, int z) { return x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size ? 0 : v[(y * size + z) * size + x]; }
    }

    static Grid voxels(Set<long[]> cells, int ox, int oy, int oz) {
        int size = 64;
        int[] v = new int[size * size * size];
        for (long[] c : cells) {
            int x = (int) c[0] + 24 + ox, y = (int) c[1] + 4 + oy, z = (int) c[2] + 24 + oz;
            if (x >= 0 && y >= 0 && z >= 0 && x < size && y < size && z < size) v[(y * size + z) * size + x] = (int) c[3];
        }
        return new Grid(size, v);
    }

    static Grid down(Grid g) {
        int s = g.size / 2;
        int[] v = new int[s * s * s];
        int[] ch = new int[8];
        for (int y = 0; y < s; y++) for (int z = 0; z < s; z++) for (int x = 0; x < s; x++) {
            for (int i = 0; i < 8; i++) ch[i] = g.get(2 * x + (i & 1), 2 * y + (i >> 2 & 1), 2 * z + (i >> 1 & 1));
            v[(y * s + z) * s + x] = mip(ch);
        }
        return new Grid(s, v);
    }

    static double[] silhouette(Grid g, int scale) {
        int side = 0, top = 0;
        for (int y = 0; y < g.size; y++) for (int x = 0; x < g.size; x++) {
            boolean any = false;
            for (int z = 0; z < g.size && !any; z++) any = g.get(x, y, z) != 0;
            if (any) side++;
        }
        for (int z = 0; z < g.size; z++) for (int x = 0; x < g.size; x++) {
            boolean any = false;
            for (int y = 0; y < g.size && !any; y++) any = g.get(x, y, z) != 0;
            if (any) top++;
        }
        return new double[]{side * (double) scale * scale, top * (double) scale * scale};
    }

    static Set<long[]> cellsOf(TreeShape.Layout l) {
        Set<long[]> s = new HashSet<>();
        for (var c : l.cells.values()) s.add(new long[]{c.x, c.y, c.z, c.kind == TreeShape.WOOD ? 1 : 2});
        return s;
    }

    static Set<long[]> vanillaOak() {
        Set<long[]> s = new HashSet<>();
        int h = 5;
        for (int y = 0; y < h; y++) s.add(new long[]{0, y, 0, 1});
        for (int y = h - 3; y <= h; y++) {
            int r = y >= h - 1 ? 1 : 2;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (Math.abs(x) == r && Math.abs(z) == r && (y == h || (x + z + y) % 2 == 0)) continue;
                if (x == 0 && z == 0 && y < h) continue;
                s.add(new long[]{x, y, z, 2});
            }
        }
        return s;
    }

    /** Silhouette share kept: side L1, side L2, top L1, top L2 (32 grid alignments averaged). */
    static double[] kept(Set<long[]> set) {
        double[][] sum = new double[3][2];
        for (int ox = 0; ox < 4; ox++) for (int oy = 0; oy < 4; oy++) for (int oz = 0; oz < 4; oz += 3) {
            Grid g = voxels(set, ox, oy, oz);
            for (int level = 0; level < 3; level++) {
                double[] s = silhouette(g, 1 << level);
                sum[level][0] += s[0]; sum[level][1] += s[1];
                if (level < 2) g = down(g);
            }
        }
        return new double[]{sum[1][0] / sum[0][0], sum[2][0] / sum[0][0], sum[1][1] / sum[0][1], sum[2][1] / sum[0][1]};
    }

    static void voxy(TreeShape.Layout layout, String name) {
        double[] tree = kept(cellsOf(layout)), oak = kept(vanillaOak());
        System.out.printf("Voxy mip, %s crown volume: side kept L1 %.0f%% L2 %.0f%%, top L1 %.0f%% L2 %.0f%% (vanilla oak: side %.0f%% / %.0f%%)%n",
                name, 100 * tree[0], 100 * tree[1], 100 * tree[2], 100 * tree[3], 100 * oak[0], 100 * oak[1]);
        require(tree[0] >= oak[0] && tree[1] >= oak[1] && tree[0] > 0.7, name + ": the crown keeps at least a vanilla oak's silhouette at Voxy levels 1 and 2");
    }

    private TreeCheck() {}

    // ----------------------------------------------------------------------------------------------- previews

    static final class Preview {
        /** By tint index: the default foliage colour, white pine's, eastern hemlock's (TreeModels). */
        static final float[][] TINT = {{0x59 / 255F, 0xAE / 255F, 0x30 / 255F}, {0x5E / 255F, 0x94 / 255F, 0x72 / 255F},
                {0x4C / 255F, 0x7A / 255F, 0x50 / 255F}};
        static BufferedImage[] sprites, proxies;

        /** A texture, or a flat grey stand-in while it does not exist yet. */
        static BufferedImage tex(File dir, String name) throws Exception {
            File f = new File(dir, name + ".png");
            if (f.exists()) return ImageIO.read(f);
            var img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, (x + y) % 3 == 0 ? 0 : 0xFF909090);
            return img;
        }

        static void load(File dir) throws Exception {
            int n = Species.values().length;
            sprites = new BufferedImage[n * TreeShape.SLOTS];
            proxies = new BufferedImage[n];
            for (Species sp : Species.values()) {
                int i = sp.ordinal() * TreeShape.SLOTS;
                sprites[i + TreeShape.BARK] = tex(dir, sp.id + "_bark");
                sprites[i + TreeShape.LEAVES] = tex(dir, sp.leavesId);
                sprites[i + TreeShape.LEAVES_MID] = tex(dir, sp.leavesId + "_mid");
                sprites[i + TreeShape.LEAVES_FAR] = tex(dir, sp.leavesId + "_far");
                proxies[sp.ordinal()] = tex(dir, sp.leavesId + "_proxy");
            }
        }

        static void draw(Raster r, TreeShape.Layout l, int lod) {
            for (var c : l.cells.values()) for (var q : c.quads[lod]) r.quad(q, sprites[q.sprite], q.kind == TreeShape.FOLIAGE ? q.tint : -1);
        }

        static void volume(Raster r, TreeShape.Layout l, Species sp) {
            for (var c : l.cells.values()) r.cube(c.x, c.y, c.z, 1, c.kind == TreeShape.WOOD ? sprites[sp.ordinal() * TreeShape.SLOTS] : proxies[sp.ordinal()],
                    c.kind == TreeShape.FOLIAGE ? sp.tint : -1);
        }

        static void render(File dir, File out) throws Exception {
            load(dir);
            for (Species sp : Species.values()) {
                var l = TreeShape.layout(sp, 0, 0);
                // frame the tree: scale from its height, middle at half of it
                float scale = Math.min(30, 560 / (l.height + 3)), mid = l.height * 0.5F;
                int W = 560, H = 600;
                var sheet = new BufferedImage(W * 5, H * 2, BufferedImage.TYPE_INT_RGB);
                for (int view = 0; view < 2; view++) {
                    for (int lod = 0; lod < TreeShape.LODS; lod++) {
                        var r = new Raster(W, H, view, scale, mid);
                        draw(r, l, lod);
                        r.blit(sheet, lod * W, view * H);
                    }
                    var vol = new Raster(W, H, view, scale, mid);
                    volume(vol, l, sp);
                    vol.blit(sheet, 3 * W, view * H);
                    var col = new Raster(W, H, view, scale, mid);
                    for (var c : l.cells.values()) {
                        if (c.solid == null) continue;
                        for (int sy = 0; sy < 8; sy++) for (int sz = 0; sz < 8; sz++) for (int sx = 0; sx < 8; sx++)
                            if (c.solid(sx, sy, sz)) col.box(c.x + sx / 8F, c.y + sy / 8F, c.z + sz / 8F, 1 / 8F, 0xC08040);
                    }
                    col.blit(sheet, 4 * W, view * H);
                }
                ImageIO.write(sheet, "png", new File(out, sp.id + "_lods.png"));
                var vars = new BufferedImage(W * 4, H * 2, BufferedImage.TYPE_INT_RGB);
                for (int v = 0; v < 8; v++) {
                    var r = new Raster(W, H, 1, scale * 0.85F, mid);
                    draw(r, TreeShape.layout(sp, v * 2, 0), 0);
                    r.blit(vars, (v % 4) * W, (v / 4) * H);
                }
                ImageIO.write(vars, "png", new File(out, sp.id + "_variants.png"));
                distance(l, sp, new File(out, sp.id + "_distance.png"), mid);
            }
            System.out.println("previews in " + out);
        }

        /** The levels at 64 / 160 / 320 m on a 2000 x 1125 window, 70 degrees vertical: about 803/d px per metre. */
        static void distance(TreeShape.Layout l, Species sp, File file, float mid) throws Exception {
            int[] dist = {64, 160, 320};
            int cell = 240, big = 840;
            float scale = 30;
            var sheet = new BufferedImage(cell * 4, cell * dist.length, BufferedImage.TYPE_INT_RGB);
            for (int col = 0; col < 4; col++) {
                var r = new Raster(big, big, 1, scale, mid);
                if (col < 3) draw(r, l, col); else volume(r, l, sp);
                for (int row = 0; row < dist.length; row++) {
                    double pxPerM = 803.0 / dist[row];
                    int f = (int) Math.max(1, Math.round(scale / pxPerM));
                    int sw = big / f;
                    int[] small = new int[sw * sw];
                    for (int y = 0; y < sw; y++) for (int x = 0; x < sw; x++) {
                        long rr = 0, gg = 0, bb = 0;
                        for (int dy = 0; dy < f; dy++) for (int dx = 0; dx < f; dx++) {
                            int v = r.rgb[(y * f + dy) * big + x * f + dx];
                            rr += v >> 16 & 255; gg += v >> 8 & 255; bb += v & 255;
                        }
                        int n = f * f;
                        small[y * sw + x] = (int) (rr / n) << 16 | (int) (gg / n) << 8 | (int) (bb / n);
                    }
                    int zoom = Math.max(1, (int) Math.floor(cell / (double) sw));
                    for (int y = 0; y < cell; y++) for (int x = 0; x < cell; x++) {
                        int sx = sw / 2 + (x - cell / 2) / zoom, sy = sw / 2 + (y - cell / 2) / zoom;
                        int v = sx >= 0 && sy >= 0 && sx < sw && sy < sw ? small[sy * sw + sx] : 0xB8C8D8;
                        sheet.setRGB(col * cell + x, row * cell + y, v);
                    }
                }
            }
            ImageIO.write(sheet, "png", file);
        }
    }

    /** A tiny z-buffered rasterizer: view 0 = 3/4 from above, view 1 = side (looking north). */
    static final class Raster {
        final int w, h;
        final int[] rgb;
        final float[] depth;
        final float yaw, pitch, scale, mid;
        static final float[] SUN = norm(new float[]{-0.5F, 0.8F, 0.4F});

        Raster(int w, int h, int view, float scale, float mid) {
            this.w = w; this.h = h; this.scale = scale; this.mid = mid;
            rgb = new int[w * h];
            depth = new float[w * h];
            java.util.Arrays.fill(depth, Float.MAX_VALUE);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float t = y / (float) h;
                rgb[y * w + x] = rgbOf(0.62F + 0.2F * t, 0.72F + 0.12F * t, 0.86F);
            }
            yaw = view == 0 ? (float) Math.toRadians(35) : 0;
            pitch = view == 0 ? (float) Math.toRadians(22) : 0;
        }

        float[] project(float x, float y, float z) {
            float cx = x - 0.5F, cz = z - 0.5F, cy = y - mid;
            float rx = cx * (float) Math.cos(yaw) - cz * (float) Math.sin(yaw);
            float rz = cx * (float) Math.sin(yaw) + cz * (float) Math.cos(yaw);
            float ry = cy * (float) Math.cos(pitch) + rz * (float) Math.sin(pitch);
            float d = -cy * (float) Math.sin(pitch) + rz * (float) Math.cos(pitch);
            return new float[]{w / 2F - rx * scale, h / 2F - ry * scale, d};
        }

        void quad(TreeShape.Quad q, BufferedImage tex, int tint) {
            float[] n = {q.n[0], q.n[1], q.n[2]};
            boolean leaf = tint >= 0;
            float light = leaf ? 0.55F + 0.45F * Math.abs(dot(n, SUN)) : 0.45F + 0.55F * Math.max(0, dot(n, SUN));
            float[] view = {(float) (Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (Math.cos(yaw) * Math.cos(pitch))};
            if (!q.twoSided && dot(n, view) > 0) return;
            tri(q, 0, 1, 2, tex, tint, light);
            tri(q, 0, 2, 3, tex, tint, light);
        }

        void tri(TreeShape.Quad q, int a, int b, int c, BufferedImage tex, int tint, float light) {
            float[] A = project(q.p[a * 3], q.p[a * 3 + 1], q.p[a * 3 + 2]), B = project(q.p[b * 3], q.p[b * 3 + 1], q.p[b * 3 + 2]),
                    C = project(q.p[c * 3], q.p[c * 3 + 1], q.p[c * 3 + 2]);
            float area = (B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0]);
            if (Math.abs(area) < 1e-6F) return;
            int x0 = Math.max(0, (int) Math.floor(Math.min(A[0], Math.min(B[0], C[0])))), x1 = Math.min(w - 1, (int) Math.ceil(Math.max(A[0], Math.max(B[0], C[0]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(A[1], Math.min(B[1], C[1])))), y1 = Math.min(h - 1, (int) Math.ceil(Math.max(A[1], Math.max(B[1], C[1]))));
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                float px = x + 0.5F, py = y + 0.5F;
                float wa = ((B[0] - px) * (C[1] - py) - (B[1] - py) * (C[0] - px)) / area;
                float wb = ((C[0] - px) * (A[1] - py) - (C[1] - py) * (A[0] - px)) / area;
                float wc = 1 - wa - wb;
                if (wa < 0 || wb < 0 || wc < 0) continue;
                float d = wa * A[2] + wb * B[2] + wc * C[2];
                int i = y * w + x;
                if (d >= depth[i]) continue;
                float u = wa * q.uv[a * 2] + wb * q.uv[b * 2] + wc * q.uv[c * 2];
                float v = wa * q.uv[a * 2 + 1] + wb * q.uv[b * 2 + 1] + wc * q.uv[c * 2 + 1];
                int tx = Math.min(tex.getWidth() - 1, Math.max(0, (int) (u * tex.getWidth()))), ty = Math.min(tex.getHeight() - 1, Math.max(0, (int) (v * tex.getHeight())));
                int argb = tex.getRGB(tx, ty);
                if ((argb >>> 24) < 128) continue;
                float ao = wa * q.ao[a] + wb * q.ao[b] + wc * q.ao[c];
                float k = ao * light;
                float r = ((argb >> 16) & 255) / 255F, g = ((argb >> 8) & 255) / 255F, bl = (argb & 255) / 255F;
                if (tint >= 0) { r *= Preview.TINT[tint][0]; g *= Preview.TINT[tint][1]; bl *= Preview.TINT[tint][2]; }
                depth[i] = d;
                rgb[i] = rgbOf(r * k, g * k, bl * k);
            }
        }

        static final float[][][] FACES = {
                {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
                {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
                {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 0, -1}},
                {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 0}},
                {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {-1, 0, 0}},
        };

        void cube(float x, float y, float z, float s, BufferedImage tex, int tint) {
            for (var f : FACES) {
                var q = new TreeShape.Quad();
                for (int c = 0; c < 4; c++) {
                    q.p[c * 3] = x + f[c][0] * s; q.p[c * 3 + 1] = y + f[c][1] * s; q.p[c * 3 + 2] = z + f[c][2] * s;
                    q.n[c * 3] = f[4][0]; q.n[c * 3 + 1] = f[4][1]; q.n[c * 3 + 2] = f[4][2];
                    q.uv[c * 2] = c == 1 || c == 2 ? 1 : 0; q.uv[c * 2 + 1] = c < 2 ? 1 : 0;
                    q.ao[c] = 1;
                }
                quad(q, tex, tint);
            }
        }

        static final BufferedImage WOOD = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        static { WOOD.setRGB(0, 0, 0xFFC08040); }

        void box(float x, float y, float z, float s, int rgb) {
            cube(x, y, z, s, WOOD, -1);
        }

        void blit(BufferedImage sheet, int ox, int oy) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) sheet.setRGB(ox + x, oy + y, rgb[y * w + x]);
        }

        static int rgbOf(float r, float g, float b) {
            return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }

        static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v * 255))); }
        static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
        static float[] norm(float[] v) { float l = (float) Math.sqrt(dot(v, v)); return new float[]{v[0] / l, v[1] / l, v[2] / l}; }
    }
}
