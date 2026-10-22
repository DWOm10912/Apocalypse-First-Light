package com.antaurora.apofirstlight.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A shovel stroke's phase (interaction/ShovelAction: START, COMMIT, CANCEL, END) to the player and everyone tracking them,
 * with its mode and the ground point it works (the third-person body's anchor). C2S: {@link Start} (the mode the client
 * chose from its input) and {@link Cancel} (let go before the commit).
 */
public record ShovelPacket(UUID player, UUID action, int mode, int phase, Vec3 target) {
    static void encode(ShovelPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.player);
        b.writeUUID(p.action);
        b.writeByte(p.mode);
        b.writeByte(p.phase);
        b.writeDouble(p.target.x);
        b.writeDouble(p.target.y);
        b.writeDouble(p.target.z);
    }

    static ShovelPacket decode(FriendlyByteBuf b) {
        return new ShovelPacket(b.readUUID(), b.readUUID(), b.readUnsignedByte(), b.readUnsignedByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }

    static void handle(ShovelPacket p, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.antaurora.apofirstlight.client.ShovelClient.receive(p)));
        c.setPacketHandled(true);
    }

    public record Start(int mode) {
        static void encode(Start p, FriendlyByteBuf b) { b.writeByte(p.mode); }

        static Start decode(FriendlyByteBuf b) { return new Start(b.readUnsignedByte()); }

        static void handle(Start p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null) com.antaurora.apofirstlight.interaction.ShovelAction.begin(c.getSender(), p.mode);
            });
            c.setPacketHandled(true);
        }
    }

    public record Cancel(UUID action) {
        static void encode(Cancel p, FriendlyByteBuf b) { b.writeUUID(p.action); }

        static Cancel decode(FriendlyByteBuf b) { return new Cancel(b.readUUID()); }

        static void handle(Cancel p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null) com.antaurora.apofirstlight.interaction.ShovelAction.cancel(c.getSender(), p.action);
            });
            c.setPacketHandled(true);
        }
    }
}
