package com.antaurora.apofirstlight.containersearch;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The single server-side entry point for search timing. Screens and renderers only display the result.
 *
 * <p>{@code duration = baseTicksPerSlot * (1 + jitter) / (player multiplier * environment multiplier)}. The jitter
 * comes from the container's search seed and slot index only, never from the slot's contents, so timing cannot
 * reveal what a hidden slot holds. Both multipliers are 1.0 in V1 and are the extension points for future
 * scavenging skills / traits and for darkness, light sources, fatigue or injuries.
 */
public final class AflContainerSearchSpeed {
    private AflContainerSearchSpeed() {
    }

    static int slotDurationTicks(AflSearchableContainer owner, ServerLevel level, int slot, long seed,
                                 List<ServerPlayer> searchers) {
        AflContainerSearchSettings settings = owner.aflSearchSettings();
        double ticks = settings.baseTicksPerSlot()
                * (1.0 + settings.durationJitter() * AflContainerSearchState.jitterUnit(seed, slot));
        double speed = sanitize(bestSearcher(owner, searchers)) * sanitize(environmentMultiplier(level, owner));
        return (int) Math.max(1L, Math.min(Short.MAX_VALUE, Math.round(ticks / speed)));
    }

    /** Future skill / trait hook. Several searchers never stack: the best one sets the pace. */
    public static double playerMultiplier(ServerPlayer player, AflSearchableContainer container) {
        return 1.0;
    }

    /** Future light / darkness / flashlight hook. */
    public static double environmentMultiplier(ServerLevel level, AflSearchableContainer container) {
        return 1.0;
    }

    private static double bestSearcher(AflSearchableContainer owner, List<ServerPlayer> searchers) {
        double best = 0.0;
        for (ServerPlayer player : searchers) {
            best = Math.max(best, sanitize(playerMultiplier(player, owner)));
        }
        return best > 0.0 ? best : 1.0;
    }

    private static double sanitize(double multiplier) {
        return Double.isFinite(multiplier) && multiplier > 0.0 ? multiplier : 1.0;
    }
}
