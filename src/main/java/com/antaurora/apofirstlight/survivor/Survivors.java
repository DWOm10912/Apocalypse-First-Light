package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Survivor Death and Succession V0, server side (docs/gameplay/survivor_death_succession_v0.md): the mode, the
 * survivor a player plays, and the death record. Everything here runs on the server thread and is decided by the server;
 * clients only receive results.
 */
public final class Survivors {
    /** Root persistent-data key on the player: the survivor the player file belongs to (stale-file check). */
    static final String PLAYER_SURVIVOR_KEY = ApocalypseFirstLight.MOD_ID + ":survivor_id";

    /** Set by a death: write the survivor file, then the player files, at the end of this server tick. */
    private static boolean flushPending;
    /**
     * The mode picked on the Create World screen (client, single player), waiting for that world's creation; null when
     * there is none (the host then chooses on first join). Written by the client thread, taken by the server thread.
     */
    @Nullable
    private static volatile SurvivorMode createScreenChoice;

    private Survivors() {
    }

    /** Survival and adventure players live under the survivor rules; creative and spectator players do not. */
    public static boolean tracked(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer().isSurvival();
    }

    public static SurvivorMode mode(MinecraftServer server) {
        return SurvivorWorldData.get(server).mode;
    }

    /**
     * Server start: a world without a mode gets one, except a fresh single-player world, whose host chooses (V0 first
     * join prompt). Vanilla hardcore is always HARD; a dedicated server reads its config; a world that existed before
     * this system (no fresh flag) continues as NORMAL.
     */
    static void resolveStartMode(MinecraftServer server) {
        SurvivorWorldData data = SurvivorWorldData.get(server);
        if (data.mode != SurvivorMode.UNSET) return;
        SurvivorMode mode;
        String why;
        if (server.getWorldData().isHardcore()) {
            mode = SurvivorMode.HARD;
            why = "vanilla hardcore world";
        } else if (server.isDedicatedServer()) {
            mode = SurvivorConfig.DEDICATED_MODE.get() == SurvivorMode.HARD ? SurvivorMode.HARD : SurvivorMode.NORMAL;
            why = "dedicated server config";
        } else if (!data.freshWorld) {
            mode = SurvivorMode.NORMAL;
            why = "world older than the survivor system";
        } else {
            ApocalypseFirstLight.LOGGER.info("[AFL Survivor] new world: waiting for the host to choose NORMAL or HARD");
            return;
        }
        data.setMode(mode);
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] mode {} ({})", mode, why);
    }

    /** The host's one-time choice in a fresh single-player world; ignored once a mode is set or from anyone else. */
    static boolean chooseMode(ServerPlayer player, SurvivorMode choice) {
        MinecraftServer server = player.server;
        SurvivorWorldData data = SurvivorWorldData.get(server);
        if (choice == SurvivorMode.UNSET || data.mode != SurvivorMode.UNSET
                || server.isDedicatedServer() || !server.isSingleplayerOwner(player.getGameProfile())) {
            return false;
        }
        data.setMode(choice);
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] mode {} (chosen by the host {})", choice, player.getGameProfile().getName());
        return true;
    }

    /** Client: the Create World screen's current pick (null clears it when the player leaves that screen). */
    public static void setCreateScreenChoice(@Nullable SurvivorMode mode) {
        createScreenChoice = mode;
    }

    /** The start scene picked on the AFL Create World page (client), waiting like the mode; null clears it. */
    @Nullable
    private static volatile String createScreenScene;

    public static void setCreateScreenScene(@Nullable String scene) {
        createScreenScene = scene;
    }

    /** The host's survivor setup made before the world exists (SurvivorSetupFlow.encode), waiting like the mode; null clears it. */
    @Nullable
    private static volatile net.minecraft.nbt.CompoundTag createScreenSetup;

    public static void setCreateScreenSetup(@Nullable net.minecraft.nbt.CompoundTag setup) {
        createScreenSetup = setup;
    }

    /**
     * World creation (LevelEvent.CreateSpawnPosition): a fresh single-player world takes the mode picked on the Create
     * World screen, if any (vanilla hardcore stays HARD). The pick is used once.
     */
    static void applyCreateScreenChoice(MinecraftServer server, SurvivorWorldData data) {
        String scene = createScreenScene;
        createScreenScene = null;
        net.minecraft.nbt.CompoundTag setup = createScreenSetup;
        createScreenSetup = null;
        if (setup != null && !server.isDedicatedServer() && data.hostSetup == null) {
            data.hostSetup = setup;
            ApocalypseFirstLight.LOGGER.info("[AFL Survivor] host setup {} waits for the first login (made before the world)",
                    setup.getString("Background"));
        }
        if (scene != null && !server.isDedicatedServer() && data.scene == null) {
            data.scene = SurvivorScenes.byId(scene).id();
            ApocalypseFirstLight.LOGGER.info("[AFL Survivor] start scene {} (chosen on the Create World page)", data.scene);
        }
        SurvivorMode chosen = createScreenChoice;
        createScreenChoice = null;
        if (chosen == null || chosen == SurvivorMode.UNSET || server.isDedicatedServer() || data.mode != SurvivorMode.UNSET) {
            return;
        }
        SurvivorMode mode = server.getWorldData().isHardcore() ? SurvivorMode.HARD : chosen;
        data.setMode(mode);
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] mode {} (chosen on the Create World screen)", mode);
    }

    /** Whether this player is the one who has to choose the mode now. */
    static boolean mustChooseMode(ServerPlayer player) {
        MinecraftServer server = player.server;
        return SurvivorWorldData.get(server).mode == SurvivorMode.UNSET && !server.isDedicatedServer()
                && server.isSingleplayerOwner(player.getGameProfile());
    }

    /** Login: a player without a survivor starts as the first one of the lineage; the player file is stamped with its id. */
    static void ensureSurvivor(ServerPlayer player) {
        SurvivorWorldData data = SurvivorWorldData.get(player.server);
        SurvivorLineage lineage = data.lineage(player);
        if (lineage.current() == null && lineage.pending() == null) {
            SurvivorRecord first = firstSurvivor(player, lineage);
            first.state = SurvivorRecord.State.ALIVE;
            first.spawnSource = "initial";
            placeAtScene(player, data, first);
            start(player, first);
            lineage.lives.add(first);
            lineage.status = SurvivorLineage.Status.ACTIVE;
            data.setDirty();
            ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} starts as survivor #{} {}", player.getGameProfile().getName(),
                    first.lifeIndex, first.fullName());
        }
        SurvivorRecord current = lineage.current();
        if (lineage.status == SurvivorLineage.Status.ACTIVE && current != null
                && !player.getPersistentData().hasUUID(PLAYER_SURVIVOR_KEY)) {
            player.getPersistentData().putUUID(PLAYER_SURVIVOR_KEY, current.id);
        }
    }

    /**
     * A lineage's first survivor stands at the world's start scene's place (SurvivorSceneStarts; the forest home: the
     * foot of the porch steps), one spot per first survivor in arrival order; none left, or no such place in this world:
     * where vanilla put the player (the world spawn area), source "initial". Before the setup screen's hold, which keeps
     * the player where this leaves them.
     */
    private static void placeAtScene(ServerPlayer player, SurvivorWorldData data, SurvivorRecord first) {
        ServerLevel overworld = player.server.overworld();
        if (player.level() != overworld || !player.isAlive()) return;
        int index = 0;
        for (SurvivorLineage other : data.lineages()) if (other.playerId != null && !other.playerId.equals(player.getUUID()) && !other.lives.isEmpty()) index++;
        SurvivorSceneStarts.Spot spot = SurvivorSceneStarts.spot(overworld, data.scene, index);
        if (spot == null) return;
        double x = spot.feet().getX() + 0.5, y = spot.feet().getY(), z = spot.feet().getZ() + 0.5;
        player.teleportTo(overworld, x, y, z, spot.yaw(), 0.0F);
        first.spawnSource = SurvivorScenes.byId(data.scene).id();
        first.spawnDimension = overworld.dimension().location().toString();
        first.spawnPos = spot.feet();
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} starts at the {} scene, spot {} ({})", player.getGameProfile().getName(),
                first.spawnSource, index, spot.feet().toShortString());
    }

    /**
     * The first survivor of a lineage is the player as they arrive: named after the account (until the player's one-time
     * choice of the account name or a nickname, SurvivorIdentity) and wearing the account's skin. Only successors are
     * rolled.
     */
    static SurvivorRecord firstSurvivor(ServerPlayer player, SurvivorLineage lineage) {
        int index = lineage.nextLifeIndex();
        long seed = SurvivorNames.rollSeed(player.server.getWorldData().worldGenOptions().seed(), lineage.playerId, index);
        SurvivorRecord record = new SurvivorRecord(UUID.nameUUIDFromBytes(longBytes(seed, lineage.playerId)), index,
                player.getGameProfile().getName(), "", seed, SurvivorRecord.State.PENDING);
        record.named = false;
        // the setup screen confirms it (SurvivorSetupFlow); until then an ordinary person, held
        record.setup = SurvivorSetup.CHOOSING;
        return record;
    }

    /**
     * A new successor for the next career index: a person from the roster (SurvivorRoster) not yet seen in this world,
     * with that person's name, sex and skin, and a background and body levels from the same seed (SurvivorSetup).
     */
    static SurvivorRecord roll(MinecraftServer server, SurvivorLineage lineage) {
        int index = lineage.nextLifeIndex();
        long seed = SurvivorNames.rollSeed(server.getWorldData().worldGenOptions().seed(), lineage.playerId, index);
        SurvivorRoster.Person person = SurvivorRoster.pick(SurvivorWorldData.get(server), seed);
        SurvivorRecord record = new SurvivorRecord(UUID.nameUUIDFromBytes(longBytes(seed, lineage.playerId)), index,
                person.first(), person.last(), seed, SurvivorRecord.State.PENDING);
        record.sex = person.sex();
        record.setAppearance(SurvivorSkins.of(person));
        SurvivorSetup.rollSuccessor(record, SurvivorCatalog.get(), seed);
        return record;
    }

    /** The survivor begins now (world day and game time). */
    static void start(ServerPlayer player, SurvivorRecord record) {
        Level level = player.level();
        record.startGameTime = level.getGameTime();
        record.startDayTime = level.getDayTime();
    }

    /**
     * The death of a tracked player, once per survivor (the record's state is the ledger: a second death event for the
     * same survivor finds it DEAD and does nothing). Returns the record, or null when nothing was recorded.
     */
    @Nullable
    static SurvivorRecord recordDeath(ServerPlayer player, DamageSource source) {
        MinecraftServer server = player.server;
        SurvivorWorldData data = SurvivorWorldData.get(server);
        if (data.mode == SurvivorMode.UNSET) {
            data.setMode(SurvivorMode.NORMAL);
            ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] {} died before the mode was chosen; the world is NORMAL now",
                    player.getGameProfile().getName());
        }
        ensureSurvivor(player);
        SurvivorLineage lineage = data.lineage(player);
        SurvivorRecord record = lineage.current();
        if (record == null || record.state != SurvivorRecord.State.ALIVE || lineage.status != SurvivorLineage.Status.ACTIVE) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        record.state = SurvivorRecord.State.DEAD;
        if (!record.named) {
            // died before choosing: the name it carried is final, and the choice is gone
            record.named = true;
            AflNetwork.survivor(player, new SurvivorPackets.NamePrompt(false, ""));
        }
        record.deathGameTime = level.getGameTime();
        record.deathDayTime = level.getDayTime();
        record.deathDimension = level.dimension().location().toString();
        record.deathPos = player.blockPosition();
        record.deathCauseId = source.type().msgId();
        record.deathCauseJson = Component.Serializer.toJson(deathCause(player, record.fullName()));
        // the bed belonged to the dead survivor: remember it (successors start away from it) and drop it, so the respawn
        // does not go there and the new body does not inherit it
        if (player.getRespawnPosition() != null) {
            lineage.lastBed = player.getRespawnPosition();
            lineage.lastBedDimension = player.getRespawnDimension().location().toString();
        }
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        lineage.status = data.mode == SurvivorMode.HARD ? SurvivorLineage.Status.ENDED : SurvivorLineage.Status.AWAITING_SUCCESSOR;
        SurvivorCorpses.create(player, record, source);
        if (lineage.status == SurvivorLineage.Status.AWAITING_SUCCESSOR) rollSuccessor(player.server, lineage, record);
        data.setDirty();
        flushPending = true;
        SurvivorRecord next = lineage.pending();
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} (survivor #{} {}) died: {} at {} {} after {} days; lineage {}{}",
                player.getGameProfile().getName(), record.lifeIndex, record.fullName(), record.deathCauseId,
                record.deathDimension, record.deathPos.toShortString(),
                String.format(java.util.Locale.ROOT, "%.2f", record.daysSurvived(record.deathDayTime)), lineage.status,
                next == null ? "" : "; successor #" + next.lifeIndex + " " + next.fullName()
                        + (next.hasSpawnPlan ? " planned at " + next.spawnPlanX + " " + next.spawnPlanZ : " (no planned start)"));
        sendSummary(player);
        return record;
    }

    /** NORMAL: the successor is decided at the death and stored, with its planned start column. */
    private static void rollSuccessor(MinecraftServer server, SurvivorLineage lineage, @Nullable SurvivorRecord dead) {
        SurvivorRecord next = roll(server, lineage);
        SurvivorSpawns.plan(server, lineage, dead, next);
        lineage.lives.add(next);
    }

    /**
     * The respawn (server; not after the End credits): the pending successor takes over the new body. It is placed at its
     * start (SurvivorSpawns), stamped into the player file, and greeted with its name as a title. Other cases:
     * <ul>
     * <li>a HARD lineage that ended never takes over: a respawn forced by other means is put back into the dead state;</li>
     * <li>a creative / spectator respawn of a living survivor keeps that survivor;</li>
     * <li>a survival death the survivor data never saw (server stopped before writing it) is recorded now, cause unknown
     * and without a corpse (the items of that death are lost, never duplicated), and succession follows the mode.</li>
     * </ul>
     */
    static void takeOver(ServerPlayer player) {
        MinecraftServer server = player.server;
        SurvivorWorldData data = SurvivorWorldData.get(server);
        if (data.mode == SurvivorMode.UNSET) data.setMode(SurvivorMode.NORMAL);
        SurvivorLineage lineage = data.lineage(player);
        SurvivorRecord current = lineage.current();
        if (lineage.status == SurvivorLineage.Status.ENDED) {
            player.setHealth(0.0F);
            sendSummary(player);
            ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] {} respawned after the end of a HARD run; back to the dead state",
                    player.getGameProfile().getName());
            return;
        }
        SurvivorRecord next = lineage.pending();
        if (next == null) {
            if (current == null || lineage.status == SurvivorLineage.Status.ACTIVE && !tracked(player)) {
                ensureSurvivor(player);
                if (current != null) player.getPersistentData().putUUID(PLAYER_SURVIVOR_KEY, current.id);
                return;
            }
            if (current.state == SurvivorRecord.State.ALIVE) {
                current.state = SurvivorRecord.State.DEAD;
                current.deathGameTime = player.level().getGameTime();
                current.deathDayTime = player.level().getDayTime();
                current.deathCauseId = "unknown";
                ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] {} respawned from a death the survivor data never recorded; "
                        + "survivor #{} {} recorded dead now (cause unknown, no corpse)", player.getGameProfile().getName(),
                        current.lifeIndex, current.fullName());
                if (data.mode == SurvivorMode.HARD) {
                    lineage.status = SurvivorLineage.Status.ENDED;
                    data.setDirty();
                    flushPending = true;
                    player.setHealth(0.0F);
                    sendSummary(player);
                    return;
                }
            }
            rollSuccessor(server, lineage, current);
            next = lineage.pending();
            if (next == null) return;
        }
        // a successor rolled before setups existed gets its background and body from its own seed now
        if (next.setup == null) SurvivorSetup.rollSuccessor(next, SurvivorCatalog.get(), next.rollSeed);
        SurvivorSpawns.Start start = SurvivorSpawns.resolve(server, lineage, current, next);
        float yaw = (float) ((SurvivorNames.stream(next.rollSeed, 999) >>> 40) % 360L);
        if (start != null) {
            double x = start.feet().getX() + 0.5, y = start.feet().getY(), z = start.feet().getZ() + 0.5;
            if (start.level() == player.level()) {
                // inside the respawn event the connection still holds the old body (vanilla swaps it after respawn()
                // returns), so teleportTo would move the old body: place the new one directly, then sync the client
                player.moveTo(x, y, z, yaw, 0.0F);
                player.connection.teleport(x, y, z, yaw, 0.0F);
            } else {
                player.teleportTo(start.level(), x, y, z, yaw, 0.0F);
            }
            next.spawnSource = start.source();
        } else {
            next.spawnSource = "fallback";
            ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] no successor start passed the ground check for {}; world spawn fallback",
                    player.getGameProfile().getName());
        }
        next.spawnDimension = player.level().dimension().location().toString();
        next.spawnPos = player.blockPosition();
        next.state = SurvivorRecord.State.ALIVE;
        start(player, next);
        lineage.status = SurvivorLineage.Status.ACTIVE;
        player.getPersistentData().putUUID(PLAYER_SURVIVOR_KEY, next.id);
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        data.setDirty();
        flushPending = true;
        announce(player, next);
        SurvivorIdentity.changed(player);
        SurvivorSetupFlow.sendSheet(player);
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} continues as survivor #{} {} at {} {} ({}), look {}/{} ({})",
                player.getGameProfile().getName(), next.lifeIndex, next.fullName(), next.spawnDimension,
                next.spawnPos.toShortString(), next.spawnSource, next.skin, next.arms.id(), next.sex);
    }

    /**
     * The death summary for the AFL death screen (client SurvivorClient), when the player's newest survivor is dead:
     * at the death, on login while dead, and when a respawn is refused or undone.
     */
    static void sendSummary(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        SurvivorRecord dead = lineage == null ? null : lineage.current();
        if (dead == null || dead.state != SurvivorRecord.State.DEAD || lineage.status == SurvivorLineage.Status.ACTIVE) return;
        BlockPos at = dead.deathPos != null ? dead.deathPos : BlockPos.ZERO;
        AflNetwork.survivor(player, new SurvivorPackets.Summary(dead.fullName(), dead.lifeIndex,
                dead.daysSurvived(dead.deathDayTime), dead.deathCause(), dead.deathDimension != null ? dead.deathDimension : "",
                at.getX(), at.getY(), at.getZ(), lineage.status == SurvivorLineage.Status.ENDED));
    }

    /**
     * Login: identities (and the first survivor's name choice); the host of a fresh single-player world chooses the mode
     * first (the client opens the mode choice before the name choice); a dead player sees the summary again.
     */
    static void onLogin(ServerPlayer player) {
        if (mustChooseMode(player)) AflNetwork.survivor(player, new SurvivorPackets.ModePrompt(true));
        SurvivorIdentity.onLogin(player);
        if (player.isDeadOrDying()) sendSummary(player);
    }

    /** The host's choice (packet): checked, locked, persisted at once; the prompt closes. */
    static void onModeChoice(ServerPlayer player, SurvivorMode choice) {
        if (chooseMode(player, choice)) {
            flushPending = true;
            AflNetwork.survivor(player, new SurvivorPackets.ModePrompt(false));
        } else if (SurvivorWorldData.get(player.server).mode != SurvivorMode.UNSET) {
            AflNetwork.survivor(player, new SurvivorPackets.ModePrompt(false));
        }
    }

    /** The new survivor's name as a title, the career index under it. */
    private static void announce(ServerPlayer player, SurvivorRecord record) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(record.fullName())));
        // the name alone; an empty subtitle so no earlier one lingers (no career number, user 2026-10-14)
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
    }

    /**
     * Server gate for the client's respawn request (mixin/SurvivorRespawnGateMixin): a player whose HARD run ended stays
     * dead. The client can ask as often as it likes; nothing it sends changes this.
     */
    public static boolean respawnBlocked(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        return lineage != null && lineage.status == SurvivorLineage.Status.ENDED;
    }

    /**
     * The vanilla death message with the survivor's name in place of the account name: the victim is the first argument
     * of every vanilla and AFL death message. Anything else is kept as it is.
     */
    static Component deathCause(ServerPlayer player, String survivorName) {
        Component message = player.getCombatTracker().getDeathMessage();
        if (!(message.getContents() instanceof TranslatableContents t) || t.getArgs().length == 0) return message;
        Object[] args = t.getArgs().clone();
        if (!(args[0] instanceof Component victim) || !victim.getString().equals(player.getDisplayName().getString())) {
            return message;
        }
        args[0] = Component.literal(survivorName);
        MutableComponent out = Component.translatableWithFallback(t.getKey(), t.getFallback(), args).setStyle(message.getStyle());
        for (Component sibling : message.getSiblings()) out.append(sibling);
        return out;
    }

    /** End of a server tick: a death this tick writes the survivor file first, then every player file. */
    static void flush(MinecraftServer server) {
        if (!flushPending) return;
        flushPending = false;
        SurvivorWorldData.get(server).writeNow(server);
        server.getPlayerList().saveAll();
    }

    static void markFlush() {
        flushPending = true;
    }

    private static byte[] longBytes(long seed, UUID player) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(24);
        b.putLong(seed).putLong(player.getMostSignificantBits()).putLong(player.getLeastSignificantBits());
        return b.array();
    }
}
