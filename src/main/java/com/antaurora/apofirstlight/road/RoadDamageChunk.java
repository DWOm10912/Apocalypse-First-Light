package com.antaurora.apofirstlight.road;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One chunk's broken asphalt (road Mesh-ification P1, docs/worldgen/road_mesh_damage_v1_design.md 4): for each road column
 * a strike has broken into, which of its {@link #N} x {@link #N} sub-cells (12.5 cm) of the wearing course are gone (bit
 * sx + 8 sz), with the column's collision top and asphalt thickness when it was first broken (the shape and the drawn hole
 * read them, never the road again). A column with none gone has no entry; one with all gone keeps its entry (nothing of
 * its asphalt is drawn or collides).
 * <p>
 * Server: on the LevelChunk (RoadDamageHolder), written on the server thread only, saved with the chunk. Client: one copy
 * per chunk in RoadDamage#CLIENT, replaced whole by every packet, never written after, so mesh builders read one
 * consistent copy.
 */
public final class RoadDamageChunk {
    public static final int VERSION = 1;
    /** sub-cells per block edge */
    public static final int N = 8;
    /** every sub-cell gone */
    public static final long ALL = -1L;

    /** A broken column: its gone sub-cells, its collision top (world y) and its asphalt's thickness (m). */
    public record Col(long gone, float top, float depth) {}

    final Int2ObjectOpenHashMap<Col> cols = new Int2ObjectOpenHashMap<>();

    /** The key of column (x, z) in its chunk (any world coordinates). */
    public static int key(int x, int z) { return (z & 15) << 4 | (x & 15); }

    public static int keyX(int k) { return k & 15; }

    public static int keyZ(int k) { return k >> 4 & 15; }

    public static int bit(int sx, int sz) { return sx | sz << 3; }

    public static boolean gone(long bits, int sx, int sz) { return (bits >>> bit(sx, sz) & 1L) != 0; }

    /**
     * The ground's 25 vertex heights rel (0.25 m apart, blocks above y; LevelGround#drawn, road Mesh-ification P2): each
     * vertex whose sub-cells in the column (1, 2 or 4 round it) are all gone down to the base, the asphalt's underside.
     */
    public static void holes(Col c, int y, double[] rel) {
        int step = N / 4;
        for (int j = 0; j <= 4; j++) for (int i = 0; i <= 4; i++) {
            boolean all = true;
            for (int sz = j * step - 1; sz <= j * step && all; sz++) for (int sx = i * step - 1; sx <= i * step; sx++) {
                if (sx < 0 || sz < 0 || sx >= N || sz >= N) continue;
                if (!gone(c.gone(), sx, sz)) { all = false; break; }
            }
            if (all) rel[j * 5 + i] = Math.min(rel[j * 5 + i], c.top() - c.depth() - y);
        }
    }

    /** Column (x, z)'s entry, null when whole. Never written by a reader. */
    public Col get(int x, int z) { return cols.get(key(x, z)); }

    void put(int x, int z, Col c) { cols.put(key(x, z), c); }

    boolean remove(int x, int z) { return cols.remove(key(x, z)) != null; }

    public boolean isEmpty() { return cols.isEmpty(); }

    public int size() { return cols.size(); }

    public Iterable<Int2ObjectMap.Entry<Col>> entries() { return cols.int2ObjectEntrySet(); }

    // ------------------------------------------------------------------------------------------------- storage

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        ListTag list = new ListTag();
        for (var en : cols.int2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("k", en.getIntKey());
            c.putLong("b", en.getValue().gone());
            c.putFloat("t", en.getValue().top());
            c.putFloat("d", en.getValue().depth());
            list.add(c);
        }
        t.put("cols", list);
        return t;
    }

    public static RoadDamageChunk load(CompoundTag t) {
        RoadDamageChunk d = new RoadDamageChunk();
        ListTag list = t.getList("cols", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.getLong("b") != 0 && c.getFloat("d") > 0) d.cols.put(c.getInt("k") & 255, new Col(c.getLong("b"), c.getFloat("t"), c.getFloat("d")));
        }
        return d;
    }

    // ------------------------------------------------------------------------------------------------- network

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(cols.size());
        for (var en : cols.int2ObjectEntrySet()) {
            b.writeByte(en.getIntKey());
            b.writeLong(en.getValue().gone());
            b.writeFloat(en.getValue().top());
            b.writeFloat(en.getValue().depth());
        }
    }

    public static RoadDamageChunk read(FriendlyByteBuf b) {
        RoadDamageChunk d = new RoadDamageChunk();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            int k = b.readUnsignedByte();
            d.cols.put(k, new Col(b.readLong(), b.readFloat(), b.readFloat()));
        }
        return d;
    }
}
