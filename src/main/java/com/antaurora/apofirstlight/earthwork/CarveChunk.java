package com.antaurora.apofirstlight.earthwork;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One chunk's carved rock (U3a-3 round 2, docs/worldgen/underground_u3_rocks_ores_design.md 13): for each natural rock
 * block a pick has cut into, which of its {@link #N}^3 sub-cells (1/8 m) are gone, one long per sub-cell layer
 * (bit sx + 8 sz of word sy). A block with none gone has no entry; a block with all gone is air instead.
 * <p>
 * Server: on the LevelChunk (CarveChunkHolder), written on the server thread only, saved with the chunk. Client: one copy
 * per chunk in RockCarve#CLIENT, replaced whole by every packet, never written after, so chunk builders read one
 * consistent copy.
 */
public final class CarveChunk {
    public static final int VERSION = 1;
    /** sub-cells per block edge */
    public static final int N = 8;
    public static final int CELLS = N * N * N;
    final Int2ObjectOpenHashMap<long[]> cells = new Int2ObjectOpenHashMap<>();

    /** The key of block (x, y, z) in its chunk (x, z: any world coordinates). */
    public static int key(int x, int y, int z) { return y << 8 | (z & 15) << 4 | (x & 15); }

    public static int keyY(int k) { return k >> 8; }

    public static int keyX(int k) { return k & 15; }

    public static int keyZ(int k) { return k >> 4 & 15; }

    /** The gone sub-cells of block (x, y, z), null when it is whole. Never written by a reader. */
    public long[] get(int x, int y, int z) { return cells.get(key(x, y, z)); }

    public static boolean gone(long[] bits, int sx, int sy, int sz) { return (bits[sy] >>> (sx | sz << 3) & 1L) != 0; }

    public static int count(long[] bits) {
        int n = 0;
        for (long w : bits) n += Long.bitCount(w);
        return n;
    }

    /** The block's bits to write (created on the first cut). */
    long[] edit(int x, int y, int z) { return cells.computeIfAbsent(key(x, y, z), k -> new long[N]); }

    boolean remove(int x, int y, int z) { return cells.remove(key(x, y, z)) != null; }

    public boolean isEmpty() { return cells.isEmpty(); }

    public int size() { return cells.size(); }

    public Iterable<Int2ObjectMap.Entry<long[]>> entries() { return cells.int2ObjectEntrySet(); }

    /** Whether any block of column (x, z) from y0 to y1 has been carved. */
    public boolean anyInColumn(int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) if (cells.containsKey(key(x, y, z))) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------------- storage

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        ListTag list = new ListTag();
        for (var en : cells.int2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("k", en.getIntKey());
            c.putLongArray("b", en.getValue());
            list.add(c);
        }
        t.put("cells", list);
        return t;
    }

    public static CarveChunk load(CompoundTag t) {
        CarveChunk d = new CarveChunk();
        ListTag list = t.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            long[] b = c.getLongArray("b");
            if (b.length == N && count(b) > 0 && count(b) < CELLS) d.cells.put(c.getInt("k"), b);
        }
        return d;
    }

    // ------------------------------------------------------------------------------------------------- network

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(cells.size());
        for (var en : cells.int2ObjectEntrySet()) {
            b.writeInt(en.getIntKey());
            for (long w : en.getValue()) b.writeLong(w);
        }
    }

    public static CarveChunk read(FriendlyByteBuf b) {
        CarveChunk d = new CarveChunk();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            int k = b.readInt();
            long[] bits = new long[N];
            for (int j = 0; j < N; j++) bits[j] = b.readLong();
            d.cells.put(k, bits);
        }
        return d;
    }
}
