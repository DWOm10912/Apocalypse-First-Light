package com.antaurora.apofirstlight.client.config;

/** Immutable, sanitized layout values for the client-only Geiger Counter HUD. */
public record GeigerHudConfig(boolean enabled, String anchor, int offsetX, int offsetY,
                              float hudScale, SymbolLayout symbol, TextLayout text, Rows rows) {
    public record SymbolLayout(int x, int y, float scale) {
    }

    public record TextLayout(int x, int y, float fontScale, int lineSpacing) {
    }

    public record RowOffset(int offsetX, int offsetY) {
    }

    public record Rows(RowOffset radiation, RowOffset dose, RowOffset zone) {
    }

    public static GeigerHudConfig defaults() {
        RowOffset zero = new RowOffset(0, 0);
        return new GeigerHudConfig(true, "bottom_right", 0, 0, 1.0F,
                new SymbolLayout(8, 15, 0.75F),
                new TextLayout(31, 7, 1.0F, 11),
                new Rows(zero, zero, zero));
    }
}
