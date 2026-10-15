package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.client.survivor.SurvivorClient;
import com.antaurora.apofirstlight.client.survivor.SurvivorIdentityClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Survivor Death and Succession V0 packets (docs/gameplay/survivor_death_succession_v0.md 11, 13). The server decides
 * everything; the client only shows the death summary, the one-time mode choice and the first survivor's name choice,
 * and sends those choices back, which the server checks (mode: fresh world, mode still unset, sender is the
 * single-player host; name: SurvivorIdentity).
 */
public final class SurvivorPackets {
    private SurvivorPackets() {
    }

    /**
     * The dead survivor's summary, sent at the death (before vanilla's kill packet) and again on login while dead.
     * {@code ended}: the HARD run is over (only "end the journey" remains).
     */
    public record Summary(String name, int lifeIndex, double days, Component cause, String dimension, int x, int y, int z,
                          boolean ended) {
    }

    /** Server: open (true) or close (false) the mode choice. */
    public record ModePrompt(boolean open) {
    }

    /** Client: the host's choice, NORMAL or HARD. */
    public record ModeChoice(SurvivorMode mode) {
    }

    /**
     * Server, to every client: who a player is now (SurvivorIdentity): the survivor's name, and the look to draw
     * (SurvivorSkins id and arm model id; "account" for both is the account's own skin). Sent on login, when a successor
     * takes over and when the first survivor's name is chosen.
     */
    public record Identity(UUID player, String name, String skin, String arms) {
    }

    /**
     * Server: open (true) or close (false) the first survivor's name choice. {@code error}: empty, or why the last choice
     * was refused ("invalid", "taken"); the choice stays open then.
     */
    public record NamePrompt(boolean open, String error) {
    }

    /** Client: the chosen name, the account name or a nickname; the server checks it (SurvivorIdentity). */
    public record NameChoice(String name) {
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Summary.class, (p, b) -> {
            b.writeUtf(p.name(), 64);
            b.writeVarInt(p.lifeIndex());
            b.writeDouble(p.days());
            b.writeComponent(p.cause());
            b.writeUtf(p.dimension(), 256);
            b.writeInt(p.x());
            b.writeInt(p.y());
            b.writeInt(p.z());
            b.writeBoolean(p.ended());
        }, b -> new Summary(b.readUtf(64), b.readVarInt(), b.readDouble(), b.readComponent(), b.readUtf(256), b.readInt(),
                b.readInt(), b.readInt(), b.readBoolean()),
                (p, s) -> client(s, () -> SurvivorClient.summary(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, ModePrompt.class, (p, b) -> b.writeBoolean(p.open()), b -> new ModePrompt(b.readBoolean()),
                (p, s) -> client(s, () -> SurvivorClient.modePrompt(p.open())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, ModeChoice.class, (p, b) -> b.writeEnum(p.mode()), b -> new ModeChoice(b.readEnum(SurvivorMode.class)),
                (p, s) -> server(s, player -> Survivors.onModeChoice(player, p.mode())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Identity.class, (p, b) -> {
            b.writeUUID(p.player());
            b.writeUtf(p.name(), 64);
            b.writeUtf(p.skin(), 64);
            b.writeUtf(p.arms(), 16);
        }, b -> new Identity(b.readUUID(), b.readUtf(64), b.readUtf(64), b.readUtf(16)),
                (p, s) -> client(s, () -> SurvivorIdentityClient.set(p.player(), p.name(), p.skin(), p.arms())),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, NamePrompt.class, (p, b) -> {
            b.writeBoolean(p.open());
            b.writeUtf(p.error(), 16);
        }, b -> new NamePrompt(b.readBoolean(), b.readUtf(16)),
                (p, s) -> client(s, () -> SurvivorClient.namePrompt(p.open(), p.error())),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, NameChoice.class, (p, b) -> b.writeUtf(p.name(), 64), b -> new NameChoice(b.readUtf(64)),
                (p, s) -> server(s, player -> SurvivorIdentity.onNameChoice(player, p.name())),
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    private static void client(Supplier<NetworkEvent.Context> supplier, Runnable work) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> work));
        context.setPacketHandled(true);
    }

    private static void server(Supplier<NetworkEvent.Context> supplier, java.util.function.Consumer<ServerPlayer> work) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> work.accept(player));
        context.setPacketHandled(true);
    }
}
