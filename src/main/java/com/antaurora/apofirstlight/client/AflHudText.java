package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Text on the survival HUD: numbers (temperature readings, the health number, needs) and labels (status badges). */
public final class AflHudText {
    private AflHudText() {}

    /** The half-size numbers' scale: the nearest whole number of screen pixels per font pixel (GUI scale 4 or 5: 2, scale
     *  6: 3, scale 2 or 3: 1) over the GUI scale. */
    public static double halfScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(1, Math.floor(gui / 2)) / gui;
    }

    /** The labels' scale (HUD V2 badges and the status card): two screen pixels per font pixel from GUI scale 2 up (so CJK
     *  glyphs land on their own 16 px grid), about 0.6 of the GUI scale above 4; one at GUI scale 1. */
    public static double labelScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (gui < 2 ? 1 : Math.max(2, Math.round(gui * 0.6))) / gui;
    }

    /**
     * Vanilla font at about half size, centred on cx, top at y (GUI pixels), with its shadow. The origin sits on the screen
     * pixel grid, so no stroke comes out one pixel wider than the others.
     */
    public static void half(GuiGraphics graphics, Font font, String text, double cx, double y, int rgb) {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        float k = (float)halfScale();
        double x0 = Math.round((cx - font.width(text) * k / 2) * gui) / gui, y0 = Math.round(y * gui) / gui;
        graphics.pose().pushPose();
        graphics.pose().translate(x0, y0, 0);
        graphics.pose().scale(k, k, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF000000 | rgb, true);
        graphics.pose().popPose();
    }

    /** A label at labelScale(), left edge x, top y (GUI px, snapped to screen pixels), with its shadow; alpha 0..1. Returns
     *  its width in GUI px. */
    public static double label(GuiGraphics graphics, Font font, Component text, double x, double y, int rgb, double alpha) {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        float k = (float)labelScale();
        int a = (int)Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a >= 8) {   // the font draws an alpha under 4 as opaque
            graphics.pose().pushPose();
            graphics.pose().translate(Math.round(x * gui) / gui, Math.round(y * gui) / gui, 0);
            graphics.pose().scale(k, k, 1F);
            graphics.drawString(font, text, 0, 0, a << 24 | rgb & 0xFFFFFF, true);
            graphics.pose().popPose();
        }
        return font.width(text) * k;
    }

    /** A label's width in GUI px. */
    public static double labelWidth(Font font, Component text) { return font.width(text) * labelScale(); }
}
