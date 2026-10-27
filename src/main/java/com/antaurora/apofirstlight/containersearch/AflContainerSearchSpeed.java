package com.antaurora.apofirstlight.containersearch;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The single server-side entry point for search timing. Screens and renderers only display the result.
 *
 * <p>{@code duration = baseTicksPerSlot * size weight * (1 + jitter) / (player multiplier * environment multiplier)}. The
 * size weight is 0.75 + 0.25 per cell of the item's footprint (a 1 x 1 item the base time, a 3 x 3 jerry can three times
 * it; the grid shows the footprint anyway, AflContainerGrid). The jitter comes from the container's search seed and slot
 * index only, so timing tells no more than the footprint does. The player multiplier is the survivor attribute search_speed (traits); the
 * environment one is 1.0, the extension point for darkness, light sources, fatigue or injuries.
 */
public final class AflContainerSearchSpeed {
    private AflContainerSearchSpeed() {
    }

    static int slotDurationTicks(AflSearchableContainer owner, ServerLevel level, int slot, long seed,
                                 List<ServerPlayer> searchers) {
        AflContainerSearchSettings settings = owner.aflSearchSettings();
        double ticks = settings.baseTicksPerSlot() * AflContainerGrid.searchWeight(owner, slot)
                * (1.0 + settings.durationJitter() * AflContainerSearchState.jitterUnit(seed, slot));
        double speed = sanitize(bestSearcher(owner, searchers)) * sanitize(environmentMultiplier(level, owner));
        return (int) Math.max(1L, Math.min(Short.MAX_VALUE, Math.round(ticks / speed)));
    }

    /** Survivor attribute search_speed. Several searchers never stack: the best one sets the pace. */
    public static double playerMultiplier(ServerPlayer player, AflSearchableContainer container) {
        return com.antaurora.apofirstlight.survivor.SurvivorStats.multiplier(player, com.antaurora.apofirstlight.registry.AflAttributes.SEARCH_SPEED, 0.1);
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
