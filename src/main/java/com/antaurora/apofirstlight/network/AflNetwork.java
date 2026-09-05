package com.antaurora.apofirstlight.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class AflNetwork {
    private static final String PROTOCOL = "9";
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
        channel.registerMessage(nextId++, CrusherBalanceSyncS2CPacket.class,
                CrusherBalanceSyncS2CPacket::encode, CrusherBalanceSyncS2CPacket::decode,
                CrusherBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, CompressorBalanceSyncS2CPacket.class,
                CompressorBalanceSyncS2CPacket::encode, CompressorBalanceSyncS2CPacket::decode,
                CompressorBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, AlloyFurnaceBalanceSyncS2CPacket.class,
                AlloyFurnaceBalanceSyncS2CPacket::encode, AlloyFurnaceBalanceSyncS2CPacket::decode,
                AlloyFurnaceBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, ProcessingMachineBalanceSyncS2CPacket.class,
                ProcessingMachineBalanceSyncS2CPacket::encode,
                ProcessingMachineBalanceSyncS2CPacket::decode,
                ProcessingMachineBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, FluidPipeVisualS2CPacket.class,
                FluidPipeVisualS2CPacket::encode, FluidPipeVisualS2CPacket::decode,
                FluidPipeVisualS2CPacket::handle);
        channel.registerMessage(nextId++, ExplosionTinnitusS2CPacket.class,
                ExplosionTinnitusS2CPacket::encode, ExplosionTinnitusS2CPacket::decode,
                ExplosionTinnitusS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    private AflNetwork() {
    }

    public static void sendExplosionTinnitus(ServerPlayer player, float severity) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new ExplosionTinnitusS2CPacket(severity, player.getId(),
                        player.level().dimension().location()), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public record ExplosionTinnitusS2CPacket(float severity, int playerId, ResourceLocation dimension) {
        public static void encode(ExplosionTinnitusS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeFloat(packet.severity);
            buffer.writeVarInt(packet.playerId);
            buffer.writeResourceLocation(packet.dimension);
        }

        public static ExplosionTinnitusS2CPacket decode(FriendlyByteBuf buffer) {
            return new ExplosionTinnitusS2CPacket(buffer.readFloat(), buffer.readVarInt(),
                    buffer.readResourceLocation());
        }

        public static void handle(ExplosionTinnitusS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() == net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.antaurora.apofirstlight.client.ExplosionTinnitusClientState
                                .trigger(packet.severity, packet.playerId, packet.dimension)));
            }
            context.setPacketHandled(true);
        }
    }

    public static void sendRadiation(ServerPlayer player, double finalRadiation) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new RadiationSyncPacket(finalRadiation), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendGeigerData(ServerPlayer player, double measuredRadiation, double cumulativeDose,
                                      double residualRadiationRate, RadiationZone zone) {
        if (channel == null) throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        channel.sendTo(new GeigerDataS2CPacket(measuredRadiation, cumulativeDose, residualRadiationRate, zone), player.connection.connection,
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

    public static void sendCrusherBalance(ServerPlayer player, int workFePerTick) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new CrusherBalanceSyncS2CPacket(workFePerTick), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendCompressorBalance(ServerPlayer player, int workFePerTick) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new CompressorBalanceSyncS2CPacket(workFePerTick), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendAlloyFurnaceBalance(ServerPlayer player, int workFePerTick) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new AlloyFurnaceBalanceSyncS2CPacket(workFePerTick), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendProcessingMachineBalance(ServerPlayer player,
                                                    int chemicalWorkFePerTick,
                                                    int industrialWorkFePerTickPerLane) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new ProcessingMachineBalanceSyncS2CPacket(
                        chemicalWorkFePerTick, industrialWorkFePerTickPerLane),
                player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendFluidPipeVisuals(ServerLevel level,
                                            Collection<FluidPipeVisualUpdate> updates) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        Map<ChunkPos, List<FluidPipeVisualUpdate>> byChunk = new LinkedHashMap<>();
        for (FluidPipeVisualUpdate update : updates) {
            byChunk.computeIfAbsent(new ChunkPos(update.position()), ignored -> new ArrayList<>())
                    .add(update);
        }
        byChunk.forEach((chunkPosition, chunkUpdates) -> {
            if (level.getChunkSource().hasChunk(chunkPosition.x, chunkPosition.z)) {
                channel.send(PacketDistributor.TRACKING_CHUNK.with(
                                () -> level.getChunk(chunkPosition.x, chunkPosition.z)),
                        new FluidPipeVisualS2CPacket(chunkUpdates));
            }
        });
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

    public record GeigerDataS2CPacket(double measuredRadiation, double cumulativeDose,
                                      double residualRadiationRate, RadiationZone zone) {
        public static void encode(GeigerDataS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.measuredRadiation);
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
                            .update(packet.measuredRadiation, packet.cumulativeDose, packet.residualRadiationRate, packet.zone)));
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

    public record CrusherBalanceSyncS2CPacket(int workFePerTick) {
        public static void encode(CrusherBalanceSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.workFePerTick);
        }

        public static CrusherBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new CrusherBalanceSyncS2CPacket(buffer.readVarInt());
        }

        public static void handle(CrusherBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientCrusherBalanceData
                            .update(packet.workFePerTick)));
            context.setPacketHandled(true);
        }
    }

    public record CompressorBalanceSyncS2CPacket(int workFePerTick) {
        public static void encode(CompressorBalanceSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.workFePerTick);
        }

        public static CompressorBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new CompressorBalanceSyncS2CPacket(buffer.readVarInt());
        }

        public static void handle(CompressorBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientCompressorBalanceData
                            .update(packet.workFePerTick)));
            context.setPacketHandled(true);
        }
    }

    public record AlloyFurnaceBalanceSyncS2CPacket(int workFePerTick) {
        public static void encode(AlloyFurnaceBalanceSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.workFePerTick);
        }

        public static AlloyFurnaceBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new AlloyFurnaceBalanceSyncS2CPacket(buffer.readVarInt());
        }

        public static void handle(AlloyFurnaceBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientAlloyFurnaceBalanceData
                            .update(packet.workFePerTick)));
            context.setPacketHandled(true);
        }
    }

    public record ProcessingMachineBalanceSyncS2CPacket(int chemicalWorkFePerTick,
                                                         int industrialWorkFePerTickPerLane) {
        public static void encode(ProcessingMachineBalanceSyncS2CPacket packet,
                                  FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.chemicalWorkFePerTick);
            buffer.writeVarInt(packet.industrialWorkFePerTickPerLane);
        }

        public static ProcessingMachineBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new ProcessingMachineBalanceSyncS2CPacket(buffer.readVarInt(), buffer.readVarInt());
        }

        public static void handle(ProcessingMachineBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientProcessingMachineBalanceData
                            .update(packet.chemicalWorkFePerTick,
                                    packet.industrialWorkFePerTickPerLane)));
            context.setPacketHandled(true);
        }
    }

    public record FluidPipeVisualUpdate(BlockPos position, ResourceLocation fluidId,
                                        int directionMask, boolean active, boolean isFlowing) {
        private static final ResourceLocation EMPTY_FLUID_ID = new ResourceLocation("minecraft", "empty");

        public FluidPipeVisualUpdate {
            position = position.immutable();
        }

        public static FluidPipeVisualUpdate clear(BlockPos position) {
            return new FluidPipeVisualUpdate(position, EMPTY_FLUID_ID, 0, false, false);
        }
    }

    public record FluidPipeVisualS2CPacket(List<FluidPipeVisualUpdate> updates) {
        public FluidPipeVisualS2CPacket {
            updates = List.copyOf(updates);
        }

        public static void encode(FluidPipeVisualS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.updates.size());
            for (FluidPipeVisualUpdate update : packet.updates) {
                buffer.writeBlockPos(update.position());
                buffer.writeBoolean(update.active());
                if (update.active()) {
                    buffer.writeResourceLocation(update.fluidId());
                    buffer.writeVarInt(update.directionMask());
                    buffer.writeBoolean(update.isFlowing());
                }
            }
        }

        public static FluidPipeVisualS2CPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<FluidPipeVisualUpdate> updates = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                BlockPos position = buffer.readBlockPos();
                boolean active = buffer.readBoolean();
                updates.add(active
                        ? new FluidPipeVisualUpdate(position, buffer.readResourceLocation(),
                        buffer.readVarInt(), true, buffer.readBoolean())
                        : FluidPipeVisualUpdate.clear(position));
            }
            return new FluidPipeVisualS2CPacket(updates);
        }

        public static void handle(FluidPipeVisualS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientFluidPipeVisuals
                            .apply(packet.updates)));
            context.setPacketHandled(true);
        }
    }
}
