package com.antaurora.apofirstlight.thirst;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

/** Thirst V1: the own thirst to the client; a sip request from the client (the server checks everything itself). */
public final class ThirstPackets {
    private ThirstPackets() {}

    public record State(float value, float max) {}
    /** Sneak + empty hand + right-click while looking at a water source. */
    public record Sip() {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, (p, b) -> { b.writeFloat(p.value()); b.writeFloat(p.max()); },
                b -> new State(b.readFloat(), b.readFloat()), ThirstPackets::handleState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Sip.class, (p, b) -> {}, b -> new Sip(), ThirstPackets::handleSip,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }
    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientThirst.accept(packet)));
        c.setPacketHandled(true);
    }
    private static void handleSip(Sip packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        var player = c.getSender();
        if (player != null) c.enqueueWork(() -> PlayerThirst.sip(player));
        c.setPacketHandled(true);
    }
}
