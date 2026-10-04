package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Numbers on the survival HUD (temperature readings, the health number). */
public final class AflHudText {
    private AflHudText() {}

    /**
     * Vanilla font at about half size, centred on cx, top at y (GUI pixels), with its shadow. The size is the nearest
     * whole number of screen pixels per font pixel (GUI scale 4 or 5: 2, scale 6: 3, scale 2 or 3: 1) and the origin
     * sits on the screen pixel grid, so no stroke comes out one pixel wider than the others.
     */
    public static void half(GuiGraphics graphics, Font font, String text, double cx, double y, int rgb) {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        float k = (float)(Math.max(1, Math.floor(gui / 2)) / gui);
        double x0 = Math.round((cx - font.width(text) * k / 2) * gui) / gui, y0 = Math.round(y * gui) / gui;
        graphics.pose().pushPose();
        graphics.pose().translate(x0, y0, 0);
        graphics.pose().scale(k, k, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF000000 | rgb, true);
        graphics.pose().popPose();
    }
}
