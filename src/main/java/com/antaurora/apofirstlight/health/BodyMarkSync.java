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
 * What every client that draws a player needs of its body (health V1a 7, the bandage on the body; fractures and splints V1):
 * {@link HealthPackets.Marks}, the parts with a bandaged wound, the splinted and the broken limbs, and where the next bandage
 * and the alcohol go. The wound and fracture lists themselves only go to their owner (HealthPackets.State). Sent to the player and the
 * players tracking it:
 * <ul>
 * <li>when it changes: checked every second with the rest of the health sync (PlayerHealth#sync: healed, a dressing soiled
 * by an infection, a fresh scratch, a fall, the dev commands) and at once after a bandage or a splint is used, so what the
 * use drew does not blink off before the server's answer;</li>
 * <li>to a player who starts tracking another (always, so a stale mark from an earlier meeting is put right);</li>
 * <li>on login, respawn (a death clears the wounds and fractures) and a change of dimension, to the player and its trackers.</li>
 * </ul>
 * (Until 2026-10-20 a single flag, BandageSync / HealthPackets.Bandaged, when wounds had no part.)
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class BodyMarkSync {
    private static final Map<UUID, HealthPackets.Marks> SENT = new HashMap<>();

    private BodyMarkSync() {
    }

    static HealthPackets.Marks marks(ServerPlayer player, List<Wounds.View> wounds, List<Fractures.View> fractures) {
        int bandaged = 0;
        for (Wounds.View w : wounds) if (w.bandaged()) bandaged |= w.part().bit();
        int bandage = Wounds.target(wounds, Wounds.Treatment.BANDAGE), clean = Wounds.target(wounds, Wounds.Treatment.ALCOHOL);
        return new HealthPackets.Marks(player.getUUID(), bandaged, Fractures.splintedMask(fractures), Fractures.brokenMask(fractures),
                bandage < 0 ? -1 : wounds.get(bandage).part().ordinal(), clean < 0 ? -1 : wounds.get(clean).part().ordinal());
    }

    private static HealthPackets.Marks marks(ServerPlayer player) {
        return marks(player, Wounds.views(player), Fractures.views(player));
    }

    /** Sends the marks to the player and its trackers when they changed since the last send. */
    static void update(ServerPlayer player, List<Wounds.View> wounds, List<Fractures.View> fractures) {
        send(player, marks(player, wounds, fractures), false);
    }

    /** After a treatment: the same, from the current state. */
    static void update(ServerPlayer player) {
        send(player, marks(player), false);
    }

    private static void send(ServerPlayer player, HealthPackets.Marks now, boolean force) {
        HealthPackets.Marks sent = SENT.put(player.getUUID(), now);
        if (!force && now.sameBody(sent)) return;
        AflNetwork.bodyMarks(player, now);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer)
            AflNetwork.bodyMarksTo(viewer, marks(target));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, marks(player), true);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, marks(player), true);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, marks(player), true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
