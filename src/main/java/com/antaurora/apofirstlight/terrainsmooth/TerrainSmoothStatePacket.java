package com.antaurora.apofirstlight.terrainsmooth;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the server's Terrain Smooth state (TerrainSmooth.SERVER), sent at login and on development changes. */
public record TerrainSmoothStatePacket(boolean enabled, boolean shapes) {
    public static void encode(TerrainSmoothStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
        buffer.writeBoolean(packet.shapes);
    }

    public static TerrainSmoothStatePacket decode(FriendlyByteBuf buffer) {
        return new TerrainSmoothStatePacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(TerrainSmoothStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.antaurora.apofirstlight.terrainsmooth.client.TerrainSmoothClient.apply(packet.enabled, packet.shapes)));
        context.setPacketHandled(true);
    }
}
