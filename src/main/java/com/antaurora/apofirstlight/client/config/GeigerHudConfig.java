package com.antaurora.apofirstlight.client.config;

/**
 * Immutable, sanitized layout values for the client-only Geiger Counter HUD (V2, 2026-10-15: one panel, so only its
 * place and size; the old symbol / text / rows fields of the three-line V1 HUD are gone and ignored if present).
 */
public record GeigerHudConfig(boolean enabled, String anchor, int offsetX, int offsetY, float hudScale) {
    public static GeigerHudConfig defaults() {
        return new GeigerHudConfig(true, "bottom_right", 0, 0, 1.0F);
    }
}
