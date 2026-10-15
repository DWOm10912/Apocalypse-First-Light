package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.survivor.SurvivorSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client copy of every player's survivor identity (docs/gameplay/survivor_death_succession_v0.md 13), keyed by account
 * UUID and filled only by the server's Identity packets.
 * <ul>
 * <li>Name: the client display name of that player (SurvivorClient's NameFormat hook), so the nametag over them shows
 * the survivor. Chat and the tab list come from the server already formatted.</li>
 * <li>Look: {@code PlayerInfo} asks here first (mixin/client/SurvivorSkinPlayerInfoMixin): a pool look replaces the
 * account's skin, arm model, cape and elytra texture wherever the game draws that player (third person, first-person
 * arms, AFL gun arms, other players, tab heads). An account look (the first survivor) is not stored and changes
 * nothing. The account skin download and cache (SkinManager) are untouched.</li>
 * </ul>
 * Main thread only.
 */
public final class SurvivorIdentityClient {
    private record Look(ResourceLocation texture, String model) {
    }

    private static final Map<UUID, Look> LOOKS = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    /** Set while a caller reads a PlayerInfo's own account skin (corpses of a first survivor). */
    private static boolean bypass;

    private SurvivorIdentityClient() {
    }

    /** Packet: the name and look of one player. A changed name refreshes that player's display name at once. */
    public static void set(UUID player, String name, String skin, String arms) {
        if (SurvivorSkins.ACCOUNT.equals(skin)) {
            LOOKS.remove(player);
        } else {
            String model = SurvivorSkins.Arms.byId(arms).modelName();
            LOOKS.put(player, new Look(SurvivorSkins.texture(skin), model != null ? model : "default"));
        }
        if (!name.equals(NAMES.put(player, name))) {
            Minecraft mc = Minecraft.getInstance();
            Player entity = mc.level == null ? null : mc.level.getPlayerByUUID(player);
            if (entity != null) entity.refreshDisplayName();
        }
    }

    /** The survivor name of this account, or null before the server sent it. */
    @Nullable
    public static String name(UUID player) {
        return NAMES.get(player);
    }

    /** The survivor skin replacing this account's, or null for the account's own. */
    @Nullable
    public static ResourceLocation skin(UUID player) {
        Look look = bypass ? null : LOOKS.get(player);
        return look == null ? null : look.texture();
    }

    /** The vanilla model name ("default" / "slim") replacing this account's, or null for the account's own. */
    @Nullable
    public static String model(UUID player) {
        Look look = bypass ? null : LOOKS.get(player);
        return look == null ? null : look.model();
    }

    /** Whether this account currently wears a survivor look (no account cape or elytra texture then). */
    public static boolean replaced(UUID player) {
        return !bypass && LOOKS.containsKey(player);
    }

    /** Reads a PlayerInfo's own account skin or model past any survivor look. */
    public static <T> T account(Supplier<T> read) {
        boolean was = bypass;
        bypass = true;
        try {
            return read.get();
        } finally {
            bypass = was;
        }
    }

    static void clear() {
        LOOKS.clear();
        NAMES.clear();
    }
}
