package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Health V1a (docs/gameplay/survival_health_v1a.md 7, bandage on the body; 2026-10-19): whether a player wears a bandage (at least one bandaged
 * wound), for every client that draws the player: the gauze on the forearm (client/BandageWrapRenderer). The wound list
 * itself only goes to its owner (HealthPackets.State); this is one flag, {@link HealthPackets.Bandaged}, to the player and
 * the players tracking it:
 * <ul>
 * <li>when it changes: checked every second with the rest of the health sync (PlayerHealth#sync: healed, a dressing soiled
 * by an infection, a fresh scratch, the dev commands) and at once after a bandage is used (MedicalItem), so the wrap the
 * use drew does not blink off before the server's answer;</li>
 * <li>to a player who starts tracking another (always, true or false, so a stale flag from an earlier meeting is put right);</li>
 * <li>on login, respawn (a death clears the wounds) and a change of dimension, to the player and its trackers.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class BandageSync {
    private static final Map<UUID, Boolean> SENT = new HashMap<>();

    private BandageSync() {
    }

    static boolean bandaged(List<Wounds.View> wounds) {
        for (Wounds.View w : wounds) if (w.bandaged()) return true;
        return false;
    }

    /** Sends the flag to the player and its trackers when it changed since the last send. */
    static void update(ServerPlayer player, List<Wounds.View> wounds) {
        send(player, bandaged(wounds), false);
    }

    /** After a treatment: the same, from the current wounds. */
    static void update(ServerPlayer player) {
        update(player, Wounds.views(player));
    }

    private static void send(ServerPlayer player, boolean now, boolean force) {
        Boolean sent = SENT.put(player.getUUID(), now);
        if (!force && sent != null && sent == now) return;
        AflNetwork.bandaged(player, new HealthPackets.Bandaged(player.getUUID(), now));
    }

    private static void force(ServerPlayer player) {
        send(player, bandaged(Wounds.views(player)), true);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer)
            AflNetwork.bandagedTo(viewer, new HealthPackets.Bandaged(target.getUUID(), bandaged(Wounds.views(target))));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) force(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) force(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) force(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
