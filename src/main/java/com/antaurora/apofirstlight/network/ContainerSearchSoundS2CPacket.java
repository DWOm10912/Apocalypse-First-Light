package com.antaurora.apofirstlight.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Server -> the clients tracking a searchable container's chunk: its search is running, with the sound to loop (sent at
 * once and then repeated as a keep-alive, AflContainerSearchState), or it stopped ({@code sound} null).
 */
public record ContainerSearchSoundS2CPacket(BlockPos pos, @Nullable ResourceLocation sound) {
    static void encode(ContainerSearchSoundS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.sound != null);
        if (packet.sound != null) buffer.writeResourceLocation(packet.sound);
    }

    static ContainerSearchSoundS2CPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        return new ContainerSearchSoundS2CPacket(pos, buffer.readBoolean() ? buffer.readResourceLocation() : null);
    }

    static void handle(ContainerSearchSoundS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.ContainerSearchSoundController.receive(packet.pos(), packet.sound())));
        context.setPacketHandled(true);
    }
}
