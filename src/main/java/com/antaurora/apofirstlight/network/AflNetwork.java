package com.antaurora.apofirstlight.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class AflNetwork {
    private static final String PROTOCOL = "2";
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
        channel.registerMessage(nextId++, GeigerDataS2CPacket.class,
                GeigerDataS2CPacket::encode, GeigerDataS2CPacket::decode, GeigerDataS2CPacket::handle);
        channel.registerMessage(nextId++, ThermalGeneratorFuelSyncS2CPacket.class,
                ThermalGeneratorFuelSyncS2CPacket::encode, ThermalGeneratorFuelSyncS2CPacket::decode,
                ThermalGeneratorFuelSyncS2CPacket::handle);
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

    public static void sendGeigerData(ServerPlayer player, double finalRadiation, double cumulativeDose,
                                      double residualRadiationRate, RadiationZone zone) {
        if (channel == null) throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        channel.sendTo(new GeigerDataS2CPacket(finalRadiation, cumulativeDose, residualRadiationRate, zone), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendThermalGeneratorFuels(ServerPlayer player,
                                                  Map<ResourceLocation, Integer> fuelEnergies) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new ThermalGeneratorFuelSyncS2CPacket(fuelEnergies), player.connection.connection,
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

    public record GeigerDataS2CPacket(double finalRadiation, double cumulativeDose,
                                      double residualRadiationRate, RadiationZone zone) {
        public static void encode(GeigerDataS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.finalRadiation);
            buffer.writeDouble(packet.cumulativeDose);
            buffer.writeDouble(packet.residualRadiationRate);
            buffer.writeEnum(packet.zone);
        }

        public static GeigerDataS2CPacket decode(FriendlyByteBuf buffer) {
            return new GeigerDataS2CPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readEnum(RadiationZone.class));
        }

        public static void handle(GeigerDataS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientGeigerData
                            .update(packet.finalRadiation, packet.cumulativeDose, packet.residualRadiationRate, packet.zone)));
            context.setPacketHandled(true);
        }
    }

    public record ThermalGeneratorFuelSyncS2CPacket(Map<ResourceLocation, Integer> fuelEnergies) {
        public ThermalGeneratorFuelSyncS2CPacket {
            fuelEnergies = Map.copyOf(fuelEnergies);
        }

        public static void encode(ThermalGeneratorFuelSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.fuelEnergies.size());
            packet.fuelEnergies.forEach((itemId, energyFe) -> {
                buffer.writeResourceLocation(itemId);
                buffer.writeVarInt(energyFe);
            });
        }

        public static ThermalGeneratorFuelSyncS2CPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Map<ResourceLocation, Integer> fuelEnergies = new LinkedHashMap<>();
            for (int index = 0; index < size; index++) {
                fuelEnergies.put(buffer.readResourceLocation(), buffer.readVarInt());
            }
            return new ThermalGeneratorFuelSyncS2CPacket(fuelEnergies);
        }

        public static void handle(ThermalGeneratorFuelSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData
                            .replace(packet.fuelEnergies)));
            context.setPacketHandled(true);
        }
    }
}
