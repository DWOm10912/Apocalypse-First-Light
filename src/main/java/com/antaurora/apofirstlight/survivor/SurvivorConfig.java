package com.antaurora.apofirstlight.survivor;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Survivor Death and Succession V0 (docs/gameplay/survivor_death_succession_v0.md 9): a SERVER config, so it belongs to
 * the world. Only the starting mode of a new dedicated-server world and the successor spawn ring live here; the mode of
 * a world is stored in its survivor data and never follows a later config edit.
 */
public final class SurvivorConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<SurvivorMode> DEDICATED_MODE;
    public static final ForgeConfigSpec.IntValue SPAWN_RING_MIN;
    public static final ForgeConfigSpec.IntValue SPAWN_RING_MAX;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("survivor");
        DEDICATED_MODE = b.comment("Mode a dedicated server world starts in when it has none yet: NORMAL (a random successor takes over",
                        "after a death) or HARD (a death ends that player's run). Read once; the world keeps its mode afterwards.",
                        "Single-player worlds ask the host instead; vanilla hardcore worlds are always HARD.")
                .defineEnum("dedicatedServerMode", SurvivorMode.NORMAL, SurvivorMode.NORMAL, SurvivorMode.HARD);
        SPAWN_RING_MIN = b.comment("Successor start: nearest distance from the world spawn, in blocks.")
                .defineInRange("successorRingMin", 256, 64, 8192);
        SPAWN_RING_MAX = b.comment("Successor start: farthest distance from the world spawn, in blocks (raised to the minimum + 64 if lower).")
                .defineInRange("successorRingMax", 1024, 128, 16384);
        b.pop();
        SPEC = b.build();
    }

    private SurvivorConfig() {
    }
}
