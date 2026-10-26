package com.antaurora.apofirstlight.time;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * World Time V1 (docs/gameplay/world_time_v1_design.md 1, 4): a SERVER config, so the day length belongs to the world and
 * the server decides; clients get it at login (WorldTimePacket). Read when the server starts and again whenever the file is
 * saved while it runs.
 */
public final class AflTimeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue DAY_MINUTES;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("world_time");
        DAY_MINUTES = b.comment("Real minutes in one day and night (sun, moon, date). Vanilla is 20; AFL's default is 60, also tried: 40, 90, 120.",
                        "A day is still 24000 world time units, so /time and saves are unchanged; only the world clock is slower. Ticks,",
                        "entities and physics are not slowed.")
                .defineInRange("day_minutes", WorldClock.DEFAULT_MINUTES, WorldClock.MIN_MINUTES, WorldClock.MAX_MINUTES);
        b.pop();
        SPEC = b.build();
    }

    private AflTimeConfig() {}

    /** The configured day length, or the default before the config is loaded. */
    public static int dayMinutes() {
        try {
            return DAY_MINUTES.get();
        } catch (IllegalStateException notLoaded) {
            return WorldClock.DEFAULT_MINUTES;
        }
    }
}
