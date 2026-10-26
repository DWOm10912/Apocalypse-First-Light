package com.antaurora.apofirstlight.earthwork;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: one chunk's whole carved rock (null: none any more), when it changes or a player starts tracking it. */
public record CarveChunkPacket(int cx, int cz, CarveChunk data) {
    public static void encode(CarveChunkPacket p, FriendlyByteBuf b) {
        b.writeInt(p.cx);
        b.writeInt(p.cz);
        b.writeBoolean(p.data != null);
        if (p.data != null) p.data.write(b);
    }

    public static CarveChunkPacket decode(FriendlyByteBuf b) {
        int cx = b.readInt(), cz = b.readInt();
        return new CarveChunkPacket(cx, cz, b.readBoolean() ? CarveChunk.read(b) : null);
    }

    public static void handle(CarveChunkPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context c = supplier.get();
        c.enqueueWork(() -> RockCarve.received(p.cx, p.cz, p.data));
        c.setPacketHandled(true);
    }
}
