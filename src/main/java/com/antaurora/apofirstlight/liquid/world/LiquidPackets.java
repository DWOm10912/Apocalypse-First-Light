package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.liquid.LiquidCell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Physical water V1 package 2 (docs/gameplay/water_v1_design.md 16.4): the liquid's surfaces, server to client. */
public final class LiquidPackets {
    private LiquidPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Surface.class, Surface::encode, Surface::decode, Surface::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Falls.class, Falls::encode, Falls::decode, Falls::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Pour.class, Pour::encode, Pour::decode, Pour::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Wet.class, Wet::encode, Wet::decode, Wet::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Burst.class, Burst::encode, Burst::decode, Burst::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    /**
     * One chunk's falls, all of them (none: they stopped; docs/gameplay/water_falling_v1_design.md 3.5): per fall its
     * edge's west / north end, the edge's direction and the way it falls (bit 0: along x; bit 1: toward -x / -z), the lip
     * and the depth over it, where it lands, the opening's width (m), the flow (L/s) and how fast it leaves (m/s).
     */
    public record Falls(int chunkX, int chunkZ, int[] x, int[] z, byte[] dir, float[] lip, float[] depth, float[] bottom,
                        float[] width, float[] flow, float[] speed) {
        public static final int ALONG_X = 1, NEGATIVE = 2;

        static Falls of(int chunkX, int chunkZ, List<com.antaurora.apofirstlight.liquid.LiquidFall> falls) {
            int n = falls.size();
            int[] x = new int[n], z = new int[n];
            byte[] dir = new byte[n];
            float[] lip = new float[n], depth = new float[n], bottom = new float[n], width = new float[n], flow = new float[n], speed = new float[n];
            for (int i = 0; i < n; i++) {
                var f = falls.get(i);
                x[i] = f.x();
                z[i] = f.z();
                dir[i] = (byte) ((f.alongX() ? ALONG_X : 0) | (f.sign() < 0 ? NEGATIVE : 0));
                lip[i] = (float) f.lip();
                depth[i] = (float) f.depth();
                bottom[i] = (float) f.bottom();
                width[i] = (float) f.width();
                flow[i] = (float) f.flow();
                speed[i] = (float) f.speed();
            }
            return new Falls(chunkX, chunkZ, x, z, dir, lip, depth, bottom, width, flow, speed);
        }

        static void encode(Falls p, FriendlyByteBuf b) {
            b.writeInt(p.chunkX);
            b.writeInt(p.chunkZ);
            b.writeVarInt(p.x.length);
            for (int i = 0; i < p.x.length; i++) {
                b.writeInt(p.x[i]);
                b.writeInt(p.z[i]);
                b.writeByte(p.dir[i]);
                b.writeFloat(p.lip[i]);
                b.writeFloat(p.depth[i]);
                b.writeFloat(p.bottom[i]);
                b.writeFloat(p.width[i]);
                b.writeFloat(p.flow[i]);
                b.writeFloat(p.speed[i]);
            }
        }

        static Falls decode(FriendlyByteBuf b) {
            int cx = b.readInt(), cz = b.readInt(), n = b.readVarInt();
            if (n < 0 || n > 16 * 16 * 4 * 8) throw new IllegalArgumentException("liquid falls: " + n);
            int[] x = new int[n], z = new int[n];
            byte[] dir = new byte[n];
            float[] lip = new float[n], depth = new float[n], bottom = new float[n], width = new float[n], flow = new float[n], speed = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = b.readInt();
                z[i] = b.readInt();
                dir[i] = b.readByte();
                lip[i] = b.readFloat();
                depth[i] = b.readFloat();
                bottom[i] = b.readFloat();
                width[i] = b.readFloat();
                flow[i] = b.readFloat();
                speed[i] = b.readFloat();
            }
            return new Falls(cx, cz, x, z, dir, lip, depth, bottom, width, flow, speed);
        }

        static void handle(Falls p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.liquid.ClientFalls.receive(p)));
            s.get().setPacketHandled(true);
        }
    }

    /**
     * A player pouring (docs/gameplay/water_falling_v1_design.md 3.3; {@code /afl dev water pour}): the client draws the
     * stream from their hand, the same parabola the server lands. Flow 0: stopped.
     */
    public record Pour(int entity, float flow, float speed) {
        static void encode(Pour p, FriendlyByteBuf b) {
            b.writeVarInt(p.entity);
            b.writeFloat(p.flow);
            b.writeFloat(p.speed);
        }

        static Pour decode(FriendlyByteBuf b) {
            return new Pour(b.readVarInt(), b.readFloat(), b.readFloat());
        }

        static void handle(Pour p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.liquid.ClientFalls.pour(p)));
            s.get().setPacketHandled(true);
        }
    }

    /**
     * A jet of water out of a burst (LiquidBursts; Mesh water M4): where it leaves, its direction, how fast (m/s), its
     * flow (L/s; 0: it stopped) and the air drag the server flies its parcels with, so the clients draw the same jet.
     */
    public record Burst(int id, double x, double y, double z, float dx, float dy, float dz, float speed, float flow, float drag) {
        static void encode(Burst p, FriendlyByteBuf b) {
            b.writeVarInt(p.id);
            b.writeDouble(p.x);
            b.writeDouble(p.y);
            b.writeDouble(p.z);
            b.writeFloat(p.dx);
            b.writeFloat(p.dy);
            b.writeFloat(p.dz);
            b.writeFloat(p.speed);
            b.writeFloat(p.flow);
            b.writeFloat(p.drag);
        }

        static Burst decode(FriendlyByteBuf b) {
            return new Burst(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat());
        }

        static void handle(Burst p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.liquid.ClientFalls.burst(p)));
            s.get().setPacketHandled(true);
        }
    }

    /** Held at a fixed level (a natural water body's stand-in, 5.7). */
    public static final int BOUNDARY = 1;
    /** Still (a sleeping basin): the client need not ease it. */
    public static final int STILL = 2;
    /** Flags bits 2–5: LiquidCell#joinedSides, the sides where the cell's water goes on into its neighbour's. */
    public static final int SIDES_SHIFT = 2;
    /** Running (LiquidCell#running): sent however thin, its mean depth in {@code film} (Mesh water A1). */
    public static final int RUNNING = 64;
    /** A river's water (LiquidCell#river, river plan R1): held like BOUNDARY, its velocity sent, AFL's water to swim in. */
    public static final int RIVER = 128;

    /**
     * One chunk's wet cells, all of them (none: the chunk has no liquid left): per cell its column in the chunk
     * (x << 4 | z), its layer's floor (slabs, 1/16 m), its surface (1/4096 m), {@link #BOUNDARY} / {@link #STILL} /
     * {@link #RUNNING} and its joined sides, its water's velocity east and south ({@link #VELOCITY} m/s: WaterQuery,
     * package 4) and, running, its water spread over the column's square metre (mm: the film's mean depth, Mesh water A2).
     */
    public record Surface(int chunkX, int chunkZ, byte[] column, int[] floor, int[] level, byte[] flags, short[] vx, short[] vz,
                          short[] film) {
        /** Velocity units a m/s (2 cm/s each). */
        public static final double VELOCITY = 50;

        /** Water shallower than this (m) is not sent: the client draws no surface for it (ClientLiquid.SHOW_FULL). */
        static final double SHOWN = 0.008;

        /**
         * The chunk's cells the client draws (docs/gameplay/water_mesh_parcels_v1_design.md A1): held at a level, deeper
         * than {@link #SHOWN}, or running however thin (the user approved 9.1 on 2026-10-26: running water drawn without a
         * shader pack too, reversing falling water 19). Whether it is drawn goes by whether it runs, not by its depth; the
         * film the floor holds after rain, not running, is still not sent (no sheet of water on grass, 2026-10-24).
         */
        public static List<LiquidCell> shown(List<LiquidCell> wet) {
            List<LiquidCell> out = new java.util.ArrayList<>(wet.size());
            for (LiquidCell c : wet) if (sends(c)) out.add(c);
            return out;
        }

        /** Is c sent to the client ({@link #shown})? */
        public static boolean sends(LiquidCell c) {
            return c.boundary() || c.level() - c.floor() >= SHOWN || c.running();
        }

        /** What it shows, to skip sending the same again. */
        int digest() {
            int h = java.util.Arrays.hashCode(column);
            h = 31 * h + java.util.Arrays.hashCode(floor);
            h = 31 * h + java.util.Arrays.hashCode(level);
            h = 31 * h + java.util.Arrays.hashCode(flags);
            h = 31 * h + java.util.Arrays.hashCode(vx);
            h = 31 * h + java.util.Arrays.hashCode(vz);
            return 31 * h + java.util.Arrays.hashCode(film);
        }

        public static Surface of(int chunkX, int chunkZ, List<LiquidCell> wet) {
            int n = wet.size();
            byte[] column = new byte[n], flags = new byte[n];
            int[] floor = new int[n], level = new int[n];
            short[] vx = new short[n], vz = new short[n], film = new short[n];
            double[] v = new double[2];
            for (int i = 0; i < n; i++) {
                LiquidCell c = wet.get(i);
                column[i] = (byte) ((c.x() & 15) << 4 | (c.z() & 15));
                floor[i] = c.floorSlab();
                boolean running = c.running();
                double depth = c.level() - c.floor();
                // a thin running film's depth to the millimetre: a steady sheet shows the same again and is not resent (A1)
                level[i] = (int) Math.round((running && depth < SHOWN ? c.floor() + Math.round(depth * 1000) / 1000.0 : c.level()) * 4096);
                flags[i] = (byte) ((c.boundary() ? BOUNDARY : 0) | (c.sleeping() ? STILL : 0) | (running ? RUNNING : 0)
                        | (c.river() ? RIVER : 0) | c.joinedSides() << SIDES_SHIFT);
                // running: its litres over the column's square metre, mm (at least 1)
                if (running) film[i] = (short) Math.max(1, Math.min(Short.MAX_VALUE, Math.round(c.volume() / 1000.0)));
                if (c.boundary() && !c.river()) continue;
                c.velocity(v);
                vx[i] = velocity(v[0]);
                vz[i] = velocity(v[1]);
            }
            return new Surface(chunkX, chunkZ, column, floor, level, flags, vx, vz, film);
        }

        private static short velocity(double v) {
            return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v * VELOCITY)));
        }

        static void encode(Surface p, FriendlyByteBuf b) {
            b.writeInt(p.chunkX);
            b.writeInt(p.chunkZ);
            b.writeVarInt(p.column.length);
            for (int i = 0; i < p.column.length; i++) {
                b.writeByte(p.column[i]);
                b.writeInt(p.floor[i]);
                b.writeInt(p.level[i]);
                b.writeByte(p.flags[i]);
                b.writeShort(p.vx[i]);
                b.writeShort(p.vz[i]);
                b.writeShort(p.film[i]);
            }
        }

        static Surface decode(FriendlyByteBuf b) {
            int cx = b.readInt(), cz = b.readInt(), n = b.readVarInt();
            if (n < 0 || n > 16 * 16 * 64) throw new IllegalArgumentException("liquid surface: " + n + " cells");
            byte[] column = new byte[n], flags = new byte[n];
            int[] floor = new int[n], level = new int[n];
            short[] vx = new short[n], vz = new short[n], film = new short[n];
            for (int i = 0; i < n; i++) {
                column[i] = b.readByte();
                floor[i] = b.readInt();
                level[i] = b.readInt();
                flags[i] = b.readByte();
                vx[i] = b.readShort();
                vz[i] = b.readShort();
                film[i] = b.readShort();
            }
            return new Surface(cx, cz, column, floor, level, flags, vx, vz, film);
        }

        static void handle(Surface p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.liquid.ClientLiquid.receive(p)));
            s.get().setPacketHandled(true);
        }
    }

    /**
     * One chunk's rain-wet ground, all of it (none: dry; docs/gameplay/water_rain_v1_design.md 4.1): per column not dry its
     * place in the chunk (x + 16 z), the top block the rain lands on, how dark it is now (0..255) and the ticks it takes
     * to pale from dark to dry (0: water stands on it, it stays dark). Sent only when a column starts or stops holding
     * water; the client pales it from when it got this.
     */
    public record Wet(int chunkX, int chunkZ, byte[] column, short[] y, byte[] dark, int[] fade) {
        static void encode(Wet p, FriendlyByteBuf b) {
            b.writeInt(p.chunkX);
            b.writeInt(p.chunkZ);
            b.writeVarInt(p.column.length);
            for (int i = 0; i < p.column.length; i++) {
                b.writeByte(p.column[i]);
                b.writeShort(p.y[i]);
                b.writeByte(p.dark[i]);
                b.writeVarInt(p.fade[i]);
            }
        }

        static Wet decode(FriendlyByteBuf b) {
            int cx = b.readInt(), cz = b.readInt(), n = b.readVarInt();
            if (n < 0 || n > 256) throw new IllegalArgumentException("liquid wet ground: " + n + " columns");
            byte[] column = new byte[n], dark = new byte[n];
            short[] y = new short[n];
            int[] fade = new int[n];
            for (int i = 0; i < n; i++) {
                column[i] = b.readByte();
                y[i] = b.readShort();
                dark[i] = b.readByte();
                fade[i] = b.readVarInt();
            }
            return new Wet(cx, cz, column, y, dark, fade);
        }

        static void handle(Wet p, Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.liquid.ClientRainWet.receive(p)));
            s.get().setPacketHandled(true);
        }
    }
}
