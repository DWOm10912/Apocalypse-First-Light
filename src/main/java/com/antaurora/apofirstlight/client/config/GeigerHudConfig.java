package com.antaurora.apofirstlight.client.config;

/** Immutable, sanitized layout values for the client-only Geiger Counter HUD. */
public record GeigerHudConfig(boolean enabled, String anchor, int offsetX, int offsetY,
                              float hudScale, float fontScale) {
    public static GeigerHudConfig defaults() {
        return new GeigerHudConfig(true, "bottom_right", 0, 0, 1.0F, 1.0F);
    }
}
