package com.antaurora.apofirstlight.health;

/**
 * The own player's collapse on its client (Knockdown, docs/gameplay/injury_knockdown_v1_plan.md 4): set when the server
 * plays the own body/collapse, cleared when it gets up, dies or leaves the level (client/skeleton/FallLandingClient). No
 * client classes here: Knockdown#collapsed reads it on both sides.
 */
public final class ClientKnockdown {
    private static boolean collapsed;

    private ClientKnockdown() {
    }

    public static boolean collapsed() {
        return collapsed;
    }

    public static void set(boolean value) {
        collapsed = value;
    }
}
