package com.antaurora.apofirstlight.road;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: one chunk's whole broken asphalt (null: none any more), when it changes or a player starts tracking it. */
public record RoadDamagePacket(int cx, int cz, RoadDamageChunk data) {
    public static void encode(RoadDamagePacket p, FriendlyByteBuf b) {
        b.writeInt(p.cx);
        b.writeInt(p.cz);
        b.writeBoolean(p.data != null);
        if (p.data != null) p.data.write(b);
    }

    public static RoadDamagePacket decode(FriendlyByteBuf b) {
        int cx = b.readInt(), cz = b.readInt();
        return new RoadDamagePacket(cx, cz, b.readBoolean() ? RoadDamageChunk.read(b) : null);
    }

    public static void handle(RoadDamagePacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context c = supplier.get();
        c.enqueueWork(() -> RoadDamage.received(p.cx, p.cz, p.data));
        c.setPacketHandled(true);
    }
}
