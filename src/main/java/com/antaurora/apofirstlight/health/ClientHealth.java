package com.antaurora.apofirstlight.health;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client copy of the own health V1a state (HealthPackets.State); cleared on logout / respawn by the next packet. Also which
 * players in view wear a bandage (HealthPackets.Bandaged, every tracked player and the own; client/BandageWrapRenderer).
 */
public final class ClientHealth {
    private static volatile HealthPackets.State state;
    private static final Set<UUID> BANDAGED = ConcurrentHashMap.newKeySet();

    private ClientHealth() {
    }

    static void accept(HealthPackets.State packet) {
        state = packet;
    }

    static void acceptBandaged(HealthPackets.Bandaged packet) {
        if (packet.bandaged()) BANDAGED.add(packet.player());
        else BANDAGED.remove(packet.player());
    }

    /** On leaving a world. */
    public static void clear() {
        state = null;
        BANDAGED.clear();
    }

    public static PlayerHealth.Regen regen() {
        var s = state;
        PlayerHealth.Regen[] all = PlayerHealth.Regen.values();
        return s == null ? PlayerHealth.Regen.NONE : all[Math.max(0, Math.min(all.length - 1, s.regen()))];
    }

    public static List<Wounds.View> wounds() {
        var s = state;
        return s == null ? List.of() : s.wounds();
    }

    /** The wound screen effect (0..1) the server asks for, before the client's own setting. */
    public static double visual() {
        var s = state;
        return s == null ? 0 : s.visual();
    }

    /** Whether the server says this player has a bandaged wound (as last sent; a player out of range may be stale). */
    public static boolean bandaged(UUID player) {
        return BANDAGED.contains(player);
    }
}
