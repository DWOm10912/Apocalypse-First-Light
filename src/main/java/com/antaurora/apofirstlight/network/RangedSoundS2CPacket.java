package com.antaurora.apofirstlight.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> each player within {@code radius} of a sound (noise/RangedSound): play it there, fading to silence at the
 * radius (client/RangedSoundInstance). The seed picks the same variant on every client, as vanilla's sound packets do.
 */
public record RangedSoundS2CPacket(ResourceLocation sound, SoundSource source, double x, double y, double z, float radius,
                                   float volume, float pitch, long seed) {
    static void encode(RangedSoundS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.sound);
        buffer.writeEnum(packet.source);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.radius);
        buffer.writeFloat(packet.volume);
        buffer.writeFloat(packet.pitch);
        buffer.writeLong(packet.seed);
    }

    static RangedSoundS2CPacket decode(FriendlyByteBuf buffer) {
        return new RangedSoundS2CPacket(buffer.readResourceLocation(), buffer.readEnum(SoundSource.class), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readLong());
    }

    static void handle(RangedSoundS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.RangedSoundInstance.play(packet)));
        context.setPacketHandled(true);
    }
}
