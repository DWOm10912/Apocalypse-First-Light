package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
import java.util.concurrent.atomic.LongAdder;

/**
 * Mesh Trees client side (docs/rendering/mesh_trees_p0a.md B/J, mesh_trees_p0b.md D): the client's tree index
 * ({@link TreeIndex#CLIENT}, read by the chunk builder and by collisions), AFL's own distance levels, the status.
 * <ul>
 * <li>Index: the roots of every chunk that arrives, and the server's root messages. A cell of a tree whose root chunk is
 * not here yet draws nothing until that chunk arrives and marks the tree's sections for a rebuild.</li>
 * <li>Levels: each tree has one level for all its cells (no seams inside a crown), from the horizontal camera distance
 * to the root: LOD0 under {@value #NEAR} blocks, LOD1 under {@value #MID}, then LOD2, with {@value #HYSTERESIS} blocks of
 * hysteresis. A change marks the tree's sections for a rebuild; the shadow pass draws the same section meshes.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class TreeClient {
    static final int NEAR = 64, MID = 160, HYSTERESIS = 8;
    static final int PROXY = TreeShape.LODS;

    /** -1: distance levels; 0..2: one level for every tree; PROXY: the voxel proxy (what Voxy and maps see). */
    static volatile int override = -1;
    static final LongAdder lookups = new LongAdder(), lookupNanos = new LongAdder(), carriers = new LongAdder(), orphans = new LongAdder(),
            quadsOut = new LongAdder();
    static long marks, marksWindowStart, marksWindow;
    static final int[] levelChanges = new int[TreeShape.LODS];

    private TreeClient() {}

    /** The level a root's cells draw now. */
    static int levelOf(TreeIndex.Root r) {
        int o = override;
        if (o >= 0) return o;
        int l = r.level;
        return l < 0 ? 0 : l;
    }

    private static void added(TreeIndex.Root r) {
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            var cam = mc.gameRenderer.getMainCamera().getPosition();
            r.level = level(Math.hypot(r.x + 0.5 - cam.x, r.z + 0.5 - cam.z), -1);
        }
        dirty(r);
    }

    /** Marks every section a tree reaches for a rebuild (sections that are not loaded are ignored by the renderer). */
    static void dirty(TreeIndex.Root r) {
        var renderer = Minecraft.getInstance().levelRenderer;
        if (renderer == null) return;
        for (int sx = r.minX >> 4; sx <= r.maxX >> 4; sx++)
            for (int sy = r.minY >> 4; sy <= r.maxY >> 4; sy++)
                for (int sz = r.minZ >> 4; sz <= r.maxZ >> 4; sz++) { renderer.setSectionDirty(sx, sy, sz); marks++; }
    }

    static void dirtyAll() {
        for (TreeIndex.Root[] list : TreeIndex.CLIENT.all()) for (var r : list) dirty(r);
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (FMLEnvironment.production || !TreeBlocks.ready() || !(event.getLevel() instanceof ClientLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
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
    }

    /** The server's messages (client thread). */
    static void handle(TreeCommands.Msg m) {
        switch (m.action()) {
            case TreeCommands.ROOT -> {
                BlockPos pos = BlockPos.of(m.pos());
                BlockState now = Block.stateById(m.value());
                var gone = TreeIndex.CLIENT.remove(pos);
                if (gone != null) dirty(gone);
                if (TreeIndex.isRoot(now)) added(TreeIndex.CLIENT.add(pos, now));
            }
            case TreeCommands.LOD -> {
                override = m.value();
                dirtyAll();
                say("Mesh tree LOD: " + (m.value() < 0 ? "auto (distance)" : m.value() == PROXY ? "voxel proxy (what Voxy and maps see)" : "LOD" + m.value() + " for every tree"));
            }
            case TreeCommands.STATUS -> status();
            default -> {}
        }
    }

    // ------------------------------------------------------------------------------------------------- levels

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production || !TreeBlocks.ready()) return;
        var mc = Minecraft.getInstance();
        TreeShaderIds.tick();
        if (mc.level == null || mc.gameRenderer == null) return;
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        for (TreeIndex.Root[] list : TreeIndex.CLIENT.all()) for (var r : list) {
            double d = Math.hypot(r.x + 0.5 - cam.x, r.z + 0.5 - cam.z);
            int old = r.level, now = level(d, old);
            if (now == old) continue;
            r.level = now;
            if (old >= 0) levelChanges[now]++;
            // a root without a level yet was meshed as LOD0
            if (override < 0 && (old >= 0 || now != 0)) dirty(r);
        }
        long t = System.nanoTime();
        if (t - marksWindowStart > 10_000_000_000L) { marksWindow = marks; marksWindowStart = t; }
    }

    /** Distance level with hysteresis (old -1: none yet). */
    static int level(double d, int old) {
        int near = NEAR, mid = MID;
        if (old == 0) near += HYSTERESIS; else if (old > 0) near -= HYSTERESIS;
        if (old == 2) mid -= HYSTERESIS; else if (old >= 0) mid += HYSTERESIS;
        return d < near ? 0 : d < mid ? 1 : 2;
    }

    // ------------------------------------------------------------------------------------------------- status

    static void say(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal(s), false);
    }

    private static void status() {
        int[] at = new int[TreeShape.LODS];
        for (TreeIndex.Root[] list : TreeIndex.CLIENT.all()) for (var r : list) at[Math.max(0, r.level)]++;
        say(String.format("Mesh tree (client): %d roots indexed in %d chunks; levels now LOD0 %d / LOD1 %d / LOD2 %d; view %s; bands <%d / <%d blocks, hysteresis %d",
                TreeIndex.CLIENT.size(), TreeIndex.CLIENT.chunks(), at[0], at[1], at[2],
                override < 0 ? "auto" : override == PROXY ? "proxy" : "LOD" + override, NEAR, MID, HYSTERESIS));
        for (Species sp : Species.values()) {
            var l = TreeShape.layout(sp, 0, 0);
            say(String.format("  %s v0: %.1f m, crown %.1f m, %d cells (wood %d, %d collide; crown %d, %d carry leaves); triangles LOD0 %d, LOD1 %d, LOD2 %d",
                    sp.id, l.height, 2 * l.crownRadius, l.cells.size(), l.count(TreeShape.WOOD), l.solidCells, l.count(TreeShape.FOLIAGE),
                    l.carriers(TreeShape.FOLIAGE), l.triangles[0], l.triangles[1], l.triangles[2]));
        }
        long n = lookups.sum();
        say(String.format("  chunk builds: %d cell lookups, %.2f us each, %d found a tree, %d orphans (root chunk not here), %d quads emitted",
                n, n == 0 ? 0 : lookupNanos.sum() / 1000.0 / n, carriers.sum(), orphans.sum(), quadsOut.sum()));
        say(String.format("  rebuild marks: %d total, %d since the last 10 s mark; level changes to LOD0/1/2: %s; baked caches %d",
                marks, marks - marksWindow, Arrays.toString(levelChanges), TreeCellModel.cacheSize()));
        say("  shaders: " + TreeShaderIds.describe() + "; Embeddium " + (ModList.get().isLoaded("embeddium") ? "loaded" : "not loaded"));
    }
}
