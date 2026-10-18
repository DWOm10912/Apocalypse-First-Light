package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The 480 x 270 canvas the New World and survivor setup pages draw on (like the inventory, AflInventoryScreen): the
 * largest whole pixel scale the window takes (4 at 1080p), independent of the GUI scale, so the pages look the same
 * at every GUI scale. Coordinates below are canvas units; text takes a size factor of the font's 9-unit line.
 */
final class SetupCanvas {
    static final float W = 480, H = 270;

    float scale = 1, originX, originY;

    void layout(Minecraft mc, int width, int height) {
        var window = mc.getWindow();
        int whole = Math.max(1, (int) Math.min(window.getWidth() / W, window.getHeight() / H));
        scale = (float) (whole / window.getGuiScale());
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
    }

    float vx(double mouseX) {
        return (float) ((mouseX - originX) / scale);
    }

    float vy(double mouseY) {
        return (float) ((mouseY - originY) / scale);
    }

    void push(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
    }

    void pop(GuiGraphics g) {
        g.pose().popPose();
    }

    /** Clips to a canvas rectangle (screen pixels are whole GUI units here). */
    void scissor(GuiGraphics g, float x, float y, float w, float h) {
        g.enableScissor(Math.round(originX + x * scale), Math.round(originY + y * scale),
                Math.round(originX + (x + w) * scale), Math.round(originY + (y + h) * scale));
    }

    static void text(GuiGraphics g, Font font, Component text, float x, float y, int rgb, float alpha, float size) {
        if (alpha <= 0.02F) return;   // vanilla's font treats alpha under 4/255 as opaque
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, alpha), false);
        g.pose().popPose();
    }

    static float width(Font font, Component text, float size) {
        return font.width(text) * size;
    }

    static void right(GuiGraphics g, Font font, Component text, float right, float y, int rgb, float alpha, float size) {
        text(g, font, text, right - width(font, text, size), y, rgb, alpha, size);
    }

    static void centred(GuiGraphics g, Font font, Component text, float cx, float y, int rgb, float alpha, float size) {
        text(g, font, text, cx - width(font, text, size) / 2, y, rgb, alpha, size);
    }

    /** A key cap with its label after it ("Esc  Back"). */
    static void keyHint(GuiGraphics g, Font font, String key, Component label, float x, float y, float size) {
        Component k = Component.literal(key);
        float capW = Math.max(9, width(font, k, size) + 4), capH = 9;
        AflUiShapes.fill(g, x, y, capW, capH, AflUiStyle.RADIUS_KEY, AflUiStyle.KEY_BACK, AflUiStyle.KEY_BACK_ALPHA);
        text(g, font, k, x + (capW - width(font, k, size)) / 2, y + 1.6F, AflUiStyle.KEY_TEXT, 1, size);
        text(g, font, label, x + capW + 3, y + 1.6F, AflUiStyle.TEXT, 1, size);
    }

    static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("survivor.apocalypse_firstlight." + key, args);
    }
}
