package com.antaurora.apofirstlight.health;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Lightning injury V1: the own states (LightningInjury) and the moment of a strike, to the player (client/LightningInjuryClient);
 * why a player lies down, to everyone tracking it.
 */
public final class LightningPackets {
    private LightningPackets() {
    }

    /** Ticks left and in all for fainted, paralysed and weak legs; the eardrum's and aftermath's share left (0..1); the bad ear (-1 left, 1 right). */
    public record State(int faint, int faintTotal, int paralysis, int paralysisTotal, int weak, int weakTotal,
                        float eardrum, byte eardrumSide, float aftermath) {
        static void encode(State p, FriendlyByteBuf b) {
            b.writeVarInt(p.faint);
            b.writeVarInt(p.faintTotal);
            b.writeVarInt(p.paralysis);
            b.writeVarInt(p.paralysisTotal);
            b.writeVarInt(p.weak);
            b.writeVarInt(p.weakTotal);
            b.writeFloat(p.eardrum);
            b.writeByte(p.eardrumSide);
            b.writeFloat(p.aftermath);
        }

        static State decode(FriendlyByteBuf b) {
            return new State(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readFloat(), b.readByte(), b.readFloat());
        }
    }

    /** Struck (way: LightningInjury.Way ordinal); out: it knocked them out (or dead). */
    public record Struck(byte way, boolean out) {
        static void encode(Struck p, FriendlyByteBuf b) {
            b.writeByte(p.way);
            b.writeBoolean(p.out);
        }

        static Struck decode(FriendlyByteBuf b) {
            return new Struck(b.readByte(), b.readBoolean());
        }
    }

    /**
     * Why a player lies on the ground (0 not for the lightning, 1 fainted, 2 paralysed legs), to the players tracking it and
     * itself: the others' skeleton draws them out cold or dragging their legs (client/skeleton/ProneClient).
     */
    public record Lying(int entity, byte kind) {
        public static final byte NONE = 0, OUT = 1, DRAG = 2;

        static void encode(Lying p, FriendlyByteBuf b) {
            b.writeVarInt(p.entity);
            b.writeByte(p.kind);
        }

        static Lying decode(FriendlyByteBuf b) {
            return new Lying(b.readVarInt(), b.readByte());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, State::encode, State::decode, LightningPackets::handleState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Struck.class, Struck::encode, Struck::decode, LightningPackets::handleStruck,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Lying.class, Lying::encode, Lying::decode, LightningPackets::handleLying,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> ClientLightningInjury.accept(packet));
        c.setPacketHandled(true);
    }

    private static void handleStruck(Struck packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.LightningInjuryClient.struck(packet)));
        c.setPacketHandled(true);
    }

    private static void handleLying(Lying packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.skeleton.ProneClient.lying(packet.entity(), packet.kind())));
        c.setPacketHandled(true);
    }
}
