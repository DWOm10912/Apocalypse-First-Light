package com.antaurora.apofirstlight.temperature;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

/** Temperature V1: the own state to the client; the client never reports temperature. */
public final class TemperaturePackets {
    private TemperaturePackets() {}

    /**
     * core / ambient in °C (ambient for a future readout device); coldness / heat / danger 0..1 for the orb, computed
     * by the server from its numbers; swayScale: weapon sway × this while shivering; trend −1 (cooling) .. +1 (warming):
     * where the core is heading, for the dial's arrows; target −1 .. +1: the core's target temperature on the dial's scale
     * (its heat − coldness), for the target frame; comfortMin °C: the lowest felt temperature the worn insulation keeps
     * comfortable (comfortLow − insulation), for the inventory page; coldStage / heatStage 0..3: the stage the core is in
     * (TemperatureConfig cold / heat stages) and wet 0..1: how soaked the body is, for the HUD's status badges (HUD V2).
     */
    public record State(float core, float ambient, float coldness, float heat, float danger, float swayScale, float trend,
                        float target, float comfortMin, int coldStage, int heatStage, float wet) {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, (p, b) -> {
            b.writeFloat(p.core()); b.writeFloat(p.ambient()); b.writeFloat(p.coldness()); b.writeFloat(p.heat());
            b.writeFloat(p.danger()); b.writeFloat(p.swayScale()); b.writeFloat(p.trend()); b.writeFloat(p.target());
            b.writeFloat(p.comfortMin()); b.writeByte(p.coldStage()); b.writeByte(p.heatStage()); b.writeFloat(p.wet());
        }, b -> new State(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readByte(), b.readByte(), b.readFloat()),
                TemperaturePackets::handleState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }
    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTemperature.accept(packet)));
        c.setPacketHandled(true);
    }
}
