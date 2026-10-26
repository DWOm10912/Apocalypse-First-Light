package com.antaurora.apofirstlight.time;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the server's day length in real minutes (World Time V1), sent at login and when it changes. */
public record WorldTimePacket(int minutes) {
    public static void encode(WorldTimePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.minutes);
    }

    public static WorldTimePacket decode(FriendlyByteBuf buffer) {
        return new WorldTimePacket(buffer.readVarInt());
    }

    public static void handle(WorldTimePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> WorldTimeClientState.apply(packet.minutes));
        context.setPacketHandled(true);
    }
}
