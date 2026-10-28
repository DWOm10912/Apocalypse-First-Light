package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who a player is in game (docs/gameplay/survivor_death_succession_v0.md 13): the current survivor's name and look.
 * <ul>
 * <li>Display name (chat sender, death / join / leave messages, nametags): the survivor's name. Vanilla still adds the
 * team formatting, the hover with the account and the click that suggests {@code /tell <account>}.</li>
 * <li>Tab list: the survivor's name, the account name after it in grey.</li>
 * <li>Every client gets the name and the look (client SurvivorIdentityClient) for nametags and skins.</li>
 * </ul>
 * The GameProfile, the UUID, permissions, command arguments and the tab order keep the account. Also the first
 * survivor's one-time name choice: the account name or a nickname (SurvivorNames rules), checked here.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorIdentity {
    private SurvivorIdentity() {
    }

    /** The survivor shown for a player: the newest one that is not pending, so a dead body keeps the dead survivor. */
    @Nullable
    static SurvivorRecord shown(MinecraftServer server, UUID player) {
        SurvivorLineage lineage = SurvivorWorldData.get(server).lineage(player);
        return lineage == null ? null : lineage.current();
    }

    /** Server display name. HIGH, so name-decorating mods running later (rank prefixes) build on the survivor's name. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SurvivorRecord shown = shown(player.server, player.getUUID());
        if (shown != null && !shown.fullName().equals(event.getUsername())) {
            event.setDisplayname(Component.literal(shown.fullName()));
        }
    }

    /**
     * Tab list entry: "Survivor Name account" with the account in grey (so commands can still be typed); nothing changes
     * while the survivor carries the account name. A set entry drops vanilla's team formatting in the tab list.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SurvivorRecord shown = shown(player.server, player.getUUID());
        String account = player.getGameProfile().getName();
        if (shown == null || shown.fullName().equals(account)) return;
        event.setDisplayName(Component.literal(shown.fullName())
                .append(Component.literal(" " + account).withStyle(ChatFormatting.GRAY)));
    }

    /** The name and look every client draws for this player (with the B5 body's tone and hair). */
    static SurvivorPackets.Identity identity(ServerPlayer player) {
        SurvivorRecord shown = shown(player.server, player.getUUID());
        return shown == null
                ? new SurvivorPackets.Identity(player.getUUID(), player.getGameProfile().getName(), SurvivorSkins.ACCOUNT,
                SurvivorSkins.Arms.ACCOUNT.id(), SurvivorAppearance.DEFAULT_TONE, SurvivorAppearance.DEFAULT_HAIR, false)
                : new SurvivorPackets.Identity(player.getUUID(), shown.fullName(), shown.skin, shown.arms.id(),
                SurvivorAppearance.tone(shown), SurvivorAppearance.hair(shown), shown.sex == SurvivorRecord.Sex.FEMALE);
    }

    /** The player's survivor changed (takeover, name choice, login): display name, tab entry, every client's copy. */
    static void changed(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
        AflNetwork.survivorToAll(identity(player));
    }

    /** Login: every other online player's identity to this client, this player's to everyone, then the name choice. */
    static void onLogin(ServerPlayer player) {
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player) AflNetwork.survivor(player, identity(other));
        }
        changed(player);
        if (mustChooseName(player)) AflNetwork.survivor(player, new SurvivorPackets.NamePrompt(true, ""));
    }

    /**
     * The living first survivor of an active lineage whose name is not chosen yet. A survivor still choosing its setup
     * picks the name on the setup screen instead (SurvivorSetupFlow).
     */
    static boolean mustChooseName(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        SurvivorRecord current = lineage == null ? null : lineage.current();
        return current != null && !current.named && current.state == SurvivorRecord.State.ALIVE
                && lineage.status == SurvivorLineage.Status.ACTIVE && !SurvivorSetup.CHOOSING.equals(current.setup);
    }

    /** A checked name ({@code name} set), or why it was refused ({@code error}: "invalid", "taken"). */
    record NameCheck(@Nullable String name, String error) {
    }

    /** The account name, or a nickname that passes the SurvivorNames rules and is not someone else's name. */
    static NameCheck checkName(ServerPlayer player, String raw) {
        String account = player.getGameProfile().getName();
        String name = raw.strip().equals(account) ? account : SurvivorNames.nickname(raw);
        if (name == null) return new NameCheck(null, "invalid");
        if (!name.equals(account) && taken(player, name)) return new NameCheck(null, "taken");
        return new NameCheck(name, "");
    }

    /** Fixes a checked name on the first survivor (the caller saves and announces). */
    static void applyName(ServerPlayer player, SurvivorRecord record, String name) {
        record.firstName = name;
        record.lastName = "";
        record.named = true;
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} names survivor #{}: {}", player.getGameProfile().getName(),
                record.lifeIndex, name);
    }

    /**
     * The one-time name choice (packet): the account name, or a nickname that passes the SurvivorNames rules and is not
     * another player's account name or current survivor name. Stored and written at once; the prompt closes. A refusal
     * keeps the prompt open with its reason.
     */
    static void onNameChoice(ServerPlayer player, String raw) {
        if (!mustChooseName(player)) {
            AflNetwork.survivor(player, new SurvivorPackets.NamePrompt(false, ""));
            return;
        }
        NameCheck check = checkName(player, raw);
        if (check.name() == null) {
            AflNetwork.survivor(player, new SurvivorPackets.NamePrompt(true, check.error()));
            return;
        }
        SurvivorWorldData data = SurvivorWorldData.get(player.server);
        SurvivorRecord record = data.lineage(player.getUUID()).current();
        applyName(player, record, check.name());
        data.setDirty();
        Survivors.markFlush();
        changed(player);
        AflNetwork.survivor(player, new SurvivorPackets.NamePrompt(false, ""));
    }

    /** Another player's account name or another lineage's current survivor name, ignoring case (no impersonation). */
    private static boolean taken(ServerPlayer player, String name) {
        for (SurvivorLineage lineage : SurvivorWorldData.get(player.server).lineages()) {
            if (lineage.playerId.equals(player.getUUID())) continue;
            SurvivorRecord current = lineage.current();
            if (name.equalsIgnoreCase(lineage.accountName) || current != null && name.equalsIgnoreCase(current.fullName())) {
                return true;
            }
        }
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player && name.equalsIgnoreCase(other.getGameProfile().getName())) return true;
        }
        return false;
    }
}
