package com.antaurora.apofirstlight.hearing;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Tinnitus V2: the own hearing load and stage, and a jolt (a blast rang the ears at once), to the player (client/TinnitusClient). */
public final class HearingPackets {
    private HearingPackets() {
    }

    public record State(float load, byte stage) {
        static void encode(State p, FriendlyByteBuf b) {
            b.writeFloat(p.load);
            b.writeByte(p.stage);
        }

        static State decode(FriendlyByteBuf b) {
            return new State(b.readFloat(), b.readByte());
        }
    }

    /** strength 0..1: how loud and how long the ring, how deaf for how long. */
    public record Jolt(float strength) {
        static void encode(Jolt p, FriendlyByteBuf b) {
            b.writeFloat(p.strength);
        }

        static Jolt decode(FriendlyByteBuf b) {
            return new Jolt(b.readFloat());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, State::encode, State::decode, HearingPackets::handleState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Jolt.class, Jolt::encode, Jolt::decode, HearingPackets::handleJolt,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.TinnitusClient.accept(packet)));
        c.setPacketHandled(true);
    }

    private static void handleJolt(Jolt packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.TinnitusClient.jolt(packet.strength())));
        c.setPacketHandled(true);
    }
}
