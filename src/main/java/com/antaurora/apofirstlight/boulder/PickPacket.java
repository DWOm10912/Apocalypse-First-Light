package com.antaurora.apofirstlight.boulder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Rock R1a: a pick mattock strike's phase (BoulderStrike START, HIT, CANCEL, END) to the striker and everyone tracking them,
 * with the rock point it strikes (the body's anchor). C2S: {@link Start} (the use key on a boulder) and {@link Cancel}
 * (switched away before the hit).
 */
public record PickPacket(UUID player, UUID action, int phase, Vec3 target) {
    private static SimpleChannel channel;

    public record Start() {}

    public record Cancel(UUID action) {}

    /** Registers the three messages from id on; the next free id. */
    public static int register(SimpleChannel ch, int id) {
        channel = ch;
        ch.registerMessage(id++, PickPacket.class, PickPacket::encode, PickPacket::decode, PickPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(id++, Start.class, (p, b) -> {}, b -> new Start(), (p, s) -> {
            var c = s.get();
            c.enqueueWork(() -> { if (c.getSender() != null) BoulderStrike.begin(c.getSender()); });
            c.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        ch.registerMessage(id++, Cancel.class, (p, b) -> b.writeUUID(p.action), b -> new Cancel(b.readUUID()), (p, s) -> {
            var c = s.get();
            c.enqueueWork(() -> { if (c.getSender() != null) BoulderStrike.cancel(c.getSender(), p.action); });
            c.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    static void encode(PickPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.player);
        b.writeUUID(p.action);
        b.writeByte(p.phase);
        b.writeDouble(p.target.x);
        b.writeDouble(p.target.y);
        b.writeDouble(p.target.z);
    }

    static PickPacket decode(FriendlyByteBuf b) {
        return new PickPacket(b.readUUID(), b.readUUID(), b.readUnsignedByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }

    static void handle(PickPacket p, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.antaurora.apofirstlight.client.PickClient.receive(p)));
        c.setPacketHandled(true);
    }

    /** A strike's phase to the striker and everyone tracking them. */
    static void send(ServerPlayer player, UUID action, int phase, Vec3 target) {
        if (channel != null) channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PickPacket(player.getUUID(), action, phase, target));
    }

    public static void start() {
        if (channel != null) channel.sendToServer(new Start());
    }

    public static void cancel(UUID action) {
        if (channel != null) channel.sendToServer(new Cancel(action));
    }
}
