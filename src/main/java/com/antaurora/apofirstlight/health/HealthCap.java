package com.antaurora.apofirstlight.health;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

/**
 * The natural-regeneration ceiling (hp_cap, docs/gameplay/survival_health_v2_design.md 3.2): later injuries register a
 * source that returns how high natural regeneration may still bring health. V1a has no source, so the ceiling is the maximum.
 * It only stops natural regeneration short: a ceiling below the current health never takes health away.
 */
public final class HealthCap {
    private static final List<ToDoubleFunction<ServerPlayer>> SOURCES = new CopyOnWriteArrayList<>();

    private HealthCap() {
    }

    /** A source of the ceiling (health; the lowest of all counts). */
    public static void register(ToDoubleFunction<ServerPlayer> source) {
        SOURCES.add(source);
    }

    /** How high natural regeneration may bring this player's health now. */
    public static double cap(ServerPlayer player) {
        double cap = player.getMaxHealth();
        for (var source : SOURCES) cap = Math.min(cap, source.applyAsDouble(player));
        return Math.max(1, cap);
    }
}
