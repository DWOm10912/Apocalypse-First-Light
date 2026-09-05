package com.antaurora.apofirstlight.compat.jade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import snownee.jade.api.ui.Element;

public final class AflJadeFluidFillElement extends Element {
    private static final int SPRITE_SIZE = 16;

    private final FluidStack fluid;

    public AflJadeFluidFillElement(FluidStack fluid) {
        this.fluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    @Override
    public Vec2 getSize() {
        return new Vec2(SPRITE_SIZE, SPRITE_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float width, float height) {
        if (fluid.isEmpty()) {
            return;
        }

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

        int startX = Mth.floor(x);
        int startY = Mth.floor(y);
        int fillWidth = Math.max(0, Mth.ceil(width));
        int fillHeight = Math.max(0, Mth.ceil(height));
        if (fillWidth <= 0 || fillHeight <= 0) {
            return;
        }

        // Jade ProgressElement draws its native box first, then invokes this overlay
        // with only the filled inner dimensions. Flush that background before atlas blits.
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        int spriteY = startY + (fillHeight - SPRITE_SIZE) / 2;
        enableTransformedScissor(graphics, startX, startY,
                startX + fillWidth, startY + fillHeight);
        try {
            for (int tileX = startX; tileX < startX + fillWidth; tileX += SPRITE_SIZE) {
                graphics.blit(tileX, spriteY, 0, SPRITE_SIZE, SPRITE_SIZE,
                        sprite, red, green, blue, alpha);
            }
        } finally {
            graphics.disableScissor();
            graphics.setColor(1, 1, 1, 1);
        }
    }

    private static void enableTransformedScissor(GuiGraphics graphics,
                                                   int left, int top, int right, int bottom) {
        Matrix4f pose = graphics.pose().last().pose();
        Vector4f topLeft = new Vector4f(left, top, 0.0F, 1.0F).mul(pose);
        Vector4f bottomRight = new Vector4f(right, bottom, 0.0F, 1.0F).mul(pose);
        int screenLeft = Mth.floor(Math.min(topLeft.x(), bottomRight.x()));
        int screenTop = Mth.floor(Math.min(topLeft.y(), bottomRight.y()));
        int screenRight = Mth.ceil(Math.max(topLeft.x(), bottomRight.x()));
        int screenBottom = Mth.ceil(Math.max(topLeft.y(), bottomRight.y()));
        graphics.enableScissor(screenLeft, screenTop, screenRight, screenBottom);
    }
}
