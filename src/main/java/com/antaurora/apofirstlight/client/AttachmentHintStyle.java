package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.ui.AflUiTween;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared label for attachment hotspots and world interaction prompts, in the overlay style
 * (docs/ui/afl_overlay_ui_style_v1.md): a rounded dark plate with a hairline and soft shadow, warm-white text, optional
 * key caps in front of the text (following the player's bindings), fading in over 150 ms while rising 2 GUI px.
 */
public final class AttachmentHintStyle {
    public static final float FADE_SECONDS = .15f;
    private static final float KEY_GAP = 3, PAD_X = 4, HEIGHT = 15;

    private AttachmentHintStyle() {}

    public static void draw(GuiGraphics g, Component label, int anchorX, int anchorY, int width, float fade) {
        draw(g, List.of(), label, anchorX, anchorY, width, fade);
    }

    public static void draw(GuiGraphics g, List<AflKeyHint> keys, Component label, int anchorX, int anchorY, int width, float fade) {
        if (fade <= 0.02F) return;
        var font = Minecraft.getInstance().font;
        float keysWidth = 0;
        for (AflKeyHint key : keys) keysWidth += key.width(font) + KEY_GAP;
        float w = PAD_X * 2 + keysWidth + font.width(label);
        float eased = AflUiTween.Ease.OUT.apply(fade);
        float x = Math.max(4, Math.min(width - w - 4, anchorX + 11)), y = Math.max(4, anchorY - 19) + AflUiTween.snap((1 - eased) * 2);
        AflUiDraw.label(g, x, y, w, HEIGHT, fade);
        long now = System.nanoTime();
        float at = x + PAD_X;
        for (AflKeyHint key : keys) {
            key.draw(g, font, at, y + 2, fade, now);
            at += key.width(font) + KEY_GAP;
        }
        AflUiDraw.text(g, font, label, at, y + 4, AflUiStyle.TEXT, fade);
    }
}
