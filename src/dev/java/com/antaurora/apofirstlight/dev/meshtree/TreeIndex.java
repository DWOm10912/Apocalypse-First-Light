package com.antaurora.apofirstlight.dev.meshtree;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesh Trees (docs/rendering/mesh_trees_p0b.md, D): the roots of the loaded sample trees, one index per side (the
 * server's overworld, the client's level). A cell finds the trees that cover it here: the client to draw it, both sides
 * to collide with it (the same block data gives both sides the same answer, so the player is not pulled back).
 * <p>
 * Per chunk, an immutable array of roots in a concurrent map: any thread may read (chunk builders, collisions); only the
 * side's main thread writes (chunk loads, root changes).
 */
public final class TreeIndex {
    public static final TreeIndex SERVER = new TreeIndex(), CLIENT = new TreeIndex();
    /** Trees reach at most this far (blocks) sideways from their root. */
    static final int REACH = 12;

    /** A root: position, layout, the tree's world bounds (cells, plus quad reach), its current view level (client). */
    public static final class Root {
        public final int x, y, z;
        public final TreeShape.Layout layout;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        volatile int level = -1;

        Root(BlockPos pos, BlockState state) {
            x = pos.getX(); y = pos.getY(); z = pos.getZ();
            layout = TreeBlocks.layoutOf(state);
            minX = x + layout.minX - 2; minY = y + layout.minY - 2; minZ = z + layout.minZ - 2;
            maxX = x + layout.maxX + 2; maxY = y + layout.maxY + 2; maxZ = z + layout.maxZ + 2;
        }

        boolean covers(int px, int py, int pz) {
            return px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ;
        }

        /** This tree's cell at a world position, or null. */
        public TreeShape.Cell cellAt(int px, int py, int pz) {
            return layout.cell(px - x, py - y, pz - z);
        }
    }

    private static final Root[] NONE = new Root[0];
    private final Map<Long, Root[]> byChunk = new ConcurrentHashMap<>();

    private TreeIndex() {}

    /** The index a block getter belongs to: a level by its side; anything else (a pathfinding region) by thread. */
    public static TreeIndex of(BlockGetter getter) {
        if (getter instanceof Level level) return level.isClientSide() ? CLIENT : SERVER;
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread() ? SERVER : CLIENT;
    }

    /** The roots whose trees may reach this cell (any thread). */
    public void roots(int px, int py, int pz, List<Root> out) {
        for (int cx = (px - REACH) >> 4; cx <= (px + REACH) >> 4; cx++)
            for (int cz = (pz - REACH) >> 4; cz <= (pz + REACH) >> 4; cz++) {
                Root[] list = byChunk.get(ChunkPos.asLong(cx, cz));
                if (list == null) continue;
                for (Root r : list) if (r.covers(px, py, pz)) out.add(r);
            }
    }

    public Iterable<Root[]> all() { return byChunk.values(); }
    public int chunks() { return byChunk.size(); }

    public int size() {
        int n = 0;
        for (Root[] list : byChunk.values()) n += list.length;
        return n;
    }

    static boolean isRoot(BlockState s) {
        return TreeBlocks.ready() && s.getBlock() == TreeBlocks.root();
    }

    /** Adds (or replaces) a root; returns it. */
    public Root add(BlockPos pos, BlockState state) {
        Root r = new Root(pos, state);
        byChunk.compute(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), (k, old) -> {
            var list = new ArrayList<Root>();
            if (old != null) for (Root o : old) if (o.x != r.x || o.y != r.y || o.z != r.z) list.add(o);
            list.add(r);
            return list.toArray(NONE);
        });
        return r;
    }

    /** Removes a root; returns it, or null. */
    public Root remove(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Root[] old = byChunk.get(key);
        if (old == null) return null;
        Root gone = null;
        var keep = new ArrayList<Root>();
        for (Root o : old) {
            if (o.x == pos.getX() && o.y == pos.getY() && o.z == pos.getZ()) gone = o;
            else keep.add(o);
        }
        if (keep.isEmpty()) byChunk.remove(key); else byChunk.put(key, keep.toArray(NONE));
        return gone;
    }

    /** Indexes the roots of a chunk that arrived (the section palettes are checked first); returns them. */
    public List<Root> load(LevelChunk chunk) {
        var found = new ArrayList<Root>();
        LevelChunkSection[] sections = chunk.getSections();
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(TreeIndex::isRoot)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState st = s.getBlockState(x, y, z);
                if (isRoot(st)) found.add(add(pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z).immutable(), st));
            }
        }
        return found;
    }

    public void unload(ChunkPos pos) {
        byChunk.remove(pos.toLong());
    }

    public void clear() {
        byChunk.clear();
    }
}
