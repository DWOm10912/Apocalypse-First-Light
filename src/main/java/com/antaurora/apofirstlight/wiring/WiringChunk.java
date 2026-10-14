package com.antaurora.apofirstlight.wiring;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * One chunk's wiring nodes (docs/gameplay/building_wiring_v1_design.md 3.3), server side only, kept on its LevelChunk
 * ({@link WiringChunkHolder}) and saved with the chunk ({@link WiringStorage}).
 * <p>
 * Key: cell x (4 bits) | z (4) | y + 2048 (12) | circuit (4); value: the packed {@link WiringNode}. One cell holds at most
 * one node per circuit, so up to 5 nodes. The revision changes with every edit (a counter shared by all chunks of the
 * server session, so a chunk loaded again never repeats a revision a client already has).
 */
public final class WiringChunk {
    /** 2 (2026-10-13): four circuits and the feeder, colour = circuit; 1 had twelve circuits, feeder 13, a free colour. */
    static final int VERSION = 2;
    private final Int2LongOpenHashMap nodes = new Int2LongOpenHashMap();
    private int revision = WiringStorage.nextRevision();

    static int key(BlockPos pos, int circuit) {
        return pos.getX() & 15 | (pos.getZ() & 15) << 4 | (pos.getY() + 2048 & 4095) << 8 | circuit << 20;
    }

    public static int circuitOf(int key) {
        return key >>> 20 & 15;
    }

    /** The cell of a key in the chunk at cp. */
    public static BlockPos cellOf(ChunkPos cp, int key) {
        return new BlockPos(cp.getMinBlockX() + (key & 15), (key >> 8 & 4095) - 2048, cp.getMinBlockZ() + (key >> 4 & 15));
    }

    long get(BlockPos pos, int circuit) {
        return nodes.get(key(pos, circuit));
    }

    void put(BlockPos pos, int circuit, long node) {
        nodes.put(key(pos, circuit), node);
        revision = WiringStorage.nextRevision();
    }

    long remove(BlockPos pos, int circuit) {
        long old = nodes.remove(key(pos, circuit));
        if (old != 0) revision = WiringStorage.nextRevision();
        return old;
    }

    /** Some node in this cell (any circuit). */
    boolean any(BlockPos pos) {
        if (nodes.isEmpty()) return false;
        int base = key(pos, 0);
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) if (nodes.containsKey(base | c << 20)) return true;
        return false;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int size() {
        return nodes.size();
    }

    public int revision() {
        return revision;
    }

    /** A copy of all keys and nodes, in the same order (for the client view and for checks). */
    public int[] keys() {
        return nodes.keySet().toIntArray();
    }

    public long[] values(int[] keys) {
        long[] out = new long[keys.length];
        for (int i = 0; i < keys.length; i++) out[i] = nodes.get(keys[i]);
        return out;
    }

    CompoundTag save() {
        int[] k = keys();
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", VERSION);
        tag.putIntArray("keys", k);
        tag.putLongArray("nodes", values(k));
        return tag;
    }

    static WiringChunk load(CompoundTag tag) {
        WiringChunk w = new WiringChunk();
        int[] k = tag.getIntArray("keys");
        long[] n = tag.getLongArray("nodes");
        if (tag.getInt("v") < 2) {
            loadV1(w, k, n);
            return w;
        }
        for (int i = 0; i < Math.min(k.length, n.length); i++) {
            int circuit = circuitOf(k[i]);
            // a node always carries its own circuit; anything else is damaged data and is dropped
            if (n[i] != 0 && circuit >= 1 && circuit <= WiringNode.CIRCUITS && WiringNode.circuit(n[i]) == circuit) w.nodes.put(k[i], n[i]);
        }
        return w;
    }

    /**
     * Version 1 (twelve circuits): each circuit goes to the circuit of its colour (user 2026-10-13: "旧线按颜色并过去"); the
     * tool laid every circuit in its default colour, 1, 5, 9 red to circuit 1, 2, 6, 10 blue to 2 and so on, so a run keeps
     * its links. The feeder (13) stays the feeder. Two circuits landing in one cell merge (WiringNode#merge).
     */
    private static void loadV1(WiringChunk w, int[] k, long[] n) {
        for (int i = 0; i < Math.min(k.length, n.length); i++) {
            int old = circuitOf(k[i]);
            if (n[i] == 0 || old < 1 || old > 13 || (int) (n[i] & 15) != old) continue;
            int circuit = old == 13 ? WiringNode.FEEDER : (old - 1) % 4 + 1;
            long node = n[i] & ~63L | circuit;
            int key = k[i] & ~(15 << 20) | circuit << 20;
            long there = w.nodes.get(key);
            w.nodes.put(key, there == 0 ? node : WiringNode.merge(there, node));
        }
    }

    /** For tests: the same nodes. */
    public boolean sameAs(WiringChunk o) {
        if (o.nodes.size() != nodes.size()) return false;
        for (Int2LongMap.Entry e : nodes.int2LongEntrySet()) if (o.nodes.get(e.getIntKey()) != e.getLongValue()) return false;
        return true;
    }
}
