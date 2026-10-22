package com.antaurora.apofirstlight.boulder;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Rock R1a boulders (docs/gameplay/rock_r1_stones_boulders_design.md 3.2, 11.2): the forms and their nodes as the offline
 * generator wrote them (tools/boulders/export.mjs, {@code assets/apocalypse_firstlight/boulders/granite_r1.bin}), read once
 * on either side from the jar. A dedicated server skips the draw geometry.
 * <p>
 * Form coordinates: x east, y up, z south in blocks; the form's origin is the bottom centre of its origin cell, y 0 is the
 * ground it was sunk into. A turn t (0..3) turns form (x, z) t quarter turns: (x, z) -> (-z, x) per turn, about the origin
 * cell's centre, so cells, the 4 x 4 columns in a cell and the earthwork's vertex grid all stay on their grids.
 */
public final class BoulderData {
    /** Nodes in the data (the root block's piece property); the generator's count, checked on load. */
    public static final int NODES = 89;
    /** The 2 m sprite tile the quads' UVs span; the sprites by quad sprite number. */
    public static final String[] SPRITES = {"granite", "granite_lichen", "granite_fresh"};
    /** A vertex of no rock: Node#top / Node#bottom. */
    public static final int NONE = Short.MIN_VALUE;

    public static final class Form {
        public final String id;
        public final boolean lichen;
        /** The nodes of its separate rocks (a split pair two, a cluster two or three), each a stage tree. */
        public final int[] top;

        Form(String id, boolean lichen, int[] top) {
            this.id = id;
            this.lichen = lichen;
            this.top = top;
        }
    }

    /** A cell a node's rock fills: its 4 x 4 columns' spans (lo, hi in 1/16 of the cell, z-major; lo == hi: empty). */
    public static final class Cell {
        public final int x, y, z;
        final byte[] cols;

        Cell(int x, int y, int z, byte[] cols) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cols = cols;
        }

        public int lo(int a, int b) { return cols[(b * 4 + a) * 2]; }
        public int hi(int a, int b) { return cols[(b * 4 + a) * 2 + 1]; }

        /** Rock in this cell, 0..1 of its volume. */
        public float fill() {
            int s = 0;
            for (int i = 0; i < 16; i++) s += Math.max(0, cols[i * 2 + 1] - cols[i * 2]);
            return s / 256F;
        }
    }

    /** One stage of a boulder: a whole rock or a piece of one. */
    public static final class Node {
        public final int index, form, parent, strikes, stones, ax, ay, az;
        public final int[] children;
        public final float volume;
        final Int2ObjectOpenHashMap<Cell> cells = new Int2ObjectOpenHashMap<>();
        public final Cell[] cellList;
        /** Bounds of the cells and the anchor (form cells). */
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        final int hi0, hj0, hw, hd;
        final short[] heights;
        /** Draw data (client): per quad its cell key and sprite, 4 corners of position (form), normal, uv. */
        int quads;
        int[] quadCell;
        byte[] quadSprite, normal;
        float[] pos, uv;

        Node(int index, DataInputStream in, boolean draw) throws IOException {
            this.index = index;
            form = in.readByte();
            parent = in.readShort();
            children = new int[in.readUnsignedByte()];
            for (int i = 0; i < children.length; i++) children[i] = in.readShort();
            strikes = in.readUnsignedByte();
            stones = in.readUnsignedByte();
            volume = in.readFloat();
            ax = in.readByte(); ay = in.readByte(); az = in.readByte();
            cellList = new Cell[in.readShort()];
            int x0 = ax, y0 = ay, z0 = az, x1 = ax, y1 = ay, z1 = az;
            for (int i = 0; i < cellList.length; i++) {
                int x = in.readByte(), y = in.readByte(), z = in.readByte();
                byte[] cols = new byte[32];
                in.readFully(cols);
                cellList[i] = new Cell(x, y, z, cols);
                cells.put(key(x, y, z), cellList[i]);
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
                x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
            }
            minX = x0; minY = y0; minZ = z0; maxX = x1; maxY = y1; maxZ = z1;
            hi0 = in.readShort(); hj0 = in.readShort(); hw = in.readShort(); hd = in.readShort();
            heights = new short[hw * hd * 2];
            for (int i = 0; i < heights.length; i++) heights[i] = in.readShort();
            int n = in.readInt();
            if (draw) {
                quads = n;
                quadCell = new int[n];
                quadSprite = new byte[n];
                pos = new float[n * 12];
                normal = new byte[n * 12];
                uv = new float[n * 8];
            }
            for (int q = 0; q < n; q++) {
                int cx = in.readByte(), cy = in.readByte(), cz = in.readByte(), sprite = in.readByte();
                if (draw) { quadCell[q] = key(cx, cy, cz); quadSprite[q] = (byte) sprite; }
                for (int v = 0; v < 4; v++) {
                    float px = in.readShort() / 2048F, py = in.readShort() / 2048F, pz = in.readShort() / 2048F;
                    byte nx = in.readByte(), ny = in.readByte(), nz = in.readByte();
                    float u = in.readUnsignedShort() / 65535F, w = in.readUnsignedShort() / 65535F;
                    if (!draw) continue;
                    int o = q * 12 + v * 3;
                    pos[o] = px; pos[o + 1] = py; pos[o + 2] = pz;
                    normal[o] = nx; normal[o + 1] = ny; normal[o + 2] = nz;
                    uv[q * 8 + v * 2] = u; uv[q * 8 + v * 2 + 1] = w;
                }
            }
        }

        public Cell cell(int x, int y, int z) { return cells.get(key(x, y, z)); }

        public boolean leaf() { return children.length == 0; }

        /**
         * The rock's top and bottom over form vertex (i, j) of the earthwork grid (form x = (i - 2) / 4), in 1/256 block
         * above the form's ground; NONE where the vertical misses it.
         */
        public int top(int i, int j) { return height(i, j, 0); }
        public int bottom(int i, int j) { return height(i, j, 1); }

        private int height(int i, int j, int k) {
            int a = i - hi0, b = j - hj0;
            return a < 0 || b < 0 || a >= hw || b >= hd ? NONE : heights[(b * hw + a) * 2 + k];
        }

        /** Form vertex bounds of the height grid (i0, j0, i1, j1 inclusive). */
        public int vi0() { return hi0; }
        public int vj0() { return hj0; }
        public int vi1() { return hi0 + hw - 1; }
        public int vj1() { return hj0 + hd - 1; }
    }

    public final Form[] forms;
    public final Node[] nodes;

    private BoulderData(DataInputStream in, boolean draw) throws IOException {
        if (in.readInt() != 0x41464C42 || in.readShort() != 1) throw new IOException("not boulder data version 1");
        in.readFloat();
        int sprites = in.readUnsignedByte();
        for (int i = 0; i < sprites; i++) in.readUTF();
        forms = new Form[in.readShort()];
        for (int i = 0; i < forms.length; i++) {
            String id = in.readUTF();
            boolean lichen = in.readByte() != 0;
            int[] top = new int[in.readUnsignedByte()];
            for (int j = 0; j < top.length; j++) top[j] = in.readShort();
            forms[i] = new Form(id, lichen, top);
        }
        nodes = new Node[in.readShort()];
        for (int i = 0; i < nodes.length; i++) nodes[i] = new Node(i, in, draw);
        if (nodes.length != NODES) throw new IOException("boulder data has " + nodes.length + " nodes, the root block " + NODES);
    }

    private static volatile BoulderData data;

    /** The data (loaded on first use; the draw geometry on a client only). */
    public static BoulderData get() {
        BoulderData d = data;
        if (d != null) return d;
        synchronized (BoulderData.class) {
            if (data == null) {
                try (InputStream raw = BoulderData.class.getResourceAsStream("/assets/apocalypse_firstlight/boulders/granite_r1.bin")) {
                    if (raw == null) throw new IOException("boulders/granite_r1.bin missing");
                    data = new BoulderData(new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16))), FMLEnvironment.dist.isClient());
                } catch (IOException e) {
                    throw new UncheckedIOException("Rock R1a boulder data", e);
                }
            }
            return data;
        }
    }

    public static Node node(int i) { return get().nodes[i]; }

    /** The form with this id, or -1. */
    public static int form(String id) {
        var f = get().forms;
        for (int i = 0; i < f.length; i++) if (f[i].id.equals(id)) return i;
        return -1;
    }

    // ------------------------------------------------------------------------------------------------- turning

    public static int key(int x, int y, int z) {
        return ((x + 512) & 1023) << 20 | ((y + 512) & 1023) << 10 | ((z + 512) & 1023);
    }

    /** x of (x, z) turned t quarter turns: (x, z) -> (-z, x) each. */
    public static int turnX(int t, int x, int z) {
        return switch (t & 3) { case 0 -> x; case 1 -> -z; case 2 -> -x; default -> z; };
    }

    public static int turnZ(int t, int x, int z) {
        return switch (t & 3) { case 0 -> z; case 1 -> x; case 2 -> -z; default -> -x; };
    }

    public static float turnX(int t, float x, float z) {
        return switch (t & 3) { case 0 -> x; case 1 -> -z; case 2 -> -x; default -> z; };
    }

    public static float turnZ(int t, float x, float z) {
        return switch (t & 3) { case 0 -> z; case 1 -> x; case 2 -> -z; default -> -x; };
    }

    // ----------------------------------------------------------------------------------------------- collision

    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /**
     * A node's cell as boxes, turned t: one box per column of the cell (0.25 x 0.25 m, its span), columns of one span next
     * to each other along x joined. Cached per node, turn and cell.
     */
    public static VoxelShape shape(Node n, int t, Cell c) {
        long k = ((long) n.index * 4 + (t & 3)) << 32 | (key(c.x, c.y, c.z) & 0xFFFFFFFFL);
        VoxelShape s = SHAPES.get(k);
        if (s != null) return s;
        // world column (A, B) of the turned cell holds form column (a, b) turned back: (a, b) -> (3 - b, a) per turn
        int[] lo = new int[16], hi = new int[16];
        for (int b = 0; b < 4; b++) for (int a = 0; a < 4; a++) {
            int A = a, B = b;
            for (int i = 0; i < (t & 3); i++) { int na = 3 - B; B = A; A = na; }
            lo[B * 4 + A] = c.lo(a, b);
            hi[B * 4 + A] = c.hi(a, b);
        }
        VoxelShape out = Shapes.empty();
        for (int B = 0; B < 4; B++)
            for (int A = 0; A < 4; ) {
                int l = lo[B * 4 + A], h = hi[B * 4 + A], A1 = A + 1;
                if (h <= l) { A++; continue; }
                while (A1 < 4 && lo[B * 4 + A1] == l && hi[B * 4 + A1] == h) A1++;
                out = Shapes.joinUnoptimized(out, Shapes.box(A / 4.0, l / 16.0, B / 4.0, A1 / 4.0, h / 16.0, (B + 1) / 4.0), BooleanOp.OR);
                A = A1;
            }
        out = out.optimize();
        VoxelShape raced = SHAPES.putIfAbsent(k, out);
        return raced != null ? raced : out;
    }
}
