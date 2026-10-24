package com.antaurora.apofirstlight.boulder;

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
 * Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 3.2): the roots of the loaded boulders, one index per side (the
 * server's overworld, the client's level), as Mesh trees keep theirs (tree/TreeIndex). A cell finds the rocks that fill it
 * here: the client to draw it, both sides to collide with it, the server's earthwork to keep the ground out of the rock.
 * Per chunk an immutable array of roots in a concurrent map: any thread reads, the side's main thread writes.
 */
public final class BoulderIndex {
    public static final BoulderIndex SERVER = new BoulderIndex(), CLIENT = new BoulderIndex();
    /** A node's cells reach at most this far (blocks) from its root (the generator reports 3). */
    static final int REACH = 4;

    /** A root: position, node, turn, the form origin, its world bounds (cells and anchor). */
    public static final class Root {
        public final int x, y, z, turn;
        public final BoulderData.Node node;
        public final BoulderRock rock;
        /** The form's origin cell. */
        public final int ox, oy, oz;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        Root(BlockPos pos, BlockState state) {
            x = pos.getX(); y = pos.getY(); z = pos.getZ();
            node = BoulderData.node(state.getValue(BoulderBlocks.PIECE));
            turn = state.getValue(BoulderBlocks.TURN);
            rock = state.getValue(BoulderBlocks.ROCK);
            ox = x - BoulderData.turnX(turn, node.ax, node.az);
            oy = y - node.ay;
            oz = z - BoulderData.turnZ(turn, node.ax, node.az);
            int ax = BoulderData.turnX(turn, node.minX, node.minZ), az = BoulderData.turnZ(turn, node.minX, node.minZ);
            int bx = BoulderData.turnX(turn, node.maxX, node.maxZ), bz = BoulderData.turnZ(turn, node.maxX, node.maxZ);
            minX = ox + Math.min(ax, bx); maxX = ox + Math.max(ax, bx);
            minZ = oz + Math.min(az, bz); maxZ = oz + Math.max(az, bz);
            minY = oy + node.minY; maxY = oy + node.maxY;
        }

        public boolean covers(int px, int py, int pz) {
            return px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ;
        }

        /** The form cell of a world cell: x, z turned back. */
        public int formX(int px, int pz) { return BoulderData.turnX(-turn & 3, px - ox, pz - oz); }
        public int formZ(int px, int pz) { return BoulderData.turnZ(-turn & 3, px - ox, pz - oz); }

        /** This rock's cell at a world position, or null. */
        public BoulderData.Cell cellAt(int px, int py, int pz) {
            return covers(px, py, pz) ? node.cell(formX(px, pz), py - oy, formZ(px, pz)) : null;
        }

        /** The world position of a form cell. */
        public BlockPos world(int fx, int fy, int fz) {
            return new BlockPos(ox + BoulderData.turnX(turn, fx, fz), oy + fy, oz + BoulderData.turnZ(turn, fx, fz));
        }

        /** The world position of a form cell of another node of the same form (its children: the same origin). */
        public BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private static final Root[] NONE = new Root[0];
    private final Map<Long, Root[]> byChunk = new ConcurrentHashMap<>();

    private BoulderIndex() {}

    /** The index a block getter belongs to: a level by its side; anything else (a render or path region) by thread. */
    public static BoulderIndex of(BlockGetter getter) {
        if (getter instanceof Level level) return level.isClientSide() ? CLIENT : SERVER;
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread() ? SERVER : CLIENT;
    }

    /** The roots whose rock may fill this cell (any thread). */
    public void roots(int px, int py, int pz, List<Root> out) {
        for (int cx = (px - REACH) >> 4; cx <= (px + REACH) >> 4; cx++)
            for (int cz = (pz - REACH) >> 4; cz <= (pz + REACH) >> 4; cz++) {
                Root[] list = byChunk.get(ChunkPos.asLong(cx, cz));
                if (list == null) continue;
                for (Root r : list) if (r.covers(px, py, pz)) out.add(r);
            }
    }

    /** The roots near a column (x, z), any height (the earthwork's vertices). */
    public void column(int px, int pz, List<Root> out) {
        for (int cx = (px - REACH) >> 4; cx <= (px + REACH) >> 4; cx++)
            for (int cz = (pz - REACH) >> 4; cz <= (pz + REACH) >> 4; cz++) {
                Root[] list = byChunk.get(ChunkPos.asLong(cx, cz));
                if (list == null) continue;
                for (Root r : list) if (px >= r.minX - 1 && px <= r.maxX + 1 && pz >= r.minZ - 1 && pz <= r.maxZ + 1) out.add(r);
            }
    }

    public Iterable<Root[]> all() { return byChunk.values(); }

    public boolean isEmpty() { return byChunk.isEmpty(); }

    public int size() {
        int n = 0;
        for (Root[] list : byChunk.values()) n += list.length;
        return n;
    }

    static boolean isRoot(BlockState s) {
        return BoulderBlocks.ready() && s.getBlock() == BoulderBlocks.root();
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

    /** Indexes the roots of a chunk that arrived (the section palettes first); returns them. */
    public List<Root> load(LevelChunk chunk) {
        var found = new ArrayList<Root>();
        LevelChunkSection[] sections = chunk.getSections();
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(BoulderIndex::isRoot)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState st = s.getBlockState(x, y, z);
                if (isRoot(st)) found.add(add(pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z).immutable(), st));
            }
        }
        return found;
    }

    public void unload(ChunkPos pos) { byChunk.remove(pos.toLong()); }

    public void clear() { byChunk.clear(); }
}
