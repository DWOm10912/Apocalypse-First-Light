package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.tree.TreeForms.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Mesh Trees client side (docs/rendering/mesh_trees_p0a.md B/J, mesh_trees_p0b.md D): the client's tree index
 * ({@link TreeIndex#CLIENT}, read by the chunk builder and by collisions), AFL's own distance levels, the status.
 * <ul>
 * <li>Index: the roots of every chunk that arrives, and the server's root messages. A cell of a tree whose root chunk is
 * not here yet draws nothing until that chunk arrives and marks the tree's sections for a rebuild.</li>
 * <li>Levels: each tree has one stage for all its cells (no seams inside a crown), from the horizontal camera distance
 * to the root (docs/rendering/mesh_trees_p4_formal.md, LOD transition): LOD0 under {@value #NEAR_FROM} blocks, LOD1 from
 * {@value #NEAR_TO} to {@value #MID_FROM}, LOD2 from {@value #MID_TO}. In each band between, {@value #STEPS} - 1 blend stages:
 * a growing share of the leaf cards (a fixed random order) comes from the next level, the wood switches halfway, so a
 * tree changes in small steps instead of all at once. {@value #HYSTERESIS} blocks of hysteresis on every stage edge. A
 * change marks the tree's sections for a rebuild; the shadow pass draws the same section meshes.</li>
 * <li>Batching (P1, docs/rendering/mesh_trees_p1_forest.md G): level changes wait up to {@value #FLUSH_TICKS} ticks and are
 * applied together (a tree that turns back inside the window never rebuilds); every rebuild mark of a tick goes through
 * one set, so a section that several trees reach is marked once. In a forest most sections hold several trees.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class TreeClient {
    static final int NEAR_FROM = 76, NEAR_TO = 100, MID_FROM = 160, MID_TO = 196, HYSTERESIS = 3;
    /** Stages per level: stage = level * STEPS + blend step; STAGES stages from LOD0 to LOD2. */
    static final int STEPS = 4, STAGES = (TreeShape.LODS - 1) * STEPS + 1;
    /** Where stage k turns into k + 1: STEPS edges evenly across each band. */
    private static final float[] EDGES = edges();
    static final int PROXY = TreeShape.LODS;

    /** -1: distance levels; 0..2: one level for every tree; PROXY: the voxel proxy (what Voxy and maps see). */
    static volatile int override = -1;
    static final LongAdder lookups = new LongAdder(), lookupNanos = new LongAdder(), carriers = new LongAdder(), orphans = new LongAdder(),
            quadsOut = new LongAdder();
    static long marks, marksWindowStart, marksWindow;
    static final int[] levelChanges = new int[STAGES];
    static final int FLUSH_TICKS = 10;
    /** Sections to mark this tick (deduplicated); trees whose new level waits for the next flush. Client thread only. */
    private static final LongOpenHashSet DIRTY = new LongOpenHashSet();
    private static final Map<TreeIndex.Root, Integer> PENDING = new HashMap<>();
    static long flushes, flushedTrees, flushedSections;
    static int ticks, lastTrees, lastSections, maxSections;

    private TreeClient() {}

    /** The stage a root's cells draw now (level * STEPS + blend step). */
    static int stageOf(TreeIndex.Root r) {
        int o = override;
        if (o >= 0) return o * STEPS;
        int l = r.level;
        return l < 0 ? 0 : l;
    }

    private static void added(TreeIndex.Root r) {
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            var cam = mc.gameRenderer.getMainCamera().getPosition();
            r.level = stage(Math.hypot(r.x + 0.5 - cam.x, r.z + 0.5 - cam.z), -1);
        }
        dirty(r);
    }

    /** Queues every section a tree reaches for a rebuild this tick (sections that are not loaded are ignored by the renderer). */
    static void dirty(TreeIndex.Root r) {
        for (int sx = r.minX >> 4; sx <= r.maxX >> 4; sx++)
            for (int sy = r.minY >> 4; sy <= r.maxY >> 4; sy++)
                for (int sz = r.minZ >> 4; sz <= r.maxZ >> 4; sz++) DIRTY.add(SectionPos.asLong(sx, sy, sz));
    }

    /** Marks the queued sections, each once. */
    private static void markQueued() {
        if (DIRTY.isEmpty()) return;
        var renderer = Minecraft.getInstance().levelRenderer;
        if (renderer != null) {
            var it = DIRTY.iterator();
            while (it.hasNext()) {
                long k = it.nextLong();
                renderer.setSectionDirty(SectionPos.x(k), SectionPos.y(k), SectionPos.z(k));
                marks++;
            }
        }
        DIRTY.clear();
    }

    static void dirtyAll() {
        for (TreeIndex.Root[] list : TreeIndex.CLIENT.all()) for (var r : list) dirty(r);
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!TreeBlocks.ready() || !(event.getLevel() instanceof ClientLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var r : TreeIndex.CLIENT.load(chunk)) added(r);
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) TreeIndex.CLIENT.unload(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TreeIndex.CLIENT.clear();
        override = -1;
        DIRTY.clear();
        PENDING.clear();
    }

    /** The server's messages (client thread). */
    static void handle(TreeNetwork.Msg m) {
        switch (m.action()) {
            case TreeNetwork.ROOT -> {
                BlockPos pos = BlockPos.of(m.pos());
                BlockState now = Block.stateById(m.value());
                var gone = TreeIndex.CLIENT.remove(pos);
                if (gone != null) dirty(gone);
                if (TreeIndex.isRoot(now)) added(TreeIndex.CLIENT.add(pos, now));
            }
            case TreeNetwork.LOD -> {
                override = m.value();
                dirtyAll();
                say("Mesh tree LOD: " + (m.value() < 0 ? "auto (distance)" : m.value() == PROXY ? "voxel proxy (what Voxy and maps see)" : "LOD" + m.value() + " for every tree"));
            }
            case TreeNetwork.STATUS -> status();
            default -> {}
        }
    }

    // ------------------------------------------------------------------------------------------------- levels

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !TreeBlocks.ready()) return;
        var mc = Minecraft.getInstance();
        TreeShaderIds.tick();
        if (mc.level == null || mc.gameRenderer == null) return;
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        for (TreeIndex.Root[] list : TreeIndex.CLIENT.all()) for (var r : list) {
            double d = Math.hypot(r.x + 0.5 - cam.x, r.z + 0.5 - cam.z);
            int old = r.level, now = stage(d, old);
            if (now == old) { if (!PENDING.isEmpty()) PENDING.remove(r); continue; }
            if (old < 0) {
                // a root without a level yet was meshed as LOD0
                r.level = now;
                if (override < 0 && now != 0) dirty(r);
                continue;
            }
            PENDING.put(r, now);
        }
        if (++ticks % FLUSH_TICKS == 0 && !PENDING.isEmpty()) {
            int before = DIRTY.size();
            for (var e : PENDING.entrySet()) {
                var r = e.getKey();
                r.level = e.getValue();
                levelChanges[r.level]++;
                if (override < 0) dirty(r);
            }
            lastTrees = PENDING.size();
            lastSections = DIRTY.size() - before;
            maxSections = Math.max(maxSections, lastSections);
            flushes++;
            flushedTrees += lastTrees;
            flushedSections += lastSections;
            PENDING.clear();
        }
        markQueued();
        long t = System.nanoTime();
        if (t - marksWindowStart > 10_000_000_000L) { marksWindow = marks; marksWindowStart = t; }
    }

    private static float[] edges() {
        float[] e = new float[STAGES - 1];
        int[][] bands = {{NEAR_FROM, NEAR_TO}, {MID_FROM, MID_TO}};
        for (int b = 0; b < bands.length; b++)
            for (int k = 0; k < STEPS; k++) e[b * STEPS + k] = bands[b][0] + (bands[b][1] - bands[b][0]) * k / (float) (STEPS - 1);
        return e;
    }

    /** Distance stage with hysteresis (old -1: none yet): the old stage holds until the camera is HYSTERESIS past its edges. */
    static int stage(double d, int old) {
        if (old >= 0 && old < STAGES && (old == 0 || d >= EDGES[old - 1] - HYSTERESIS) && (old == STAGES - 1 || d < EDGES[old] + HYSTERESIS))
            return old;
        int s = 0;
        while (s < STAGES - 1 && d >= EDGES[s]) s++;
        return s;
    }

    /** The plain level nearest a distance (a falling tree's frozen mesh). */
    static int level(double d) {
        return (stage(d, -1) + STEPS / 2) / STEPS;
    }

    // ------------------------------------------------------------------------------------------------- status

    static void say(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal(s), false);
    }

    private static void status() {
        int[] at = new int[TreeShape.LODS];
        int blending = 0;
        for (TreeIndex.Root[] list : TreeIndex.CLIENT.all()) for (var r : list) {
            int s = Math.max(0, r.level);
            at[s / STEPS]++;
            if (s % STEPS != 0) blending++;
        }
        say(String.format("Mesh tree (client): %d roots indexed in %d chunks; levels now LOD0 %d / LOD1 %d / LOD2 %d (%d of them blending); view %s; blends %d-%d / %d-%d blocks in %d steps, hysteresis %d",
                TreeIndex.CLIENT.size(), TreeIndex.CLIENT.chunks(), at[0], at[1], at[2], blending,
                override < 0 ? "auto" : override == PROXY ? "proxy" : "LOD" + override, NEAR_FROM, NEAR_TO, MID_FROM, MID_TO, STEPS, HYSTERESIS));
        for (Species sp : Species.values()) {
            var l = TreeShape.layout(sp, 0, 0);
            say(String.format("  %s v0: %.1f m, crown %.1f m, %d cells (wood %d, %d collide; crown %d, %d carry leaves); triangles LOD0 %d, LOD1 %d, LOD2 %d",
                    sp.id, l.height, 2 * l.crownRadius, l.cells.size(), l.count(TreeShape.WOOD), l.solidCells, l.count(TreeShape.FOLIAGE),
                    l.carriers(TreeShape.FOLIAGE), l.triangles[0], l.triangles[1], l.triangles[2]));
        }
        long n = lookups.sum();
        say(String.format("  chunk builds: %d cell lookups, %.2f us each, %d found a tree, %d orphans (root chunk not here), %d quads emitted",
                n, n == 0 ? 0 : lookupNanos.sum() / 1000.0 / n, carriers.sum(), orphans.sum(), quadsOut.sum()));
        say(String.format("  rebuild marks: %d total, %d since the last 10 s mark; stage changes (LOD0, blends, LOD1, blends, LOD2): %s; baked caches %d",
                marks, marks - marksWindow, Arrays.toString(levelChanges), TreeCellModel.cacheSize()));
        say(String.format("  level batches (every %d ticks): %d, %d trees -> %d sections (%.1f sections per tree); last %d trees -> %d sections, largest %d sections; %d waiting",
                FLUSH_TICKS, flushes, flushedTrees, flushedSections, flushedTrees == 0 ? 0 : flushedSections / (double) flushedTrees, lastTrees, lastSections, maxSections, PENDING.size()));
        say("  shaders: " + TreeShaderIds.describe() + "; Embeddium " + (ModList.get().isLoaded("embeddium") ? "loaded" : "not loaded"));
    }
}
