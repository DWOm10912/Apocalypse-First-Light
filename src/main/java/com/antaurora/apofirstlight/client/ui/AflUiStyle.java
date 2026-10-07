package com.antaurora.apofirstlight.client.ui;

/**
 * The AFL overlay UI style (docs/ui/afl_overlay_ui_style_v1.md): the survival HUD's visual language for panels, hints,
 * buttons, key caps and the hotbar. Colours are 0xRRGGBB with a separate alpha; sizes in GUI pixels.
 */
public final class AflUiStyle {
    private AflUiStyle() {}

    /** Backing of every panel, label and tile. */
    public static final int BACK = 0x101010;
    public static final float BACK_ALPHA = 0.62F;
    /** Backing of a hovered tile or button. */
    public static final int BACK_HOVER = 0x1E2124;
    public static final float BACK_HOVER_ALPHA = 0.72F;
    /** Pressed button backing. */
    public static final int BACK_DOWN = 0x08090A;
    public static final float BACK_DOWN_ALPHA = 0.80F;
    /** Hairline: the survival HUD's outline colour and alpha. */
    public static final int LINE = 0xE8E4DC;
    public static final float LINE_ALPHA = 0.28F;
    public static final float LINE_HOVER_ALPHA = 0.55F;
    /** Soft drop shadow: alpha, spread and downward offset. */
    public static final float SHADOW_ALPHA = 0.22F;
    public static final float SHADOW_SPREAD = 3.0F;
    public static final float SHADOW_DROP = 1.0F;

    public static final int TEXT = 0xE8E4DC;
    public static final int TEXT_DIM = 0xA8A49C;
    public static final int TEXT_DISABLED = 0x6C6A66;
    /** The one accent: selection and pressed keys only. */
    public static final int ACCENT = 0xE0A84A;

    public static final int KEY_BACK = 0xE8E4DC;
    public static final float KEY_BACK_ALPHA = 0.92F;
    public static final int KEY_TEXT = 0x16181A;

    /** Corner radii. */
    public static final float RADIUS_KEY = 2.5F, RADIUS_TILE = 3.0F, RADIUS_LABEL = 3.0F, RADIUS_PANEL = 4.0F;

    public static int argb(int rgb, float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | rgb & 0xFFFFFF;
    }
}
