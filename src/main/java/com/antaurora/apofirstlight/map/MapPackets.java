package com.antaurora.apofirstlight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The map's record from the server to the player carrying it (AFL Map V1, docs/ui/afl_map_v1_plan.md 5.3): Reset says
 * which map the player now carries (0: none) and clears the client's record; Chunks brings samples of that map (the
 * biome as the map's palette index), with the palette entries the client does not have yet. Deflated on the wire.
 * Marks brings the map's pencil marks (StartMap), all of them each time.
 */
public final class MapPackets {
    private MapPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Reset.class, Reset::encode, Reset::decode, Reset::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Chunks.class, Chunks::encode, Chunks::decode, Chunks::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Marks.class, Marks::encode, Marks::decode, Marks::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    /** The map the player carries now (0: none): the client's record starts again empty. */
    public record Reset(int map) {
        static void encode(Reset p, FriendlyByteBuf b) { b.writeVarInt(p.map); }

        static Reset decode(FriendlyByteBuf b) { return new Reset(b.readVarInt()); }

        static void handle(Reset p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.map.MapClient.reset(p.map)));
            s.get().setPacketHandled(true);
        }
    }

    /** The pencil marks of a map (all of them; none clears them). */
    public record Marks(int map, List<MapStore.Mark> marks) {
        static void encode(Marks p, FriendlyByteBuf b) {
            b.writeVarInt(p.map);
            b.writeVarInt(p.marks.size());
            for (MapStore.Mark m : p.marks) {
                b.writeByte(m.kind());
                b.writeVarInt(m.points().length);
                for (float f : m.points()) b.writeFloat(f);
            }
        }

        static Marks decode(FriendlyByteBuf b) {
            int map = b.readVarInt(), n = b.readVarInt();
            if (n > 64) throw new IllegalStateException("bad map marks packet");
            List<MapStore.Mark> marks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int kind = b.readUnsignedByte(), len = b.readVarInt();
                if (len > 1 << 16) throw new IllegalStateException("bad map marks packet");
                float[] f = new float[len];
                for (int j = 0; j < len; j++) f[j] = b.readFloat();
                marks.add(new MapStore.Mark(kind, f));
            }
            return new Marks(map, marks);
        }

        static void handle(Marks p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.map.MapClient.receive(p)));
            s.get().setPacketHandled(true);
        }
    }

    /**
     * Samples of a map: palette entries from index paletteFrom on (those the client has not had), then per chunk its key
     * (MapStore#key) and its 64 samples.
     */
    public record Chunks(int map, int paletteFrom, List<String> palette, long[] keys, int[][] samples) {
        static void encode(Chunks p, FriendlyByteBuf b) {
            b.writeVarInt(p.map);
            b.writeVarInt(p.paletteFrom);
            b.writeVarInt(p.palette.size());
            for (String s : p.palette) b.writeUtf(s);
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
                out.writeInt(p.keys.length);
                for (int i = 0; i < p.keys.length; i++) {
                    out.writeLong(p.keys[i]);
                    for (int v : p.samples[i]) out.writeInt(v);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            b.writeByteArray(raw.toByteArray());
        }

        static Chunks decode(FriendlyByteBuf b) {
            int map = b.readVarInt(), from = b.readVarInt(), n = b.readVarInt();
            List<String> palette = new ArrayList<>();
            for (int i = 0; i < n; i++) palette.add(b.readUtf());
            byte[] data = b.readByteArray(1 << 21);
            try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
                int count = in.readInt();
                long[] keys = new long[count];
                int[][] samples = new int[count][MapGround.CELLS * MapGround.CELLS];
                for (int i = 0; i < count; i++) {
                    keys[i] = in.readLong();
                    for (int j = 0; j < samples[i].length; j++) samples[i][j] = in.readInt();
                }
                return new Chunks(map, from, palette, keys, samples);
            } catch (IOException e) {
                throw new IllegalStateException("bad map packet", e);
            }
        }

        static void handle(Chunks p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.map.MapClient.receive(p)));
            s.get().setPacketHandled(true);
        }
    }
}
