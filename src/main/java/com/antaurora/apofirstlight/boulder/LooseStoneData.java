package com.antaurora.apofirstlight.boulder;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Rock R1b loose stones (docs/gameplay/rock_r1_stones_boulders_design.md 2.2-2.4, 11.3): the stones' shapes as the offline
 * generator wrote them (tools/boulders/build-loose-stones.mjs, {@code assets/apocalypse_firstlight/boulders/loose_r1.bin}),
 * read once on the client (the server needs none: a loose_stones block's contents are its state).
 * <p>
 * A stone is at real size in blocks (metres), its base centre at the origin, its base at y = 0.
 */
public final class LooseStoneData {
    /** The sprites by quad sprite number (textures/block/boulder/...). */
    public static final String[] SPRITES = {"granite", "flint", "flint_cortex", "granite_worn"};
    public static final int PEBBLE_FIELD = 0, PEBBLE_RIVER = 1, COBBLE_FIELD = 2, COBBLE_RIVER = 3, FLINT_FIELD = 4, FLINT_RIVER = 5, KINDS = 6;

    public static final class Stone {
        public final String id;
        public final int kind;
        public final float height;
        final int quads;
        final byte[] sprite, normal;
        final float[] pos, uv;

        Stone(DataInputStream in) throws IOException {
            id = in.readUTF();
            kind = in.readByte();
            height = in.readFloat();
            quads = in.readInt();
            sprite = new byte[quads];
            pos = new float[quads * 12];
            normal = new byte[quads * 12];
            uv = new float[quads * 8];
            for (int q = 0; q < quads; q++) {
                sprite[q] = in.readByte();
                for (int v = 0; v < 4; v++) {
                    int o = q * 12 + v * 3;
                    for (int k = 0; k < 3; k++) pos[o + k] = in.readShort() / 8192F;
                    for (int k = 0; k < 3; k++) normal[o + k] = in.readByte();
                    uv[q * 8 + v * 2] = in.readUnsignedShort() / 65535F;
                    uv[q * 8 + v * 2 + 1] = in.readUnsignedShort() / 65535F;
                }
            }
        }

        /** Half its widest horizontal extent (blocks), for spacing and the ground under it. */
        float radius() {
            float r = 0;
            for (int i = 0; i < pos.length; i += 3) r = Math.max(r, (float) Math.hypot(pos[i], pos[i + 2]));
            return r;
        }
    }

    /** The shapes of each kind. */
    public final Stone[][] byKind = new Stone[KINDS][];
    final float[][] radius = new float[KINDS][];

    private LooseStoneData(DataInputStream in) throws IOException {
        if (in.readInt() != 0x41464C4C || in.readShort() != 1) throw new IOException("not loose stone data version 1");
        in.readFloat();
        int sprites = in.readUnsignedByte();
        if (sprites != SPRITES.length) throw new IOException("loose stone data has " + sprites + " sprites, the model " + SPRITES.length);
        for (int i = 0; i < sprites; i++) in.readUTF();
        List<List<Stone>> kinds = new ArrayList<>();
        for (int k = 0; k < KINDS; k++) kinds.add(new ArrayList<>());
        int n = in.readShort();
        for (int i = 0; i < n; i++) {
            Stone s = new Stone(in);
            kinds.get(s.kind).add(s);
        }
        for (int k = 0; k < KINDS; k++) {
            if (kinds.get(k).isEmpty()) throw new IOException("loose stone data has no stone of kind " + k);
            byKind[k] = kinds.get(k).toArray(Stone[]::new);
            radius[k] = new float[byKind[k].length];
            for (int i = 0; i < byKind[k].length; i++) radius[k][i] = byKind[k][i].radius();
        }
    }

    private static volatile LooseStoneData data;

    public static LooseStoneData get() {
        LooseStoneData d = data;
        if (d != null) return d;
        synchronized (LooseStoneData.class) {
            if (data == null) {
                try (InputStream raw = LooseStoneData.class.getResourceAsStream("/assets/apocalypse_firstlight/boulders/loose_r1.bin")) {
                    if (raw == null) throw new IOException("boulders/loose_r1.bin missing");
                    data = new LooseStoneData(new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16))));
                } catch (IOException e) {
                    throw new UncheckedIOException("Rock R1b loose stone data", e);
                }
            }
            return data;
        }
    }
}
