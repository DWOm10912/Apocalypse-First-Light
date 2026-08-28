package com.antaurora.apofirstlight.compat.jade.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

public final class AflJadeEnergyFillElement extends Element {
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "textures/gui/common/energy_fill_green_tile.png");
    private static final int FRAME_SIZE = 8;
    private static final int FRAME_COUNT = 6;
    private static final int FRAME_TICKS = 8;
    private static final int TEXTURE_WIDTH = 8;
    private static final int TEXTURE_HEIGHT = 48;

    @Override
    public Vec2 getSize() {
        return new Vec2(FRAME_SIZE, FRAME_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float width, float height) {
        int fillWidth = Math.max(0, Mth.floor(width));
        int availableHeight = Math.max(0, Mth.floor(height));
        int fillHeight = Math.min(FRAME_SIZE, availableHeight);
        if (fillWidth <= 0 || fillHeight <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        int frameV = (int) ((gameTime / FRAME_TICKS) % FRAME_COUNT) * FRAME_SIZE;
        int startX = Mth.floor(x);
        int startY = Mth.floor(y) + (availableHeight - fillHeight) / 2;
        int fullTiles = fillWidth / FRAME_SIZE;
        int remainder = fillWidth % FRAME_SIZE;

        for (int tile = 0; tile < fullTiles; tile++) {
            graphics.blit(TEXTURE,
                    startX + tile * FRAME_SIZE, startY,
                    0, frameV,
                    FRAME_SIZE, fillHeight,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        if (remainder > 0) {
            graphics.blit(TEXTURE,
                    startX + fullTiles * FRAME_SIZE, startY,
                    0, frameV,
                    remainder, fillHeight,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }
}
