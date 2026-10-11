package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.worldgen.roads.construction.RoadConstructionProtection;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Survey and preflight on the server (docs/worldgen/highway_r4_sample_v1.md, F): reads loaded blocks only (never forces a
 * chunk), designs the profile from the measured ground, plans the earthworks and checks every write against the world.
 * The result is a journal: positions with their exact before and after states. Nothing here writes a block.
 */
final class R4Works {
    static final int MAX_TREE_BLOCKS = 6000, HEADROOM = 6, FOUNDATION = 3, MAX_SCAN = 48;

    private R4Works() {}

    /** The measured ground of a rectangle of columns. */
    static final class Survey implements R4Earthworks.Ground {
        final int minX, minZ, w, h;
        final int[] top;
        final byte[] plants;
        final boolean[] grassy;
        final boolean[] tree;
        Survey(int minX, int minZ, int w, int h) {
            this.minX = minX; this.minZ = minZ; this.w = w; this.h = h;
            top = new int[w * h];
            plants = new byte[w * h];
            grassy = new boolean[w * h];
            tree = new boolean[w * h];
            java.util.Arrays.fill(top, Integer.MIN_VALUE);
        }
        private int index(int x, int z) {
            int i = x - minX, j = z - minZ;
            return i < 0 || j < 0 || i >= w || j >= h ? -1 : i * h + j;
        }
        @Override public int top(int x, int z) { int i = index(x, z); return i < 0 ? Integer.MIN_VALUE : top[i]; }
        @Override public int plants(int x, int z) { int i = index(x, z); return i < 0 ? 0 : plants[i]; }
        @Override public boolean grassy(int x, int z) { int i = index(x, z); return i >= 0 && grassy[i]; }
        boolean tree(int x, int z) { int i = index(x, z); return i >= 0 && tree[i]; }
    }

    /** A checked plan: the journal and what it does. */
    record Prepared(R4Recipe recipe, R4Design design, R4Earthworks.Plan plan, long[] pos, BlockState[] before, BlockState[] after,
                    int treeBlocks, List<String> problems, int[] bounds, long checkedAt) {
        boolean ok() { return problems.isEmpty(); }

        /** Fingerprints of the works (positions + written states) and of the originals, for the log. */
        String worksPrint() { return R4Journal.fingerprint(pos, ids(after)); }
        String originalPrint() { return R4Journal.fingerprint(pos, ids(before)); }
    }

    private static int[] ids(BlockState[] states) {
        int[] out = new int[states.length];
        for (int i = 0; i < states.length; i++) out[i] = net.minecraft.world.level.block.Block.getId(states[i]);
        return out;
    }

    static final R4Journal.States<BlockState> STATES = new R4Journal.States<>() {
        @Override public boolean natural(BlockState was, BlockState now) { return R4Materials.naturalChange(was, now); }
        @Override public String name(BlockState s) { return s.toString(); }
    };

    /** How a newer preflight of the same request differs from an older one (R4Journal rules). */
    static R4Journal.Diff diff(Prepared older, Prepared newer, int maxExamples) {
        return R4Journal.diff(older.recipe().version(), older.pos(), older.before(), older.after(), newer.recipe().version(), newer.pos(), newer.before(), newer.after(),
                STATES, maxExamples, p -> BlockPos.of(p).toShortString());
    }

    static Survey survey(ServerLevel level, int minX, int minZ, int maxX, int maxZ, List<String> problems) {
        // one ring more than the planner's bounds: corner means read the neighbouring columns
        Survey s = new Survey(minX - 1, minZ - 1, maxX - minX + 2, maxZ - minZ + 2);
        Set<Long> unloaded = new HashSet<>();
        Map<String, Integer> unknown = new LinkedHashMap<>();
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < s.w; i++) for (int j = 0; j < s.h; j++) {
            int x = s.minX + i, z = s.minZ + j;
            if (!level.hasChunk(x >> 4, z >> 4)) { unloaded.add((long) (x >> 4) << 32 | ((z >> 4) & 0xFFFFFFFFL)); continue; }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, floor = y - MAX_SCAN;
            boolean tree = false;
            String problem = null;
            for (; y > floor && y >= level.getMinBuildHeight(); y--) {
                pos.set(x, y, z);
                BlockState b = level.getBlockState(pos);
                if (!b.getFluidState().isEmpty()) { problem = "WATER_OR_FLUID"; break; }
                if (b.hasBlockEntity()) { problem = "BLOCK_ENTITY " + b.getBlock().getDescriptionId(); break; }
                if (R4Materials.treeLog(b) || R4Materials.naturalLeaves(b)) { tree = true; continue; }
                if (R4Materials.playerLeaves(b)) { problem = "PERSISTENT_LEAVES"; break; }
                if (R4Materials.clearable(b)) continue;
                if (R4Materials.ground(b)) break;
                problem = "UNKNOWN_BLOCK " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b.getBlock());
                break;
            }
            if (problem == null && y <= floor) problem = "NO_GROUND_WITHIN_" + MAX_SCAN;
            if (problem != null) { unknown.merge(problem, 1, Integer::sum); if (unknown.get(problem) == 1) problems.add(problem + " at " + x + "," + y + "," + z); continue; }
            int k = i * s.h + j;
            s.top[k] = y;
            s.grassy[k] = R4Materials.grassy(level.getBlockState(pos.set(x, y, z)));
            s.tree[k] = tree;
            int p = 0;
            while (p < 2 && R4Materials.plant(level.getBlockState(pos.set(x, y + 1 + p, z)))) p++;
            s.plants[k] = (byte) p;
        }
        if (!unloaded.isEmpty()) problems.add(0, "UNLOADED_CHUNKS " + unloaded.size() + "; stand near the segment centre with render distance >= 16 and wait; no chunk is forced");
        unknown.forEach((k, v) -> { if (v > 1) problems.add(k + ": " + v + " columns"); });
        return s;
    }

    static Prepared prepare(ServerLevel level, R4Recipe request, long now) {
        List<String> problems = new ArrayList<>();
        var alignment = request.alignment();
        int n = (int) Math.round(request.length() / R4Profile.DS) + 1;
        var flat = new R4Design(alignment, R4Profile.of(new double[n], request.length()));
        int[] b = R4Earthworks.bounds(flat);
        if (b[2] - b[0] > 1000 || b[3] - b[1] > 1000) throw new IllegalArgumentException("R4_BOUNDS_TOO_LARGE");
        Survey survey = survey(level, b[0], b[1], b[2], b[3], problems);
        if (!problems.isEmpty() && problems.get(0).startsWith("UNLOADED"))
            return new Prepared(request, null, null, new long[0], new BlockState[0], new BlockState[0], 0, problems, b, now);
        var profile = R4Profile.design(R4Earthworks.groundProfile(flat, survey), request.length());
        R4Recipe recipe = request.withProfile(profile.samples()).canonical();
        R4Design design = recipe.design();
        var plan = R4Earthworks.plan(design, survey, b[0], b[1], b[2], b[3]);
        int reported = 0;
        for (var p : plan.problems()) { if (reported++ < 12) problems.add(p.toString()); }
        if (plan.problems().size() > 12) problems.add("... " + (plan.problems().size() - 12) + " more earthworks problems");
        if (profile.maxGrade() > R4Profile.MAX_GRADE + 1e-6) problems.add(String.format(Locale.ROOT, "GRADE %.2f %%", profile.maxGrade() * 100));

        // protection: highway claims, a placed bunker, authoring reservations, vanilla structures, world height
        var area = new BoundsXZ(b[0], b[1], b[2], b[3]);
        var claims = RoadConstructionProtection.query(level, area);
        if (!claims.isEmpty()) problems.add("PROTECTED_AREA " + claims.get(0).id() + (claims.size() > 1 ? " (+" + (claims.size() - 1) + ")" : ""));
        AABB box = new AABB(b[0], level.getMinBuildHeight(), b[1], b[2], level.getMaxBuildHeight(), b[3]);
        if (com.antaurora.apofirstlight.authoring.BuildingAuthoringCommands.overlaps(level.dimension(), box)) problems.add("AUTHORING_RESERVATION");
        for (int cx = b[0] >> 4; cx <= (b[2] - 1) >> 4; cx++) for (int cz = b[1] >> 4; cz <= (b[3] - 1) >> 4; cz++) {
            if (!level.hasChunk(cx, cz)) continue;
            ChunkAccess chunk = level.getChunk(cx, cz);
            if (!chunk.getAllStarts().isEmpty() || !chunk.getAllReferences().isEmpty()) { problems.add("STRUCTURE in chunk " + cx + "," + cz); cx = Integer.MAX_VALUE - 1; break; }
        }

        // the writes, checked one by one against the world
        Map<Long, BlockState> after = new LinkedHashMap<>();
        Map<Long, BlockState> before = new HashMap<>();
        int conflicts = 0;
        var pos = new BlockPos.MutableBlockPos();
        Set<Long> solidNew = new HashSet<>();
        for (var wr : plan.writes()) {
            pos.set(wr.x(), wr.y(), wr.z());
            if (wr.y() < level.getMinBuildHeight() || wr.y() >= level.getMaxBuildHeight()) { problems.add("WORLD_HEIGHT at " + pos.toShortString()); break; }
            BlockState cur = level.getBlockState(pos);
            if (!cur.getFluidState().isEmpty() || cur.hasBlockEntity() || !(R4Materials.ground(cur) || R4Materials.clearable(cur))) {   // hasBlockEntity: Level.getBlockEntity may create one
                if (conflicts++ < 6) problems.add("NOT_REPLACEABLE " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(cur.getBlock()) + " at " + pos.toShortString());
                continue;
            }
            BlockState target = R4Materials.state(wr.kind(), wr.data());
            long key = pos.asLong();
            if (cur.equals(target)) continue;
            before.put(key, cur);
            after.put(key, target);
            if (!target.isAir() && (cur.isAir() || R4Materials.plant(cur))) solidNew.add(key);
        }
        if (conflicts > 6) problems.add("NOT_REPLACEABLE: " + conflicts + " positions");

        // foundation under every column, headroom above it
        Map<Long, int[]> columns = new HashMap<>();
        for (var c : plan.columns()) columns.put((long) c.x() << 32 | (c.z() & 0xFFFFFFFFL), new int[]{c.bottom(), c.surface(), c.naturalTop()});
        int voids = 0, overhangs = 0;
        for (var c : plan.columns()) {
            for (int y = c.bottom() - FOUNDATION; y < c.bottom(); y++) {
                BlockState st = level.getBlockState(pos.set(c.x(), y, c.z()));
                if (!R4Materials.ground(st) || !st.getFluidState().isEmpty()) { if (voids++ < 4) problems.add("SHALLOW_VOID_OR_UNKNOWN_FOUNDATION at " + pos.toShortString()); break; }
            }
            int from = Math.max(c.surface(), c.naturalTop()) + 1, to = c.surface() + HEADROOM;
            for (int y = from; y <= to; y++) {
                BlockState st = level.getBlockState(pos.set(c.x(), y, c.z()));
                if (after.containsKey(pos.asLong()) || R4Materials.clearable(st)) continue;
                if (overhangs++ < 4) problems.add("OVERHANG " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()) + " at " + pos.toShortString());
                break;
            }
        }
        if (voids > 4) problems.add("FOUNDATION problems: " + voids + " columns");
        if (overhangs > 4) problems.add("OVERHANG problems: " + overhangs + " columns");

        // natural trees touching the works: whole trees are cleared first, top-down (and restored last on remove)
        Map<Long, BlockState> treeBefore = new HashMap<>();
        int trees = clearTrees(level, plan, survey, after, treeBefore, problems);

        // nothing living where a block becomes solid
        int entities = 0;
        for (Entity e : level.getEntities((Entity) null, new AABB(b[0], level.getMinBuildHeight(), b[1], b[2], level.getMaxBuildHeight(), b[3]))) {
            AABB eb = e.getBoundingBox();
            boolean hit = false;
            for (int x = (int) Math.floor(eb.minX); x <= (int) Math.floor(eb.maxX) && !hit; x++)
                for (int y = (int) Math.floor(eb.minY); y <= (int) Math.floor(eb.maxY) && !hit; y++)
                    for (int z = (int) Math.floor(eb.minZ); z <= (int) Math.floor(eb.maxZ) && !hit; z++)
                        if (solidNew.contains(BlockPos.asLong(x, y, z))) hit = true;
            if (hit && entities++ < 4) problems.add((e instanceof Player ? "PLAYER" : "ENTITY " + e.getType().toShortString()) + " in the work zone at " + e.blockPosition().toShortString()
                    + (e instanceof Player ? "; fly above the road" : "; move it out of the road"));
        }

        List<Long> treeOrder = new ArrayList<>(treeBefore.keySet());
        // top-down, ties by position: check must reproduce the journal exactly
        treeOrder.sort((p, q) -> BlockPos.getY(p) != BlockPos.getY(q) ? Integer.compare(BlockPos.getY(q), BlockPos.getY(p)) : Long.compare(p, q));
        int total = treeOrder.size() + after.size();
        long[] keys = new long[total];
        BlockState[] bs = new BlockState[total], as = new BlockState[total];
        int k = 0;
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (long p : treeOrder) { keys[k] = p; bs[k] = treeBefore.get(p); as[k] = air; k++; }
        for (var e : after.entrySet()) { keys[k] = e.getKey(); as[k] = e.getValue(); bs[k] = before.get(e.getKey()); k++; }
        if (!R4Journal.unique(keys)) problems.add("JOURNAL_DUPLICATE_POSITION (internal); nothing will be written");
        return new Prepared(recipe, design, plan, keys, bs, as, trees, problems, b, now);
    }

    /**
     * Natural trees with any part inside the works (a modified column, from its ground up to 40 blocks above) are removed
     * whole: logs, natural leaves and vines connected to them (26-neighbourhood). A tree that touches player leaves, a
     * block entity or another block, or that grows into a forest larger than MAX_TREE_BLOCKS, stops the plan.
     */
    private static int clearTrees(ServerLevel level, R4Earthworks.Plan plan, Survey survey, Map<Long, BlockState> after,
                                  Map<Long, BlockState> treeBefore, List<String> problems) {
        var pos = new BlockPos.MutableBlockPos();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        for (var c : plan.columns()) {
            if (!survey.tree(c.x(), c.z())) continue;
            for (int y = c.naturalTop() + 1; y <= c.naturalTop() + 40; y++) {
                BlockState st = level.getBlockState(pos.set(c.x(), y, c.z()));
                if ((R4Materials.treeLog(st) || R4Materials.naturalLeaves(st)) && seen.add(pos.asLong())) queue.add(pos.asLong());
            }
        }
        List<Long> tree = new ArrayList<>();
        while (!queue.isEmpty()) {
            long p = queue.poll();
            tree.add(p);
            if (tree.size() > MAX_TREE_BLOCKS) { problems.add("FOREST_TOO_LARGE (> " + MAX_TREE_BLOCKS + " tree blocks); pick a more open site"); return 0; }
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                pos.set(x + dx, y + dy, z + dz);
                long q = pos.asLong();
                if (seen.contains(q)) continue;
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) { problems.add("TREE_INTO_UNLOADED_CHUNK at " + pos.toShortString()); return 0; }
                BlockState st = level.getBlockState(pos);
                if (R4Materials.treeLog(st) || R4Materials.naturalLeaves(st) || st.is(net.minecraft.world.level.block.Blocks.VINE)) { seen.add(q); queue.add(q); continue; }
                if (R4Materials.playerLeaves(st)) { problems.add("TREE_WITH_PERSISTENT_LEAVES at " + pos.toShortString()); return 0; }
                if (st.hasBlockEntity()) { problems.add("TREE_WITH_BLOCK_ENTITY (bee nest?) at " + pos.toShortString()); return 0; }
            }
        }
        for (long p : tree) {
            if (after.containsKey(p)) continue;   // rewritten by the earthworks themselves
            treeBefore.put(p, level.getBlockState(pos.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))));
        }
        return tree.size();
    }
}
