package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.vehicle.physics.Drivetrain;
import com.antaurora.apofirstlight.vehicle.physics.VehicleState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vehicle physics V0-b (docs/vehicles/first_pickup_v0_design.md section 4.3): the driver's client simulates its truck and
 * sends the state every tick ({@link Drive}); the server checks it, takes it and passes it on to the clients that track
 * the truck ({@link Sync}), which ease towards it. Without a driver the server simulates (parked: handbrake) and sends
 * {@link Sync} while the truck moves. A client that starts tracking a truck gets its state at once. The vanilla vehicle
 * move packet is ignored for the truck (mixin/ServerGamePacketListenerVehicleMixin): its checks know neither the smoothed
 * terrain nor a tilted body.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class VehiclePackets {
    /** Driver's client -> server: its truck's state this tick. */
    public record Drive(int entity, VehicleState state) {}

    /** Server -> clients: a truck's state; correct: the driver must take it too (the server refused its own). */
    public record Sync(int entity, VehicleState state, boolean correct) {}

    /** Driver's client -> server: the light switch or the high beam (PickupTruckEntity#SWITCH_*). */
    public record Lamps(int entity, int action) {}

    /** The client's handling of a {@link Sync} (client/vehicle/VehicleDriving, set at client setup). */
    public static volatile Consumer<Sync> clientSync = s -> {};

    private VehiclePackets() {
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Drive.class, (p, b) -> {
            b.writeVarInt(p.entity());
            write(b, p.state());
        }, b -> new Drive(b.readVarInt(), read(b)), VehiclePackets::onDrive, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Sync.class, (p, b) -> {
            b.writeVarInt(p.entity());
            write(b, p.state());
            b.writeBoolean(p.correct());
        }, b -> new Sync(b.readVarInt(), read(b), b.readBoolean()), (p, s) -> {
            NetworkEvent.Context context = s.get();
            context.enqueueWork(() -> clientSync.accept(p));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Lamps.class, (p, b) -> {
            b.writeVarInt(p.entity());
            b.writeVarInt(p.action());
        }, b -> new Lamps(b.readVarInt(), b.readVarInt()), VehiclePackets::onLamps, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    private static void onLamps(Lamps packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (player.level().getEntity(packet.entity()) instanceof PickupTruckEntity truck && truck.getControllingPassenger() == player)
                truck.lampSwitch(packet.action());
        });
        context.setPacketHandled(true);
    }

    private static void write(FriendlyByteBuf b, VehicleState s) {
        b.writeDouble(s.x());
        b.writeDouble(s.y());
        b.writeDouble(s.z());
        for (float f : new float[]{s.qx(), s.qy(), s.qz(), s.qw(), s.vx(), s.vy(), s.vz(), s.wx(), s.wy(), s.wz(), s.steer()}) b.writeFloat(f);
        for (int i = 0; i < 4; i++) {
            b.writeFloat(s.travel()[i]);
            b.writeFloat(s.spinRate()[i]);
        }
        Drivetrain.State d = s.drive();
        b.writeByte((s.asleep() ? 1 : 0) | (d.running() ? 2 : 0) | (d.locked() ? 4 : 0) | (d.fourWheel() ? 8 : 0) | (d.low() ? 16 : 0));
        b.writeByte(d.gear());
        b.writeByte(d.shiftTarget());
        b.writeByte(d.ignition());
        for (float f : new float[]{d.omega(), d.clutch(), d.pedal(), d.shiftLeft(), d.shiftTotal(), d.ignitionTime(), d.coolant(), d.fuelFlow()}) b.writeFloat(f);
    }

    private static VehicleState read(FriendlyByteBuf b) {
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
        float[] f = new float[11];
        for (int i = 0; i < f.length; i++) f[i] = b.readFloat();
        float[] travel = new float[4], spin = new float[4];
        for (int i = 0; i < 4; i++) {
            travel[i] = b.readFloat();
            spin[i] = b.readFloat();
        }
        int flags = b.readByte();
        byte gear = b.readByte(), target = b.readByte(), ignition = b.readByte();
        float[] d = new float[8];
        for (int i = 0; i < d.length; i++) d[i] = b.readFloat();
        Drivetrain.State drive = new Drivetrain.State((flags & 2) != 0, d[0], gear, d[1], (flags & 4) != 0, d[2], target, d[3], d[4],
                (flags & 8) != 0, (flags & 16) != 0, ignition, d[5], d[6], d[7]);
        return new VehicleState(x, y, z, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], travel, spin, (flags & 1) != 0, drive);
    }

    private static void onDrive(Drive packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (player.level().getEntity(packet.entity()) instanceof PickupTruckEntity truck && truck.getControllingPassenger() == player)
                truck.acceptDrive(packet.state(), player);
        });
        context.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof PickupTruckEntity truck && event.getEntity() instanceof ServerPlayer player) truck.sendStateTo(player);
    }

    /** Server: a truck's state to the clients that track it. */
    static void toTracking(PickupTruckEntity truck, Sync sync) {
        AflNetwork.vehicleToTracking(truck, sync);
    }
}
