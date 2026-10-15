package com.antaurora.apofirstlight.worldgen.lock;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The world generation version lock's one per-world setting (SERVER config,
 * {@code <world>/serverconfig/apocalypse_firstlight-worldgen.toml}; docs/worldgen/worldgen_version_lock_v1.md).
 */
public final class WorldgenLockConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> ACCEPT_CHANGE;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("worldgen_lock");
        ACCEPT_CHANGE = b.comment("Empty: a world whose terrain rules differ from this build's is not opened (nothing is written).",
                        "To open it anyway, back the world up, then paste the 8-digit token from the refusal message here.",
                        "Only that exact change (stored rules -> this build's rules) is accepted; it is recorded in the world.")
                .define("acceptWorldgenChange", "");
        b.pop();
        SPEC = b.build();
    }

    private WorldgenLockConfig() {}

    /** The configured token, or "" when the config is not loaded. */
    static String acceptToken() {
        try {
            return ACCEPT_CHANGE.get();
        } catch (IllegalStateException e) {
            return "";
        }
    }
}
