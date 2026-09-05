package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public final class MachineGuiRenderHelper {
    private static final int FILL_FRAME_SIZE = 8;
    private static final int FILL_FRAME_COUNT = 6;
    private static final int FILL_FRAME_TICKS = 8;
    private static final int FILL_TEXTURE_WIDTH = 8;
    private static final int FILL_TEXTURE_HEIGHT = 48;

    private MachineGuiRenderHelper() {
    }

    public static void drawVanillaStylePanel(GuiGraphics graphics, int left, int top,
                                             int width, int height) {
        graphics.fill(left, top, left + width, top + height, 0xFF373737);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, 0xFFFFFFFF);
        graphics.fill(left + 2, top + 2, left + width - 2, top + height - 2, 0xFFC6C6C6);
    }

    public static void drawSlotFrame(GuiGraphics graphics, int itemX, int itemY) {
        graphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF555555);
        graphics.fill(itemX, itemY, itemX + 17, itemY + 17, 0xFFFFFFFF);
        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
    }

    public static void drawGridSlotFrames(GuiGraphics graphics, int left, int top,
                                          MachineGuiLayout.Grid grid) {
        for (int row = 0; row < grid.rows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                drawSlotFrame(graphics,
                        left + grid.x() + column * grid.spacing(),
                        top + grid.y() + row * grid.spacing());
            }
        }
    }

    public static void drawAnimatedEnergyFill(GuiGraphics graphics, ResourceLocation texture,
                                              int left, int top, MachineGuiLayout.Element fill,
                                              int storedEnergy, int capacity, long gameTime) {
        int stored = Mth.clamp(storedEnergy, 0, Math.max(capacity, 0));
        double ratio = capacity <= 0 ? 0.0 : Mth.clamp((double) stored / capacity, 0.0, 1.0);
        double fillPixels = fill.height() * ratio;
        int fullRows = Mth.floor(fillPixels);
        float fractionalRowAlpha = (float) (fillPixels - fullRows);
        int frameIndex = (int) ((gameTime / FILL_FRAME_TICKS) % FILL_FRAME_COUNT);
        int frameV = frameIndex * FILL_FRAME_SIZE;
        int cursorY = fill.y() + fill.height();
        int remaining = fullRows;

        while (remaining > 0) {
            int tileHeight = Math.min(FILL_FRAME_SIZE, remaining);
            cursorY -= tileHeight;
            drawFillRow(graphics, texture, left, top, fill, cursorY,
                    frameV + FILL_FRAME_SIZE - tileHeight, tileHeight);
            remaining -= tileHeight;
        }

        if (fractionalRowAlpha > 0.0F && fullRows < fill.height()) {
            int rowY = fill.y() + fill.height() - fullRows - 1;
            int tileV = frameV + FILL_FRAME_SIZE - 1 - fullRows % FILL_FRAME_SIZE;
            try {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fractionalRowAlpha);
                drawFillRow(graphics, texture, left, top, fill, rowY, tileV, 1);
            } finally {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    public static void drawFluidFill(GuiGraphics graphics, int left, int top,
                                     MachineGuiLayout.Element fill, FluidStack fluid,
                                     int amount, int capacity) {
        if (fluid.isEmpty() || amount <= 0 || capacity <= 0) {
            return;
        }
        int fillHeight = Mth.clamp(Mth.ceil((double) amount * fill.height() / capacity), 1, fill.height());
        int minX = left + fill.x();
        int maxX = minX + fill.width();
        int maxY = top + fill.y() + fill.height();
        int minY = maxY - fillHeight;

        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation texture = properties.getStillTexture(fluid);
        if (texture == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(texture);
        int tint = properties.getTintColor(fluid);
        float alpha = (tint >>> 24 & 0xFF) / 255.0F;
        float red = (tint >>> 16 & 0xFF) / 255.0F;
        float green = (tint >>> 8 & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;

        graphics.enableScissor(minX, minY, maxX, maxY);
        try {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(red, green, blue, alpha);
            for (int tileY = maxY - 16; tileY >= minY - 15; tileY -= 16) {
                for (int tileX = minX; tileX < maxX; tileX += 16) {
                    graphics.blit(tileX, tileY, 0, 16, 16, sprite);
                }
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            graphics.disableScissor();
        }
    }

    private static void drawFillRow(GuiGraphics graphics, ResourceLocation texture,
                                    int left, int top, MachineGuiLayout.Element fill,
                                    int rowY, int textureV, int rowHeight) {
        for (int tileX = 0; tileX < fill.width(); tileX += FILL_FRAME_SIZE) {
            int tileWidth = Math.min(FILL_FRAME_SIZE, fill.width() - tileX);
            graphics.blit(texture,
                    left + fill.x() + tileX, top + rowY,
                    0, textureV,
                    tileWidth, rowHeight,
                    FILL_TEXTURE_WIDTH, FILL_TEXTURE_HEIGHT);
        }
    }
}
