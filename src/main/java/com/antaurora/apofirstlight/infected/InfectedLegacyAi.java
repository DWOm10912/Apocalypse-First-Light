package com.antaurora.apofirstlight.infected;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.Level;

/**
 * The early infected AI injected into minecraft:zombie: breaching doors and glass, hearing noise, vision targeting.
 * Off since 2026-10-26 (rough and buggy; AFL's own infected, Z1, replaces it). The code stays. The GameTest server keeps
 * it on so its regression tests still guard it; -Dafl.infected.legacyAi=true turns it on in a dev world.
 */
public final class InfectedLegacyAi {
    private InfectedLegacyAi() {
    }

    public static boolean enabled(Level level) {
        return Boolean.getBoolean("afl.infected.legacyAi") || level.getServer() instanceof GameTestServer;
    }
}
