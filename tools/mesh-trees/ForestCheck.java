package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanV2;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Mesh Trees P1 offline forest check (docs/rendering/mesh_trees_p1_forest.md, N). Run by tools/mesh-trees/check.mjs
 * (--forest): ForestPlan over a real Terrain V2 plan (the game's plan cache for a seed), no Minecraft.
 * Arguments: plan cache file, world seed, preview folder, centre x, centre z.
 * <ol>
 * <li>Layouts: build time of all 128 (species x variant x turn) and their memory (estimate).</li>
 * <li>Census of a 2048 m window round the centre: area and trees per biome (trees per chunk), species shares,
 * nearest neighbours, site rejections.</li>
 * <li>Invariants: spacing never violated; each tree in exactly one chunk; a second plan on another thread, chunks in
 * shuffled order, gives the same trees (determinism, generation order); no tree within the buffer of a vanilla tree
 * biome (finer sampling than the plan's ring).</li>
 * <li>Cells with the planned ground (three scenario windows): trees a hillside would block, crown cells inside the
 * ground, shared cells, leaf cells lost to another tree's wood, and the shared-cell result in two orders.</li>
 * <li>Cost: trees per chunk cold and warm (ms).</li>
 * <li>Test coordinates for open white oak, white pine forest and mixed forest, and a map of the three.</li>
 * </ol>
 */
public final class ForestCheck {
    static int failures;

    static void require(boolean ok, String what) {
        if (!ok) { failures++; if (failures < 40) System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        Path cache = Path.of(args[0]);
        long seed = Long.parseLong(args[1]);
        File out = new File(args[2]);
        out.mkdirs();
        int cx0 = Integer.parseInt(args[3]), cz0 = Integer.parseInt(args[4]);
        TerrainPlanSurface s;
        try (var in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(cache)), 1 << 16))) {
            s = TerrainPlanSurface.read(in, seed, TerrainPlanV2.VERSION);
        }
        require(s != null && s.ecology != null, "plan cache readable with ecology zones");
        if (s == null) { System.out.println("FOREST CHECK: no plan"); System.exit(1); }
        System.out.printf("seed %d, centre %d %d%n", seed, cx0, cz0);
        layouts();

        var plan = new ForestPlan(s, seed);
        census(plan, cx0, cz0, 2048);
        invariants(plan, s, seed, cx0, cz0);
        cost(s, seed, cx0, cz0);
        int[][] spots = spots(plan, cx0, cz0);
        for (int[] p : spots) if (p != null) cells(plan, p[0], p[1]);
        // the biomes of the spots the centre window may not hold (coastal pine, swamp)
        for (String name : new String[]{"mixed", "swamp"}) {
            int k = ForestPlan.scenario(name);
            if (spots[k] != null) census(plan, spots[k][0], spots[k][1], 1024);
        }
        map(plan, spots, new File(out, "forest_scenarios.png"));
        System.out.println(failures == 0 ? "MESH FOREST CHECK OK" : "MESH FOREST CHECK: " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------------------------------------- layouts

    static void layouts() {
        long t0 = System.nanoTime();
        for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) TreeShape.layout(sp, v, 0);
        long t1 = System.nanoTime();
        long quads = 0, cells = 0, baked = 0;
        int[] lodQuads = new int[TreeShape.LODS];
        for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) for (int t = 0; t < 4; t++) {
            var l = TreeShape.layout(sp, v, t);
            cells += l.cells.size();
            for (var c : l.cells.values()) for (int k = 0; k < TreeShape.LODS; k++) { quads += c.quads[k].size(); lodQuads[k] += c.quads[k].size(); }
            for (int k = 0; k < TreeShape.LODS; k++) baked += l.quadsPerLod[k];
        }
        long t2 = System.nanoTime();
        // Quad: header + 4 float arrays (12, 8, 12, 4) + fields; Cell: header, list array, 3 lists, optional voxels;
        // a baked quad: header + int[32] + fields; one baked map per layout, level and shading mode in use
        double layoutMb = (quads * 260.0 + cells * 150.0) / 1e6, bakedMb = baked * 190.0 / 1e6;
        int base = Species.values().length * TreeShape.VARIANTS;
        System.out.printf("layouts: %d base %.0f ms, %d turns %.0f ms; %d layouts hold %d cells, %d quads (LOD0/1/2 %d/%d/%d), about %.0f MB; baked (client, one shading mode, every layout at every level) about %.0f MB%n",
                base, (t1 - t0) / 1e6, 3 * base, (t2 - t1) / 1e6, 4 * base, cells, quads, lodQuads[0], lodQuads[1], lodQuads[2], layoutMb, bakedMb);
        for (Species sp : Species.values()) {
            float rmin = 99, rmax = 0, hmin = 99, hmax = 0;
            int ext = 0;
            for (int v = 0; v < TreeShape.VARIANTS; v++) {
                var l = TreeShape.layout(sp, v, 0);
                rmin = Math.min(rmin, l.crownRadius); rmax = Math.max(rmax, l.crownRadius);
                hmin = Math.min(hmin, l.height); hmax = Math.max(hmax, l.height);
                ext = Math.max(ext, Math.max(Math.max(-l.minX, l.maxX), Math.max(-l.minZ, l.maxZ)));
            }
            System.out.printf("  %s: crown radius %.1f-%.1f m, height %.1f-%.1f m, cells reach %d blocks from the root%n", sp.id, rmin, rmax, hmin, hmax, ext);
            require(ext <= ForestPlan.REACH, sp.id + ": cells within the index reach " + ForestPlan.REACH + " (FEATURES may write one chunk out)");
        }
    }

    // ----------------------------------------------------------------------------------------------- census

    static List<ForestPlan.Tree> window(ForestPlan plan, int x0, int z0, int size) {
        return plan.window(x0, z0, size);
    }

    static void census(ForestPlan plan, int cx0, int cz0, int W) {
        int x0 = (cx0 - W / 2) >> 4 << 4, z0 = (cz0 - W / 2) >> 4 << 4;
        int nb = EcologyBiomes.IDS.length;
        double[] area = new double[nb];
        for (int z = z0; z < z0 + W; z += 4) for (int x = x0; x < x0 + W; x += 4) area[plan.biome(x, z)] += 16.0 / 256;
        long t0 = System.nanoTime();
        var trees = window(plan, x0, z0, W);
        long t1 = System.nanoTime();
        int ns = Species.values().length;
        int[] count = new int[nb];
        int[][] bySpecies = new int[nb][ns];
        for (var t : trees) { count[t.biome()]++; bySpecies[t.biome()][t.species().ordinal()]++; }
        var nn = nearest(trees);
        System.out.printf("census %d x %d m at %d %d: %d trees (%.1f ms for %d chunks)%n", W, W, x0, z0, trees.size(), (t1 - t0) / 1e6, (W / 16) * (W / 16));
        for (int b = 0; b < nb; b++) {
            if (area[b] < 1) continue;
            var st = ForestPlan.stand(b);
            double per = count[b] / area[b];
            var d = new ArrayList<Double>();
            for (int i = 0; i < trees.size(); i++) if (trees.get(i).biome() == b) d.add(nn[i]);
            Collections.sort(d);
            var mix = new StringBuilder();
            for (Species sp : Species.values()) if (bySpecies[b][sp.ordinal()] > 0)
                mix.append(String.format(" %s %.0f%%", SHORT[sp.ordinal()], 100.0 * bySpecies[b][sp.ordinal()] / count[b]));
            System.out.printf("  %-24s %7.0f chunks  %5d trees  %.2f / chunk%s  nearest p5 %.1f median %.1f m %s%n",
                    EcologyBiomes.IDS[b], area[b], count[b], per, st == null ? (ForestPlan.VANILLA_TREES[b] ? "  (vanilla trees kept)" : "") : String.format(" (target %.2f)", st.target()),
                    d.isEmpty() ? 0 : d.get(d.size() / 20), d.isEmpty() ? 0 : d.get(d.size() / 2), mix.length() == 0 ? "" : " mix" + mix);
            // the mix follows the stand's weights (patch noise moves it about; large areas only)
            if (st != null && area[b] >= 1000 && count[b] >= 200) for (Species sp : Species.values()) {
                double got = bySpecies[b][sp.ordinal()] / (double) count[b], want = st.share(sp);
                require(want > 0 || got == 0, EcologyBiomes.IDS[b] + ": no " + sp.id + " outside the stand's mix");
                require(Math.abs(got - want) <= 0.12 + 0.25 * want, EcologyBiomes.IDS[b] + ": " + sp.id + String.format(" share %.2f near the mix's %.2f", got, want));
            }
            // large areas only: small woodlots lose their edges to the vanilla-tree buffer and the hillside rule
            if (st != null && area[b] >= 1000) require(Math.abs(per - st.target()) <= 0.25 * st.target() + 0.03, EcologyBiomes.IDS[b] + ": density " + String.format("%.2f", per) + " within 25 % of the target " + st.target());
            if (st == null) require(count[b] == 0, EcologyBiomes.IDS[b] + ": no mesh tree outside the mesh forest biomes");
        }
        // why candidates drop (kept by density first)
        var why = new TreeMap<String, Integer>();
        String[] w = new String[1];
        int sites = 0;
        for (int gz = z0 / 4; gz < (z0 + W) / 4; gz++) for (int gx = x0 / 4; gx < (x0 + W) / 4; gx++) {
            w[0] = null;
            if (plan.evaluate(gx, gz, w) != null) { sites++; continue; }
            why.merge(w[0], 1, Integer::sum);
        }
        System.out.printf("  grid cells: %d candidates are sites, %d of them stand (spacing); dropped: %s%n", sites, trees.size(), why);
    }

    /** Nearest other tree per tree (grid hash). */
    static double[] nearest(List<ForestPlan.Tree> trees) {
        var grid = new HashMap<Long, List<Integer>>();
        for (int i = 0; i < trees.size(); i++) grid.computeIfAbsent(key(trees.get(i).x() >> 5, trees.get(i).z() >> 5), k -> new ArrayList<>()).add(i);
        double[] nn = new double[trees.size()];
        for (int i = 0; i < trees.size(); i++) {
            var t = trees.get(i);
            double best = 999;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                var list = grid.get(key((t.x() >> 5) + dx, (t.z() >> 5) + dz));
                if (list == null) continue;
                for (int j : list) if (j != i) best = Math.min(best, Math.hypot(trees.get(j).x() - t.x(), trees.get(j).z() - t.z()));
            }
            nn[i] = best;
        }
        return nn;
    }

    static long key(int a, int b) { return (long) a << 32 ^ (b & 0xFFFFFFFFL); }

    // ------------------------------------------------------------------------------------------- invariants

    static void invariants(ForestPlan plan, TerrainPlanSurface s, long seed, int cx0, int cz0) throws Exception {
        int W = 1024, x0 = (cx0 - W / 2) >> 4 << 4, z0 = (cz0 - W / 2) >> 4 << 4;
        var trees = window(plan, x0, z0, W);
        int close = 0;
        for (int i = 0; i < trees.size(); i++) for (int j = i + 1; j < trees.size(); j++) {
            var a = trees.get(i); var b = trees.get(j);
            double d = Math.hypot(a.x() - b.x(), a.z() - b.z());
            if (d < ForestPlan.spacing(a, b) - 1e-9) close++;
        }
        require(close == 0, "spacing kept (" + close + " pairs too close)");
        // each tree in exactly one chunk (the chunk of its root column)
        var seen = new HashSet<Long>();
        int dup = 0, wrong = 0;
        for (int cz = z0 >> 4; cz < (z0 + W) >> 4; cz++) for (int cx = x0 >> 4; cx < (x0 + W) >> 4; cx++)
            for (var t : plan.trees(cx, cz)) { if (!seen.add(key(t.x(), t.z()))) dup++; if (t.x() >> 4 != cx || t.z() >> 4 != cz) wrong++; }
        require(dup == 0 && wrong == 0, "each tree belongs to exactly one chunk (" + dup + " twice, " + wrong + " outside)");
        // a second plan, on another thread (its own caches), chunks in shuffled order
        var chunks = new ArrayList<long[]>();
        for (int cz = z0 >> 4; cz < (z0 + W) >> 4; cz++) for (int cx = x0 >> 4; cx < (x0 + W) >> 4; cx++) chunks.add(new long[]{cx, cz});
        Collections.shuffle(chunks, new Random(7));
        var other = new HashMap<Long, String>();
        var thread = new Thread(() -> {
            var p2 = new ForestPlan(s, seed);
            for (long[] c : chunks) for (var t : p2.trees((int) c[0], (int) c[1])) other.put(key(t.x(), t.z()), t.toString());
        });
        thread.start();
        thread.join();
        int same = 0;
        for (var t : trees) if (t.toString().equals(other.get(key(t.x(), t.z())))) same++;
        require(same == trees.size() && other.size() == trees.size(), "same trees from another thread in shuffled chunk order (" + same + " of " + trees.size() + ", other " + other.size() + ")");
        // the vanilla tree biome buffer, sampled every 2 blocks on the disc
        int near = 0, nearest = 999;
        for (var t : trees) {
            int r = ForestPlan.BUFFER;
            for (int dz = -r; dz <= r; dz += 2) for (int dx = -r; dx <= r; dx += 2) {
                if (dx * dx + dz * dz > r * r) continue;
                if (ForestPlan.VANILLA_TREES[plan.biome(t.x() + dx, t.z() + dz)]) { near++; nearest = Math.min(nearest, (int) Math.hypot(dx, dz)); dz = r + 1; break; }
            }
        }
        System.out.printf("invariants (%d m window, %d trees): spacing OK, one chunk each, order/thread independent; vanilla-tree biome inside %d m of %d trees (closest %s)%n",
                W, trees.size(), ForestPlan.BUFFER, near, near == 0 ? "-" : nearest + " m");
        require(near <= Math.max(2, trees.size() / 100), "at most 1 % of the trees see a vanilla tree biome inside the buffer on the fine disc (" + near + ")");
        require(nearest >= ForestPlan.BUFFER / 2 || near == 0, "none closer than half the buffer");
    }

    static void cost(TerrainPlanSurface s, long seed, int cx0, int cz0) throws Exception {
        double[] ms = new double[2];
        var thread = new Thread(() -> {
            var p = new ForestPlan(s, seed);
            int n = 32, c0x = (cx0 >> 4) - n / 2, c0z = (cz0 >> 4) - n / 2;
            long t0 = System.nanoTime();
            for (int cz = c0z; cz < c0z + n; cz++) for (int cx = c0x; cx < c0x + n; cx++) p.trees(cx, cz);
            long t1 = System.nanoTime();
            for (int cz = c0z; cz < c0z + n; cz++) for (int cx = c0x; cx < c0x + n; cx++) p.trees(cx, cz);
            long t2 = System.nanoTime();
            ms[0] = (t1 - t0) / 1e6 / (n * n); ms[1] = (t2 - t1) / 1e6 / (n * n);
        });
        thread.start();
        thread.join();
        System.out.printf("cost: the plan's trees for one chunk %.3f ms (fresh thread, rows of chunks), %.3f ms when its neighbours were asked before%n", ms[0], ms[1]);
        require(ms[0] < 2.0, "planning stays under 2 ms per chunk");
    }

    // ------------------------------------------------------------------------------------------------ cells

    /** One tree's cells against the planned ground and the others (rank/species maximum, in two orders). */
    static void cells(ForestPlan plan, int px, int pz) {
        var s = plan.surface;
        int R = 128;
        var trees = window(plan, px - R, pz - R, 2 * R);
        int refused = 0, buried = 0, written = 0;
        var placed = new ArrayList<ForestPlan.Tree>();
        var roots = new ArrayList<int[]>();
        for (var t : trees) {
            int y = plan.top(t.x(), t.z());
            var l = TreeShape.layout(t.species(), t.variant(), t.turns());
            boolean blocked = false;
            int under = 0;
            for (var c : l.cells.values()) {
                int wx = t.x() + c.x, wy = y + c.y, wz = t.z() + c.z;
                if (wy >= plan.top(wx, wz)) continue;
                if (c.kind == TreeShape.WOOD) { blocked = true; break; }
                under++;
            }
            if (blocked) { refused++; continue; }
            buried += under;
            placed.add(t);
            roots.add(new int[]{t.x(), y, t.z()});
        }
        long[] a = merge(placed, roots, false), b = merge(placed, roots, true);
        require(a[0] == b[0] && a[4] == b[4], "shared cells end the same in either order");
        require(refused == 0, "the plan already drops every tree a planned hillside would block (" + refused + " left)");
        System.out.printf("cells round %d %d (%d trees): %d refused by the planned ground (a branch in the hillside), %d crown cells inside the ground skipped; %d cells, %d shared, %d leaf cells lost to another tree's wood (%.2f %%)%n",
                px, pz, trees.size(), refused, buried, a[1], a[2], a[3], a[1] == 0 ? 0 : 100.0 * a[3] / a[1]);
    }

    /** {hash of the final cells, cells, shared, leaf cells lost, count}; reverse: the trees in reverse order. */
    static long[] merge(List<ForestPlan.Tree> trees, List<int[]> roots, boolean reverse) {
        var fin = new HashMap<Long, int[]>();           // rank, species, tree
        var owners = new HashMap<Long, Integer>();
        var order = new ArrayList<Integer>();
        for (int i = 0; i < trees.size(); i++) order.add(i);
        if (reverse) Collections.reverse(order);
        long cells = 0;
        for (int i : order) {
            var t = trees.get(i);
            int[] r = roots.get(i);
            var l = TreeShape.layout(t.species(), t.variant(), t.turns());
            for (var c : l.cells.values()) {
                long k = ((long) (r[0] + c.x) & 0x3FFFFF) << 42 | ((long) (r[1] + c.y) & 0xFFFFF) << 22 | ((long) (r[2] + c.z) & 0x3FFFFF);
                int rank = ForestPlan.rank(c), sp = t.species().ordinal();
                cells++;
                owners.merge(k, 1, Integer::sum);
                int[] was = fin.get(k);
                if (was == null || ForestPlan.wins(rank, sp, was[0], was[1])) fin.put(k, new int[]{rank, sp, i});
            }
        }
        long lost = 0, shared = 0, hash = 0;
        for (var e : owners.entrySet()) if (e.getValue() > 1) shared++;
        for (int i = 0; i < trees.size(); i++) {
            var t = trees.get(i);
            int[] r = roots.get(i);
            for (var c : TreeShape.layout(t.species(), t.variant(), t.turns()).cells.values()) {
                if (c.kind != TreeShape.FOLIAGE || !c.carries()) continue;
                long k = ((long) (r[0] + c.x) & 0x3FFFFF) << 42 | ((long) (r[1] + c.y) & 0xFFFFF) << 22 | ((long) (r[2] + c.z) & 0x3FFFFF);
                if (fin.get(k)[0] >= 3) lost++;
            }
        }
        // wood is one block whoever wrote it: the species counts only for crown cells
        for (var e : new TreeMap<>(fin).entrySet()) hash = hash * 31 + e.getKey() * 7 + e.getValue()[0] * 3L + (e.getValue()[0] <= 2 ? e.getValue()[1] : 0);
        return new long[]{hash, cells, shared, lost, fin.size()};
    }

    // ------------------------------------------------------------------------------------------- test spots

    static final String[] SHORT = {"wo", "wp", "ro", "hi", "rm", "he"};

    /** The nearest test spot of each scenario (ForestPlan#find, the command's search; rings out to 6 km). */
    static int[][] spots(ForestPlan plan, int cx0, int cz0) {
        int n = ForestPlan.SCENARIOS.length;
        int[][] found = new int[n][];
        for (int k = 0; k < n; k++) {
            var sc = ForestPlan.SCENARIOS[k];
            found[k] = plan.find(k, cx0, cz0, 190);
            if (found[k] == null) { System.out.println("spot " + sc.label() + ": none within 6 km"); require(false, "a " + sc.label() + " spot exists"); continue; }
            int x = found[k][0], z = found[k][1];
            var trees = window(plan, x - 64, z - 64, 128);
            int[] by = new int[Species.values().length];
            for (var t : trees) by[t.species().ordinal()]++;
            var mix = new StringBuilder();
            for (Species sp : Species.values()) if (by[sp.ordinal()] > 0) mix.append(" ").append(SHORT[sp.ordinal()]).append(" ").append(by[sp.ordinal()]);
            int y = plan.top(x, z);
            System.out.printf("spot %-12s %-32s x %d z %d (ground y %d, %.0f m from the centre): biome %s, %d trees in the 128 m square (%s) -> /tp @s %d %d %d%n",
                    sc.name(), sc.label(), x, z, y, Math.hypot(x - cx0, z - cz0), EcologyBiomes.IDS[plan.biome(x, z)], trees.size(), mix.toString().trim(), x, y + 2, z);
        }
        return found;
    }

    /** Map colours per species: white oak, white pine, red oak, hickory, red maple, hemlock. */
    static final Color[] SPECIES_COLOURS = {new Color(58, 112, 34, 170), new Color(20, 80, 64, 170), new Color(140, 70, 30, 170),
            new Color(170, 150, 40, 170), new Color(200, 40, 40, 170), new Color(30, 40, 110, 170)};

    // -------------------------------------------------------------------------------------------------- map

    static void map(ForestPlan plan, int[][] spots, File file) throws Exception {
        int M = 320, S = 2, P = M * S, cols = 5, rows = (spots.length + cols - 1) / cols;
        var img = new BufferedImage(P * cols, (P + 40) * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int k = 0; k < spots.length; k++) {
            if (spots[k] == null) continue;
            int x0 = spots[k][0] - M / 2, z0 = spots[k][1] - M / 2, ox = (k % cols) * P, oy = (k / cols) * (P + 40);
            for (int z = 0; z < M; z++) for (int x = 0; x < M; x++) {
                int b = plan.biome(x0 + x, z0 + z);
                int c = EcologyBiomes.COLOURS[b];
                double h = plan.surface.heightAt(x0 + x, z0 + z);
                double shade = 0.86 + 0.14 * Math.sin(h * 0.9);
                if (plan.surface.waterAt(x0 + x, z0 + z)) c = 0x3F6F9F;
                int rr = (int) ((c >> 16 & 255) * shade * 0.55 + 115), gg = (int) ((c >> 8 & 255) * shade * 0.55 + 115), bb = (int) ((c & 255) * shade * 0.55 + 115);
                g.setColor(new Color(Math.min(255, rr), Math.min(255, gg), Math.min(255, bb)));
                g.fillRect(ox + x * S, oy + z * S, S, S);
            }
            for (var t : window(plan, x0 - 16, z0 - 16, M + 32)) {
                float r = t.radius() * S;
                double px = ox + (t.x() - x0 + 0.5) * S, pz = oy + (t.z() - z0 + 0.5) * S;
                if (px < ox - r || px > ox + P + r) continue;
                g.setClip(ox, oy, P, P);
                g.setColor(SPECIES_COLOURS[t.species().ordinal()]);
                g.fill(new java.awt.geom.Ellipse2D.Double(px - r, pz - r, 2 * r, 2 * r));
                g.setColor(new Color(30, 30, 20));
                g.fill(new java.awt.geom.Ellipse2D.Double(px - 1.5, pz - 1.5, 3, 3));
                g.setClip(null);
            }
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(2));
            g.drawRect(ox, oy, P - 1, P - 1);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            g.drawString(ForestPlan.SCENARIOS[k].label() + "  centre " + spots[k][0] + " " + spots[k][1] + "  (320 m)", ox + 8, oy + P + 26);
        }
        g.dispose();
        ImageIO.write(img, "png", file);
        System.out.println("map: " + file);
    }

    private ForestCheck() {}
}
