package com.antaurora.apofirstlight.weather;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Regional weather V1 (docs/gameplay/regional_weather_v1_plan.md 5): the server's weather to a player, so its client runs
 * the same model (AflWeather): the weather seed, the climate grid (one byte a value) and what was set by hand.
 */
public final class WeatherPackets {
    private WeatherPackets() {
    }

    public record Sync(long seed, double originX, double originZ, double step, int nx, int nz, byte[] grid,
                       List<RegionalWeather.Force> forces, List<RegionalWeather.Cell> cells) {
        static void encode(Sync p, FriendlyByteBuf b) {
            b.writeLong(p.seed);
            b.writeDouble(p.originX);
            b.writeDouble(p.originZ);
            b.writeDouble(p.step);
            b.writeVarInt(p.nx);
            b.writeVarInt(p.nz);
            b.writeByteArray(p.grid);
            b.writeVarInt(p.forces.size());
            for (RegionalWeather.Force f : p.forces) {
                b.writeVarInt(f.regime().ordinal());
                b.writeLong(f.start());
                b.writeLong(f.end());
            }
            b.writeVarInt(p.cells.size());
            for (RegionalWeather.Cell c : p.cells) writeCell(b, c);
        }

        static Sync decode(FriendlyByteBuf b) {
            long seed = b.readLong();
            double ox = b.readDouble(), oz = b.readDouble(), step = b.readDouble();
            int nx = b.readVarInt(), nz = b.readVarInt();
            byte[] grid = b.readByteArray(1 << 20);
            int n = b.readVarInt();
            List<RegionalWeather.Force> forces = new ArrayList<>(n);
            for (int i = 0; i < n; i++) forces.add(new RegionalWeather.Force(WeatherRegime.values()[b.readVarInt()], b.readLong(), b.readLong()));
            n = b.readVarInt();
            List<RegionalWeather.Cell> cells = new ArrayList<>(n);
            for (int i = 0; i < n; i++) cells.add(readCell(b));
            return new Sync(seed, ox, oz, step, nx, nz, grid, forces, cells);
        }
    }

    static void writeCell(FriendlyByteBuf b, RegionalWeather.Cell c) {
        b.writeLong(c.id());
        b.writeVarInt(c.kind().ordinal());
        b.writeDouble(c.x0());
        b.writeDouble(c.z0());
        b.writeDouble(c.vx());
        b.writeDouble(c.vz());
        b.writeLong(c.born());
        b.writeVarInt(c.life());
        b.writeDouble(c.radius());
        b.writeDouble(c.peak());
        b.writeDouble(c.strength());
    }

    static RegionalWeather.Cell readCell(FriendlyByteBuf b) {
        return new RegionalWeather.Cell(b.readLong(), RegionalWeather.Kind.values()[b.readVarInt()], b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readLong(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Sync.class, Sync::encode, Sync::decode, WeatherPackets::handleSync,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void handleSync(Sync packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> AflWeather.accept(packet));
        c.setPacketHandled(true);
    }
}
