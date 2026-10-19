package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.client.survivor.SurvivorClient;
import com.antaurora.apofirstlight.client.survivor.SurvivorIdentityClient;
import com.antaurora.apofirstlight.client.survivor.SurvivorSheetClient;
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

    /**
     * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 3), server to the clients tracking a corpse: its falling body's boxes
     * (RagdollSnapshot relative to {@code origin}) every tick, and once more with {@code last} when it is still (then
     * {@code origin} is where it lies; an empty pose: it lies on its back).
     */
    public record CorpsePose(int entity, net.minecraft.world.phys.Vec3 origin, float[] pose, boolean last) {
    }

    /**
     * Survivor setup (docs/gameplay/survivor_background_skills_v1_plan.md 9, 11), server: open (with the catalog files as
     * JSON and the screen's info: base values, scene, account, a fixed name) or close the setup screen.
     */
    public record SetupPrompt(boolean open, String catalog, net.minecraft.nbt.CompoundTag info) {
    }

    /** Client: the setup to confirm; the server checks all of it (SurvivorSetupFlow). Empty name = the account name. */
    public record SetupChoice(String background, java.util.Map<String, Integer> body, java.util.List<String> traits, String name) {
    }

    /** Server: why the confirm was refused (problem lines; "name.invalid", "name.taken" for the name). */
    public record SetupResult(java.util.List<String> problems) {
    }

    /** Server, to the player alone: its current survivor (SurvivorSetupFlow#sheet), for the character page. */
    public record Sheet(net.minecraft.nbt.CompoundTag record) {
    }

    /** Server, to the player alone: a body stat or growth item reached a level (SurvivorGrowth), for the corner notice. */
    public record LevelUp(String id, int level, boolean body) {
    }

    /** A pose as a count and floats (0: none). */
    public static void writePose(net.minecraft.network.FriendlyByteBuf b, @org.jetbrains.annotations.Nullable float[] pose) {
        if (pose == null) {
            b.writeVarInt(0);
            return;
        }
        b.writeVarInt(pose.length);
        for (float f : pose) b.writeFloat(f);
    }

    public static float[] readPose(net.minecraft.network.FriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n < 0 || n > 256) throw new IllegalArgumentException("corpse pose of " + n + " floats");
        float[] pose = new float[n];
        for (int i = 0; i < n; i++) pose[i] = b.readFloat();
        return pose;
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
        channel.registerMessage(id++, CorpsePose.class, (p, b) -> {
            b.writeVarInt(p.entity());
            b.writeDouble(p.origin().x);
            b.writeDouble(p.origin().y);
            b.writeDouble(p.origin().z);
            writePose(b, p.pose());
            b.writeBoolean(p.last());
        }, b -> new CorpsePose(b.readVarInt(), new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                readPose(b), b.readBoolean()),
                (p, s) -> client(s, () -> SurvivorClient.corpsePose(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, SetupPrompt.class, (p, b) -> {
            b.writeBoolean(p.open());
            b.writeUtf(p.catalog(), 262144);
            b.writeNbt(p.info());
        }, b -> new SetupPrompt(b.readBoolean(), b.readUtf(262144), b.readNbt()),
                (p, s) -> client(s, () -> SurvivorClient.setupPrompt(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, SetupChoice.class, (p, b) -> {
            b.writeUtf(p.background(), 256);
            b.writeMap(p.body(), (buf, k) -> buf.writeUtf(k, 256), net.minecraft.network.FriendlyByteBuf::writeVarInt);
            b.writeCollection(p.traits(), (buf, t) -> buf.writeUtf(t, 256));
            b.writeUtf(p.name(), 64);
        }, b -> new SetupChoice(b.readUtf(256), b.readMap(java.util.LinkedHashMap::new, buf -> buf.readUtf(256),
                net.minecraft.network.FriendlyByteBuf::readVarInt), b.readList(buf -> buf.readUtf(256)), b.readUtf(64)),
                (p, s) -> server(s, player -> SurvivorSetupFlow.onChoice(player, p)), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, SetupResult.class, (p, b) -> b.writeCollection(p.problems(), (buf, t) -> buf.writeUtf(t, 512)),
                b -> new SetupResult(b.readList(buf -> buf.readUtf(512))),
                (p, s) -> client(s, () -> SurvivorClient.setupResult(p)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Sheet.class, (p, b) -> b.writeNbt(p.record()), b -> new Sheet(b.readNbt()),
                (p, s) -> client(s, () -> SurvivorSheetClient.set(p.record())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, LevelUp.class, (p, b) -> {
                    b.writeUtf(p.id(), 128);
                    b.writeVarInt(p.level());
                    b.writeBoolean(p.body());
                }, b -> new LevelUp(b.readUtf(128), b.readVarInt(), b.readBoolean()),
                (p, s) -> client(s, () -> com.antaurora.apofirstlight.client.survivor.SurvivorGrowthClient.levelUp(p.id(), p.level(), p.body())),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
