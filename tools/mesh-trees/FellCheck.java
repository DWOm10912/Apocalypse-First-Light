package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanV2;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Mesh Trees P2 offline felling check (docs/rendering/mesh_trees_p2_felling.md, O). Run by tools/mesh-trees/check.mjs:
 * FellingRules, TreeCut, FellingLedger (pure Java) over every species, variant and turn; with a Terrain V2 plan cache
 * also the shared cells of natural trees. Arguments: [plan cache, seed, centre x, centre z].
 * <ol>
 * <li>Chops per species and size with each axe tier; drops per species.</li>
 * <li>Cut geometry: stump below the cut with a closed cap, the falling tree above it with a cap, the notch of every
 * stage and side closed (each moved point the same in both bands) and inside the trunk.</li>
 * <li>Fall: durations, slow start then speeding up, the rest angle on flat, uphill and downhill ground.</li>
 * <li>Direction: deterministic, never toward the chopper, obstacles avoided.</li>
 * <li>Ledger: two players, a double last chop, chop spam, a reload in the middle, a fall restored twice.</li>
 * <li>Shared cells (plan): felling any natural tree leaves every cell as if it had never been there.</li>
 * </ol>
 */
public final class FellCheck {
    static int failures;

    static void require(boolean ok, String what) {
        if (!ok) { failures++; if (failures < 40) System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        chops();
        geometry();
        falls();
        directions();
        ledger();
        logs();
        if (args.length >= 4) shared(Path.of(args[0]), Long.parseLong(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        else System.out.println("shared cells: no plan cache given, skipped");
        System.out.println(failures == 0 ? "MESH FELLING CHECK OK" : "MESH FELLING CHECK: " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------------------------------------- 1. chops

    static final String[] TOOLS = {"wood", "stone", "iron", "diamond", "netherite", "gold"};
    static final float[] SPEED = {2, 4, 6, 8, 9, 12};

    static void chops() {
        for (Species sp : Species.values()) {
            int[] lo = new int[TOOLS.length], hi = new int[TOOLS.length];
            Arrays.fill(lo, 999);
            int ironSmall = 999, ironSmallMax = 0, ironBig = 0, ironBigMin = 999;
            float logs = 0, branches = 0, leaves = 0;
            int minLogs = 999, maxLogs = 0;
            for (int v = 0; v < TreeShape.VARIANTS; v++) {
                var f = TreeForms.grow(sp, v);
                var l = TreeShape.layout(sp, v, 0);
                for (int k = 0; k < 8; k++) {
                    float cut = FellingRules.cutHeight(sp, k * 37, 64, v * 11);
                    require(cut >= FellingRules.params(sp).cutLow() - 1e-4F && cut <= FellingRules.params(sp).cutHigh() + 1e-4F, sp.id + ": cut height in range");
                    float need = FellingRules.work(sp, FellingRules.trunkRadius(f, cut));
                    for (int t = 0; t < TOOLS.length; t++) {
                        int n = FellingRules.chops(need, FellingRules.power(SPEED[t], 0));
                        lo[t] = Math.min(lo[t], n); hi[t] = Math.max(hi[t], n);
                    }
                    int iron = FellingRules.chops(need, FellingRules.power(6, 0));
                    require(FellingRules.chops(need, FellingRules.power(2, 0)) > iron && FellingRules.chops(need, FellingRules.power(12, 0)) < iron, sp.id + " " + v + ": a wooden axe needs more chops than iron, gold fewer");
                    // the size of a variant: its trunk radius at 1.3 m (breast height)
                    float dbh = FellingRules.trunkRadius(f, 1.3F) * 2;
                    boolean small = sp == Species.WHITE_OAK && dbh < 0.80F;
                    if (small) { ironSmall = Math.min(ironSmall, iron); ironSmallMax = Math.max(ironSmallMax, iron); }
                    else { ironBig = Math.max(ironBig, iron); ironBigMin = Math.min(ironBigMin, iron); }
                }
                var d = FellingRules.drops(sp, l.trunkVolume, l.limbVolume, l.twigVolume, l.crownRadius);
                logs += d.logs(); branches += d.branches(); leaves += d.leaves();
                minLogs = Math.min(minLogs, d.logs()); maxLogs = Math.max(maxLogs, d.logs());
            }
            var sb = new StringBuilder();
            for (int t = 0; t < TOOLS.length; t++) sb.append(String.format(" %s %d-%d", TOOLS[t], lo[t], hi[t]));
            System.out.printf("%s chops:%s%n", sp.id, sb);
            if (sp == Species.WHITE_OAK) {
                // the chop count follows the cut's cross-section (variant size and the cut height on the flare)
                int all0 = Math.min(ironSmall, ironBigMin), all1 = Math.max(ironSmallMax, ironBig);
                System.out.printf("  iron axe: %d-%d chops (slimmer oaks, dbh under 0.80 m: %d-%d; the rest %d-%d)%n", all0, all1, ironSmall, ironSmallMax, ironBigMin, ironBig);
                require(all0 >= 6 && all0 <= 8, "the smallest white oak cuts: 6-8 chops with an iron axe");
                require(all1 >= 10 && all1 <= 14, "the biggest white oak cuts: 10-14 chops with an iron axe");
            } else {
                System.out.printf("  iron axe: %d-%d chops%n", ironBigMin, ironBig);
                require(ironBigMin >= 7 && ironBig <= 13, "white pine: 8-12 chops with an iron axe (about)");
            }
            System.out.printf("  drops per tree (16 variants): logs %d-%d (mean %.1f), branches mean %.1f, leaf items mean %.1f%n",
                    minLogs, maxLogs, logs / 16, branches / 16, leaves / 16);
        }
        var oak = TreeShape.layout(Species.WHITE_OAK, 0, 0);
        var pine = TreeShape.layout(Species.WHITE_PINE, 0, 0);
        var a = FellingRules.drops(Species.WHITE_OAK, oak.trunkVolume, oak.limbVolume, oak.twigVolume, oak.crownRadius);
        var b = FellingRules.drops(Species.WHITE_PINE, pine.trunkVolume, pine.limbVolume, pine.twigVolume, pine.crownRadius);
        require(a.logs() != b.logs(), "white oak and white pine give different log counts");
        // stages
        require(FellingRules.stage(0, 10) == 0 && FellingRules.stage(2.1F, 10) == 1 && FellingRules.stage(8.1F, 10) == 4 && FellingRules.stage(9.99F, 10) == 4, "stages at 20/40/60/80 %");
    }

    // ------------------------------------------------------------------------------------------- 2. geometry

    static List<TreeShape.Quad> lod(TreeShape.Layout l, int level) {
        var out = new ArrayList<TreeShape.Quad>();
        for (var c : TreeShape.ordered(l)) out.addAll(c.quads[level]);
        return out;
    }

    static void geometry() {
        int stumps = 0, notches = 0, falls = 0, maxTris = 0;
        for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) for (int t = 0; t < 4; t++) {
            var l = TreeShape.layout(sp, v, t);
            String id = sp.id + " " + v + " turn " + t;
            var root = l.cell(0, 0, 0).quads[0];
            float cut = FellingRules.cutHeight(sp, v, 70, t * 5);
            // stump: nothing above the cut, a closed cap
            var stump = TreeCut.stump(root, cut, sp.ordinal());
            int caps = 0;
            for (var q : stump) {
                for (int c = 0; c < 4; c++) require(q.p[c * 3 + 1] <= cut + 1e-4F && Float.isFinite(q.p[c * 3]), id + ": stump below the cut");
                if (q.sprite == TreeCut.END_SPRITE + sp.ordinal()) caps++;
            }
            require(caps >= 6, id + ": the stump has a cap (" + caps + " fan triangles)");
            require(closed(stump, TreeCut.END_SPRITE + sp.ordinal()), id + ": the stump cap is one closed ring");
            for (var q : stump) for (int c = 0; c < 8; c++) require(q.uv[c] >= -1e-4F && q.uv[c] <= 1 + 1e-4F, id + ": stump UV inside the sprite");
            stumps++;
            // falling tree, every level: no trunk below the cut, a cap under it
            for (int level = 0; level < TreeShape.LODS; level++) {
                var all = lod(l, level);
                var fallen = TreeCut.fallen(all, cut, sp.ordinal());
                int under = 0, tris = 0;
                for (var q : fallen) {
                    if (q.kind == TreeShape.WOOD && q.sprite != TreeCut.END_SPRITE + sp.ordinal())
                        for (int c = 0; c < 4; c++) if (q.p[c * 3 + 1] < cut - 1e-4F) under++;
                    tris += 2 * (q.twoSided ? 2 : 1);
                }
                require(under == 0, id + " LOD" + level + ": no falling trunk below the cut (" + under + ")");
                require(closed(fallen, TreeCut.END_SPRITE + sp.ordinal()), id + " LOD" + level + ": the falling log's cap is a closed ring");
                maxTris = Math.max(maxTris, tris);
                falls++;
            }
            // notch: every stage on two sides
            float[] ring = TreeCut.ring(root, cut);
            require(ring != null, id + ": the trunk crosses the cut in the root cell");
            if (ring == null) continue;
            for (int stage = 1; stage <= FellingRules.STAGES; stage++) for (int side : new int[]{v % 8, (v + 3) % 8}) {
                var n = TreeCut.notch(root, cut, stage, FellingRules.sideAngle(side), sp.ordinal());
                // closed: every corner of the new cut faces either sits on another face's corner (shared edge) or on the bark
                var corners = new HashMap<String, Integer>();
                float deepest = 0;
                for (var q : n) {
                    if (q.sprite != TreeCut.CUT_SPRITE + sp.ordinal()) continue;
                    for (int c = 0; c < 4; c++) {
                        corners.merge(key(q, c), 1, Integer::sum);
                        float d = (float) Math.hypot(q.p[c * 3] - ring[0], q.p[c * 3 + 2] - ring[1]);
                        require(d <= ring[2] * 1.35F + 0.02F, id + ": notch inside the trunk");
                        deepest = Math.max(deepest, ring[2] - d);
                    }
                }
                int lonely = 0;
                for (var q : n) if (q.sprite == TreeCut.CUT_SPRITE + sp.ordinal())
                    for (int c : new int[]{2, 3}) if (q.p[c * 3 + 1] < cut + 1e-4F && q.p[c * 3 + 1] > cut - 1e-4F && corners.get(key(q, c)) < 2) lonely++;
                require(lonely == 0, id + " stage " + stage + ": the notch's inner edge is shared by its neighbours (" + lonely + " open)");
                // P2.1: every cut-wood UV inside the sprite (outside it, the atlas neighbour would show)
                for (var q : n) if (q.sprite == TreeCut.CUT_SPRITE + sp.ordinal())
                    for (int c = 0; c < 8; c++) require(q.uv[c] >= 0 && q.uv[c] <= 1, id + " stage " + stage + ": notch UV inside the sprite");
                require(deepest <= ring[2] * (TreeCut.DEPTH[stage - 1] + 0.05F), id + " stage " + stage + ": depth as specified");
                require(deepest >= ring[2] * TreeCut.DEPTH[stage - 1] * 0.6F, id + " stage " + stage + ": a visible notch (" + deepest + ")");
                notches++;
            }
        }
        System.out.printf("geometry: %d stumps with closed caps, %d falling meshes (largest %d triangles), %d notches closed and inside the trunk%n", stumps, falls, maxTris, notches);
    }

    static String key(TreeShape.Quad q, int c) {
        return Math.round(q.p[c * 3] * 1e4) + "," + Math.round(q.p[c * 3 + 1] * 1e4) + "," + Math.round(q.p[c * 3 + 2] * 1e4);
    }

    /** The cap's outer edges (its triangles' A-B sides) form closed loops: every outer point is used by two triangles. */
    static boolean closed(List<TreeShape.Quad> quads, int sprite) {
        var count = new HashMap<String, Integer>();
        int n = 0;
        for (var q : quads) {
            if (q.sprite != sprite) continue;
            n++;
            count.merge(key(q, 1), 1, Integer::sum);
            count.merge(key(q, 2), 1, Integer::sum);
        }
        if (n < 3) return false;
        for (int c : count.values()) if (c != 2) return false;
        return true;
    }

    // ------------------------------------------------------------------------------------------------ 3. falls

    static void falls() {
        float[] durOak = {99, 0}, durPine = {99, 0};
        for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) {
            var f = TreeForms.grow(sp, v);
            float cut = 0.6F;
            var prof = FellingRules.profile(f, cut, sp);
            float flat = FellingRules.restAngle(prof, d -> -cut);
            float uphill = FellingRules.restAngle(prof, d -> -cut + 0.35 * d);
            float down = FellingRules.restAngle(prof, d -> -cut - 0.35 * d);
            require(flat > 68 && flat <= 92, sp.id + " " + v + ": rests near level on flat ground (" + flat + ")");
            require(uphill < flat - 5 && down > flat + 5, sp.id + " " + v + ": uphill rests higher, downhill lower (" + uphill + " / " + flat + " / " + down + ")");
            for (float rest : new float[]{uphill, flat, down}) {
                var c = FellingRules.curve(sp, f.crownTop, cut, rest);
                float sec = c.contact() / 20F;
                float[] d = sp == Species.WHITE_OAK ? durOak : durPine;
                if (rest == flat) { d[0] = Math.min(d[0], sec); d[1] = Math.max(d[1], sec); }
                require(sec >= 1.2F && sec <= 3.6F, sp.id + " " + v + ": falls in 1.2-3.6 s (" + sec + ")");
                // slow start, speeding up until the contact, the rest after it
                float last = -1;
                boolean faster = true;
                for (int i = 2; i <= c.contact(); i++) {
                    float w = c.angle()[i] - c.angle()[i - 1];
                    if (i < c.contact() && w < last - 1e-3F) faster = false;
                    last = w;
                }
                require(faster, sp.id + " " + v + ": the fall gathers speed");
                require(c.angle()[1] < 1.0F, sp.id + " " + v + ": a slow first tick (" + c.angle()[1] + " degrees)");
                require(Math.abs(c.at(c.end() + 5) - rest) < 1e-4F, sp.id + " " + v + ": ends at the rest angle");
                float minAfter = rest;
                for (int i = c.contact(); i <= c.end(); i++) minAfter = Math.min(minAfter, c.angle()[i]);
                require(rest - minAfter <= 3.01F, sp.id + " " + v + ": a small rebound");
            }
        }
        System.out.printf("falls: white oak %.2f-%.2f s, white pine %.2f-%.2f s to the ground on flat land (pause %.2f s before, lying %.2f s after)%n",
                durOak[0], durOak[1], durPine[0], durPine[1], FellingRules.PAUSE_TICKS / 20F, FellingRules.LIE_TICKS / 20F);
        require(durPine[0] > durOak[0], "the taller white pine takes longer to fall");
    }

    // ------------------------------------------------------------------------------------------- 4. directions

    static void directions() {
        var r = new Random(11);
        int toward = 0, unstable = 0;
        for (int i = 0; i < 2000; i++) {
            double look = r.nextDouble() * 6.283, chopper = look + Math.PI + (r.nextDouble() - 0.5) * 1.2;
            double lean = r.nextDouble() * 6.283, w = r.nextDouble(), jitter = (r.nextDouble() - 0.5) * 0.25;
            double blocked = r.nextDouble() * 6.283;
            FellingRules.Scorer s = a -> Math.abs(Math.atan2(Math.sin(a - blocked), Math.cos(a - blocked))) < 0.4 ? 10 : 0;
            double d1 = FellingRules.direction(look, chopper, lean, w, jitter, s), d2 = FellingRules.direction(look, chopper, lean, w, jitter, s);
            if (d1 != d2) unstable++;
            if (Math.abs(Math.atan2(Math.sin(d1 - chopper), Math.cos(d1 - chopper))) < Math.toRadians(100) - 1e-6) toward++;
        }
        require(unstable == 0, "direction is deterministic");
        require(toward == 0, "never toward the chopper (" + toward + ")");
        // an obstacle straight ahead turns the fall aside
        double d = FellingRules.direction(0, Math.PI, 0, 0, 0, a -> Math.abs(a) < 0.3 ? 10 : 0);
        require(Math.abs(d) >= 0.25 && Math.abs(d) <= Math.toRadians(46), "an obstacle ahead turns the fall by 15-45 degrees (" + Math.toDegrees(d) + ")");
        System.out.println("directions: 2000 random cases deterministic, none within 100 degrees of the chopper; an obstacle ahead turns the fall " + Math.round(Math.toDegrees(d)) + " degrees");
    }

    // ----------------------------------------------------------------------------------------------- 5. ledger

    static void ledger() {
        var L = new FellingLedger();
        UUID a = new UUID(1, 1), b = new UUID(2, 2);
        long root = 12345L;
        float need = 10;
        long tick = 1000;
        // chop spam: one counts
        int counted = 0;
        for (int i = 0; i < 8; i++) if (L.chop(root, a, tick + i, 1, need).result() != FellingLedger.Result.COOLDOWN) counted++;
        require(counted == 1, "chops closer than the minimum are ignored (" + counted + " counted)");
        // two players alternate on the same tree: one shared progress
        tick += 20;
        for (int i = 0; i < 4; i++) { L.chop(root, a, tick + i * 10, 1, need); L.chop(root, b, tick + i * 10 + 5, 1, need); }
        require(Math.abs(L.work.get(root) - 9) < 1e-4F, "two players add to one progress (" + L.work.get(root) + ")");
        // reload in the middle: the saved work comes back (the per-player cooldown does not need to)
        var saved = new HashMap<>(L.work);
        var L2 = new FellingLedger();
        L2.work.putAll(saved);
        // a double last chop in one tick: the server handles them one after the other; the first fells, the tree is gone
        boolean[] standing = {true};
        int fells = 0;
        for (UUID p : new UUID[]{a, b}) {
            if (!standing[0]) continue;                        // FellingServer: no root here any more
            var c = L2.chop(root, p, tick + 100, 1, need);
            if (c.result() == FellingLedger.Result.FELL) { fells++; standing[0] = false; L2.fell(root, new UUID(9, 9)); }
        }
        require(fells == 1, "a double last chop fells once (" + fells + ")");
        require(!L2.work.containsKey(root), "the felled tree's work is gone");
        // the fall settles once, even if its entity is restored twice; settled ids are dropped after a day
        UUID fall = new UUID(9, 9);
        require(L2.settle(fall, tick + 200) && !L2.settle(fall, tick + 220), "a fall settles once");
        require(L2.isSettled(fall) && !L2.falling.containsKey(fall), "settled, no longer falling");
        L2.settle(new UUID(7, 7), tick + 200 + FellingLedger.KEEP_SETTLED + 10);
        require(!L2.isSettled(fall), "settled ids are pruned after " + FellingLedger.KEEP_SETTLED + " ticks");
        System.out.println("ledger: spam ignored, two players share one progress, a reload keeps it, a double last chop fells once, a fall settles once");
    }

    // ------------------------------------------------------------------------------------------- 5b. log blocks

    /**
     * P2.2 log blocks (LogShape), every species and axis: inside the block; culled parts on their face plane and, when
     * rectangles, in vanilla's corner order; facing outward; UVs in the sprite; one closed surface (every edge shared by
     * two quads); the end caps cover the rounded square's area; every part either culled or a corner strip that faces
     * the two sides it takes its light from (LogShape.cornerSides).
     */
    /** Direction ordinals (DOWN, UP, NORTH, SOUTH, WEST, EAST) as unit vectors. */
    static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    static void logs() {
        int parts = 0;
        for (Species sp : Species.values()) for (int axis = 0; axis < 3; axis++) {
            var list = LogShape.vanillaOrder(LogShape.axis(LogShape.build(sp), axis));
            String id = sp.id + " log axis " + "XYZ".charAt(axis);
            var edges = new HashMap<String, Integer>();
            double capArea = 0;
            for (var part : list) {
                var q = part.quad();
                parts++;
                float cx = 0, cy = 0, cz = 0;
                for (int c = 0; c < 4; c++) {
                    for (int k = 0; k < 3; k++) require(q.p[c * 3 + k] >= -1e-5F && q.p[c * 3 + k] <= 1 + 1e-5F, id + ": inside the block");
                    require(q.uv[c * 2] >= -1e-5F && q.uv[c * 2] <= 1 + 1e-5F && q.uv[c * 2 + 1] >= -1e-5F && q.uv[c * 2 + 1] <= 1 + 1e-5F, id + ": UV in the sprite");
                    cx += q.p[c * 3] / 4; cy += q.p[c * 3 + 1] / 4; cz += q.p[c * 3 + 2] / 4;
                }
                // outward: the normal points away from the block's centre
                float nx = (q.n[0] + q.n[3] + q.n[6] + q.n[9]) / 4, ny = (q.n[1] + q.n[4] + q.n[7] + q.n[10]) / 4, nz = (q.n[2] + q.n[5] + q.n[8] + q.n[11]) / 4;
                require(nx * (cx - 0.5F) + ny * (cy - 0.5F) + nz * (cz - 0.5F) > 0, id + ": faces outward");
                // winding by the diagonals (robust for a fan triangle with its repeated corner)
                float[] e1 = {q.p[6] - q.p[0], q.p[7] - q.p[1], q.p[8] - q.p[2]}, e2 = {q.p[9] - q.p[3], q.p[10] - q.p[4], q.p[11] - q.p[5]};
                float[] g = TreeShape.cross(e1, e2);
                require(g[0] * nx + g[1] * ny + g[2] * nz > 0, id + ": wound counter-clockwise from outside");
                if (part.cull() != LogShape.NONE) {
                    int d = part.cull(), ax = d < 2 ? 1 : d < 4 ? 2 : 0;
                    float plane = d % 2 == 1 ? 1 : 0;
                    for (int c = 0; c < 4; c++) require(Math.abs(q.p[c * 3 + ax] - plane) < 1e-5F, id + ": a culled part lies on its face");
                    if (LogShape.rectangle(q)) {
                        float[] lo = {9, 9, 9}, hi = {-9, -9, -9};
                        for (int c = 0; c < 4; c++) for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], q.p[c * 3 + k]); hi[k] = Math.max(hi[k], q.p[c * 3 + k]); }
                        for (int c = 0; c < 4; c++) for (int k = 0; k < 3; k++) {
                            int want = LogShape.FACE_INFO[d][c][k];
                            if (want >= 0) require(Math.abs(q.p[c * 3 + k] - (want == 1 ? hi[k] : lo[k])) < 1e-5F, id + ": rectangle in vanilla corner order");
                        }
                    }
                    // the two end planes' area (along the axis)
                    if (ax == axis) capArea += Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]) / 2;
                } else {
                    require(part.corner() >= 0, id + ": an unculled part is a corner strip");
                    if (part.corner() >= 0) for (int side : LogShape.cornerSides(part.corner(), axis)) {
                        var dir = DIRS[side];
                        require(nx * dir[0] + ny * dir[1] + nz * dir[2] > 0.1F, id + ": a corner strip faces the sides that light it");
                        require(dir[axis] == 0, id + ": a corner's light sides lie across the log");
                    }
                }
                for (int c = 0; c < 4; c++) {
                    String a = key(q, c), b = key(q, (c + 1) % 4);
                    if (a.equals(b)) continue;
                    String e = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
                    edges.merge(e, 1, Integer::sum);
                }
            }
            int open = 0;
            for (int c : edges.values()) if (c != 2) open++;
            require(open == 0, id + ": one closed surface (" + open + " edges not shared by two quads)");
            float r = LogShape.radius(sp);
            double want = 2 * (1 - (4 - Math.PI) * r * r);
            require(Math.abs(capArea - want) < 0.02, id + ": the end caps cover the rounded square (" + capArea + " vs " + want + ")");
        }
        System.out.printf("log blocks: 2 species x 3 axes, %d parts: closed, outward, culled faces on their planes in vanilla order, end caps %.3f (oak) / %.3f (pine) of the square%n",
                parts, 1 - (4 - Math.PI) * LogShape.radius(Species.WHITE_OAK) * LogShape.radius(Species.WHITE_OAK),
                1 - (4 - Math.PI) * LogShape.radius(Species.WHITE_PINE) * LogShape.radius(Species.WHITE_PINE));
    }

    // ------------------------------------------------------------------------------------------ 6. shared cells

    record Placed(ForestPlan.Tree t, int y) {}

    static void shared(Path cache, long seed, int x0, int z0) throws Exception {
        TerrainPlanSurface s;
        try (var in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(cache)), 1 << 16))) {
            s = TerrainPlanSurface.read(in, seed, TerrainPlanV2.VERSION);
        }
        if (s == null) { require(false, "plan cache readable"); return; }
        var plan = new ForestPlan(s, seed);
        int checked = 0, sharedCells = 0, crossChunk = 0;
        for (int k = 0; k < 3; k++) {
            int[] spot = plan.find(k, x0, z0, 190);
            if (spot == null) continue;
            var placed = new ArrayList<Placed>();
            for (var t : plan.window(spot[0] - 48, spot[1] - 48, 96)) placed.add(new Placed(t, plan.top(t.x(), t.z())));
            var all = merge(placed, -1);
            for (int fell = 0; fell < placed.size(); fell++) {
                var p = placed.get(fell);
                var rest = new ArrayList<>(placed);
                rest.remove(fell);
                var expected = merge(rest, -1);
                // what the server does: every cell of the felled tree keeps the best of the others there, or empties
                var after = new HashMap<>(all);
                var l = TreeShape.layout(p.t().species(), p.t().variant(), p.t().turns());
                boolean crosses = false;
                for (var c : l.cells.values()) {
                    long key = pack(p.t().x() + c.x, p.y() + c.y, p.t().z() + c.z);
                    if (((p.t().x() + c.x) >> 4) != (p.t().x() >> 4) || ((p.t().z() + c.z) >> 4) != (p.t().z() >> 4)) crosses = true;
                    int[] rank = new int[placed.size()], sp = new int[placed.size()];
                    int n = 0;
                    for (var o : rest) {
                        var oc = TreeShape.layout(o.t().species(), o.t().variant(), o.t().turns()).cell(p.t().x() + c.x - o.t().x(), p.y() + c.y - o.y(), p.t().z() + c.z - o.t().z());
                        if (oc == null) continue;
                        rank[n] = ForestPlan.rank(oc); sp[n] = o.t().species().ordinal(); n++;
                    }
                    int keep = FellingRules.keeper(rank, sp, n);
                    if (n > 0) sharedCells++;
                    if (keep < 0) after.remove(key);
                    else after.put(key, rank[keep] * 4 + (rank[keep] <= 2 ? sp[keep] : 0));
                }
                if (crosses) crossChunk++;
                require(after.equals(expected), "felling a tree leaves the others' cells as if it had never been there (" + p.t() + ")");
                checked++;
            }
        }
        System.out.printf("shared cells: %d natural trees felled one at a time (%d reach into another chunk), %d of their cells shared, every cell ends as if the tree had never been there%n",
                checked, crossChunk, sharedCells);
    }

    static long pack(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | ((long) z & 0x3FFFFF); }

    /** rank * 4 + species (wood: species 0) per cell, by the rank maximum. */
    static Map<Long, Integer> merge(List<Placed> trees, int skip) {
        var best = new HashMap<Long, int[]>();
        for (int i = 0; i < trees.size(); i++) {
            if (i == skip) continue;
            var p = trees.get(i);
            for (var c : TreeShape.layout(p.t().species(), p.t().variant(), p.t().turns()).cells.values()) {
                long k = pack(p.t().x() + c.x, p.y() + c.y, p.t().z() + c.z);
                int rank = ForestPlan.rank(c), sp = p.t().species().ordinal();
                int[] was = best.get(k);
                if (was == null || ForestPlan.wins(rank, sp, was[0], was[1])) best.put(k, new int[]{rank, sp});
            }
        }
        var out = new HashMap<Long, Integer>();
        best.forEach((k, v) -> out.put(k, v[0] * 4 + (v[0] <= 2 ? v[1] : 0)));
        return out;
    }

    private FellCheck() {}
}
