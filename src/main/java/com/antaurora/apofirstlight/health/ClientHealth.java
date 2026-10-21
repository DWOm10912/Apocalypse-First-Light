package com.antaurora.apofirstlight.health;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client copy of the own health state (HealthPackets.State: regeneration, wounds, fractures); cleared on leaving a world,
 * replaced by the next packet. Also every drawn player's body marks (HealthPackets.Marks, every tracked player and the own:
 * dressings, splints, broken limbs; client/BandageWrapRenderer, the splint action's limb).
 */
public final class ClientHealth {
    private static volatile HealthPackets.State state;
    private static final Map<UUID, HealthPackets.Marks> MARKS = new ConcurrentHashMap<>();

    private ClientHealth() {
    }

    static void accept(HealthPackets.State packet) {
        state = packet;
    }

    static void acceptMarks(HealthPackets.Marks packet) {
        if (packet.empty()) MARKS.remove(packet.player());
        else MARKS.put(packet.player(), packet);
    }

    /** On leaving a world. */
    public static void clear() {
        state = null;
        MARKS.clear();
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

    public static List<Fractures.View> fractures() {
        var s = state;
        return s == null ? List.of() : s.fractures();
    }

    /** The wound screen effect (0..1) the server asks for, before the client's own setting. */
    public static double visual() {
        var s = state;
        return s == null ? 0 : s.visual();
    }

    /** What the server last said of this player's body (a player out of range may be stale). */
    public static HealthPackets.Marks marks(UUID player) {
        return MARKS.getOrDefault(player, HealthPackets.Marks.NONE);
    }

    /** The own player cannot sprint on a broken leg (LocalPlayerWeightSprintMixin). */
    public static boolean sprintBlocked() {
        return Fractures.sprintBlocked(fractures());
    }
}
