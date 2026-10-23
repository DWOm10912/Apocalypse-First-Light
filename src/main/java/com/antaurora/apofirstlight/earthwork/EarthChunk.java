package com.antaurora.apofirstlight.earthwork;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

import static com.antaurora.apofirstlight.earthwork.EarthGrid.*;

/**
 * One chunk's earthwork data (docs/worldgen/earthwork_a1.md 3, docs/worldgen/earthwork_a2.md 3): the heights h and first
 * heights base of the vertices it owns (EarthGrid#CHUNK squared, NONE where no edited column reads them), the loose earth
 * on them (quanta) and its material (BulkMaterials index), and per column whether it is edited, with its natural surface
 * block y and state when it was first edited (top0, topState: Terrain Smooth reads an edited column as that, so the
 * slopes around it never change; its undisturbed skin keeps that block's top texture), its hard floor (quanta: the
 * shovel digs no deeper) and, where that floor is rock (Rock R2), the deepest a pick may cut it.
 * <p>
 * Server: on the LevelChunk (EarthChunkHolder), written on the server thread only, saved with the chunk. Client: one copy
 * per chunk in Earthwork#CLIENT, replaced whole by every packet, never written after, so chunk builders read one
 * consistent copy.
 */
public final class EarthChunk {
    /**
     * 2: loose earth, materials, hard floors (A2); a version 1 save reads with no loose earth and floors at top0 - 1.
     * 3: rock depth limits (Rock R2); a version 2 save reads with none (no rock to cut, as before).
     */
    public static final int VERSION = 3;
    final int[] h = new int[CHUNK * CHUNK], base = new int[CHUNK * CHUNK], loose = new int[CHUNK * CHUNK];
    final byte[] mat = new byte[CHUNK * CHUNK];
    final long[] edited = new long[4];
    final int[] top0 = new int[256], floor = new int[256], rock = new int[256];
    final BlockState[] topState = new BlockState[256];
    int columns, vertices;
    /** client copies: the block, shade and vehicle grip of each material index, as the server sent them (null on the server) */
    BlockState[] matBlock;
    float[] matShade, matGrip;
    /** client copies: the sprite each material's loose earth is drawn with instead of its block's top (null: the block's) */
    ResourceLocation[] matTexture;

    public EarthChunk() {
        Arrays.fill(h, NONE);
        Arrays.fill(base, NONE);
        Arrays.fill(rock, NONE);
    }

    public boolean edited(int col) { return (edited[col >> 6] >>> (col & 63) & 1L) != 0; }

    public int top0(int col) { return top0[col]; }

    public BlockState topState(int col) { return topState[col]; }

    public int h(int i) { return h[i]; }

    public int base(int i) { return base[i]; }

    public int loose(int i) { return loose[i]; }

    public int mat(int i) { return mat[i] & 255; }

    /** Hard floor of column col (quanta): the top of the first block under it a shovel cannot move, or a cave's lid. */
    public int floor(int col) { return floor[col]; }

    /** Rock R2: the deepest a pick may cut the rock under column col (quanta), NONE where its floor is not rock. */
    public int rock(int col) { return rock[col]; }

    /** The lowest the skin of column col may go (quanta): its rock's depth limit, else its hard floor. */
    public int bottom(int col) { return rock[col] == NONE ? floor[col] : Math.min(floor[col], rock[col]); }

    /** The block loose earth of material m is drawn as (client copies: as sent; the server: its table). */
    public BlockState matBlock(int m) {
        if (matBlock != null) return m >= 0 && m < matBlock.length ? matBlock[m] : null;
        return BulkMaterials.table().get(m).block();
    }

    /** The sprite loose earth of material m is drawn with, null: its block's top (client copies only). */
    public ResourceLocation matTexture(int m) {
        return matTexture != null && m >= 0 && m < matTexture.length ? matTexture[m] : null;
    }

    public float matShade(int m) {
        if (matShade != null) return m >= 0 && m < matShade.length ? matShade[m] : 1;
        return BulkMaterials.table().get(m).shade();
    }

    /** A vehicle tyre's grip on loose earth of material m (A3; the driving client needs it too). */
    public double matGrip(int m) {
        if (matGrip != null) return m >= 0 && m < matGrip.length ? matGrip[m] : 0.6;
        return BulkMaterials.table().get(m).grip();
    }

    public int columns() { return columns; }

    public int vertices() { return vertices; }

    public boolean isEmpty() { return columns == 0 && vertices == 0; }

    void mark(int col, int top, BlockState state, int hardFloor, int rockLimit) {
        if (!edited(col)) columns++;
        edited[col >> 6] |= 1L << (col & 63);
        top0[col] = top;
        topState[col] = state;
        floor[col] = hardFloor;
        rock[col] = rockLimit;
    }

    void unmark(int col) {
        if (!edited(col)) return;
        columns--;
        edited[col >> 6] &= ~(1L << (col & 63));
        topState[col] = null;
        rock[col] = NONE;
    }

    /** A vertex's first height (snapshot), once: later edits move h only. */
    void first(int i, int height) {
        if (h[i] == NONE) vertices++;
        h[i] = base[i] = height;
    }

    void set(int i, int height) { h[i] = height; }

    void setLoose(int i, int quanta, int material) {
        loose[i] = Math.max(0, quanta);
        mat[i] = (byte) (quanta > 0 ? material : 0);
    }

    void clear(int i) {
        if (h[i] != NONE) vertices--;
        h[i] = base[i] = NONE;
        loose[i] = 0;
        mat[i] = 0;
    }

    /** Loose earth on the vertices (quanta). */
    public long looseTotal() {
        long s = 0;
        for (int l : loose) s += l;
        return s;
    }

    /** Net quanta above the first heights (dug: negative). */
    public long net() {
        long s = 0;
        for (int i = 0; i < h.length; i++) if (h[i] != NONE) s += h[i] - base[i];
        return s;
    }

    /** {lowest, highest} block y its skins and first tops reach, with the cells next to them; null when empty. */
    public int[] yRange() {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < h.length; i++) {
            if (h[i] == NONE) continue;
            lo = Math.min(lo, Math.floorDiv(Math.min(h[i], base[i]), Q));
            hi = Math.max(hi, Math.floorDiv(Math.max(h[i], base[i]), Q));
        }
        for (int c = 0; c < 256; c++) {
            if (!edited(c)) continue;
            lo = Math.min(lo, Math.floorDiv(bottom(c), Q));
            hi = Math.max(hi, top0[c] + Earthwork.RAISE);
        }
        return lo > hi ? null : new int[]{lo - 1, hi + 1};
    }

    // ------------------------------------------------------------------------------------------------- storage

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putIntArray("h", h);
        t.putIntArray("base", base);
        t.putLongArray("edited", edited);
        t.putIntArray("loose", loose);
        t.putByteArray("mat", mat);
        ListTag mats = new ListTag();   // the material ids by index, so a save survives a changed table
        for (BulkMaterials.Material m : BulkMaterials.table().all) mats.add(StringTag.valueOf(m.id()));
        t.put("mats", mats);
        ListTag cols = new ListTag();
        for (int c = 0; c < 256; c++) {
            if (!edited(c)) continue;
            CompoundTag e = new CompoundTag();
            e.putShort("i", (short) c);
            e.putInt("y", top0[c]);
            e.putInt("f", floor[c]);
            if (rock[c] != NONE) e.putInt("r", rock[c]);
            e.put("s", NbtUtils.writeBlockState(topState[c]));
            cols.add(e);
        }
        t.put("cols", cols);
        return t;
    }

    public static EarthChunk load(CompoundTag t) {
        EarthChunk d = new EarthChunk();
        int[] h = t.getIntArray("h"), base = t.getIntArray("base");
        if (h.length == d.h.length && base.length == d.base.length) {
            System.arraycopy(h, 0, d.h, 0, h.length);
            System.arraycopy(base, 0, d.base, 0, base.length);
            for (int v : h) if (v != NONE) d.vertices++;
        }
        int[] loose = t.getIntArray("loose");
        byte[] mat = t.getByteArray("mat");
        if (loose.length == d.loose.length && mat.length == d.mat.length) {
            ListTag mats = t.getList("mats", Tag.TAG_STRING);
            var table = BulkMaterials.table();
            int[] map = new int[256];
            for (int k = 0; k < map.length; k++) map[k] = table.index(k < mats.size() ? mats.getString(k) : "");
            for (int i = 0; i < loose.length; i++) if (loose[i] > 0 && d.h[i] != NONE) d.setLoose(i, loose[i], map[mat[i] & 255]);
        }
        ListTag cols = t.getList("cols", Tag.TAG_COMPOUND);
        for (int k = 0; k < cols.size(); k++) {
            CompoundTag e = cols.getCompound(k);
            int y = e.getInt("y");
            d.mark(e.getShort("i") & 255, y, NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), e.getCompound("s")),
                    e.contains("f", Tag.TAG_INT) ? e.getInt("f") : (y - 1) * Q, e.contains("r", Tag.TAG_INT) ? e.getInt("r") : NONE);
        }
        return d;
    }

    // ------------------------------------------------------------------------------------------------- network

    /**
     * Sparse: the material table's blocks, shades, grips and sprites, the set vertices (with their loose earth), then the
     * edited columns.
     */
    public void write(FriendlyByteBuf b) {
        var table = BulkMaterials.table();
        b.writeVarInt(table.size());
        for (BulkMaterials.Material m : table.all) {
            b.writeVarInt(Block.getId(m.block()));
            b.writeFloat(m.shade());
            b.writeFloat((float) m.grip());
            b.writeBoolean(m.texture() != null);
            if (m.texture() != null) b.writeResourceLocation(m.texture());
        }
        b.writeVarInt(vertices);
        for (int i = 0; i < h.length; i++) {
            if (h[i] == NONE) continue;
            b.writeShort(i);
            b.writeInt(h[i]);
            b.writeInt(base[i]);
            b.writeVarInt(loose[i]);
            if (loose[i] > 0) b.writeByte(mat[i]);
        }
        b.writeVarInt(columns);
        for (int c = 0; c < 256; c++) {
            if (!edited(c)) continue;
            b.writeByte(c);
            b.writeInt(top0[c]);
            b.writeInt(floor[c]);
            b.writeInt(rock[c]);
            b.writeVarInt(Block.getId(topState[c]));
        }
    }

    public static EarthChunk read(FriendlyByteBuf b) {
        EarthChunk d = new EarthChunk();
        int mats = b.readVarInt();
        d.matBlock = new BlockState[mats];
        d.matShade = new float[mats];
        d.matGrip = new float[mats];
        d.matTexture = new ResourceLocation[mats];
        for (int k = 0; k < mats; k++) {
            d.matBlock[k] = Block.stateById(b.readVarInt());
            d.matShade[k] = b.readFloat();
            d.matGrip[k] = b.readFloat();
            if (b.readBoolean()) d.matTexture[k] = b.readResourceLocation();
        }
        int n = b.readVarInt();
        for (int k = 0; k < n; k++) {
            int i = b.readShort() & 0xFFFF;
            int hv = b.readInt(), bv = b.readInt(), l = b.readVarInt();
            if (d.h[i] == NONE) d.vertices++;
            d.h[i] = hv;
            d.base[i] = bv;
            if (l > 0) d.setLoose(i, l, b.readUnsignedByte());
        }
        int m = b.readVarInt();
        for (int k = 0; k < m; k++) {
            int c = b.readUnsignedByte();
            int y = b.readInt(), f = b.readInt(), r = b.readInt();
            d.mark(c, y, Block.stateById(b.readVarInt()), f, r);
        }
        return d;
    }
}
