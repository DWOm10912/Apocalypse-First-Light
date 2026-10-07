package com.antaurora.apofirstlight.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The overlay UI's composite pieces (docs/ui/afl_overlay_ui_style_v1.md): panels, labels, buttons, text. */
public final class AflUiDraw {
    private AflUiDraw() {}

    /** A panel: dark translucent backing, hairline, soft shadow. */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, float alpha) {
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA * alpha,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA * alpha, AflUiStyle.SHADOW_ALPHA * alpha, 0);
    }

    /** A small label plate (hover labels, world hints). */
    public static void label(GuiGraphics g, float x, float y, float w, float h, float alpha) {
        AflUiShapes.box(g, x, y, w, h, AflUiStyle.RADIUS_LABEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA * alpha,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA * alpha, AflUiStyle.SHADOW_ALPHA * alpha, 0);
    }

    /** A button plate; hover brightens the hairline, down darkens and sinks it one GUI px. Returns the sink. */
    public static float button(GuiGraphics g, float x, float y, float w, float h, boolean hover, boolean down, boolean enabled, float alpha) {
        float sink = down ? AflUiTween.snap(1) : 0;
        int back = down ? AflUiStyle.BACK_DOWN : hover && enabled ? AflUiStyle.BACK_HOVER : AflUiStyle.BACK;
        float backAlpha = down ? AflUiStyle.BACK_DOWN_ALPHA : hover && enabled ? AflUiStyle.BACK_HOVER_ALPHA : AflUiStyle.BACK_ALPHA;
        float line = !enabled ? AflUiStyle.LINE_ALPHA * 0.5F : hover ? AflUiStyle.LINE_HOVER_ALPHA : AflUiStyle.LINE_ALPHA;
        AflUiShapes.box(g, x, y + sink, w, h, AflUiStyle.RADIUS_TILE, back, backAlpha * alpha, AflUiStyle.LINE, line * alpha, 0, 0);
        return sink;
    }

    /** Text at a GUI position that may be fractional (snapped to screen pixels); skipped when nearly invisible. */
    public static void text(GuiGraphics g, Font font, Component text, float x, float y, int rgb, float alpha) {
        if (alpha <= 0.02F) return;   // vanilla's font treats alpha under 4/255 as opaque
        g.pose().pushPose();
        g.pose().translate(AflUiTween.snap(x), AflUiTween.snap(y), 0);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, alpha), false);
        g.pose().popPose();
    }

    public static void centred(GuiGraphics g, Font font, Component text, float cx, float y, int rgb, float alpha) {
        text(g, font, text, cx - font.width(text) / 2F, y, rgb, alpha);
    }
}
