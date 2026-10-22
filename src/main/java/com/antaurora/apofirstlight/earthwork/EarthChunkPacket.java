package com.antaurora.apofirstlight.earthwork;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: one chunk's whole earthwork data (null: it has none any more), when it changes or a player starts tracking it. */
public record EarthChunkPacket(int cx, int cz, EarthChunk data) {
    public static void encode(EarthChunkPacket p, FriendlyByteBuf b) {
        b.writeInt(p.cx);
        b.writeInt(p.cz);
        b.writeBoolean(p.data != null);
        if (p.data != null) p.data.write(b);
    }

    public static EarthChunkPacket decode(FriendlyByteBuf b) {
        int cx = b.readInt(), cz = b.readInt();
        return new EarthChunkPacket(cx, cz, b.readBoolean() ? EarthChunk.read(b) : null);
    }

    public static void handle(EarthChunkPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context c = supplier.get();
        c.enqueueWork(() -> Earthwork.received(p.cx, p.cz, p.data));
        c.setPacketHandled(true);
    }
}
