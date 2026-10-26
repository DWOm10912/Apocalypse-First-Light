package com.antaurora.apofirstlight.terrainsmooth;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Seam audit of the smoothed ground (docs/worldgen/terrain_smooth_v1.md X): along every vertical boundary between two
 * horizontally adjacent ground columns, is the step between their drawn tops closed by a face somebody draws? A step
 * nobody draws is a see-through hole (the C3.3a earthworks seams: graded_turf beside smoothed natural ground).
 * <p>
 * Uses the real SmoothRules plus mirrors of the client's face logic: SmoothSurfaceModel#build (a band reaching above
 * the block's cell is unculled, the rest of a band and the part under it are culled by a full neighbour),
 * TerrainSmoothClient#underSides (the part under a band beside a neighbour that hides nothing, or beside a graded_turf
 * column one level down), GradedTurfModel (sides culled unless a corner is above the cell) and vanilla face culling
 * (smoothed natural blocks keep the full-cube occlusion shape). Keep these mirrors in step with the client classes.
 * <p>
 * Two modes:
 * <ul>
 *   <li>no arguments: synthetic earthworks scenes (random natural ground, a road with turf slopes quantised by
 *   RoadWorks#fit's rules, planned ground off the actual one by up to 1.2 blocks, slopes up to 1.6 blocks per block);
 *   every scene must give 0 holes (exit 1 otherwise). Run by check.mjs.</li>
 *   <li>{@code <area.bin> <label> <outDir>}: an area exported from a save by export-states.mjs; writes
 *   {@code <label>_summary.txt} and {@code <label>_holes.tsv} (each hole with its mechanism and the columns around it).</li>
 * </ul>
 */
public final class SeamAudit {
    // ---------------------------------------------------------------- area
    int minX, minZ, w, d, minY, h;
    String[] pal;
    char[] blocks;
    // per palette entry
    int[] cellOf;
    boolean[] ground, fullOcc, turf, invisibleRoad, roadStruct;
    float[][] turfCorners;
    String[] base;

    static final Set<String> GROUND = Set.of("grass_block", "dirt", "coarse_dirt", "sand", "gravel", "stone", "podzol", "mud", "clay",
            "snow_block", "red_sand", "terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta", "brown_terracotta",
            "red_terracotta", "light_gray_terracotta");
    static final Set<String> COVER = Set.of("grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy", "blue_orchid", "allium",
            "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
            "torchflower", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "sweet_berry_bush", "brown_mushroom", "red_mushroom",
            "lily_pad", "snow", "wheat", "carrots", "potatoes", "beetroots", "pink_petals", "azalea", "flowering_azalea", "small_dripleaf",
            "mangrove_propagule", "seagrass", "tall_seagrass");
    static final Set<String> WET = Set.of("water", "bubble_column", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "lava");
    // full opaque cubes that cull a neighbour's face (canOcclude + full occlusion face)
    static final Set<String> FULL = new HashSet<>(Arrays.asList("andesite", "diorite", "granite", "deepslate", "tuff", "calcite", "smooth_basalt",
            "amethyst_block", "budding_amethyst", "cobblestone", "moss_block", "rooted_dirt", "magma_block", "raw_copper_block", "mycelium",
            "bedrock", "sandstone", "red_sandstone", "packed_ice", "obsidian", "spruce_log", "oak_log", "birch_log", "dark_oak_log", "mossy_cobblestone"));

    void load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 20))) {
            minX = in.readInt(); minZ = in.readInt(); w = in.readInt(); d = in.readInt(); minY = in.readInt(); h = in.readInt();
            int n = in.readInt();
            pal = new String[n];
            for (int i = 0; i < n; i++) { byte[] b = new byte[in.readUnsignedShort()]; in.readFully(b); pal[i] = new String(b, "UTF-8"); }
            blocks = new char[w * d * h];
            for (int i = 0; i < blocks.length; i++) blocks[i] = in.readChar();
        }
        classifyAll();
    }

    void classifyAll() {
        int n = pal.length;
        cellOf = new int[n]; ground = new boolean[n]; fullOcc = new boolean[n]; turf = new boolean[n]; invisibleRoad = new boolean[n];
        roadStruct = new boolean[n]; turfCorners = new float[n][]; base = new String[n];
        for (int i = 0; i < n; i++) classify(i);
    }

    static String prop(String s, String key) {
        int b = s.indexOf('[');
        if (b < 0) return null;
        for (String kv : s.substring(b + 1, s.length() - 1).split(",")) {
            int e = kv.indexOf('=');
            if (kv.substring(0, e).equals(key)) return kv.substring(e + 1);
        }
        return null;
    }

    /** TerrainSmooth#cell by name (the export has no block classes). */
    void classify(int i) {
        String full = pal[i];
        int b = full.indexOf('[');
        String ns = b < 0 ? full : full.substring(0, b);
        String mod = ns.substring(0, ns.indexOf(':')), name = ns.substring(ns.indexOf(':') + 1);
        base[i] = mod.equals("minecraft") ? name : mod.substring(0, 3) + ":" + name;
        int cell;
        boolean waterlogged = "true".equals(prop(full, "waterlogged"));
        if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) cell = SmoothRules.AIR;
        else if (WET.contains(name) || waterlogged) cell = SmoothRules.FLUID;
        else if (mod.equals("minecraft") && (COVER.contains(name) || name.endsWith("_sapling"))) cell = SmoothRules.COVER;
        else if (mod.equals("apocalypse_firstlight") && name.equals("road_structure")) cell = SmoothRules.COVER;
        else if (mod.equals("minecraft") && GROUND.contains(name)) cell = SmoothRules.NATURAL;
        else if (name.endsWith("_leaves") || name.endsWith("_canopy") || name.endsWith("_foliage")) cell = SmoothRules.LEAF;
        else if (name.endsWith("_log") || name.equals("tree_wood") || name.equals("tree_root") || name.equals("tree_stump") || name.equals("vine")
                || name.endsWith("_mushroom_block") || name.equals("mushroom_stem")) cell = SmoothRules.TREE;
        else cell = SmoothRules.OTHER;
        cellOf[i] = cell;
        ground[i] = cell == SmoothRules.NATURAL;
        turf[i] = name.equals("graded_turf");
        invisibleRoad[i] = name.equals("road_surface") || name.equals("road_structure");
        roadStruct[i] = name.equals("road_structure");
        if (turf[i]) {
            float base0 = "true".equals(prop(full, "shift")) ? 0.5F : 0;
            turfCorners[i] = new float[]{base0 + Integer.parseInt(prop(full, "nw")) / 8F, base0 + Integer.parseInt(prop(full, "ne")) / 8F,
                    base0 + Integer.parseInt(prop(full, "sw")) / 8F, base0 + Integer.parseInt(prop(full, "se")) / 8F};
        }
        boolean vanillaCube = mod.equals("minecraft") && (GROUND.contains(name) || FULL.contains(name) || name.endsWith("_ore"));
        fullOcc[i] = vanillaCube || (mod.equals("apocalypse_firstlight") && name.equals("asphalt"));
    }

    boolean in(int x, int y, int z) { return x >= minX && x < minX + w && z >= minZ && z < minZ + d && y >= minY && y < minY + h; }

    int id(int x, int y, int z) {
        if (!in(x, y, z)) return -1;
        char c = blocks[((z - minZ) * w + (x - minX)) * h + (y - minY)];
        return c == 0xFFFF ? -1 : c;
    }

    // ---------------------------------------------------------------- cells
    int[] surfaceCache;

    int surface(int x, int z) {
        if (x < minX || x >= minX + w || z < minZ || z >= minZ + d) return SmoothRules.NO_SURFACE;
        int k = (z - minZ) * w + (x - minX);
        if (surfaceCache == null) { surfaceCache = new int[w * d]; Arrays.fill(surfaceCache, Integer.MAX_VALUE); }
        if (surfaceCache[k] != Integer.MAX_VALUE) return surfaceCache[k];
        int s = SmoothRules.NO_SURFACE;
        for (int y = minY + h - 1; y >= minY; y--) {
            int i = id(x, y, z);
            if (i < 0) break;
            int c = cellOf[i];
            if (SmoothRules.skyward(c)) continue;
            s = c == SmoothRules.NATURAL ? y : SmoothRules.NO_SURFACE;
            break;
        }
        return surfaceCache[k] = s;
    }

    final SmoothRules.Cells cells = new SmoothRules.Cells() {
        public int cell(int x, int y, int z) { int i = id(x, y, z); return i < 0 ? SmoothRules.UNKNOWN : cellOf[i]; }
        public int surface(int x, int z) { return SeamAudit.this.surface(x, z); }
        public float turf(int x, int y, int z, int k) { int i = id(x, y, z); return i >= 0 && turf[i] ? y + turfCorners[i][k] : Float.NaN; }
    };

    // ---------------------------------------------------------------- columns
    static final int T_NONE = 0, T_SMOOTH = 1, T_NATCUBE = 2, T_TURF = 3, T_CUBE = 4, T_MESH = 5, T_OTHER = 6;
    static final String[] TN = {"none", "nat~", "nat#", "turf", "cube", "mesh", "other"};

    static final class Col {
        int x, z, y, type, top = -1;   // y: ground top block level
        long g = -1;
        float[] c;   // corner heights above floor (nw ne sw se) of the drawn top
        boolean water, struct, tree;
        int under;
        float topAt(int k) { return y + c[k]; }
    }

    Col[] cols;

    Col col(int x, int z) {
        if (x < minX || x >= minX + w || z < minZ || z >= minZ + d) return null;
        int k = (z - minZ) * w + (x - minX);
        if (cols[k] != null) return cols[k];
        Col c = new Col();
        c.x = x; c.z = z; c.type = T_NONE;
        for (int y = minY + h - 1; y >= minY; y--) {
            int i = id(x, y, z);
            if (i < 0) break;
            int cl = cellOf[i];
            if (roadStruct[i]) { c.struct = true; continue; }
            if (cl == SmoothRules.AIR || cl == SmoothRules.COVER) continue;
            if (cl == SmoothRules.LEAF || cl == SmoothRules.TREE) { c.tree = true; continue; }
            if (cl == SmoothRules.FLUID) { c.water = true; continue; }
            c.y = y;
            c.top = i;
            if (invisibleRoad[i]) c.type = T_MESH;
            else if (turf[i]) { c.type = T_TURF; c.c = turfCorners[i].clone(); }
            else if (ground[i]) {
                // TerrainSmoothClient#renderGeometry: above AIR or COVER, the column's natural surface
                int above = cells.cell(x, y + 1, z);
                long g = (above == SmoothRules.AIR || above == SmoothRules.COVER) && surface(x, z) == y ? SmoothRules.geometry(cells, SmoothRules.MODE_B, x, y, z) : -1;
                c.g = g;
                if (g >= 0) { c.type = T_SMOOTH; c.c = SmoothRules.corners(g); c.under = underSides(x, y, z, g); }
                else { c.type = T_NATCUBE; c.c = new float[]{1, 1, 1, 1}; }
            } else if (fullOcc[i]) { c.type = T_CUBE; c.c = new float[]{1, 1, 1, 1}; }
            else c.type = T_OTHER;
            break;
        }
        return cols[k] = c;
    }

    static final int[][] SIDE_CORNERS = {{1, 0}, {2, 3}, {0, 2}, {3, 1}};   // N S W E: corner at band end a, b
    static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /** Mirror of TerrainSmoothClient#underSides (the mask only). */
    int underSides(int x, int y, int z, long g) {
        float[] c = SmoothRules.corners(g);
        int mask = 0;
        for (int s = 0; s < 4; s++) {
            float la = Math.min(Math.min((g >>> (16 + 8 * s) & 15) / 4F, c[SIDE_CORNERS[s][0]]), 1);
            float lb = Math.min(Math.min((g >>> (20 + 8 * s) & 15) / 4F, c[SIDE_CORNERS[s][1]]), 1);
            if (la <= 1e-4F && lb <= 1e-4F) continue;
            int nx = x + DIRS[s][0], nz = z + DIRS[s][1];
            int n = id(nx, y, nz);
            if (n < 0) continue;
            int k = cellOf[n];
            if ((k == SmoothRules.NATURAL || k == SmoothRules.OTHER || k == SmoothRules.TREE) && !fullOcc[n]) mask |= 1 << s;
            else if (k == SmoothRules.AIR || k == SmoothRules.COVER) {
                int below = id(nx, y - 1, nz);
                if (below >= 0 && turf[below]) mask |= 1 << s;
            }
        }
        return mask;
    }

    // ---------------------------------------------------------------- faces of a column toward a neighbour column
    record Iv(double lo0, double hi0, double lo1, double hi1, String what) {}

    String blockType(int i, int x, int y, int z, Col owner) {
        if (i < 0) return "unknown";
        if (owner != null && owner.y == y && owner.type == T_SMOOTH) return "nat~" + base[i];
        if (turf[i]) return "turf";
        if (ground[i]) return "nat#" + base[i];
        if (fullOcc[i]) return "cube:" + base[i];
        int cl = cellOf[i];
        return (cl == SmoothRules.AIR ? "air" : cl == SmoothRules.COVER ? "cover:" : cl == SmoothRules.FLUID ? "fluid:" : "other:") + (cl == SmoothRules.AIR ? "" : base[i]);
    }

    static final int[][] SIDE = {{1, 1, 0}, {7, 2, 3}, {3, 0, 2}, {5, 3, 1}};

    static float bandLow(long g, int s, int k) {
        if (k == SIDE[s][1]) return (g >>> (16 + 8 * s) & 15) / 4F;
        if (k == SIDE[s][2]) return (g >>> (20 + 8 * s) & 15) / 4F;
        throw new IllegalStateException();
    }

    /** Drawn intervals of column C's blocks on its side s (0 N, 1 S, 2 W, 3 E), at its corners k0, k1, for levels y0..C.y. */
    List<Iv> faces(Col C, int s, int k0, int k1, int y0, Map<Integer, String> culled) {
        List<Iv> out = new ArrayList<>();
        int nx = C.x + DIRS[s][0], nz = C.z + DIRS[s][1];
        for (int y = y0; y <= C.y; y++) {
            int i = id(C.x, y, C.z);
            if (i < 0) continue;
            int n = id(nx, y, nz);
            boolean occl = n >= 0 && fullOcc[n];
            String nt = blockType(n, nx, y, nz, col(nx, nz));
            if (y == C.y && C.type == T_SMOOTH) {
                // SmoothSurfaceModel#build
                long g = C.g;
                float[] c = C.c;
                float la0 = bandLow(g, s, k0), la1 = bandLow(g, s, k1);
                float ua0 = c[k0], ua1 = c[k1];
                boolean hasBand = ua0 > la0 + 1e-4F || ua1 > la1 + 1e-4F, hasUnder = (C.under >> s & 1) != 0;
                la0 = Math.min(la0, ua0); la1 = Math.min(la1, ua1);
                boolean above = ua0 > 1 + 1e-4F || ua1 > 1 + 1e-4F;
                if (hasBand && (above || !occl)) out.add(new Iv(y + la0, y + ua0, y + la1, y + ua1, above ? "band-above" : "band"));
                if (hasUnder && !occl) out.add(new Iv(y, y + Math.min(la0, 1), y, y + Math.min(la1, 1), "under"));
                if (occl && !(hasBand && above)) culled.put(y, (hasBand || hasUnder ? "nat~ side culled by " : "nat~ no band (cull ") + nt);
                else if (!hasBand && !hasUnder) culled.put(y, "nat~ no band vs " + nt);
                continue;
            }
            if (y == C.y && C.type == T_TURF) {
                // GradedTurfModel: sides in the culled list unless a corner is above the cell
                float[] c = C.c;
                float tall = Math.max(Math.max(c[0], c[1]), Math.max(c[2], c[3]));
                if (tall <= 1.0001F && occl) { culled.put(y, "turf side culled by " + nt); continue; }
                out.add(new Iv(y, y + c[k0], y, y + c[k1], "turfside"));
                continue;
            }
            if (fullOcc[i] || ground[i]) {
                if (occl) { culled.put(y, blockType(i, C.x, y, C.z, null) + " face culled by " + nt); continue; }
                out.add(new Iv(y, y + 1, y, y + 1, "cube"));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- audit
    record Hole(int x, int z, char axis, double maxGap, double area, String key, String detail, int nearWorks, double gapY, String mech) {}

    int[] worksDist;
    List<Hole> holes = new ArrayList<>();

    void works() {
        worksDist = new int[w * d];
        Arrays.fill(worksDist, 99);
        List<int[]> seeds = new ArrayList<>();
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            boolean any = false;
            for (int y = 0; y < h && !any; y++) {
                char c = blocks[(z * w + x) * h + y];
                if (c != 0xFFFF && (turf[c] || invisibleRoad[c])) any = true;
            }
            if (any) seeds.add(new int[]{x, z});
        }
        for (int[] s : seeds) for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
            int x = s[0] + dx, z = s[1] + dz;
            if (x < 0 || z < 0 || x >= w || z >= d) continue;
            int k = z * w + x, dd = Math.max(Math.abs(dx), Math.abs(dz));
            if (dd < worksDist[k]) worksDist[k] = dd;
        }
    }

    String colDesc(Col c) {
        if (c == null) return "null";
        StringBuilder b = new StringBuilder();
        b.append('(').append(c.x).append(',').append(c.y).append(',').append(c.z).append(") ").append(TN[c.type]).append(' ')
                .append(c.top >= 0 ? pal[c.top].replace("minecraft:", "").replace("apocalypse_firstlight:", "afl:") : "");
        if (c.c != null) b.append(String.format(Locale.ROOT, " corners[nw %.3f ne %.3f sw %.3f se %.3f]", c.c[0], c.c[1], c.c[2], c.c[3]));
        if (c.water) b.append(" water");
        if (c.struct) b.append(" struct");
        if (c.tree) b.append(" tree");
        return b.toString();
    }

    /** The four columns around corner point (px, pz) and their drawn top heights there (absolute). */
    String cornerDesc(int px, int pz) {
        StringBuilder b = new StringBuilder("pt(" + px + "," + pz + "):");
        int[][] around = {{px - 1, pz - 1, 3}, {px, pz - 1, 2}, {px - 1, pz, 1}, {px, pz, 0}};   // column and its corner index at the point
        for (int[] a : around) {
            Col c = col(a[0], a[1]);
            if (c == null || c.c == null) { b.append(" [").append(c == null ? "-" : TN[c.type]).append(']'); continue; }
            b.append(String.format(Locale.ROOT, " [%s y%d %.3f]", TN[c.type], c.y, c.topAt(a[2])));
        }
        return b.toString();
    }

    void auditEdge(Col P, Col Q, char axis) {
        if (P == null || Q == null || P.c == null || Q.c == null) return;
        if (P.type == T_MESH || Q.type == T_MESH || P.type == T_OTHER || Q.type == T_OTHER) return;
        int sP, sQ, kP0, kP1, kQ0, kQ1, ex0, ez0, ex1, ez1;
        if (axis == 'x') { sP = 3; sQ = 2; kP0 = 1; kP1 = 3; kQ0 = 0; kQ1 = 2; ex0 = P.x + 1; ez0 = P.z; ex1 = P.x + 1; ez1 = P.z + 1; }
        else { sP = 1; sQ = 0; kP0 = 2; kP1 = 3; kQ0 = 0; kQ1 = 1; ex0 = P.x; ez0 = P.z + 1; ex1 = P.x + 1; ez1 = P.z + 1; }
        double hp0 = P.topAt(kP0), hp1 = P.topAt(kP1), hq0 = Q.topAt(kQ0), hq1 = Q.topAt(kQ1);
        if (Math.abs(hp0 - hq0) < 1e-4 && Math.abs(hp1 - hq1) < 1e-4) return;
        int y0 = (int) Math.floor(Math.min(Math.min(hp0, hp1), Math.min(hq0, hq1))) - 1;
        Map<Integer, String> cullP = new TreeMap<>(), cullQ = new TreeMap<>();
        List<Iv> fP = faces(P, sP, kP0, kP1, y0, cullP), fQ = faces(Q, sQ, kQ0, kQ1, y0, cullQ);
        double maxGap = 0, sum = 0, gapY = Double.NaN;
        int N = 16;
        String why = null;
        for (int i = 0; i <= N; i++) {
            double t = i / (double) N;
            double hp = hp0 + (hp1 - hp0) * t, hq = hq0 + (hq1 - hq0) * t;
            if (Math.abs(hp - hq) < 1e-4) continue;
            boolean pHigh = hp > hq;
            List<Iv> f = pHigh ? fP : fQ;
            double lo = Math.min(hp, hq), hi = Math.max(hp, hq);
            List<double[]> iv = new ArrayList<>();
            for (Iv v : f) {
                double a = v.lo0 + (v.lo1 - v.lo0) * t, b = v.hi0 + (v.hi1 - v.hi0) * t;
                if (b > a + 1e-6) iv.add(new double[]{a, b});
            }
            iv.sort(Comparator.comparingDouble(a -> a[0]));
            double cur = lo, gap = 0, gy = Double.NaN, bestPiece = 0;
            for (double[] v : iv) {
                if (v[1] <= cur) continue;
                if (v[0] > cur) { double e = Math.min(v[0], hi); if (e > cur) { gap += e - cur; if (e - cur > bestPiece) { bestPiece = e - cur; gy = (cur + e) / 2; } } }
                cur = Math.max(cur, v[1]);
                if (cur >= hi) break;
            }
            if (cur < hi) { gap += hi - cur; if (hi - cur > bestPiece) { bestPiece = hi - cur; gy = (cur + hi) / 2; } }
            // a level where the higher column is open (a cave mouth, an overhang) needs no wall: subtract it
            Col hc = pHigh ? P : Q;
            for (int ly = (int) Math.floor(lo); ly < hc.y && ly <= hi; ly++) {
                int bi = id(hc.x, ly, hc.z);
                if (bi >= 0 && (fullOcc[bi] || ground[bi] || turf[bi])) continue;
                double a = Math.max(lo, ly), b = Math.min(hi, ly + 1);
                if (b <= a) continue;
                double unc = b - a;
                for (double[] v : iv) unc -= Math.max(0, Math.min(b, v[1]) - Math.max(a, v[0]));
                gap -= Math.max(0, unc);
            }
            if (gap > 1e-3) {
                double wgt = (i == 0 || i == N) ? 0.5 : 1;
                sum += gap * wgt;
                if (gap > maxGap) {
                    maxGap = gap;
                    gapY = gy;
                    Col hiC = pHigh ? P : Q, loC = pHigh ? Q : P;
                    Map<Integer, String> cm = pHigh ? cullP : cullQ;
                    int ly = (int) Math.floor(gy);
                    String c = cm.get(ly);
                    if (c == null) {
                        int bi = id(hiC.x, ly, hiC.z);
                        c = "no face: " + blockType(bi, hiC.x, ly, hiC.z, hiC) + (ly > hiC.y ? " (above higher column's top block)" : "");
                    }
                    why = TN[hiC.type] + ">" + TN[loC.type] + " | " + c.replaceAll("nat[~#][a-z_]+", "NAT").replaceAll("cube:[a-z_:]+", "CUBE");
                }
            }
        }
        if (maxGap < 0.01) return;
        double area = sum / N;
        int k = (P.z - minZ) * w + (P.x - minX);
        String detail = "P " + colDesc(P) + " | Q " + colDesc(Q) + " | " + cornerDesc(ex0, ez0) + " | " + cornerDesc(ex1, ez1)
                + " | cullP " + cullP + " | cullQ " + cullQ;
        String m0 = mech(P, Q, ex0, ez0, kP0, kQ0), m1 = mech(P, Q, ex1, ez1, kP1, kQ1);
        String mech = rank(m0) >= rank(m1) ? m0 : m1;
        holes.add(new Hole(P.x, P.z, axis, maxGap, area, why, detail, worksDist[k], gapY, mech));
    }

    static int rank(String m) {
        return m.startsWith("WINDOW") ? 6 : m.startsWith("FIT") ? 5 : m.startsWith("CLAMP") ? 4 : m.startsWith("Q8") ? 3 : m.startsWith("other") ? 2 : m.startsWith("NOTURF") ? 1 : 0;
    }

    /** Why P and Q disagree at corner point (px, pz) (P corner kp, Q corner kq): the C3.3a seam mechanisms. */
    String mech(Col P, Col Q, int px, int pz, int kp, int kq) {
        double hp = P.topAt(kp), hq = Q.topAt(kq), diff = Math.abs(hp - hq);
        if (diff < 1e-4) return "same";
        int[][] around = {{px - 1, pz - 1, 3}, {px, pz - 1, 2}, {px - 1, pz, 1}, {px, pz, 0}};
        List<Double> tv = new ArrayList<>();
        for (int[] a : around) { Col c = col(a[0], a[1]); if (c != null && c.type == T_TURF) tv.add((double) c.topAt(a[2])); }
        if (tv.isEmpty()) return "NOTURF";
        double tmin = Collections.min(tv), tmax = Collections.max(tv);
        boolean disagree = tmax - tmin > 1e-3;
        Col[] two = {P, Q};
        int[] ks = {kp, kq};
        boolean[] took = new boolean[2], own = new boolean[2], clamped = new boolean[2], rounded = new boolean[2];
        for (int i = 0; i < 2; i++) {
            Col c = two[i];
            if (c.type != T_SMOOTH) continue;
            double v = c.topAt(ks[i]);
            own[i] = Math.abs(v - (c.y + 1)) < 1e-4;
            for (double t : tv) {
                double rel = t - c.y, q = Math.max(0, Math.min(8, Math.round(rel * 4))) / 4.0;
                if (Math.abs(c.y + q - v) < 1e-4) {
                    took[i] = true;
                    if (rel < 0 || rel > 2) clamped[i] = true;
                    if (Math.abs(q - rel) > 1e-4 && rel >= 0 && rel <= 2) rounded[i] = true;
                }
            }
        }
        boolean natNat = P.type == T_SMOOTH && Q.type == T_SMOOTH;
        if (natNat && (took[0] && !took[1] && own[1] || took[1] && !took[0] && own[0])) return "WINDOW turf corner taken by one block, the other pinned at its own H";
        if (P.type == T_TURF && Q.type == T_TURF) return disagree ? "FIT turf-turf corners disagree" : "other turf-turf";
        if (disagree && (P.type == T_TURF || Q.type == T_TURF)) return "FIT turf corners disagree; natural took another turf's value";
        if (clamped[0] || clamped[1]) return "CLAMP turf corner outside the natural block's 0..2 range";
        if (diff <= 0.125 + 1e-4 && (rounded[0] || rounded[1])) return "Q8 turf 1/8 corner rounded to 1/4 by the natural block";
        if (disagree) return "FIT turf corners disagree (natural-natural)";
        return "other " + TN[P.type] + "/" + TN[Q.type] + String.format(Locale.ROOT, " took %b/%b own %b/%b", took[0], took[1], own[0], own[1]);
    }

    /** Audits every column edge at least {@code margin} columns inside the area. */
    void audit(int margin) {
        cols = new Col[w * d];
        surfaceCache = null;
        holes.clear();
        works();
        for (int z = minZ + margin; z < minZ + d - margin; z++) for (int x = minX + margin; x < minX + w - margin; x++) {
            Col P = col(x, z);
            auditEdge(P, col(x + 1, z), 'x');
            auditEdge(P, col(x, z + 1), 'z');
        }
    }

    // ---------------------------------------------------------------- odd faces (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md 3.9)

    /** Odd-face counts: tops seen, folds over 35 degrees, a lone corner 1/2 off the other three (beside turf of those), raised bands, slivers. */
    static final int ODD_TOPS = 0, ODD_FOLDS = 1, ODD_LONE = 2, ODD_LONE_TURF = 3, ODD_RAISED = 4, ODD_SLIVERS = 5;
    static final String[] ODD_NAMES = {"tops", "folds > 35 deg", "lone corner >= 1/2", "of them beside turf", "bands above the cell", "slivers < 0.002 m2"};
    final int[] odd = new int[ODD_NAMES.length];

    /**
     * Counts the odd faces of every sloped natural top and road turf top at least margin columns inside (audit first): a top
     * folded on its diagonal by more than 35 degrees, one corner half a block or more above or below the other three, a
     * side band rising above its own cell (a standing triangle), a band thinner than 0.002 m2. A baseline, not a failure:
     * the rework's merged height field (R2) must bring each to 0 or say why not. T-joints are not counted yet.
     */
    void oddFaces(int margin) {
        for (int z = minZ + margin; z < minZ + d - margin; z++) for (int x = minX + margin; x < minX + w - margin; x++) {
            Col C = col(x, z);
            if (C.type != T_SMOOTH && C.type != T_TURF) continue;
            float[] c = C.c;
            odd[ODD_TOPS]++;
            // the two triangles nw-sw-se and nw-se-ne (SmoothRules#surface; the turf model splits the same way)
            double[] n1 = normal(0, c[0], 0, 0, c[2], 1, 1, c[3], 1), n2 = normal(0, c[0], 0, 1, c[3], 1, 1, c[1], 0);
            double cos = (n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2]) / Math.sqrt((n1[0] * n1[0] + n1[1] * n1[1] + n1[2] * n1[2]) * (n2[0] * n2[0] + n2[1] * n2[1] + n2[2] * n2[2]));
            if (Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))) > 35) odd[ODD_FOLDS]++;
            float[] s = c.clone();
            Arrays.sort(s);
            if (s[3] - s[2] >= 0.5F - 1e-4F || s[1] - s[0] >= 0.5F - 1e-4F) {
                odd[ODD_LONE]++;
                boolean turfNear = false;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    Col n = col(x + dx, z + dz);
                    if ((dx != 0 || dz != 0) && n != null && n.type == T_TURF) turfNear = true;
                }
                if (turfNear && C.type == T_SMOOTH) odd[ODD_LONE_TURF]++;
            }
            if (C.type != T_SMOOTH) continue;
            for (int side = 0; side < 4; side++) {
                int k0 = SIDE_CORNERS[side][0], k1 = SIDE_CORNERS[side][1];
                float la0 = bandLow(C.g, side, k0), la1 = bandLow(C.g, side, k1), ua0 = c[k0], ua1 = c[k1];
                double area = (Math.max(0, ua0 - la0) + Math.max(0, ua1 - la1)) / 2;
                if (area <= 1e-6) continue;
                if (ua0 > 1 + 1e-4F || ua1 > 1 + 1e-4F) odd[ODD_RAISED]++;
                if (area < 0.002) odd[ODD_SLIVERS]++;
            }
        }
    }

    static double[] normal(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
        double ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        return new double[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    static String oddLine(int[] o) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < o.length; i++) b.append(i == 0 ? "" : ", ").append(ODD_NAMES[i]).append(' ').append(o[i]);
        return b.toString();
    }

    void report(String label, Path outDir) throws IOException {
        Map<String, double[]> byKey = new TreeMap<>(), byMech = new TreeMap<>();
        for (Hole o : holes) {
            String near = o.nearWorks <= 8 ? "near " : "far  ";
            double[] a = byKey.computeIfAbsent(near + o.key, kk -> new double[3]);
            a[0]++; a[1] += o.area; a[2] = Math.max(a[2], o.maxGap);
            double[] m = byMech.computeIfAbsent(near + o.mech, kk -> new double[3]);
            m[0]++; m[1] += o.area; m[2] = Math.max(m[2], o.maxGap);
        }
        Files.createDirectories(outDir);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outDir.resolve(label + "_summary.txt")))) {
            pw.printf(Locale.ROOT, "%s: %d hole edges (max gap >= 0.01)%n", label, holes.size());
            pw.println("-- by mechanism");
            byMech.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1])).forEach(e ->
                    pw.printf(Locale.ROOT, "%5d edges  area %8.2f  maxgap %5.2f  %s%n", (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getKey()));
            pw.println("-- by face");
            byKey.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1])).forEach(e ->
                    pw.printf(Locale.ROOT, "%5d edges  area %8.2f  maxgap %5.2f  %s%n", (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getKey()));
        }
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outDir.resolve(label + "_holes.tsv")))) {
            pw.println("x\tz\taxis\tmaxGap\tarea\tgapY\tnearWorks\tmech\tkey\tdetail");
            List<Hole> sorted = new ArrayList<>(holes);
            sorted.sort((a, b) -> Double.compare(b.area, a.area));
            for (Hole o : sorted) pw.printf(Locale.ROOT, "%d\t%d\t%c\t%.3f\t%.3f\t%.2f\t%d\t%s\t%s\t%s%n", o.x, o.z, o.axis, o.maxGap, o.area, o.gapY, o.nearWorks, o.mech, o.key, o.detail);
        }
        System.out.println(label + ": " + holes.size() + " hole edges");
    }

    // ---------------------------------------------------------------- synthetic earthworks scenes
    /** Smooth random field in about [-1, 1]: three plane waves, wavelengths 24-64. */
    static final class Waves {
        final double[] kx = new double[3], kz = new double[3], ph = new double[3];

        Waves(Random r) {
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2, len = 24 + r.nextDouble() * 40;
                kx[i] = Math.cos(a) * Math.PI * 2 / len;
                kz[i] = Math.sin(a) * Math.PI * 2 / len;
                ph[i] = r.nextDouble() * Math.PI * 2;
            }
        }

        double at(double x, double z) {
            double s = 0;
            for (int i = 0; i < 3; i++) s += Math.sin(kx[i] * x + kz[i] * z + ph[i]);
            return s / 2;
        }
    }

    /** RoadWorks#fit, unchanged: {y, shift, nw, ne, sw, se, clamped}. */
    static int[] fit(double[] f) {
        int[] q = new int[4];
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) { q[i] = (int) Math.round(f[i] * 8); lo = Math.min(lo, q[i]); hi = Math.max(hi, q[i]); }
        int ya = Math.floorDiv(hi - 1, 8);
        if (lo >= ya * 8) return pack(ya, 0, q, ya * 8, false);
        int yb = Math.floorDiv(hi - 4 - 1, 8);
        if (lo >= yb * 8 + 4 && hi <= yb * 8 + 12) return pack(yb, 1, q, yb * 8 + 4, false);
        return pack(ya, 0, q, ya * 8, true);
    }

    private static int[] pack(int y, int shift, int[] q, int base, boolean clamp) {
        int[] out = new int[7];
        out[0] = y;
        out[1] = shift;
        for (int i = 0; i < 4; i++) out[2 + i] = Math.max(0, Math.min(8, q[i] - base));
        out[6] = clamp ? 1 : 0;
        return out;
    }

    /**
     * A road across random natural ground: a paved core (asphalt under the invisible road surface), turf earthworks on
     * both sides (fill or cut slope at {@code slope} blocks per block down / up to the planned ground, which is off the
     * actual ground by up to {@code offset} blocks, then planned ground to the works edge), natural grass beyond with
     * plants on some tops. Shared corner heights, quantised and fitted like RoadChunkPass's TURF columns.
     */
    static SeamAudit scene(long seed, double relief, double slope, double rise, double angle, double offset) {
        SeamAudit a = new SeamAudit();
        a.minX = 0; a.minZ = 0; a.w = 72; a.d = 72; a.minY = 0; a.h = 64;
        Random r = new Random(seed);
        Waves nat = new Waves(r), off = new Waves(r);
        double cx = a.w / 2.0, cz = a.d / 2.0, sin = Math.sin(Math.toRadians(angle)), cos = Math.cos(Math.toRadians(angle));
        double base = 28, core = 4, half = 26;
        java.util.function.DoubleBinaryOperator natural = (x, z) -> base + relief * nat.at(x, z);
        java.util.function.DoubleBinaryOperator planned = (x, z) -> natural.applyAsDouble(x, z) + offset * Math.max(-1, Math.min(1, off.at(x, z)));
        double road = base + rise;
        java.util.function.DoubleBinaryOperator across = (x, z) -> (x - cx) * -sin + (z - cz) * cos;
        java.util.function.DoubleBinaryOperator works = (x, z) -> {
            double u = Math.abs(across.applyAsDouble(x, z)), pg = planned.applyAsDouble(x, z);
            if (u <= core) return road;
            double dd = (u - core) * slope;
            return road > pg ? Math.max(pg, road - dd) : Math.min(pg, road + dd);
        };
        Map<String, Integer> ids = new LinkedHashMap<>();
        java.util.function.Function<String, Character> idOf = s -> (char) (int) ids.computeIfAbsent(s, k -> ids.size());
        char air = idOf.apply("minecraft:air"), grass = idOf.apply("minecraft:grass_block"), dirt = idOf.apply("minecraft:dirt"),
                stone = idOf.apply("minecraft:stone"), plant = idOf.apply("minecraft:grass"), asphalt = idOf.apply("apocalypse_firstlight:asphalt"),
                surface = idOf.apply("apocalypse_firstlight:road_surface");
        a.blocks = new char[a.w * a.d * a.h];
        for (int z = 0; z < a.d; z++) for (int x = 0; x < a.w; x++) {
            int at = (z * a.w + x) * a.h;
            double u = Math.abs(across.applyAsDouble(x + 0.5, z + 0.5));
            double pg = planned.applyAsDouble(x + 0.5, z + 0.5);
            double toe = core + Math.abs(road - pg) / slope;
            if (u <= core) {
                int top = (int) Math.floor(road);
                for (int y = 0; y < a.h; y++) a.blocks[at + y] = y < top - 1 ? dirt : y == top - 1 ? asphalt : y == top ? surface : air;
            } else if (u <= Math.min(half, toe + 4)) {
                double[] f = {works.applyAsDouble(x, z), works.applyAsDouble(x + 1, z), works.applyAsDouble(x, z + 1), works.applyAsDouble(x + 1, z + 1)};
                int[] t = fit(f);
                char turf = idOf.apply("apocalypse_firstlight:graded_turf[ne=" + t[3] + ",nw=" + t[2] + ",se=" + t[5] + ",shift=" + (t[1] != 0) + ",sw=" + t[4] + "]");
                for (int y = 0; y < a.h; y++) a.blocks[at + y] = y < t[0] ? dirt : y == t[0] ? turf : air;
            } else {
                int top = (int) Math.round(natural.applyAsDouble(x + 0.5, z + 0.5)) - 1;
                boolean grows = r.nextInt(4) == 0;
                for (int y = 0; y < a.h; y++) a.blocks[at + y] = y < top - 2 ? stone : y < top ? dirt : y == top ? grass : y == top + 1 && grows ? plant : air;
            }
        }
        a.pal = ids.keySet().toArray(new String[0]);
        a.classifyAll();
        return a;
    }

    /** The synthetic suite: every scene must be watertight. */
    static int suite() {
        double[] reliefs = {0.8, 3, 7}, slopes = {0.5, 1.0, 1.6}, rises = {-6, 0, 6}, angles = {0, 27}, offsets = {0, 1.2};
        int scenes = 0, bad = 0, total = 0;
        int[] odd = new int[ODD_NAMES.length];
        long seed = 1;
        List<String> examples = new ArrayList<>();
        for (double relief : reliefs) for (double slope : slopes) for (double rise : rises) for (double angle : angles) for (double offset : offsets) {
            SeamAudit a = scene(seed++, relief, slope, rise, angle, offset);
            a.audit(3);
            a.oddFaces(3);
            for (int i = 0; i < odd.length; i++) odd[i] += a.odd[i];
            scenes++;
            if (a.holes.isEmpty()) continue;
            bad++;
            total += a.holes.size();
            String name = String.format(Locale.ROOT, "relief %.1f slope %.1f rise %+.0f angle %.0f offset %.1f (seed %d)", relief, slope, rise, angle, offset, seed - 1);
            for (Hole o : a.holes) if (examples.size() < 12)
                examples.add(String.format(Locale.ROOT, "  %s: (%d,%d)%c gap %.3f %s | %s | %s", name, o.x, o.z, o.axis, o.maxGap, o.mech, o.key, o.detail));
        }
        System.out.println("odd faces (rework R0 baseline, not a failure), " + scenes + " scenes: " + oddLine(odd));
        if (bad == 0) {
            System.out.println("seam audit: " + scenes + " synthetic earthworks scenes, 0 see-through holes");
            return 0;
        }
        System.out.println("FAIL seam audit: " + total + " hole edges in " + bad + " of " + scenes + " synthetic scenes");
        examples.forEach(System.out::println);
        return 1;
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) System.exit(suite());
        SeamAudit a = new SeamAudit();
        a.load(Paths.get(args[0]));
        a.audit(3);
        a.oddFaces(3);
        System.out.println("odd faces: " + oddLine(a.odd));
        a.report(args.length > 1 ? args[1] : "area", Paths.get(args.length > 2 ? args[2] : "."));
    }
}
