package com.antaurora.apofirstlight.client.hudlayout;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public interface ClientHudLayout extends HudLayoutDescriptor {
    JsonObject current();
    ResourceLocation overlayId();
    void render(GuiGraphics graphics, JsonObject preview, int width, int height);
}
