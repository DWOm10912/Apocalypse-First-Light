package com.antaurora.apofirstlight.temperature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The numbers above the temperature orb, hidden by default. A future thermometer / survival monitor registers a Source
 * (client setup) that decides, for the local player, whether the core body temperature and / or the ambient temperature
 * may be shown (for example while the device is carried). Nothing is registered in V1, so the orb shows no numbers.
 */
public final class TemperatureReadout {
    public interface Source {
        boolean showsCore(LocalPlayer player);
        boolean showsAmbient(LocalPlayer player);
    }
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private TemperatureReadout() {}

    public static void register(Source source) { SOURCES.add(source); }

    /** Lines stacked upward from bottomY, centred on cx: core first (nearest the orb), then ambient. */
    static void render(GuiGraphics graphics, Font font, double cx, double bottomY, TemperaturePackets.State state) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || SOURCES.isEmpty()) return;
        boolean core = false, ambient = false;
        for (var source : SOURCES) { core |= source.showsCore(player); ambient |= source.showsAmbient(player); }
        int y = (int)Math.floor(bottomY) - font.lineHeight;
        if (core) {
            drawCentred(graphics, font, Component.translatable("hud.apocalypse_firstlight.temperature.core",
                    ClientTemperatureHud.celsius(state.core())), cx, y);
            y -= font.lineHeight;
        }
        if (ambient) drawCentred(graphics, font, Component.translatable("hud.apocalypse_firstlight.temperature.ambient",
                ClientTemperatureHud.celsius(state.ambient())), cx, y);
    }
    private static void drawCentred(GuiGraphics graphics, Font font, Component text, double cx, int y) {
        graphics.drawString(font, text, (int)Math.round(cx - font.width(text) / 2.0), y, 0xFFD8D6CE, true);
    }
}
