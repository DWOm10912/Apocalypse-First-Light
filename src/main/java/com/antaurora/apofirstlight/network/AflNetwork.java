package com.antaurora.apofirstlight.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;

import java.util.function.Supplier;

public final class AflNetwork {
    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;
    private static int nextId;

    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "main"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(nextId++, RadiationSyncPacket.class,
                RadiationSyncPacket::encode, RadiationSyncPacket::decode, RadiationSyncPacket::handle);
    }

    private AflNetwork() {
    }

    public static void sendRadiation(ServerPlayer player, double finalRadiation) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new RadiationSyncPacket(finalRadiation), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public record RadiationSyncPacket(double finalRadiation) {
        public static void encode(RadiationSyncPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.finalRadiation);
        }

        public static RadiationSyncPacket decode(FriendlyByteBuf buffer) {
            return new RadiationSyncPacket(buffer.readDouble());
        }

        public static void handle(RadiationSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.radiation.client.RadiationAtmosphereClient
                            .setTargetRadiation(packet.finalRadiation)));
            context.setPacketHandled(true);
        }
    }
}
