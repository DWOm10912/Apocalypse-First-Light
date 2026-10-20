package com.antaurora.apofirstlight.health;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Survival and health V1a: the own regeneration state, the wounds and the screen effect's strength to the client; and
 * whether a player wears a bandage, to everyone tracking that player (the gauze on the forearm, client/BandageWrapRenderer,
 * BandageSync; 2026-10-19).
 */
public final class HealthPackets {
    private HealthPackets() {
    }

    /**
     * {@code regen}: {@link PlayerHealth.Regen} ordinal; {@code visual}: the wound screen effect (0..1, before the client's
     * setting); the wounds as {@link Wounds.View}s.
     */
    public record State(byte regen, float visual, List<Wounds.View> wounds) {
        static void encode(State p, FriendlyByteBuf b) {
            b.writeByte(p.regen);
            b.writeFloat(p.visual);
            b.writeVarInt(p.wounds.size());
            for (Wounds.View w : p.wounds) {
                b.writeByte(w.stage().ordinal());
                b.writeBoolean(w.cleaned());
                b.writeBoolean(w.bandaged());
                b.writeFloat(w.recovery());
            }
        }

        static State decode(FriendlyByteBuf b) {
            byte regen = b.readByte();
            float visual = b.readFloat();
            int n = Math.min(16, b.readVarInt());
            List<Wounds.View> wounds = new ArrayList<>(n);
            Wounds.Stage[] stages = Wounds.Stage.values();
            for (int i = 0; i < n; i++) {
                int stage = b.readByte();
                wounds.add(new Wounds.View(stages[Math.max(0, Math.min(stages.length - 1, stage))], b.readBoolean(), b.readBoolean(), b.readFloat()));
            }
            return new State(regen, visual, List.copyOf(wounds));
        }
    }

    /** Whether this player has at least one bandaged wound (BandageSync): the player itself and the players tracking it. */
    public record Bandaged(UUID player, boolean bandaged) {
        static void encode(Bandaged p, FriendlyByteBuf b) {
            b.writeUUID(p.player);
            b.writeBoolean(p.bandaged);
        }

        static Bandaged decode(FriendlyByteBuf b) {
            return new Bandaged(b.readUUID(), b.readBoolean());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, State::encode, State::decode, HealthPackets::handleState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Bandaged.class, Bandaged::encode, Bandaged::decode, HealthPackets::handleBandaged,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHealth.accept(packet)));
        c.setPacketHandled(true);
    }

    private static void handleBandaged(Bandaged packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHealth.acceptBandaged(packet)));
        c.setPacketHandled(true);
    }
}
